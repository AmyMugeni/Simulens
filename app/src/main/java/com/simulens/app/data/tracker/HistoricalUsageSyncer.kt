package com.simulens.app.data.tracker

import android.annotation.SuppressLint
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.data.local.entity.DailyTrackingCoverageEntity
import com.simulens.app.data.repositories.MonitoredAppsRepository
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.rules.intervention.InterventionEngine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class RawSession(val pkg: String, val startMs: Long, val endMs: Long)

object HistoricalUsageSyncer {

    private const val TAG = "HistoricalUsageSyncer"

    // Up to 30 days retention from Android UsageStatsManager
    private const val MAX_EVENT_RETENTION_DAYS = 30

    // Matches the live tracker: it polls every 2 s and does no merging, so only
    // activity-to-activity transitions inside one app should be joined here.
    private const val MERGE_GAP_MS = 3_000L
    private const val MIN_SESSION_MS = 2_000L

    suspend fun sync(
        context: Context,
        database: SimulensDatabase,
        lookbackDays: Int = MAX_EVENT_RETENTION_DAYS
    ) {
        try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            if (usm == null) {
                Log.w(TAG, "UsageStatsManager is not available on this device.")
                return
            }

            val monitoredApps = MonitoredAppsRepository(context).monitoredApps.first()
            if (monitoredApps.isEmpty()) {
                Log.w(TAG, "No monitored apps configured.")
                return
            }

            val userProfile = UserProfileRepository(context).userProfileFlow.first()
            val timetableEntries = database.timetableDao().getAllEntries()
            val interventionEngine = InterventionEngine()

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val days = lookbackDays.coerceIn(1, MAX_EVENT_RETENTION_DAYS)
            val nowMs = System.currentTimeMillis()

            val windowStart = today.minusDays((days - 1).toLong())
                .atStartOfDay(zone).toInstant().toEpochMilli()

            // One query for the whole window, then split by end date (the same rule the
            // live tracker uses for BehavioralSession.timestamp).
            val sessions = reconstructSessions(usm, monitoredApps, windowStart, nowMs)
                .sortedBy { it.endMs }
            val sessionsByEndDate = sessions.groupBy {
                Instant.ofEpochMilli(it.endMs).atZone(zone).toLocalDate()
            }

            var previousEndMs: Long? = null

            for (dayOffset in (days - 1) downTo 0) {
                val date = today.minusDays(dayOffset.toLong())
                val startOfDay = date.atStartOfDay(zone).toInstant().toEpochMilli()
                val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
                val coverageEnd = if (dayOffset == 0) nowMs else endOfDay

                // Coverage row only for days the event log actually covers.
                if (database.dailyTrackingCoverageDao().getByDate(date.toString()) == null) {
                    database.dailyTrackingCoverageDao().upsert(
                        DailyTrackingCoverageEntity(
                            date = date.toString(),
                            firstObservedAtEpochMs = startOfDay,
                            lastSuccessfulPollAtEpochMs = coverageEnd,
                            coveredDurationMs = coverageEnd - startOfDay,
                            successfulPollCount = 0,
                            failedPollCount = 0,
                            assessed = true,
                            trackingReliable = true
                        )
                    )
                }

                // Never duplicate a day the live tracker (or an earlier sync) already wrote.
                val existing = database.behavioralSessionDao()
                    .getSessionCountBetween(startOfDay, endOfDay + 1L)
                if (existing > 0) {
                    previousEndMs = null
                    continue
                }

                val classBlocks = ClassScheduleMapper.blocksFor(date, timetableEntries)

                for (s in sessionsByEndDate[date].orEmpty()) {
                    val startLdt = LocalDateTime.ofInstant(Instant.ofEpochMilli(s.startMs), zone)
                    val startTime = startLdt.toLocalTime()

                    val inClass = interventionEngine.isInClass(startTime, classBlocks)
                    val minutesSinceWake =
                        ChronoUnit.MINUTES.between(userProfile.wakeUpTime, startTime).toInt()
                    val minutesUntilSleep =
                        ChronoUnit.MINUTES.between(startTime, userProfile.sleepTime).toInt()

                    val wakeWindowEnd =
                        userProfile.wakeUpTime.plusMinutes(userProfile.morningBufferMinutes)
                    val isMorning = isTimeBetween(startTime, userProfile.wakeUpTime, wakeWindowEnd)
                    val windDownStart =
                        userProfile.sleepTime.minusMinutes(userProfile.bedtimeBufferMinutes)
                    val isBedtime = isTimeBetween(startTime, windDownStart, userProfile.wakeUpTime)

                    // Real gap since the previous session ended (any monitored app).
                    val gapMins = previousEndMs
                        ?.let { ((s.startMs - it) / 60_000.0).coerceAtLeast(0.0) }
                        ?: 60.0

                    val session = BehavioralSession(
                        packageName = s.pkg,
                        timestamp = s.endMs, // session END, same as the live tracker
                        elapsedDuration = (s.endMs - s.startMs) / 60_000.0,
                        previousSessionGap = gapMins,
                        sessionsToday = 0,               // filled by insertSessionWithDailyTotals
                        dailySocialMediaDuration = 0.0,  // filled by insertSessionWithDailyTotals
                        timeOfDay = startLdt.hour,
                        dayOfWeek = startLdt.dayOfWeek.value,
                        scheduleConflict = if (inClass) 1 else 0,
                        minutesSinceWake = minutesSinceWake,
                        minutesUntilSleep = minutesUntilSleep,
                        morningSocialMedia = if (isMorning) 1 else 0,
                        bedtimeSocialMedia = if (isBedtime) 1 else 0,
                        baselineDurationRatio = 1.0,
                        baselineFrequencyRatio = 1.0,
                        label = 0 // left as-is for now; revisit before using the ML export
                    )

                    database.behavioralSessionDao().insertSessionWithDailyTotals(
                        session = session,
                        startOfDayTimestamp = startOfDay,
                        endOfDayTimestamp = endOfDay + 1L, // DAO uses an exclusive upper bound
                        baselineDailyMinutes = null,
                        baselineSessionsPerDay = null
                    )
                    previousEndMs = s.endMs
                }
            }
            Log.i(TAG, "Reconstructed ${sessions.size} sessions over the last $days days.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed historical usage sync", e)
        }
    }

    /**
     * Rebuilds individual foreground spans from the raw event log.
     * A span still open at the end of the window is dropped: the live tracker will record
     * it when it closes, and keeping it here would store the same visit twice.
     */
    @Suppress("DEPRECATION")
    internal fun reconstructSessions(
        usm: UsageStatsManager,
        monitored: Set<String>,
        fromMs: Long,
        toMs: Long
    ): List<RawSession> {
        val events = usm.queryEvents(fromMs, toMs)
        val event = UsageEvents.Event()
        val openSince = HashMap<String, Long>()
        val spans = ArrayList<RawSession>()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // Screen off, lock screen or shutdown closes everything still open.
            if (closesAllSessions(event.eventType)) {
                openSince.forEach { (pkg, start) -> spans += RawSession(pkg, start, event.timeStamp) }
                openSince.clear()
                continue
            }

            val pkg = event.packageName ?: continue
            if (pkg !in monitored) continue

            when (event.eventType) {
                UsageEvents.Event.MOVE_TO_FOREGROUND ->
                    openSince.putIfAbsent(pkg, event.timeStamp)

                UsageEvents.Event.MOVE_TO_BACKGROUND ->
                    openSince.remove(pkg)?.let { spans += RawSession(pkg, it, event.timeStamp) }
            }
        }

        return mergeAndFilter(spans)
    }

    @SuppressLint("InlinedApi")
    private fun closesAllSessions(type: Int): Boolean = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
                (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE ||
                        type == UsageEvents.Event.KEYGUARD_SHOWN) -> true
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                type == UsageEvents.Event.DEVICE_SHUTDOWN -> true
        else -> false
    }

    /** Pure and unit-testable: merges same-app spans with tiny gaps, drops very short ones. */
    internal fun mergeAndFilter(
        spans: List<RawSession>,
        mergeGapMs: Long = MERGE_GAP_MS,
        minSessionMs: Long = MIN_SESSION_MS
    ): List<RawSession> =
        spans.sortedWith(compareBy({ it.pkg }, { it.startMs }))
            .fold(mutableListOf<RawSession>()) { acc, s ->
                val last = acc.lastOrNull()
                if (last != null && last.pkg == s.pkg && s.startMs - last.endMs <= mergeGapMs) {
                    acc[acc.lastIndex] = last.copy(endMs = maxOf(last.endMs, s.endMs))
                } else {
                    acc += s
                }
                acc
            }
            .filter { it.endMs - it.startMs >= minSessionMs }

    private fun isTimeBetween(target: LocalTime, start: LocalTime, end: LocalTime): Boolean =
        if (start.isBefore(end)) !target.isBefore(start) && !target.isAfter(end)
        else !target.isBefore(start) || !target.isAfter(end)
}