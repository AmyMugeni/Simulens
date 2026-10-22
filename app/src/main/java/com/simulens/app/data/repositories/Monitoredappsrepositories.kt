package com.simulens.app.data.repositories

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.simulens.app.data.apps.SocialAppCatalog

private val Context.monitoredAppsStore by preferencesDataStore(name = "monitored_apps")

/**
 * Single source of truth for WHICH apps Simulens tracks.
 * Read by the tracker service and by the dashboard, written by the app-selection screen.
 */
class MonitoredAppsRepository(context: Context) {

    private val store = context.applicationContext.monitoredAppsStore

    /** Falls back to social & media catalog apps until configured by the user. */
    val monitoredApps: Flow<Set<String>> = store.data.map { it[KEY_APPS] ?: DEFAULT_APPS }

    val isConfigured: Flow<Boolean> = store.data.map { it[KEY_CONFIGURED] ?: false }

    suspend fun save(packages: Set<String>) {
        store.edit {
            it[KEY_APPS] = packages
            it[KEY_CONFIGURED] = true
        }
    }

    companion object {
        private val KEY_APPS = stringSetPreferencesKey("monitored_packages")
        private val KEY_CONFIGURED = booleanPreferencesKey("is_configured")

        val DEFAULT_APPS: Set<String> = SocialAppCatalog.feedApps + SocialAppCatalog.otherSocialApps
    }
}