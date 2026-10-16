package com.cognilens.app.data.apps

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Suggestion {
    /** Feed-style social apps: pre-selected the first time. */
    FEED,

    /** Messaging / video apps: suggested, but the user decides. */
    OTHER_SOCIAL,

    NONE
}

data class InstalledApp(
    val packageName: String,
    val label: String,
    val suggestion: Suggestion
)

object SocialAppCatalog {
    val feedApps: Set<String> = setOf(
        "com.instagram.android",
        "com.instagram.barcelona",          // Threads
        "com.zhiliaoapp.musically",         // TikTok
        "com.zhiliaoapp.musically.go",      // TikTok Lite
        "com.ss.android.ugc.trill",         // TikTok (some regions)
        "com.twitter.android",              // X
        "com.snapchat.android",
        "com.facebook.katana",
        "com.facebook.lite",
        "com.reddit.frontpage",
        "com.pinterest",
        "com.linkedin.android",
        "com.bereal.ft",
        "com.tumblr"
    )

    val otherSocialApps: Set<String> = setOf(
        "com.whatsapp",
        "com.facebook.orca",                // Messenger
        "org.telegram.messenger",
        "com.discord",
        "com.google.android.youtube",
        "tv.twitch.android.app",
        "com.tencent.mm"                    // WeChat
    )
}

class InstalledAppsProvider(private val context: Context) {

    /**
     * Needs the <queries> LAUNCHER intent in the manifest (Android 11+),
     * otherwise this only returns a handful of apps.
     */
    suspend fun loadLaunchableApps(): List<InstalledApp> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcherIntent, 0)
        }

        resolved
            .asSequence()
            .map { it.activityInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { info ->
                val declaredSocial = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                        info.applicationInfo?.category == ApplicationInfo.CATEGORY_SOCIAL

                val suggestion = when {
                    info.packageName in SocialAppCatalog.feedApps -> Suggestion.FEED
                    info.packageName in SocialAppCatalog.otherSocialApps || declaredSocial -> Suggestion.OTHER_SOCIAL
                    else -> Suggestion.NONE
                }

                InstalledApp(
                    packageName = info.packageName,
                    label = info.loadLabel(pm).toString(),
                    suggestion = suggestion
                )
            }
            .sortedBy { it.label.lowercase() }
            .toList()
    }
}