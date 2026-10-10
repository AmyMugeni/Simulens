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
import androidx.core.app.NotificationCompat
import com.cognilens.app.data.local.CogniLensDatabase
import com.cognilens.app.data.local.entity.InterventionLogEntity
import com.cognilens.app.data.repositories.UserProfileRepository
import com.cognilens.app.domain.model.UserProfile
import com.cognilens.app.domain.rules.RuleEngine
import com.cognilens.app.domain.rules.RuleEvaluationResult
import com.cognilens.app.ml.models.ModelEvaluator
import kotlinx.coroutines.*
import java.time.LocalDateTime

class AppUsageTrackerService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private lateinit var usageStatsManager: UsageStatsManager
    private lateinit var ruleEngine: RuleEngine
    private lateinit var modelEvaluator: ModelEvaluator
    private lateinit var userProfileRepository: UserProfileRepository
    private lateinit var database: CogniLensDatabase

    // Live baseline state held in memory and updated via DataStore Flow
    private var cachedUserProfile = UserProfile()

    private var currentForegroundApp: String? = null
    private var sessionStartTime: Long = 0L
    private var lastClosedTime: Long = 0L

    // Default target social media apps to monitor
    private val monitoredApps = setOf(
        "com.instagram.android",
        "com.zhiliaoapp.musically", // TikTok
        "com.twitter.android",
        "com.snapchat.android"
    )

    override fun onCreate() {
        super.onCreate()
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
        val startTime = endTime - (POLL_INTERVAL_MS * 2)

        val events = usageStatsManager.queryEvents(startTime, endTime)
        val event = UsageEvents.Event()

        while (events.hasNextEvent()) {
            events.getNextEvent(event)

            // Detect App Launch
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                val packageName = event.packageName

                if (monitoredApps.contains(packageName) && packageName != currentForegroundApp) {
                    currentForegroundApp = packageName
                    val now = System.currentTimeMillis()

                    val reopenIntervalMins = if (lastClosedTime > 0) {
                        ((now - lastClosedTime) / 60000f)
                    } else {
                        60f
                    }

                    sessionStartTime = now
                    evaluateRealtimeSession(
                        packageName = packageName,
                        durationMins = 0f,
                        reopenIntervalMins = reopenIntervalMins
                    )
                }
            }

            // Detect App Close / Backgrounding
            if (event.eventType == UsageEvents.Event.ACTIVITY_PAUSED ||
                event.eventType == UsageEvents.Event.ACTIVITY_STOPPED
            ) {
                if (event.packageName == currentForegroundApp) {
                    currentForegroundApp = null
                    lastClosedTime = System.currentTimeMillis()
                }
            }
        }

        // Active In-Session Progress Monitor
        currentForegroundApp?.let { app ->
            val activeDurationMins = ((System.currentTimeMillis() - sessionStartTime) / 60000f)
            val reopenIntervalMins = if (lastClosedTime > 0) {
                ((sessionStartTime - lastClosedTime) / 60000f)
            } else {
                60f
            }

            evaluateRealtimeSession(
                packageName = app,
                durationMins = activeDurationMins,
                reopenIntervalMins = reopenIntervalMins
            )
        }
    }

    private fun evaluateRealtimeSession(
        packageName: String,
        durationMins: Float,
        reopenIntervalMins: Float
    ) {
        val currentTime = LocalDateTime.now()
        val userProfile = cachedUserProfile // Live UserProfile from DataStore

        // 1. HARD RULE CHECK (Zero-latency O(1) Circadian & Safety Net Guardrail)
        when (val ruleResult = ruleEngine.evaluate(userProfile, currentTime, durationMins, reopenIntervalMins)) {
            is RuleEvaluationResult.TriggerIntervention -> {
                triggerAndLogIntervention(
                    packageName = packageName,
                    reason = ruleResult.reason,
                    durationMins = durationMins,
                    reopenIntervalMins = reopenIntervalMins
                )
                return
            }

            is RuleEvaluationResult.PassToML -> {
                // 2. GRADIENT BOOSTING ML EVALUATION (Sub-millisecond Local ONNX Inference)
                val scheduleConflict = ruleEngine.isScheduleConflict(userProfile, currentTime)

                val isCompulsive = modelEvaluator.evaluateSession(
                    sessionDurationMins = durationMins,
                    reopenIntervalMins = reopenIntervalMins,
                    bsmasScore = userProfile.bsmasScore.toFloat(),
                    isScheduleConflict = scheduleConflict
                )

                if (isCompulsive) {
                    triggerAndLogIntervention(
                        packageName = packageName,
                        reason = "ML Model: Compulsive pattern detected.",
                        durationMins = durationMins,
                        reopenIntervalMins = reopenIntervalMins
                    )
                }
            }
        }
    }

    private fun triggerAndLogIntervention(
        packageName: String,
        reason: String,
        durationMins: Float,
        reopenIntervalMins: Float
    ) {
        println(" INTERVENTION TRIGGERED for $packageName. Reason: $reason")

        // Log intervention event to local Room Database
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

    private fun createNotification(): Notification {
        val channelId = "cognilens_tracker_channel"
        val channelName = "CogniLens Active Protection"

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_LOW
            )
            manager.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("CogniLens Active")
            .setContentText("Monitoring digital wellness in the background.")
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