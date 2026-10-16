package com.cognilens.app.data.tracker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.cognilens.app.data.local.CogniLensDatabase
import com.cognilens.app.data.local.entity.BehavioralSession
import com.cognilens.app.data.local.entity.InterventionLogEntity
import com.cognilens.app.data.repositories.UserProfileRepository
import com.cognilens.app.domain.model.UserProfile
import com.cognilens.app.domain.rules.RuleEngine
import com.cognilens.app.domain.rules.RuleEvaluationResult
import com.cognilens.app.ml.models.ModelEvaluator
import kotlinx.coroutines.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class AppUsageTrackerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var ruleEngine: RuleEngine
    private lateinit var modelEvaluator: ModelEvaluator
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var database: CogniLensDatabase

    private var cachedUserProfile = UserProfile()

    private var currentForegroundApp: String? = null
    private var sessionStartTime: Long = 0L
    private var currentReopenIntervalMins: Float = 60f

    // Track the last event timestamp we've seen.
    private var lastProcessedEventTime: Long = 0L

    // Tracks the last time ANY monitored app was closed to detect rapid switching/reopening.
    private var lastAnyMonitoredAppClosedTime: Long = 0L

    // Tracks when the currently active monitored app was paused or stopped (if any).
    private var lastPauseOrStopTimestamp: Long = 0L

    // Throttle intervention logs to prevent spam during ongoing sessions
    private var lastInterventionReason: String? = null
    private var lastInterventionTime: Long = 0L

    // UsageEvents queries overlap, so remember recently processed events.
    private val processedEventSignatures = LinkedHashSet<String>()

    private data class TrackedUsageEvent(
        val timestamp: Long,
        val eventType: Int,
        val packageName: String?,
        val className: String?
    )

    private val monitoredApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android",
        "com.snapchat.android",
        "com.facebook.katana"
    )

    override fun onCreate() {
        super.onCreate()
        Log.d("CogniLens", "Service: onCreate")
        usageStatsManager = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        ruleEngine = RuleEngine()
        modelEvaluator = ModelEvaluator(applicationContext)
        userProfileRepository = UserProfileRepository(applicationContext)
        database = CogniLensDatabase.getDatabase(applicationContext)

        // Observe DataStore updates in real time to update the local memory cache
        serviceScope.launch {
            userProfileRepository.userProfileFlow.collect { profile ->
                cachedUserProfile = profile
            }
        }

        startForeground(NOTIFICATION_ID, createNotification())
        startPollingLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("CogniLens", "Service: onStartCommand")
        return START_STICKY
    }

    private fun startPollingLoop() {
        serviceScope.launch {
            while (isActive) {
                checkForegroundApp()
                delay(POLL_INTERVAL_MS)
            }
        }
    }


    private fun checkForegroundApp() {
        val endTime = System.currentTimeMillis()

        // The overlap catches events with identical timestamps or events
        // that appeared near the boundary of the previous polling window.
        val startTime = if (lastProcessedEventTime > 0L) {
            (lastProcessedEventTime - 1_000L).coerceAtLeast(0L)
        } else {
            endTime - 10_000L
        }

        val usageEvents = try {
            usageStatsManager.queryEvents(startTime, endTime)
        } catch (e: Exception) {
            Log.e("CogniLens", "Unable to query usage events", e)
            return
        }

        val batch = mutableListOf<TrackedUsageEvent>()
        val event = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)

            batch.add(
                TrackedUsageEvent(
                    timestamp = event.timeStamp,
                    eventType = event.eventType,
                    packageName = event.packageName,
                    className = event.className
                )
            )
        }

        // Advance lastProcessedEventTime so empty queries don't keep looking back indefinitely.
        lastProcessedEventTime = maxOf(lastProcessedEventTime, endTime - 1_000L)

        // Process events in timestamp order.
        batch.sortBy { it.timestamp }

        for (usageEvent in batch) {
            val signature = buildString {
                append(usageEvent.timestamp)
                append('|')
                append(usageEvent.eventType)
                append('|')
                append(usageEvent.packageName)
                append('|')
                append(usageEvent.className)
            }

            // Skip events returned again by an overlapping query.
            if (!processedEventSignatures.add(signature)) {
                continue
            }

            lastProcessedEventTime = maxOf(
                lastProcessedEventTime,
                usageEvent.timestamp
            )

            when (usageEvent.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val packageName = usageEvent.packageName ?: continue

                    // A different foreground app marks the end of the previous monitored app's session.
                    if (packageName != currentForegroundApp) {
                        if (currentForegroundApp != null) {
                            val endTs = if (lastPauseOrStopTimestamp in 1..usageEvent.timestamp) {
                                lastPauseOrStopTimestamp
                            } else {
                                usageEvent.timestamp
                            }
                            closeCurrentSession(endTs)
                        }
                    }

                    // Start a session only if the foreground app is monitored and no monitored session is active.
                    if (packageName in monitoredApps) {
                        if (currentForegroundApp == null) {
                            // Use a global tracker for the gap to detect compulsive switching between social apps.
                            currentReopenIntervalMins = if (lastAnyMonitoredAppClosedTime > 0L) {
                                ((usageEvent.timestamp - lastAnyMonitoredAppClosedTime) / 60_000f).coerceAtLeast(0f)
                            } else {
                                60f
                            }

                            currentForegroundApp = packageName
                            sessionStartTime = usageEvent.timestamp
                            lastPauseOrStopTimestamp = 0L

                            Log.d("CogniLens", "Started session for: $packageName (Gap: $currentReopenIntervalMins min)")

                            evaluateRealtimeSession(
                                packageName,
                                0f,
                                currentReopenIntervalMins
                            )
                        } else if (currentForegroundApp == packageName) {
                            // Activity resumed again within the same app (internal transition): reset pause marker
                            lastPauseOrStopTimestamp = 0L
                        }
                    } else {
                        // Resumed app is NOT in monitoredApps (e.g., Launcher, Settings)
                        currentForegroundApp = null
                        lastPauseOrStopTimestamp = 0L
                    }
                }

                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val packageName = usageEvent.packageName ?: continue
                    if (packageName == currentForegroundApp) {
                        lastPauseOrStopTimestamp = usageEvent.timestamp
                    }
                }

                // Treat screen turning off or device locking as the end of active usage.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN -> {
                    if (currentForegroundApp != null) {
                        val endTs = if (lastPauseOrStopTimestamp in 1..usageEvent.timestamp) {
                            lastPauseOrStopTimestamp
                        } else {
                            usageEvent.timestamp
                        }
                        closeCurrentSession(endTs)
                    }
                }
            }
        }

        // --- Post-batch check for app exit (e.g. Home screen) without new RESUMED events ---
        currentForegroundApp?.let { packageName ->
            if (lastPauseOrStopTimestamp > 0L && (endTime - lastPauseOrStopTimestamp) >= 1_500L) {
                Log.d("CogniLens", "App $packageName paused/stopped for >1.5s, closing session.")
                closeCurrentSession(lastPauseOrStopTimestamp)
            } else if (lastPauseOrStopTimestamp == 0L) {
                // Ongoing Session Evaluation
                val ongoingDurationMins = (endTime - sessionStartTime) / 60_000f
                evaluateRealtimeSession(packageName, ongoingDurationMins, currentReopenIntervalMins)
            }
        }

        // Bound the deduplication cache so it cannot grow indefinitely.
        while (processedEventSignatures.size > 2_000) {
            val iterator = processedEventSignatures.iterator()
            if (iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
    }

    private fun closeCurrentSession(endTimestamp: Long) {
        val packageName = currentForegroundApp ?: return

        // Guard against invalid or out-of-order event timestamps.
        val safeEndTimestamp = maxOf(endTimestamp, sessionStartTime)
        val durationMins = (safeEndTimestamp - sessionStartTime) / 60_000f

        Log.d("CogniLens", "Closing session: $packageName, duration=$durationMins min")

        if (durationMins >= 0.016f) { // Only save sessions that lasted ~1 second or longer
            recordSession(
                packageName = packageName,
                durationMins = durationMins,
                reopenIntervalMins = currentReopenIntervalMins,
                sessionEndTime = safeEndTimestamp
            )
            lastAnyMonitoredAppClosedTime = safeEndTimestamp
        }

        // Clearing the active session prevents recording the same session twice.
        currentForegroundApp = null
        sessionStartTime = 0L
        currentReopenIntervalMins = 60f
        lastPauseOrStopTimestamp = 0L
        lastInterventionReason = null
        lastInterventionTime = 0L
    }

    private fun evaluateRealtimeSession(packageName: String, durationMins: Float, reopenIntervalMins: Float) {
        val currentTime = LocalDateTime.now()
        val userProfile = cachedUserProfile

        when (val ruleResult = ruleEngine.evaluate(userProfile, currentTime, durationMins, reopenIntervalMins)) {
            is RuleEvaluationResult.TriggerIntervention -> {
                triggerAndLogIntervention(packageName, ruleResult.reason, durationMins, reopenIntervalMins)
            }
            is RuleEvaluationResult.PassToML -> {
                val scheduleConflict = ruleEngine.isScheduleConflict(userProfile, currentTime)
                val isCompulsive = modelEvaluator.evaluateSession(
                    sessionDurationMins = durationMins,
                    reopenIntervalMins = reopenIntervalMins,
                    bsmasScore = userProfile.bsmasScore.toFloat(),
                    isScheduleConflict = scheduleConflict
                )
                if (isCompulsive) {
                    triggerAndLogIntervention(packageName, "ML Model: Compulsive pattern detected.", durationMins, reopenIntervalMins)
                }
            }
        }
    }

    private fun triggerAndLogIntervention(packageName: String, reason: String, durationMins: Float, reopenIntervalMins: Float) {
        val now = System.currentTimeMillis()
        if (reason == lastInterventionReason && (now - lastInterventionTime) < 30_000L) {
            return
        }
        lastInterventionReason = reason
        lastInterventionTime = now

        Log.i("CogniLens", "Triggering Intervention: $reason")
        serviceScope.launch {
            database.interventionLogDao().insertLog(
                InterventionLogEntity(
                    packageName = packageName,
                    triggerReason = reason,
                    sessionDurationMins = durationMins,
                    reopenIntervalMins = reopenIntervalMins
                )
            )
        }
    }

    private fun recordSession(
        packageName: String,
        durationMins: Float,
        reopenIntervalMins: Float,
        sessionEndTime: Long
    ) {
        if (durationMins < 0.016f) return 

        serviceScope.launch {
            try {
                val sessionTime = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(sessionEndTime),
                    ZoneId.systemDefault()
                )

                val zone = ZoneId.systemDefault()
                val sessionDate = sessionTime.toLocalDate()

                val startOfDay = sessionDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val endOfDay = sessionDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

                val userProfile = cachedUserProfile
                val scheduleConflict = ruleEngine.isScheduleConflict(userProfile, sessionTime)
                val wakeTime = userProfile.wakeUpTime
                val minutesSinceWake = ChronoUnit.MINUTES.between(wakeTime, sessionTime.toLocalTime()).toInt()
                val sleepTime = userProfile.sleepTime
                val minutesUntilSleep = ChronoUnit.MINUTES.between(sessionTime.toLocalTime(), sleepTime).toInt()

                val wakeWindowEnd = userProfile.wakeUpTime.plusMinutes(userProfile.morningBufferMinutes)
                val isMorning = isTimeBetween(sessionTime.toLocalTime(), userProfile.wakeUpTime, wakeWindowEnd)
                val windDownStart = userProfile.sleepTime.minusMinutes(userProfile.bedtimeBufferMinutes)
                val isBedtime = isOvernightWindow(sessionTime.toLocalTime(), windDownStart, userProfile.wakeUpTime)

                val isCompulsive = modelEvaluator.evaluateSession(
                    durationMins, reopenIntervalMins, userProfile.bsmasScore.toFloat(), scheduleConflict
                )

                val session = BehavioralSession(
                    packageName = packageName,
                    timestamp = sessionEndTime,
                    elapsedDuration = durationMins.toDouble(),
                    previousSessionGap = reopenIntervalMins.toDouble(),
                    sessionsToday = 0, // Handled by DAO's insertSessionWithDailyTotals
                    dailySocialMediaDuration = 0.0, // Handled by DAO's insertSessionWithDailyTotals
                    timeOfDay = sessionTime.hour,
                    dayOfWeek = sessionTime.dayOfWeek.value,
                    scheduleConflict = scheduleConflict.toInt(),
                    minutesSinceWake = minutesSinceWake,
                    minutesUntilSleep = minutesUntilSleep,
                    morningSocialMedia = if (isMorning) 1 else 0,
                    bedtimeSocialMedia = if (isBedtime) 1 else 0,
                    baselineDurationRatio = 1.0,
                    baselineFrequencyRatio = 1.0,
                    label = if (isCompulsive) 1 else 0
                )

                database.behavioralSessionDao().insertSessionWithDailyTotals(
                    session = session,
                    startOfDayTimestamp = startOfDay,
                    endOfDayTimestamp = endOfDay
                )

                Log.d("CogniLens", "Saved Session: $packageName. Duration: ${session.elapsedDuration} min")
            } catch (e: Exception) {
                Log.e("CogniLens", "Error recording session", e)
            }
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
        val channelId = "cognilens_tracker_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "CogniLens Protection", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CogniLens Protection Active")
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

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val POLL_INTERVAL_MS = 2000L
    }
}
