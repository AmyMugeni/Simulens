package com.simulens.app.ui.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.BsmasResultEntity
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.model.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime

class OnboardingViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application)
    private val database = SimulensDatabase.getDatabase(application)
    private val bsmasDao = database.bsmasResultDao()
    private val timetableDao = database.timetableDao()

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    // 0 = not answered yet
    private val _bsmasAnswers = MutableStateFlow(IntArray(6) { 0 })
    val bsmasAnswers: StateFlow<IntArray> = _bsmasAnswers.asStateFlow()

    private val _isBsmasCompleted = MutableStateFlow(false)
    val isBsmasCompleted: StateFlow<Boolean> = _isBsmasCompleted.asStateFlow()

    val bsmasHistory: StateFlow<List<BsmasResultEntity>> = bsmasDao.observeAll()
        .map { list -> list.sortedByDescending { it.takenAtEpochMs } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val completionPercentage: StateFlow<Float> = combine(
        userProfile,
        isBsmasCompleted
    ) { profile, bsmasDone ->
        var completed = 0
        if (profile.username.trim().length >= 3) completed++
        if (profile.isSleepConfigured) completed++
        if (profile.isTimetableConfigured) completed++
        if (bsmasDone) completed++
        completed / 4.0f
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = 0f
    )

    init {
        viewModelScope.launch {
            _userProfile.value = repository.userProfileFlow.first()
        }
        viewModelScope.launch {
            bsmasDao.observeLatest().collect { latest ->
                if (latest != null) {
                    _bsmasAnswers.value = latest.items().toIntArray()
                    _isBsmasCompleted.value = true
                }
            }
        }
        viewModelScope.launch {
            timetableDao.getTimetableFlow().collect { entries ->
                if (entries.isNotEmpty() && !_userProfile.value.isTimetableConfigured) {
                    _userProfile.value = _userProfile.value.copy(isTimetableConfigured = true)
                }
            }
        }
    }

    fun updateUsername(name: String) {
        _userProfile.value = _userProfile.value.copy(username = name)
    }

    fun updateWakeUpTime(time: LocalTime) {
        _userProfile.value = _userProfile.value.copy(wakeUpTime = time, isSleepConfigured = true)
    }

    fun updateSleepTime(time: LocalTime) {
        _userProfile.value = _userProfile.value.copy(sleepTime = time, isSleepConfigured = true)
    }

    fun updateTimetableStatus(isConfigured: Boolean) {
        _userProfile.value = _userProfile.value.copy(isTimetableConfigured = isConfigured)
    }

    fun updateBsmasAnswer(index: Int, score: Int) {
        val current = _bsmasAnswers.value.copyOf()
        current[index] = score
        _bsmasAnswers.value = current
        val done = current.all { it in 1..5 }
        _isBsmasCompleted.value = done
        if (done) {
            _userProfile.value = _userProfile.value.copy(bsmasScore = current.sum())
            viewModelScope.launch {
                persistBsmasIfNew()
            }
        }
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

    private suspend fun persistBsmasIfNew() {
        val a = _bsmasAnswers.value
        if (!a.all { it in 1..5 }) return
        if (bsmasDao.getLatest()?.items() == a.toList()) return // no duplicate rows on re-save
        bsmasDao.insert(BsmasResultEntity.from(a, System.currentTimeMillis()))
    }

    /** Explicitly records and saves a new BSMAS questionnaire assessment attempt in database history. */
    fun recordNewBsmasAssessment(answers: IntArray = _bsmasAnswers.value, onSuccess: () -> Unit = {}) {
        if (!answers.all { it in 1..5 }) return
        viewModelScope.launch {
            val entity = BsmasResultEntity.from(answers, System.currentTimeMillis())
            bsmasDao.insert(entity)
            _bsmasAnswers.value = answers.copyOf()
            _isBsmasCompleted.value = true
            val updatedProfile = _userProfile.value.copy(bsmasScore = answers.sum())
            _userProfile.value = updatedProfile
            repository.saveUserProfile(updatedProfile)
            onSuccess()
        }
    }

    /** Saves baseline profile permanently to Preferences DataStore. */
    fun completeOnboarding(onSuccess: () -> Unit) {
        viewModelScope.launch {
            persistBsmasIfNew()
            repository.saveUserProfile(_userProfile.value)
            onSuccess()
        }
    }

    /** Generic save for profile editing */
    fun saveProfile(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            persistBsmasIfNew()
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
            db.bsmasResultDao().deleteAll()
            repository.clearAllData()
            _userProfile.value = UserProfile()
            _bsmasAnswers.value = IntArray(6) { 0 }
            _isBsmasCompleted.value = false
            onSuccess()
        }
    }

    /** Resets all database tables and sets up profile fresh for Amy Mugeni. Dev tooling only. */
    fun resetAccountForAmyMugeni(onSuccess: () -> Unit) {
        viewModelScope.launch {
            val db = SimulensDatabase.getDatabase(getApplication())
            db.behavioralSessionDao().deleteAllSessions()
            db.dailyTrackingCoverageDao().deleteAllCoverageRecords()
            db.interventionLogDao().clearAllLogs()
            db.timetableDao().clearTimetable()
            db.bsmasResultDao().deleteAll()
            repository.clearAllData()
            val amyProfile = UserProfile(username = "Amy Mugeni")
            repository.saveUserProfile(amyProfile)
            _userProfile.value = amyProfile
            _bsmasAnswers.value = IntArray(6) { 0 }
            _isBsmasCompleted.value = false
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