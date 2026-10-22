package com.simulens.app.data.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.repositories.MonitoredAppsRepository
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.model.UserProfile
import com.simulens.app.domain.rules.intervention.BaselineCalculator
import com.simulens.app.domain.rules.intervention.ClassBlock
import com.simulens.app.domain.rules.intervention.DailyTrackingRecord
import com.simulens.app.domain.rules.intervention.HistoricalSession
import com.simulens.app.domain.rules.intervention.Intervention
import com.simulens.app.domain.rules.intervention.InterventionEngine
import com.simulens.app.domain.rules.intervention.InterventionPolicy
import com.simulens.app.domain.rules.intervention.SessionContext
import com.simulens.app.domain.rules.intervention.SleepSchedule
import com.simulens.app.domain.rules.intervention.UsageSnapshot
import com.simulens.app.data.local.entity.DailyTrackingCoverageEntity
import kotlinx.coroutines.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap

class AppUsageTrackerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var monitoredAppsRepository: MonitoredAppsRepository
    private lateinit var database: SimulensDatabase
    private lateinit var notifier: InterventionNotifier

    private val interventionEngine = InterventionEngine()
    private val interventionPolicy = InterventionPolicy()

    private var cachedUserProfile = UserProfile()

    // Which apps to track: user-chosen, defaults until configured.
    @Volatile private var monitoredApps: Set<String> = MonitoredAppsRepository.DEFAULT_APPS

    // Baseline for "high deviation" rules, refreshed every minute and after each saved session.
    @Volatile private var usageSnapshot: UsageSnapshot = UsageSnapshot.EMPTY

    // Timetable -> today's class blocks (recomputed when the date or the timetable changes).
    @Volatile private var timetableEntries: List<TimetableEntry> = emptyList()
    @Volatile private var classesDate: LocalDate? = null
    @Volatile private var classesToday: List<ClassBlock> = emptyList()

    private val labelCache = ConcurrentHashMap<String, String>()

    private var currentForegroundApp: String? = null
    private var sessionStartTime: Long = 0L
    private var currentReopenIntervalMins: Float = 60f

    // Track the last event timestamp we've seen.
    private var lastProcessedEventTime: Long = 0L

    // Tracks the last time ANY monitored app was closed to detect rapid switching/reopening.
    private var lastAnyMonitoredAppClosedTime: Long = 0L

    // Tracks when the currently active monitored app was paused or stopped (if any).
    private var lastPauseOrStopTimestamp: Long = 0L

    // UsageEvents queries overlap, so remember recently processed events.
    private val processedEventSignatures = LinkedHashSet<String>()

    private data class TrackedUsageEvent(
        val timestamp: Long,
        val eventType: Int,
        val packageName: String?,
        val className: String?
    )

    override fun onCreate() {
        super.onCreate()
        Log.d("Simulens", "Service: onCreate")
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        userProfileRepository = UserProfileRepository(applicationContext)
        monitoredAppsRepository = MonitoredAppsRepository(applicationContext)
        database = SimulensDatabase.getDatabase(applicationContext)
        notifier = InterventionNotifier(applicationContext)

        serviceScope.launch {
            userProfileRepository.userProfileFlow.collect { profile -> cachedUserProfile = profile }
        }
        serviceScope.launch {
            monitoredAppsRepository.monitoredApps.collect { apps ->
                monitoredApps = apps
                refreshSnapshot()
            }
        }
        serviceScope.launch {
            database.timetableDao().getTimetableFlow().collect { entries ->
                timetableEntries = entries
                classesDate = null
            }
        }
        serviceScope.launch {
            while (isActive) {
                refreshSnapshot()
                delay(SNAPSHOT_REFRESH_MS)
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())

        Log.i("SimulensTracker", "AppUsageTrackerService started! Initializing polling loop...")

        // 1. Always start background polling loop
        startPollingLoop()

        // 2. Initialize tracking coverage in background
        serviceScope.launch {
            try {
                initializeCoverage()
            } catch (e: Exception) {
                Log.e("SimulensTracker", "Error initializing tracking coverage", e)
            }
        }

        // 3. Sync initial system usage stats if Room DB is empty
        serviceScope.launch {
            try {
                syncSystemUsageStatsIfEmpty()
            } catch (e: Exception) {
                Log.e("SimulensTracker", "Error syncing system usage stats", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("Simulens", "Service: onStartCommand")
        return START_STICKY
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            while (isActive) {
                try {
                    checkForegroundApp()
                } catch (e: Exception) {
                    Log.e("Simulens", "Polling iteration failed", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun isIgnoredSystemPackage(pkg: String): Boolean {
        return pkg == "android" ||
                pkg == "com.android.systemui" ||
                pkg == "com.android.keyguard" ||
                pkg == "com.google.android.gms" ||
                pkg.contains("inputmethod") ||
                pkg.contains("keyboard") ||
                pkg.contains("swiftkey") ||
                pkg.contains("honeyboard")
    }

    private fun detectForegroundPackage(startTime: Long, endTime: Long): Pair<String?, Long>? {
        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.e("SimulensTracker", "Unable to query events from $startTime to $endTime", e)
            return null
        }

        var lastResumedPkg: String? = null
        var lastResumedTime = 0L
        var lastPausedTime = 0L

        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val pkg = event.packageName ?: continue
            if (isIgnoredSystemPackage(pkg)) continue

            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    lastResumedPkg = pkg
                    lastResumedTime = event.timeStamp
                    lastPausedTime = 0L
                }
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    if (pkg == lastResumedPkg) {
                        lastPausedTime = event.timeStamp
                    }
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    lastResumedPkg = null
                    lastResumedTime = 0L
                    lastPausedTime = 0L
                }
            }
        }

        // If the last resumed package was paused/stopped more than 2 seconds ago, it is no longer active
        if (lastPausedTime > 0L && (endTime - lastPausedTime) >= 2_000L) {
            return null
        }

        if (lastResumedPkg == null) return null
        return Pair(lastResumedPkg, lastResumedTime)
    }

    private suspend fun checkForegroundApp() {
        val hasPermission = UsageAccess.isGranted(applicationContext)
        if (!hasPermission) {
            Log.w("SimulensTracker", "Usage Access Permission is NOT granted! Grant permission in Android Settings.")
            return
        }

        val endTime = System.currentTimeMillis()
        recordSuccessfulPoll(endTime)
        flushCoverageIfDue(endTime)

        val zone = ZoneId.systemDefault()
        val startOfDay = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val queryStart = minOf(startOfDay, endTime - 12 * 3600_000L)

        val activeResult = detectForegroundPackage(queryStart, endTime)
        val activePkg = activeResult?.first
        val activeStartTs = activeResult?.second ?: endTime

        val current = currentForegroundApp

        if (current == null) {
            if (activePkg != null && activePkg in monitoredApps) {
                currentReopenIntervalMins = if (lastAnyMonitoredAppClosedTime > 0L) {
                    ((endTime - lastAnyMonitoredAppClosedTime) / 60_000f).coerceAtLeast(0f)
                } else {
                    60f
                }
                currentForegroundApp = activePkg
                sessionStartTime = if (activeStartTs in (startOfDay..endTime)) activeStartTs else endTime
                lastPauseOrStopTimestamp = 0L

                Log.i("SimulensTracker", ">>> DETECTED ACTIVE MONITORED APP: $activePkg (Start: $sessionStartTime) <<<")
                evaluateRealtimeSession(activePkg, 0f, currentReopenIntervalMins)
            }
        } else {
            // A monitored app session is currently active
            if (activePkg == current) {
                val ongoingMins = ((endTime - sessionStartTime) / 60_000f).coerceAtLeast(0f)
                Log.i("SimulensTracker", "ONGOING MONITORED SESSION: $current (${String.format("%.2f", ongoingMins)} min)")
                evaluateRealtimeSession(current, ongoingMins, currentReopenIntervalMins)
            } else if (activePkg != null && activePkg in monitoredApps) {
                Log.i("SimulensTracker", "SWITCHED MONITORED APP: $current -> $activePkg")
                closeCurrentSession(endTime)

                currentReopenIntervalMins = if (lastAnyMonitoredAppClosedTime > 0L) {
                    ((endTime - lastAnyMonitoredAppClosedTime) / 60_000f).coerceAtLeast(0f)
                } else {
                    60f
                }
                currentForegroundApp = activePkg
                sessionStartTime = if (activeStartTs in (startOfDay..endTime)) activeStartTs else endTime
                lastPauseOrStopTimestamp = 0L

                evaluateRealtimeSession(activePkg, 0f, currentReopenIntervalMins)
            } else {
                Log.i("SimulensTracker", "EXITED MONITORED APP: $current -> ${activePkg ?: "None/Home"}")
                closeCurrentSession(endTime)
            }
        }
    }

    private fun closeCurrentSession(endTimestamp: Long) {
        val packageName = currentForegroundApp ?: return

        val safeEndTimestamp = maxOf(endTimestamp, sessionStartTime)
        val durationMins = (safeEndTimestamp - sessionStartTime) / 60_000f

        Log.i("SimulensTracker", ">>> SAVING SESSION TO DATABASE: $packageName, duration=$durationMins min ($safeEndTimestamp - $sessionStartTime) <<<")

        if (durationMins >= MIN_SESSION_MINS) {
            recordSession(
                packageName = packageName,
                durationMins = durationMins,
                reopenIntervalMins = currentReopenIntervalMins,
                sessionEndTime = safeEndTimestamp
            )
            lastAnyMonitoredAppClosedTime = safeEndTimestamp
        } else {
            Log.w("SimulensTracker", "Session for $packageName too short ($durationMins min < $MIN_SESSION_MINS min), skipped")
        }

        currentForegroundApp = null
        sessionStartTime = 0L
        currentReopenIntervalMins = 60f
        lastPauseOrStopTimestamp = 0L
    }

    // ---------------------------------------------------------------------------------------------
    // Interventions: schedule conflict, sleep window / wind-down / morning, baseline deviation
    // ---------------------------------------------------------------------------------------------

    private fun evaluateRealtimeSession(packageName: String, durationMins: Float, reopenIntervalMins: Float) {
        try {
            if (InterventionActionReceiver.isSnoozed(applicationContext)) {
                Log.d("Simulens", "Skipping intervention evaluation: Interventions currently snoozed")
                return
            }

            val now = LocalDateTime.now()
            val snapshot = usageSnapshot

            val session = SessionContext(
                packageName = packageName,
                appLabel = labelFor(packageName),
                sessionMins = durationMins.toDouble(),
                todayMinutes = snapshot.todayCompletedMins + durationMins,
                now = now
            )

            val intervention = interventionEngine.evaluate(
                session = session,
                sleep = sleepScheduleOf(cachedUserProfile),
                classes = classesFor(now.toLocalDate()),
                snapshot = snapshot
            ) ?: return

            if (!interventionPolicy.tryFire(intervention, System.currentTimeMillis(), now.toLocalDate())) return

            deliverIntervention(packageName, intervention, durationMins, reopenIntervalMins)
        } catch (e: Exception) {
            Log.e("Simulens", "Intervention evaluation failed", e)
        }
    }

    private fun deliverIntervention(
        packageName: String,
        intervention: Intervention,
        durationMins: Float,
        reopenIntervalMins: Float
    ) {
        Log.i("Simulens", "Triggering intervention: ${intervention.reason}")
        notifier.show(intervention, packageName)

        serviceScope.launch {
            try {
                database.interventionLogDao().insertLog(
                    InterventionLogEntity(
                        packageName = packageName,
                        triggerReason = intervention.reason,
                        sessionDurationMins = durationMins,
                        reopenIntervalMins = reopenIntervalMins
                    )
                )
            } catch (e: Exception) {
                Log.e("Simulens", "Failed to log intervention", e)
            }
        }
    }

    private fun sleepScheduleOf(profile: UserProfile) = SleepSchedule(
        wake = profile.wakeUpTime,
        sleep = profile.sleepTime,
        windDownMinutes = profile.bedtimeBufferMinutes.toLong(),
        morningMinutes = profile.morningBufferMinutes.toLong()
    )

    private fun classesFor(date: LocalDate): List<ClassBlock> {
        if (classesDate != date) {
            classesToday = ClassScheduleMapper.blocksFor(date, timetableEntries)
            classesDate = date
        }
        return classesToday
    }

    /** Rebuilds the user's baseline and today's completed total from saved sessions. */
    private suspend fun refreshSnapshot() {
        try {
            val now = LocalDateTime.now()
            val zone = ZoneId.systemDefault()
            val startDate = now.toLocalDate().minusDays(29)
            val since = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
            val monitored = monitoredApps

            val history = database.behavioralSessionDao().getSessionsSince(since)
                .filter { it.packageName in monitored }
                .map { HistoricalSession(endEpochMs = it.timestamp, durationMins = it.elapsedDuration) }

            val records = database.dailyTrackingCoverageDao()
                .getBetweenDates(startDate.toString(), now.toLocalDate().toString())
                .map { DailyTrackingRecord(date = LocalDate.parse(it.date), trackingReliable = it.trackingReliable) }

            usageSnapshot = BaselineCalculator.compute(
                history = history,
                trackingRecords = records,
                now = now,
                zone = zone
            )
        } catch (e: Exception) {
            Log.e("Simulens", "Failed to refresh usage snapshot", e)
        }
    }

    private fun labelFor(packageName: String): String = labelCache.getOrPut(packageName) {
        try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar { it.titlecase() }.ifBlank { "This app" }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Persistence
    // ---------------------------------------------------------------------------------------------

    private fun recordSession(
        packageName: String,
        durationMins: Float,
        reopenIntervalMins: Float,
        sessionEndTime: Long
    ) {
        if (durationMins < MIN_SESSION_MINS) return

        serviceScope.launch {
            try {
                val zone = ZoneId.systemDefault()
                val sessionTime = LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(sessionEndTime), zone)
                val sessionDate = sessionTime.toLocalDate()

                val startOfDay = sessionDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val endOfDay = sessionDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()


                val baselineStartDate = sessionDate.minusDays(28)

                val baselineStartTimestamp = baselineStartDate
                    .atStartOfDay(zone)
                    .toInstant()
                    .toEpochMilli()

                val historicalSessions = database
                    .behavioralSessionDao()
                    .getSessionsSince(baselineStartTimestamp)
                    .map {
                        HistoricalSession(
                            endEpochMs = it.timestamp,
                            durationMins = it.elapsedDuration
                        )
                    }

                val coverageRecords = database
                    .dailyTrackingCoverageDao()
                    .getBetweenDates(
                        startDate = baselineStartDate.toString(),
                        endDate = sessionDate.minusDays(1).toString()
                    )
                    .map {
                        DailyTrackingRecord(
                            date = LocalDate.parse(it.date),
                            trackingReliable = it.trackingReliable
                        )
                    }

                val usageSnapshot = BaselineCalculator.compute(
                    history = historicalSessions,
                    trackingRecords = coverageRecords,
                    now = sessionTime,
                    zone = zone,
                    lookbackDays = 28
                )

                val baseline = usageSnapshot.baseline

                val baselineDailyMinutes =
                    baseline.medianDailyMinutes?.takeIf {
                        baseline.isReliable && it > 0.0
                    }

                val baselineSessionsPerDay =
                    baseline.medianSessionsPerDay?.takeIf {
                        baseline.isReliable && it > 0.0
                    }


                val userProfile = cachedUserProfile
                val scheduleConflict = interventionEngine.isInClass(sessionTime.toLocalTime(), classesFor(sessionDate))
                val minutesSinceWake = ChronoUnit.MINUTES.between(userProfile.wakeUpTime, sessionTime.toLocalTime()).toInt()
                val minutesUntilSleep = ChronoUnit.MINUTES.between(sessionTime.toLocalTime(), userProfile.sleepTime).toInt()

                val wakeWindowEnd = userProfile.wakeUpTime.plusMinutes(userProfile.morningBufferMinutes)
                val isMorning = isTimeBetween(sessionTime.toLocalTime(), userProfile.wakeUpTime, wakeWindowEnd)
                val windDownStart = userProfile.sleepTime.minusMinutes(userProfile.bedtimeBufferMinutes)
                val isBedtime = isOvernightWindow(sessionTime.toLocalTime(), windDownStart, userProfile.wakeUpTime)

                val session = BehavioralSession(
                    packageName = packageName,
                    timestamp = sessionEndTime,
                    elapsedDuration = durationMins.toDouble(),
                    previousSessionGap = reopenIntervalMins.toDouble(),
                    sessionsToday = 0, // Handled by DAO's insertSessionWithDailyTotals
                    dailySocialMediaDuration = 0.0, // Handled by DAO's insertSessionWithDailyTotals
                    timeOfDay = sessionTime.hour,
                    dayOfWeek = sessionTime.dayOfWeek.value,
                    scheduleConflict = if (scheduleConflict) 1 else 0,
                    minutesSinceWake = minutesSinceWake,
                    minutesUntilSleep = minutesUntilSleep,
                    morningSocialMedia = if (isMorning) 1 else 0,
                    bedtimeSocialMedia = if (isBedtime) 1 else 0,
                    baselineDurationRatio = 1.0,
                    baselineFrequencyRatio = 1.0,
                    label = 0 // unused now that interventions are rule-based
                )


                database.behavioralSessionDao().insertSessionWithDailyTotals(
                    session = session,
                    startOfDayTimestamp = startOfDay,
                    endOfDayTimestamp = endOfDay,
                    baselineDailyMinutes = baselineDailyMinutes,
                    baselineSessionsPerDay = baselineSessionsPerDay
                )


                Log.d("Simulens", "Saved Session: $packageName. Duration: ${session.elapsedDuration} min")
                refreshSnapshot()
            } catch (e: Exception) {
                Log.e("Simulens", "Error recording session", e)
            }
        }
    }

    private suspend fun syncSystemUsageStatsIfEmpty() {
        try {
            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
            val endOfDay = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1L
            val nowMs = System.currentTimeMillis()

            val existingTodayCount = database.behavioralSessionDao().getSessionCountBetween(startOfDay, endOfDay)
            if (existingTodayCount == 0) {
                val usageStatsList = usageStatsManager.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY,
                    startOfDay,
                    nowMs
                )
                if (!usageStatsList.isNullOrEmpty()) {
                    for (stats in usageStatsList) {
                        val pkg = stats.packageName ?: continue
                        if (pkg in monitoredApps && stats.totalTimeInForeground > 1000L) {
                            val durationMins = (stats.totalTimeInForeground / 60_000.0)
                            if (durationMins > 0.01) {
                                val sessionTime = if (stats.lastTimeUsed in startOfDay..nowMs) stats.lastTimeUsed else nowMs
                                val localDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(sessionTime), zone)

                                val userProfile = cachedUserProfile
                                val scheduleConflict = interventionEngine.isInClass(localDateTime.toLocalTime(), classesFor(today))
                                val minutesSinceWake = ChronoUnit.MINUTES.between(userProfile.wakeUpTime, localDateTime.toLocalTime()).toInt()
                                val minutesUntilSleep = ChronoUnit.MINUTES.between(localDateTime.toLocalTime(), userProfile.sleepTime).toInt()

                                val wakeWindowEnd = userProfile.wakeUpTime.plusMinutes(userProfile.morningBufferMinutes)
                                val isMorning = isTimeBetween(localDateTime.toLocalTime(), userProfile.wakeUpTime, wakeWindowEnd)
                                val windDownStart = userProfile.sleepTime.minusMinutes(userProfile.bedtimeBufferMinutes)
                                val isBedtime = isOvernightWindow(localDateTime.toLocalTime(), windDownStart, userProfile.wakeUpTime)

                                val session = BehavioralSession(
                                    packageName = pkg,
                                    timestamp = sessionTime,
                                    elapsedDuration = durationMins,
                                    previousSessionGap = 60.0,
                                    sessionsToday = 0,
                                    dailySocialMediaDuration = 0.0,
                                    timeOfDay = localDateTime.hour,
                                    dayOfWeek = localDateTime.dayOfWeek.value,
                                    scheduleConflict = if (scheduleConflict) 1 else 0,
                                    minutesSinceWake = minutesSinceWake,
                                    minutesUntilSleep = minutesUntilSleep,
                                    morningSocialMedia = if (isMorning) 1 else 0,
                                    bedtimeSocialMedia = if (isBedtime) 1 else 0,
                                    baselineDurationRatio = 1.0,
                                    baselineFrequencyRatio = 1.0,
                                    label = 0
                                )
                                database.behavioralSessionDao().insertSessionWithDailyTotals(
                                    session = session,
                                    startOfDayTimestamp = startOfDay,
                                    endOfDayTimestamp = endOfDay,
                                    baselineDailyMinutes = null,
                                    baselineSessionsPerDay = null
                                )
                            }
                        }
                    }
                    Log.d("Simulens", "Synced initial UsageStats from system for today.")
                    refreshSnapshot()
                }
            }
        } catch (e: Exception) {
            Log.e("Simulens", "Error syncing system usage stats", e)
        }
    }

    private fun isTimeBetween(target: LocalTime, start: LocalTime, end: LocalTime): Boolean {
        return if (start.isBefore(end)) !target.isBefore(start) && !target.isAfter(end)
        else !target.isBefore(start) || !target.isAfter(end)
    }

    private fun isOvernightWindow(target: LocalTime, sleepStart: LocalTime, wakeTime: LocalTime): Boolean {
        return if (sleepStart.isAfter(wakeTime)) target.isAfter(sleepStart) || target.isBefore(wakeTime)
        else target.isAfter(sleepStart) && target.isBefore(wakeTime)
    }

    private fun createNotification(): Notification {
        val channelId = "simulens_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Simulens Protection", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Simulens Protection Active")
            .setContentText("Helping you stay focused and intentional.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // Daily tracking coverage. These collections are accessed by the polling coroutine.
    private val coverageRecords =
        mutableMapOf<String, DailyTrackingCoverageEntity>()

    private val dirtyCoverageDates = mutableSetOf<String>()

    private var lastSuccessfulCoveragePollAt: Long? = null
    private var lastCoverageFlushAt = 0L

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 2_000L
        private const val SNAPSHOT_REFRESH_MS = 60_000L
        private const val MIN_SESSION_MINS = 0.001f

        private const val MAX_COVERABLE_GAP_MS = 5_000L
        private const val COVERAGE_FLUSH_INTERVAL_MS = 30_000L
        private const val MIN_RELIABLE_COVERAGE = 0.30
        private const val MAX_RELIABLE_FAILURE_RATE = 0.30
    }

    private suspend fun initializeCoverage() {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        val records = database.dailyTrackingCoverageDao().getBetweenDates(
            startDate = today.minusDays(29).toString(),
            endDate = today.toString()
        )

        coverageRecords.clear()
        dirtyCoverageDates.clear()

        records.forEach { record ->
            coverageRecords[record.date] = record
        }

        // Assess previously completed days after loading their saved coverage.
        assessCompletedCoverageDays(today)

        flushCoverage()
    }

    private fun recordSuccessfulPoll(nowMs: Long) {
        val previousMs = lastSuccessfulCoveragePollAt

        if (previousMs != null &&
            nowMs > previousMs &&
            nowMs - previousMs <= MAX_COVERABLE_GAP_MS
        ) {
            addCoveredInterval(previousMs, nowMs)
        }

        val date = Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        val existing = coverageRecords[date]

        coverageRecords[date] = if (existing == null) {
            DailyTrackingCoverageEntity(
                date = date,
                firstObservedAtEpochMs = nowMs,
                lastSuccessfulPollAtEpochMs = nowMs,
                coveredDurationMs = 0L,
                successfulPollCount = 1,
                failedPollCount = 0
            )
        } else {
            existing.copy(
                firstObservedAtEpochMs =
                    minOf(existing.firstObservedAtEpochMs, nowMs),
                lastSuccessfulPollAtEpochMs = nowMs,
                successfulPollCount = existing.successfulPollCount + 1
            )
        }

        dirtyCoverageDates.add(date)
        lastSuccessfulCoveragePollAt = nowMs
    }

    private fun recordFailedPoll(nowMs: Long) {
        val date = Instant.ofEpochMilli(nowMs)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .toString()

        val existing = coverageRecords[date]

        coverageRecords[date] = if (existing == null) {
            DailyTrackingCoverageEntity(
                date = date,
                firstObservedAtEpochMs = nowMs,
                lastSuccessfulPollAtEpochMs = 0L,
                coveredDurationMs = 0L,
                successfulPollCount = 0,
                failedPollCount = 1
            )
        } else {
            existing.copy(
                failedPollCount = existing.failedPollCount + 1
            )
        }

        dirtyCoverageDates.add(date)

        // Never count a failed query or the gap after it as covered time.
        lastSuccessfulCoveragePollAt = null
    }

    private fun addCoveredInterval(startMs: Long, endMs: Long) {
        val zone = ZoneId.systemDefault()
        var cursor = startMs

        while (cursor < endMs) {
            val date = Instant.ofEpochMilli(cursor)
                .atZone(zone)
                .toLocalDate()

            val dateKey = date.toString()
            val nextMidnight = date.plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()

            val segmentEnd = minOf(endMs, nextMidnight)
            val segmentDuration = segmentEnd - cursor

            if (segmentDuration > 0L) {
                val existing = coverageRecords[dateKey]

                coverageRecords[dateKey] = if (existing == null) {
                    DailyTrackingCoverageEntity(
                        date = dateKey,
                        firstObservedAtEpochMs = cursor,
                        lastSuccessfulPollAtEpochMs = startMs,
                        coveredDurationMs = segmentDuration,
                        successfulPollCount = 0,
                        failedPollCount = 0
                    )
                } else {
                    existing.copy(
                        coveredDurationMs =
                            existing.coveredDurationMs + segmentDuration,
                        firstObservedAtEpochMs =
                            minOf(existing.firstObservedAtEpochMs, cursor)
                    )
                }

                dirtyCoverageDates.add(dateKey)
            }

            cursor = segmentEnd
        }
    }

    private suspend fun assessCompletedCoverageDays(today: LocalDate) {
        val zone = ZoneId.systemDefault()
        val dao = database.dailyTrackingCoverageDao()

        for ((dateKey, record) in coverageRecords.toMap()) {
            val date = LocalDate.parse(dateKey)

            // Today's coverage is incomplete, so don't assess it yet.
            if (!date.isBefore(today) || record.assessed) continue

            val dayStart = date.atStartOfDay(zone)
                .toInstant().toEpochMilli()

            val nextDayStart = date.plusDays(1)
                .atStartOfDay(zone)
                .toInstant().toEpochMilli()

            val dayDurationMs = nextDayStart - dayStart

            val coverageRatio = if (dayDurationMs > 0L) {
                record.coveredDurationMs.toDouble() / dayDurationMs
            } else {
                0.0
            }

            val totalPolls =
                record.successfulPollCount + record.failedPollCount

            val failureRate = if (totalPolls > 0) {
                record.failedPollCount.toDouble() / totalPolls
            } else {
                1.0
            }

            val reliable =
                coverageRatio >= MIN_RELIABLE_COVERAGE &&
                        failureRate <= MAX_RELIABLE_FAILURE_RATE

            dao.markAssessed(
                date = dateKey,
                reliable = reliable
            )

            coverageRecords[dateKey] = record.copy(
                assessed = true,
                trackingReliable = reliable
            )
        }
    }

    private suspend fun flushCoverage() {
        val dao = database.dailyTrackingCoverageDao()
        val datesToFlush = dirtyCoverageDates.toList()

        for (date in datesToFlush) {
            val record = coverageRecords[date] ?: continue

            dao.upsert(record)
            dirtyCoverageDates.remove(date)
        }

        lastCoverageFlushAt = System.currentTimeMillis()
    }

    private suspend fun flushCoverageIfDue(nowMs: Long) {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(nowMs)
            .atZone(zone)
            .toLocalDate()

        assessCompletedCoverageDays(today)

        if (nowMs - lastCoverageFlushAt >= COVERAGE_FLUSH_INTERVAL_MS) {
            flushCoverage()
        }
    }

}