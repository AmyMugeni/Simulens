package com.simulens.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.local.entity.sortedByWeeklyOrder
import com.simulens.app.data.remote.RetrofitClient
import com.simulens.app.data.repositories.GoogleCalendarRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalTime

data class CalendarSyncUiState(
    val isConnected: Boolean = false,
    val isSyncing: Boolean = false,
    val accountEmail: String? = null,
    val events: List<TimetableEntry> = emptyList(),
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class CalendarSyncViewModel(
    private val repository: GoogleCalendarRepository
) : ViewModel() {

    private val _isSyncing = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _successMessage = MutableStateFlow<String?>(null)

    private val _messagesFlow = combine(_errorMessage, _successMessage) { err, succ -> err to succ }

    val uiState: StateFlow<CalendarSyncUiState> = combine(
        combine(repository.isConnectedFlow, repository.accountEmailFlow, repository.savedTimetable) { connected, email, events ->
            Triple(connected, email, events.sortedByWeeklyOrder())
        },
        _isSyncing,
        _messagesFlow
    ) { (connected, email, events), isSyncing, (error, success) ->
        CalendarSyncUiState(
            isConnected = connected,
            isSyncing = isSyncing,
            accountEmail = email,
            events = events,
            errorMessage = error,
            successMessage = success
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarSyncUiState()
    )

    fun connectGoogleAccount(token: String, email: String) {
        viewModelScope.launch {
            repository.saveAuthToken(token, email)
            syncCalendar()
        }
    }

    fun disconnectGoogleAccount() {
        viewModelScope.launch {
            repository.clearAuthToken()
            _successMessage.value = "Google Calendar disconnected"
        }
    }

    fun syncCalendar() {
        viewModelScope.launch {
            _isSyncing.value = true
            _errorMessage.value = null

            try {
                val result = repository.syncCalendarEvents()
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Synced ${it.size} calendar entries"
                    },
                    onFailure = {
                        _errorMessage.value = it.localizedMessage ?: "Sync error encountered"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Sync failed"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun addClassEvent(
        summary: String,
        description: String = "",
        location: String = "",
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.createClassEvent(
                    summary = summary,
                    description = description,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    startTime = startTime,
                    endTime = endTime
                )
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Class '$summary' added!"
                        onSuccess()
                    },
                    onFailure = {
                        _errorMessage.value = it.localizedMessage ?: "Failed to add class"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to add class"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun updateClassEvent(entry: TimetableEntry, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.updateClassEvent(entry)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Class updated"
                        onSuccess()
                    },
                    onFailure = {
                        _errorMessage.value = it.localizedMessage ?: "Failed to update class"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to update class"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun deleteClassEvent(id: String) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val result = repository.deleteClassEvent(id)
                result.fold(
                    onSuccess = {
                        _successMessage.value = "Class removed"
                    },
                    onFailure = {
                        _errorMessage.value = it.localizedMessage ?: "Failed to delete class"
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Failed to delete class"
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val database = SimulensDatabase.getDatabase(context.applicationContext)
                val apiService = RetrofitClient.googleCalendarApiService
                val repository = GoogleCalendarRepository(context.applicationContext, apiService, database.timetableDao())
                return CalendarSyncViewModel(repository) as T
            }
        }
    }
}