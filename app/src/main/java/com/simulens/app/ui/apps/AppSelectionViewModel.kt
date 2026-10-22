package com.simulens.app.ui.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.apps.InstalledApp
import com.simulens.app.data.apps.InstalledAppsProvider
import com.simulens.app.data.apps.Suggestion
import com.simulens.app.data.repositories.MonitoredAppsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppSelectionUiState(
    val isLoading: Boolean = true,
    val apps: List<InstalledApp> = emptyList(),
    val selected: Set<String> = emptySet(),
    val query: String = ""
)

class AppSelectionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MonitoredAppsRepository(application)
    private val provider = InstalledAppsProvider(application)

    private val _state = MutableStateFlow(AppSelectionUiState())
    val state: StateFlow<AppSelectionUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val apps = provider.loadLaunchableApps()
            val configured = repository.isConfigured.first()
            val initial = if (configured) {
                repository.monitoredApps.first()
            } else {
                // First run: pre-select feed-style social apps only. Messaging/video stay opt-in.
                apps.filter { it.suggestion == Suggestion.FEED }.map { it.packageName }.toSet()
            }
            _state.update { it.copy(isLoading = false, apps = apps, selected = initial) }
        }
    }

    fun toggle(packageName: String) {
        _state.update {
            val next = if (packageName in it.selected) it.selected - packageName else it.selected + packageName
            it.copy(selected = next)
        }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    fun save(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.save(_state.value.selected)
            onDone()
        }
    }
}