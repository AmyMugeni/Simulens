package com.simulens.app.data.tracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED ||
            action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            Log.i("BootReceiver", "Boot or package update detected. Restarting AppUsageTrackerService...")
            if (PermissionUtils.hasUsageStatsPermission(context)) {
                val serviceIntent = Intent(context, AppUsageTrackerService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
