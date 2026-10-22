package com.simulens.app.data.tracker

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.simulens.app.domain.rules.intervention.Intervention

/** Extras put on the intent that opens Simulens, so a future intervention screen or chatbot can read them. */
object InterventionExtras {
    const val TYPE = "simulens.intervention.type"
    const val TITLE = "simulens.intervention.title"
    const val MESSAGE = "simulens.intervention.message"
    const val EVIDENCE_TAG = "simulens.intervention.evidence_tag"
    const val SOURCE_PACKAGE = "simulens.intervention.source_package"
}

/**
 * Presents an intervention as a high-priority heads-up notification with
 * interactive actions: Accept ("Go Home"), Snooze (15m), Dismiss, and "Learn More" (RAG Chatbot link).
 */
class InterventionNotifier(private val context: Context) {

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Simulens nudges", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gentle reminders and real-time focus boundary alerts"
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    @SuppressLint("MissingPermission")
    fun show(intervention: Intervention, sourcePackage: String) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return

        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

        // Default Intent when tapping notification body
        val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(InterventionExtras.TYPE, intervention.type.name)
            putExtra(InterventionExtras.TITLE, intervention.title)
            putExtra(InterventionExtras.MESSAGE, intervention.message)
            putExtra(InterventionExtras.EVIDENCE_TAG, intervention.type.evidenceTag)
            putExtra(InterventionExtras.SOURCE_PACKAGE, sourcePackage)
        }
        val openPi = openApp?.let { PendingIntent.getActivity(context, 100, it, flags) }

        // Action 1: "Go Home" (Accept)
        val home = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_HOME)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val homePi = PendingIntent.getActivity(context, 101, home, flags)

        // Action 2: "Snooze 15m"
        val snoozeIntent = Intent(context, InterventionActionReceiver::class.java).apply {
            action = InterventionActionReceiver.ACTION_SNOOZE
            putExtra(InterventionActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            putExtra(InterventionExtras.SOURCE_PACKAGE, sourcePackage)
            putExtra(InterventionExtras.TITLE, intervention.title)
            putExtra(InterventionExtras.MESSAGE, intervention.message)
        }
        val snoozePi = PendingIntent.getBroadcast(context, 102, snoozeIntent, flags)

        // Action 3: "Dismiss"
        val dismissIntent = Intent(context, InterventionActionReceiver::class.java).apply {
            action = InterventionActionReceiver.ACTION_DISMISS
            putExtra(InterventionActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            putExtra(InterventionExtras.SOURCE_PACKAGE, sourcePackage)
            putExtra(InterventionExtras.TITLE, intervention.title)
            putExtra(InterventionExtras.MESSAGE, intervention.message)
        }
        val dismissPi = PendingIntent.getBroadcast(context, 103, dismissIntent, flags)

        // Action 4: "Learn More" (Link to RAG Chatbot)
        val learnMoreIntent = Intent(context, InterventionActionReceiver::class.java).apply {
            action = InterventionActionReceiver.ACTION_LEARN_MORE
            putExtra(InterventionActionReceiver.EXTRA_NOTIFICATION_ID, NOTIFICATION_ID)
            putExtra(InterventionExtras.TYPE, intervention.type.name)
            putExtra(InterventionExtras.TITLE, intervention.title)
            putExtra(InterventionExtras.MESSAGE, intervention.message)
            putExtra(InterventionExtras.EVIDENCE_TAG, intervention.type.evidenceTag)
            putExtra(InterventionExtras.SOURCE_PACKAGE, sourcePackage)
        }
        val learnMorePi = PendingIntent.getBroadcast(context, 104, learnMoreIntent, flags)

        val fullText = "${intervention.message}\n\n💡 Tap 'Learn More' to discuss this focus rule with AI."

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🛡️ ${intervention.title}")
            .setContentText(intervention.message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fullText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .apply {
                if (openPi != null) setContentIntent(openPi)
            }
            .addAction(0, "Go Home", homePi)
            .addAction(0, "Snooze 15m", snoozePi)
            .addAction(0, "Dismiss", dismissPi)
            .addAction(0, "Learn More 🤖", learnMorePi)

        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private companion object {
        const val CHANNEL_ID = "simulens_interventions"
        const val NOTIFICATION_ID = 2001
    }
}
