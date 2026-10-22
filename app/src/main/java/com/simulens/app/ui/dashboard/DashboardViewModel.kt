package com.simulens.app.ui.dashboard

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.simulens.app.data.export.RoomCsvExporter
import com.simulens.app.data.local.SimulensDatabase
import java.io.File
import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.local.entity.distinctWeeklySchedule
import com.simulens.app.data.repositories.MonitoredAppsRepository
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.data.tracker.UsageAccess
import com.simulens.app.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class TimeRange(val label: String) {
    TODAY("Today"),
    WEEKLY("7 Days"),
    ALL_TIME("All Time")
}

data class TodayClassItem(
    val id: String,
    val summary: String,
    val location: String = "",
    val startTimeStr: String,
    val endTimeStr: String,
    val start: LocalTime,
    val end: LocalTime,
    val isOngoing: Boolean = false,
    val isCompleted: Boolean = false,
    val isUpcoming: Boolean = false,
    val progressPercent: Float = 0f,
    val remainingMinutes: Int = 0
)

data class ActivityStatus(
    val currentActivity: String? = null,
    val nextActivity: String = "",
    val nextActivityTime: String = "",
    val isOngoing: Boolean = false,
    val todayClassesCount: Int = 0,
    val displayScheduleText: String = "No classes scheduled today",
    val todayClasses: List<TodayClassItem> = emptyList()
)

data class DailyUsageTrend(
    val dayLabel: String,
    val date: LocalDate,
    val totalMinutes: Double,
    val sessionCount: Int,
    val isToday: Boolean
)

data class AppUsageStat(
    val packageName: String,
    val displayName: String,
    val totalMinutes: Double,
    val percentage: Float
)

/** A span of the day, in minutes since midnight (0..1440). */
data class TimeBlock(val startMin: Int, val endMin: Int)

/** Today's sessions split by how long they lasted: quick checks vs. longer scrolls. */
data class SessionMix(
    val quickCount: Int = 0,
    val quickMinutes: Double = 0.0,
    val mediumCount: Int = 0,
    val mediumMinutes: Double = 0.0,
    val longCount: Int = 0,
    val longMinutes: Double = 0.0
) {
    val total: Int get() = quickCount + mediumCount + longCount
}

data class DayStrip(
    val sessions: List<TimeBlock> = emptyList(),
    val classes: List<TimeBlock> = emptyList(),
    val windDown: List<TimeBlock> = emptyList(),
    val nowMin: Int = 0,
    val classOverlapMin: Int = 0,
    val windDownOverlapMin: Int = 0,
    val morningOverlapMin: Int = 0,
    val morningBufferMin: Int = 0
)

data class TodayComparison(
    /** Minutes the user usually has by this time of day. */
    val baselineMinutes: Double,
    /** e.g. "your usual Tuesday" or "your daily average". */
    val label: String
)

data class DashboardState(
    val selectedTimeRange: TimeRange = TimeRange.TODAY,
    val todayMinutes: Double = 0.0,
    val todaySessions: Int = 0,
    val todaySessionsList: List<BehavioralSession> = emptyList(),
    val nudgesToday: Int = 0,
    val comparison: TodayComparison? = null,
    val mix: SessionMix = SessionMix(),
    val dayStrip: DayStrip = DayStrip(),
    val appsToday: List<AppUsageStat> = emptyList(),
    val appsWeek: List<AppUsageStat> = emptyList(),
    val weekly: List<DailyUsageTrend> = emptyList(),
    val weeklyTotalMinutes: Double = 0.0,
    val weeklyDailyAverage: Double = 0.0,
    val allTimeTotalMinutes: Double = 0.0,
    val allTimeTotalSessions: Int = 0,
    val daysTrackedCount: Int = 0,
    val monitoredCount: Int = 0,
    /** Today's wellness score, risk level and the reasons behind it (see WellnessEngine). */
    val wellness: WellnessResult = WellnessResult(),
    /** The score from midnight to now, for the curve on the wellness card. */
    val scoreTimeline: List<ScorePoint> = emptyList()
)

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = SimulensDatabase.getDatabase(application)
    private val userProfileRepository = UserProfileRepository(application)
    private val monitoredAppsRepository = MonitoredAppsRepository(application)
    private val timetableDao = database.timetableDao()
    private val behavioralSessionDao = database.behavioralSessionDao()
    private val interventionLogDao = database.interventionLogDao()
    private val dailyTrackingCoverageDao = database.dailyTrackingCoverageDao()

    private val _isExportingCsv = MutableStateFlow(false)
    val isExportingCsv: StateFlow<Boolean> = _isExportingCsv.asStateFlow()

    fun exportCsv(context: Context, onResult: (String) -> Unit) {
        viewModelScope.launch {
            _isExportingCsv.value = true
            try {
                val files = RoomCsvExporter.export(
                    context = context,
                    sessionDao = behavioralSessionDao,
                    coverageDao = dailyTrackingCoverageDao
                )
                val names = files.joinToString { it.name }
                onResult("CSV exported successfully: $names")
            } catch (e: Exception) {
                onResult("Failed to export CSV: ${e.localizedMessage}")
            } finally {
                _isExportingCsv.value = false
            }
        }
    }

    private val labelCache = ConcurrentHashMap<String, String>()
    private val _timeRange = MutableStateFlow(TimeRange.TODAY)

    val userProfile: StateFlow<UserProfile> = userProfileRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    private val _usageAccessGranted = MutableStateFlow(UsageAccess.isGranted(application))
    val usageAccessGranted: StateFlow<Boolean> = _usageAccessGranted.asStateFlow()

    fun setTimeRange(range: TimeRange) {
        _timeRange.value = range
    }

    fun refreshUsageAccess() {
        _usageAccessGranted.value = UsageAccess.isGranted(getApplication())
    }

    val todayTimetableEntries: StateFlow<List<TimetableEntry>> = timetableDao.getTimetableFlow()
        .map { list ->
            val now = LocalDate.now()
            val fullDay = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val shortDay = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            list.filter {
                val entryDay = it.dayOfWeek.trim()
                entryDay.isBlank() || entryDay.equals(fullDay, ignoreCase = true) || entryDay.equals(shortDay, ignoreCase = true)
            }.distinctWeeklySchedule()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activityStatus: StateFlow<ActivityStatus> = combine(todayTimetableEntries, userProfile) { entries, profile ->
        val now = LocalTime.now()
        val nowInMins = now.hour * 60 + now.minute

        val todayClasses = entries.mapNotNull { entry ->
            val start = parseTime(entry.startTime)
            val end = parseTime(entry.endTime)
            if (start != null && end != null && start.isBefore(end)) {
                val startInMins = start.hour * 60 + start.minute
                val endInMins = end.hour * 60 + end.minute

                val ongoing = !now.isBefore(start) && now.isBefore(end)
                val completed = !now.isBefore(end)
                val upcoming = now.isBefore(start)

                val totalDuration = (endInMins - startInMins).coerceAtLeast(1)
                val elapsed = (nowInMins - startInMins).coerceIn(0, totalDuration)
                val progress = if (ongoing) elapsed.toFloat() / totalDuration.toFloat() else if (completed) 1f else 0f
                val remaining = if (ongoing) (endInMins - nowInMins).coerceAtLeast(0) else 0

                TodayClassItem(
                    id = entry.id,
                    summary = entry.summary,
                    location = entry.location,
                    startTimeStr = entry.startTime,
                    endTimeStr = entry.endTime,
                    start = start,
                    end = end,
                    isOngoing = ongoing,
                    isCompleted = completed,
                    isUpcoming = upcoming,
                    progressPercent = progress,
                    remainingMinutes = remaining
                )
            } else null
        }.sortedBy { it.start }

        val currentClass = todayClasses.find { it.isOngoing }
        val nextClass = todayClasses.find { it.isUpcoming }
        val firstClass = todayClasses.firstOrNull()

        val (displayText, ongoing) = when {
            currentClass != null -> {
                "${currentClass.summary} (${currentClass.startTimeStr} - ${currentClass.endTimeStr})" to true
            }
            nextClass != null -> {
                "${nextClass.summary} (Next at ${nextClass.startTimeStr})" to false
            }
            firstClass != null -> {
                "${firstClass.summary} (${firstClass.startTimeStr} - ${firstClass.endTimeStr})" to false
            }
            else -> {
                "No classes scheduled today" to false
            }
        }

        ActivityStatus(
            currentActivity = currentClass?.summary ?: nextClass?.summary ?: firstClass?.summary,
            nextActivity = nextClass?.summary ?: "Sleep",
            nextActivityTime = (nextClass?.start ?: profile.sleepTime).toString(),
            isOngoing = ongoing,
            todayClassesCount = todayClasses.size,
            displayScheduleText = displayText,
            todayClasses = todayClasses
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ActivityStatus())

    val monitoredApps: StateFlow<Set<String>> = monitoredAppsRepository.monitoredApps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonitoredAppsRepository.DEFAULT_APPS)

    /** Re-emits every minute so "now" markers and time-of-day comparisons stay fresh. */
    private val minuteTicker = flow {
        while (true) {
            emit(Unit)
            delay(60_000L)
        }
    }

    private data class Inputs(
        val sessions: List<BehavioralSession>,
        val logs: List<InterventionLogEntity>,
        val monitored: Set<String>,
        val timetableEntries: List<TimetableEntry>,
        val profile: UserProfile,
        val timeRange: TimeRange
    )

    val state: StateFlow<DashboardState> = combine(
        combine(
            behavioralSessionDao.getAllSessions(),
            interventionLogDao.getAllLogs(),
            monitoredApps,
            todayTimetableEntries,
            userProfile
        ) { sessions, logs, monitored, timetableEntries, profile ->
            Tuple5(sessions, logs, monitored, timetableEntries, profile)
        },
        _timeRange,
        minuteTicker
    ) { (sessions, logs, monitored, timetableEntries, profile), timeRange, _ ->
        compute(Inputs(sessions, logs, monitored, timetableEntries, profile, timeRange))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun getGreeting(): String {
        return when (LocalDateTime.now().hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    private data class Tuple5<T1, T2, T3, T4, T5>(
        val t1: T1, val t2: T2, val t3: T3, val t4: T4, val t5: T5
    )

    // -----------------------------------------------------------------------------------------
    // Computation
    // -----------------------------------------------------------------------------------------

    private fun compute(input: Inputs): DashboardState {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        val today = now.toLocalDate()
        val nowMin = now.hour * 60 + now.minute
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()

        // Only count apps the user currently chooses to track.
        val sessions = input.sessions.filter { it.packageName in input.monitored }
        val byDate = sessions.groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val todaySessions = byDate[today].orEmpty()
        val todayMinutes = todaySessions.sumOf { it.elapsedDuration }

        val allTimeTotalMinutes = sessions.sumOf { it.elapsedDuration }
        val allTimeTotalSessions = sessions.size

        val earliestSessionDate = sessions.minOfOrNull { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
        val daysTrackedCount = if (earliestSessionDate != null) {
            (ChronoUnit.DAYS.between(earliestSessionDate, today).toInt() + 1).coerceAtMost(28)
        } else {
            1
        }

        // --- Comparison against the user's own baseline, up to the same time of day ---------
        val history = (1..28).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val daySessions = byDate[date].orEmpty()
            val cutoff = date.atStartOfDay(zone).toInstant().toEpochMilli() + nowMin * 60_000L
            val minutesByNow = daySessions.filter { it.timestamp <= cutoff }.sumOf { it.elapsedDuration }
            date to minutesByNow
        }
        val validHistory = if (earliestSessionDate != null) {
            history.filter { !it.first.isBefore(earliestSessionDate) }
        } else {
            emptyList()
        }
        val sameWeekday = validHistory.filter { it.first.dayOfWeek == today.dayOfWeek }
        val comparison = when {
            sameWeekday.size >= 2 -> TodayComparison(
                baselineMinutes = sameWeekday.map { it.second }.average(),
                label = "your usual ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            )
            validHistory.size >= 3 -> TodayComparison(
                baselineMinutes = validHistory.map { it.second }.average(),
                label = "your daily average"
            )
            else -> null
        }

        // --- Quick checks vs. longer scrolls --------------------------------------------------
        val quick = todaySessions.filter { it.elapsedDuration < QUICK_CHECK_MAX_MINS }
        val long = todaySessions.filter { it.elapsedDuration >= LONG_SESSION_MIN_MINS }
        val medium = todaySessions.filter {
            it.elapsedDuration >= QUICK_CHECK_MAX_MINS && it.elapsedDuration < LONG_SESSION_MIN_MINS
        }
        val mix = SessionMix(
            quickCount = quick.size, quickMinutes = quick.sumOf { it.elapsedDuration },
            mediumCount = medium.size, mediumMinutes = medium.sumOf { it.elapsedDuration },
            longCount = long.size, longMinutes = long.sumOf { it.elapsedDuration }
        )

        // --- Day strip: sessions against class, wind-down and wake windows ------------------
        val profile = input.profile
        val sessionBlocks = todaySessions.mapNotNull { s ->
            val endMs = s.timestamp
            val startMs = endMs - (s.elapsedDuration * 60_000.0).toLong()
            val a = ((startMs - startOfToday) / 60_000L).toInt().coerceIn(0, 1440)
            val b = ((endMs - startOfToday) / 60_000L).toInt().coerceIn(0, 1440)
            if (a >= 1440) null else TimeBlock(a, maxOf(b, a + 1).coerceAtMost(1440))
        }

        val classBlocks = mergeAdjacent(
            classSlots(input.timetableEntries).map { TimeBlock(it.start.toMinuteOfDay(), it.end.toMinuteOfDay()) }
        )

        val wakeMin = profile.wakeUpTime.toMinuteOfDay()
        val sleepMin = profile.sleepTime.toMinuteOfDay()
        val bedtimeBuffer = profile.bedtimeBufferMinutes.toInt()
        val morningBuffer = profile.morningBufferMinutes.toInt()

        val windStart = ((sleepMin - bedtimeBuffer) % 1440 + 1440) % 1440
        val windDownBlocks = if (windStart > wakeMin) {
            listOf(TimeBlock(0, wakeMin), TimeBlock(windStart, 1440))
        } else {
            listOf(TimeBlock(windStart, wakeMin))
        }.filter { it.endMin > it.startMin }

        val morningBlock = TimeBlock(wakeMin, (wakeMin + morningBuffer).coerceAtMost(1440))

        val dayStrip = DayStrip(
            sessions = sessionBlocks,
            classes = classBlocks,
            windDown = windDownBlocks,
            nowMin = nowMin,
            classOverlapMin = overlap(sessionBlocks, classBlocks),
            windDownOverlapMin = overlap(sessionBlocks, windDownBlocks),
            morningOverlapMin = overlap(sessionBlocks, listOf(morningBlock)),
            morningBufferMin = morningBuffer
        )

        // --- Wellness score -------------------------------------------------------------------
        // Sessions are stored at the moment they end, so each one becomes a span ending at its
        // timestamp. The baseline days are the same ones behind `comparison` above.
        fun spansFor(date: LocalDate): List<UsageSpan> {
            val dayStart = date.atStartOfDay(zone).toInstant().toEpochMilli()
            return byDate[date].orEmpty().map { s ->
                val end = (s.timestamp - dayStart) / 60_000.0
                UsageSpan(startMin = end - s.elapsedDuration, endMin = end)
            }
        }

        val baselineDates = when {
            sameWeekday.size >= 2 -> sameWeekday.map { it.first }
            validHistory.size >= 3 -> validHistory.map { it.first }
            else -> emptyList()
        }

        val wellnessInput = WellnessInput(
            todaySpans = spansFor(today),
            baselineDays = baselineDates.map { spansFor(it) },
            baselineLabel = comparison?.label.orEmpty(),
            classBands = classBlocks,
            windDownBands = windDownBlocks,
            morningBand = morningBlock
        )
        val wellness = WellnessEngine.evaluate(wellnessInput, atMin = nowMin + 1.0)
        val scoreTimeline = WellnessEngine.timeline(wellnessInput, nowMin)

        // --- Apps -----------------------------------------------------------------------------
        val weekStart = today.minusDays(6).atStartOfDay(zone).toInstant().toEpochMilli()
        val appsToday = appStats(todaySessions)
        val appsWeek = appStats(sessions.filter { it.timestamp >= weekStart })

        // --- Weekly trend -----------------------------------------------------------------------
        val weekly = (6 downTo 0).map { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val day = byDate[date].orEmpty()
            DailyUsageTrend(
                dayLabel = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                date = date,
                totalMinutes = day.sumOf { it.elapsedDuration },
                sessionCount = day.size,
                isToday = daysAgo == 0
            )
        }
        val weeklyTotal = weekly.sumOf { it.totalMinutes }

        return DashboardState(
            selectedTimeRange = input.timeRange,
            todayMinutes = todayMinutes,
            todaySessions = todaySessions.size,
            todaySessionsList = todaySessions,
            nudgesToday = input.logs.count { it.timestamp >= startOfToday },
            comparison = comparison,
            mix = mix,
            dayStrip = dayStrip,
            appsToday = appsToday,
            appsWeek = appsWeek,
            weekly = weekly,
            weeklyTotalMinutes = weeklyTotal,
            weeklyDailyAverage = weeklyTotal / 7.0,
            allTimeTotalMinutes = allTimeTotalMinutes,
            allTimeTotalSessions = allTimeTotalSessions,
            daysTrackedCount = daysTrackedCount,
            monitoredCount = input.monitored.size,
            wellness = wellness,
            scoreTimeline = scoreTimeline
        )
    }

    private fun appStats(list: List<BehavioralSession>): List<AppUsageStat> {
        val total = list.sumOf { it.elapsedDuration }.coerceAtLeast(1.0)
        return list.groupBy { it.packageName }
            .map { (pkg, group) ->
                val minutes = group.sumOf { it.elapsedDuration }
                AppUsageStat(
                    packageName = pkg,
                    displayName = labelFor(pkg),
                    totalMinutes = minutes,
                    percentage = (minutes / total * 100.0).toFloat().coerceIn(0f, 100f)
                )
            }
            .sortedByDescending { it.totalMinutes }
    }

    /** Real app name from the system, falling back to a prettified package name. */
    private fun labelFor(packageName: String): String = labelCache.getOrPut(packageName) {
        try {
            val pm = getApplication<Application>().packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.').replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ENGLISH) else it.toString()
            }.ifBlank { "Unknown" }
        }
    }

    private data class SlotInfo(val start: LocalTime, val end: LocalTime, val subject: String)

    private fun classSlots(entries: List<TimetableEntry>): List<SlotInfo> {
        return entries.mapNotNull { entry ->
            val start = parseTime(entry.startTime)
            val end = parseTime(entry.endTime)
            if (start != null && end != null && start.isBefore(end)) {
                SlotInfo(start, end, entry.summary)
            } else null
        }
    }

    private fun parseTime(timeStr: String): LocalTime? {
        if (timeStr.isBlank()) return null
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) LocalTime.of(parts[0].toInt(), parts[1].toInt()) else null
        } catch (e: Exception) {
            null
        }
    }

    private fun LocalTime.toMinuteOfDay(): Int = hour * 60 + minute

    private fun mergeAdjacent(blocks: List<TimeBlock>): List<TimeBlock> {
        if (blocks.isEmpty()) return blocks
        val sorted = blocks.sortedBy { it.startMin }
        val merged = mutableListOf(sorted.first())
        for (b in sorted.drop(1)) {
            val last = merged.last()
            if (b.startMin <= last.endMin) {
                merged[merged.lastIndex] = TimeBlock(last.startMin, maxOf(last.endMin, b.endMin))
            } else {
                merged.add(b)
            }
        }
        return merged
    }

    private fun overlap(a: List<TimeBlock>, b: List<TimeBlock>): Int =
        a.sumOf { x ->
            b.sumOf { y -> (minOf(x.endMin, y.endMin) - maxOf(x.startMin, y.startMin)).coerceAtLeast(0) }
        }

    private companion object {
        const val QUICK_CHECK_MAX_MINS = 1.0
        const val LONG_SESSION_MIN_MINS = 10.0
    }
}