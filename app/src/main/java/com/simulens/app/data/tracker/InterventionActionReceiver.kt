package com.simulens.app.data.tracker

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.simulens.app.MainActivity
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.InterventionLogEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InterventionActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_SNOOZE = "com.simulens.app.ACTION_SNOOZE"
        const val ACTION_DISMISS = "com.simulens.app.ACTION_DISMISS"
        const val ACTION_LEARN_MORE = "com.simulens.app.ACTION_LEARN_MORE"

        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val EXTRA_OPEN_CHATBOT = "open_chatbot"

        const val EXTRA_LOG_ID = "log_id"

        const val SNOOZE_MINUTES = 15L
        const val BREAK_MINUTES_DEFAULT = 15L
        private const val SNOOZE_PREFS = "simulens_snooze_prefs"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until_ms"
        private const val KEY_BREAK_UNTIL_PREFIX = "break_until_"
        private const val KEY_CONSECUTIVE_DISMISSALS = "consecutive_dismissals"

        fun snooze(context: Context, mins: Long = SNOOZE_MINUTES) {
            context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit()
                .putLong(KEY_SNOOZED_UNTIL, System.currentTimeMillis() + mins * 60_000L)
                .apply()
        }

        fun getSnoozedUntilMs(context: Context): Long {
            val prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
        }

        fun isSnoozed(context: Context): Boolean {
            return System.currentTimeMillis() < getSnoozedUntilMs(context)
        }

        fun startBreak(context: Context, packageName: String, mins: Long = BREAK_MINUTES_DEFAULT) {
            context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit()
                .putLong("$KEY_BREAK_UNTIL_PREFIX$packageName", System.currentTimeMillis() + mins * 60_000L)
                .putLong("${KEY_BREAK_UNTIL_PREFIX}${packageName}_duration", mins)
                .apply()
        }

        fun getRemainingBreakMins(context: Context, packageName: String): Long {
            val prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
            val breakUntil = prefs.getLong("$KEY_BREAK_UNTIL_PREFIX$packageName", 0L)
            val diff = breakUntil - System.currentTimeMillis()
            return if (diff > 0) (diff / 60_000L) + 1 else 0L
        }

        fun getOriginalBreakDurationMins(context: Context, packageName: String): Long {
            val prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
            return prefs.getLong("${KEY_BREAK_UNTIL_PREFIX}${packageName}_duration", BREAK_MINUTES_DEFAULT)
        }

        fun isBreakActive(context: Context, packageName: String): Boolean {
            return getRemainingBreakMins(context, packageName) > 0L
        }

        fun recordDismissal(context: Context): Int {
            val prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
            val current = prefs.getInt(KEY_CONSECUTIVE_DISMISSALS, 0) + 1
            prefs.edit().putInt(KEY_CONSECUTIVE_DISMISSALS, current).apply()
            return current
        }

        fun resetDismissals(context: Context) {
            context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE).edit()
                .putInt(KEY_CONSECUTIVE_DISMISSALS, 0)
                .apply()
        }
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return
        val action = intent.action ?: return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 2001)
        val sourcePackage = intent.getStringExtra(InterventionExtras.SOURCE_PACKAGE) ?: "com.simulens.app"
        val title = intent.getStringExtra(InterventionExtras.TITLE) ?: "Nudge"
        val message = intent.getStringExtra(InterventionExtras.MESSAGE) ?: ""

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(notificationId)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = SimulensDatabase.getDatabase(context.applicationContext).interventionLogDao()

                when (action) {
                    ACTION_SNOOZE -> {
                        snooze(context)
                        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
                        if (logId > 0) {
                            dao.setDismissed(logId, true)
                        } else {
                            dao.insertLog(
                                InterventionLogEntity(
                                    packageName = sourcePackage,
                                    triggerReason = "SNOOZED (15m): $title - $message",
                                    sessionDurationMins = 0f,
                                    reopenIntervalMins = 0f,
                                    wasDismissed = true
                                )
                            )
                        }
                    }

                    ACTION_DISMISS -> {
                        val logId = intent.getLongExtra(EXTRA_LOG_ID, -1L)
                        if (logId > 0) {
                            dao.setDismissed(logId, true)
                        } else {
                            dao.insertLog(
                                InterventionLogEntity(
                                    packageName = sourcePackage,
                                    triggerReason = "DISMISSED: $title - $message",
                                    sessionDurationMins = 0f,
                                    reopenIntervalMins = 0f,
                                    wasDismissed = true
                                )
                            )
                        }
                    }

                    ACTION_LEARN_MORE -> {
                        val mainIntent = Intent(context, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            putExtra(EXTRA_OPEN_CHATBOT, true)
                            putExtra(InterventionExtras.TITLE, title)
                            putExtra(InterventionExtras.MESSAGE, message)
                            putExtra(InterventionExtras.SOURCE_PACKAGE, sourcePackage)
                        }
                        context.startActivity(mainIntent)
                    }
                }
            } catch (e: Exception) {
                Log.e("Simulens", "Failed processing intervention action: $action", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
