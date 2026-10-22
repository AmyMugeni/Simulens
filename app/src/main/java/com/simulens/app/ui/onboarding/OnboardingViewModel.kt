package com.simulens.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application)

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _bsmasAnswers = MutableStateFlow(IntArray(6) { 3 })
    val bsmasAnswers: StateFlow<IntArray> = _bsmasAnswers.asStateFlow()

    private val _isBsmasCompleted = MutableStateFlow(false)
    val isBsmasCompleted: StateFlow<Boolean> = _isBsmasCompleted.asStateFlow()

    init {
        viewModelScope.launch {
            // Load existing profile if available
            val savedProfile = repository.userProfileFlow.first()
            _userProfile.value = savedProfile
            
            // If sleep is configured, we consider it configured in UI too
            if (savedProfile.isSleepConfigured) {
                // We don't have individual answers stored yet in this simplified repo, 
                // but we can at least mark BSMAS as "touched" if score is not default 18
                if (savedProfile.bsmasScore != 18) {
                    _isBsmasCompleted.value = true
                }
            }
        }
    }

    fun updateUsername(name: String) {
        _userProfile.value = _userProfile.value.copy(username = name)
    }

    fun updateWakeUpTime(time: LocalTime) {
        _userProfile.value = _userProfile.value.copy(
            wakeUpTime = time,
            isSleepConfigured = true
        )
    }

    fun updateSleepTime(time: LocalTime) {
        _userProfile.value = _userProfile.value.copy(
            sleepTime = time,
            isSleepConfigured = true
        )
    }

    fun updateTimetableStatus(isConfigured: Boolean) {
        _userProfile.value = _userProfile.value.copy(isTimetableConfigured = isConfigured)
    }

    fun updateBsmasAnswer(index: Int, score: Int) {
        val current = _bsmasAnswers.value.copyOf()
        current[index] = score
        _bsmasAnswers.value = current
        _isBsmasCompleted.value = true
        _userProfile.value = _userProfile.value.copy(bsmasScore = current.sum())
    }

    fun calculateCompletionPercentage(): Float {
        val profile = _userProfile.value
        var completed = 0
        if (profile.username.trim().length >= 3) completed++
        if (profile.isSleepConfigured) completed++
        if (profile.isTimetableConfigured) completed++
        if (_isBsmasCompleted.value) completed++
        return completed / 4.0f
    }

    /** Saves baseline profile permanently to Preferences DataStore. */
    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            repository.saveUserProfile(_userProfile.value)
            onSuccess()
        }
    }

    /** Generic save for profile editing */
    fun saveProfile(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            repository.saveUserProfile(_userProfile.value)
            onSuccess()
        }
    }

    /** Clears all database tables and resets profile/onboarding so the user can sign in fresh. */
    fun resetUserDatabaseAndSignOut(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val db = SimulensDatabase.getDatabase(getApplication())
            db.behavioralSessionDao().deleteAllSessions()
            db.dailyTrackingCoverageDao().deleteAllCoverageRecords()
            db.interventionLogDao().clearAllLogs()
            db.timetableDao().clearTimetable()
            repository.clearAllData()
            _userProfile.value = UserProfile()
            onSuccess()
        }
    }

    /** Resets all database tables and sets up profile fresh for Amy Mugeni. */
    fun resetAccountForAmyMugeni(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val db = SimulensDatabase.getDatabase(getApplication())
            db.behavioralSessionDao().deleteAllSessions()
            db.dailyTrackingCoverageDao().deleteAllCoverageRecords()
            db.interventionLogDao().clearAllLogs()
            db.timetableDao().clearTimetable()
            repository.clearAllData()
            val amyProfile = UserProfile(username = "Amy Mugeni")
            repository.saveUserProfile(amyProfile)
            _userProfile.value = amyProfile
            onSuccess()
        }
    }

    companion object {
        val BSMAS_QUESTIONS = listOf(
            "You spend a lot of time thinking about social media or planning how to use it.",
            "You feel an urge to use social media more and more.",
            "You use social media in order to forget about personal problems.",
            "You have tried to cut down on the use of social media without success.",
            "You become restless or troubled if you are prohibited from using social media.",
            "You use social media so much that it has had a negative impact on your job/studies."
        )
    }
}