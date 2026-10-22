package com.simulens.app.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.simulens.app.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalTime

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_profile_prefs")

class UserProfileRepository(private val context: Context) {

    companion object {
        private val USERNAME = stringPreferencesKey("username")
        private val WAKE_HOUR = intPreferencesKey("wake_hour")
        private val WAKE_MINUTE = intPreferencesKey("wake_minute")
        private val SLEEP_HOUR = intPreferencesKey("sleep_hour")
        private val SLEEP_MINUTE = intPreferencesKey("sleep_minute")
        private val BSMAS_SCORE = intPreferencesKey("bsmas_score")
        private val IS_TIMETABLE_CONFIGURED = booleanPreferencesKey("is_timetable_configured")
        private val IS_SLEEP_CONFIGURED = booleanPreferencesKey("is_sleep_configured")
        private val IS_ONBOARDING_COMPLETE = booleanPreferencesKey("is_onboarding_complete")
    }

    val userProfileFlow: Flow<UserProfile> = context.dataStore.data.map { prefs ->
        UserProfile(
            username = prefs[USERNAME] ?: "",
            wakeUpTime = LocalTime.of(
                prefs[WAKE_HOUR] ?: 7,
                prefs[WAKE_MINUTE] ?: 0
            ),
            sleepTime = LocalTime.of(
                prefs[SLEEP_HOUR] ?: 23,
                prefs[SLEEP_MINUTE] ?: 0
            ),
            bsmasScore = prefs[BSMAS_SCORE] ?: 18,
            isTimetableConfigured = prefs[IS_TIMETABLE_CONFIGURED] ?: false,
            isSleepConfigured = prefs[IS_SLEEP_CONFIGURED] ?: false
        )
    }

    val isOnboardingCompleteFlow: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[IS_ONBOARDING_COMPLETE] ?: false
    }

    suspend fun saveUserProfile(profile: UserProfile) {
        context.dataStore.edit { prefs ->
            prefs[USERNAME] = profile.username
            prefs[WAKE_HOUR] = profile.wakeUpTime.hour
            prefs[WAKE_MINUTE] = profile.wakeUpTime.minute
            prefs[SLEEP_HOUR] = profile.sleepTime.hour
            prefs[SLEEP_MINUTE] = profile.sleepTime.minute
            prefs[BSMAS_SCORE] = profile.bsmasScore
            prefs[IS_TIMETABLE_CONFIGURED] = profile.isTimetableConfigured
            prefs[IS_SLEEP_CONFIGURED] = profile.isSleepConfigured
            prefs[IS_ONBOARDING_COMPLETE] = true
        }
    }

    suspend fun clearAllData() {
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
    }
}