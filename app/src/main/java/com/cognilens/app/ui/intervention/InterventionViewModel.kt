package com.cognilens.app.ui.intervention

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cognilens.app.data.local.CogniLensDatabase
import com.cognilens.app.data.local.entity.InterventionLogEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.ConcurrentHashMap

enum class InterventionFilter(val label: String) {
    ALL("All"),
    CIRCADIAN("Circadian"),
    SESSION_LIMIT("Session Limit"),
    RELAPSE("Relapse"),
    SCHEDULE("Schedule")
}

data class NudgePreview(
    val title: String,
    val message: String,
    val packageName: String,
    val appName: String,
    val reason: String
)

data class InterventionUiState(
    val logs: List<InterventionLogEntity> = emptyList(),
    val filteredLogs: List<InterventionLogEntity> = emptyList(),
    val filter: InterventionFilter = InterventionFilter.ALL,
    val todayCount: Int = 0,
    val weekCount: Int = 0,
    val topAppPackage: String? = null,
    val topAppName: String? = null,
    val mostCommonReason: String = "No interventions yet",
    val activePreviewNudge: NudgePreview? = null
)

class InterventionViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CogniLensDatabase.getDatabase(application)
    private val interventionLogDao = database.interventionLogDao()
    private val packageManager: PackageManager = application.packageManager
    private val appNameCache = ConcurrentHashMap<String, String>()

    private val _filter = MutableStateFlow(InterventionFilter.ALL)
    private val _previewNudge = MutableStateFlow<NudgePreview?>(null)

    val uiState: StateFlow<InterventionUiState> = combine(
        interventionLogDao.getAllLogs(),
        _filter,
        _previewNudge
    ) { logs, filter, preview ->
        val zone = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli()
        val weekStart = LocalDate.now().minusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()

        val todayLogs = logs.filter { it.timestamp >= todayStart }
        val weekLogs = logs.filter { it.timestamp >= weekStart }

        val filtered = when (filter) {
            InterventionFilter.ALL -> logs
            InterventionFilter.CIRCADIAN -> logs.filter { it.triggerReason.contains("Circadian", ignoreCase = true) }
            InterventionFilter.SESSION_LIMIT -> logs.filter { it.triggerReason.contains("Limit", ignoreCase = true) || it.sessionDurationMins >= 30f }
            InterventionFilter.RELAPSE -> logs.filter { it.triggerReason.contains("Relapse", ignoreCase = true) || it.reopenIntervalMins < 2f }
            InterventionFilter.SCHEDULE -> logs.filter { it.triggerReason.contains("Schedule", ignoreCase = true) || it.triggerReason.contains("Lecture", ignoreCase = true) }
        }

        val topPkg = logs.groupBy { it.packageName }
            .maxByOrNull { it.value.size }?.key

        val topAppName = topPkg?.let { getAppName(it) }

        val mostCommonReason = logs.groupBy { it.triggerReason }
            .maxByOrNull { it.value.size }?.key ?: "No interventions yet"

        InterventionUiState(
            logs = logs,
            filteredLogs = filtered,
            filter = filter,
            todayCount = todayLogs.size,
            weekCount = weekLogs.size,
            topAppPackage = topPkg,
            topAppName = topAppName,
            mostCommonReason = mostCommonReason,
            activePreviewNudge = preview
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InterventionUiState()
    )

    fun setFilter(filter: InterventionFilter) {
        _filter.value = filter
    }

    fun clearAllLogs() {
        viewModelScope.launch(Dispatchers.IO) {
            interventionLogDao.clearAllLogs()
        }
    }

    fun deleteLog(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            interventionLogDao.deleteLogById(id)
        }
    }

    fun simulateIntervention(
        reason: String = "Circadian Override: App opened during wake-up window.",
        packageName: String = "com.instagram.android"
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val appName = getAppName(packageName)
            val log = InterventionLogEntity(
                packageName = packageName,
                timestamp = System.currentTimeMillis(),
                triggerReason = reason,
                sessionDurationMins = 12.5f,
                reopenIntervalMins = 0.5f,
                wasDismissed = false
            )
            interventionLogDao.insertLog(log)

            val nudge = NudgePreview(
                title = "Mindful Pause",
                message = "CogniLens detected an intervention trigger:\n\n\"$reason\"\n\nTake a 10-second breath before proceeding.",
                packageName = packageName,
                appName = appName,
                reason = reason
            )
            withContext(Dispatchers.Main) {
                _previewNudge.value = nudge
            }
        }
    }

    fun dismissPreviewNudge() {
        _previewNudge.value = null
    }

    fun getAppName(packageName: String): String {
        return appNameCache.getOrPut(packageName) {
            try {
                val appInfo = packageManager.getApplicationInfo(packageName, 0)
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                packageName.substringAfterLast('.')
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
        }
    }
}
