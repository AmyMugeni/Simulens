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

        private const val SNOOZE_PREFS = "simulens_snooze_prefs"
        private const val KEY_SNOOZED_UNTIL = "snoozed_until_ms"

        fun getSnoozedUntilMs(context: Context): Long {
            val prefs = context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
            return prefs.getLong(KEY_SNOOZED_UNTIL, 0L)
        }

        fun isSnoozed(context: Context): Boolean {
            return System.currentTimeMillis() < getSnoozedUntilMs(context)
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
                        val snoozeDurationMins = 15L
                        val snoozedUntil = System.currentTimeMillis() + (snoozeDurationMins * 60_000L)
                        context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
                            .edit()
                            .putLong(KEY_SNOOZED_UNTIL, snoozedUntil)
                            .apply()

                        Log.d("Simulens", "Interventions snoozed for 15 minutes until $snoozedUntil")

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

                    ACTION_DISMISS -> {
                        Log.d("Simulens", "Intervention notification dismissed by user")
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
