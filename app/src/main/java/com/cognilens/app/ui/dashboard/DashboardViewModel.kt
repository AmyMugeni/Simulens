package com.cognilens.app.ui.dashboard

import android.app.Application
import android.content.pm.PackageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cognilens.app.data.local.CogniLensDatabase
import com.cognilens.app.data.local.entity.BehavioralSession
import com.cognilens.app.data.local.entity.InterventionLogEntity
import com.cognilens.app.data.local.entity.TimetableEntry
import com.cognilens.app.data.repositories.MonitoredAppsRepository
import com.cognilens.app.data.repositories.UserProfileRepository
import com.cognilens.app.data.tracker.UsageAccess
import com.cognilens.app.domain.model.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

// ---------------------------------------------------------------------------------------------
// UI models
// ---------------------------------------------------------------------------------------------

data class ActivityStatus(
    val currentActivity: String? = null,
    val nextActivity: String = "",
    val nextActivityTime: String = "",
    val isOngoing: Boolean = false
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
    val monitoredCount: Int = 0
)

// ---------------------------------------------------------------------------------------------
// ViewModel
// ---------------------------------------------------------------------------------------------

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val database = CogniLensDatabase.getDatabase(application)
    private val userProfileRepository = UserProfileRepository(application)
    private val monitoredAppsRepository = MonitoredAppsRepository(application)
    private val timetableDao = database.timetableDao()
    private val behavioralSessionDao = database.behavioralSessionDao()
    private val interventionLogDao = database.interventionLogDao()

    private val labelCache = ConcurrentHashMap<String, String>()

    val userProfile: StateFlow<UserProfile> = userProfileRepository.userProfileFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserProfile())

    private val _usageAccessGranted = MutableStateFlow(UsageAccess.isGranted(application))
    val usageAccessGranted: StateFlow<Boolean> = _usageAccessGranted.asStateFlow()

    fun refreshUsageAccess() {
        _usageAccessGranted.value = UsageAccess.isGranted(getApplication())
    }

    val todayTimetable: StateFlow<TimetableEntry?> = timetableDao.getTimetableFlow()
        .map { list ->
            val now = LocalDate.now()
            val fullDay = now.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            val shortDay = now.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
            list.find {
                val entryDay = it.day.trim()
                entryDay.equals(fullDay, ignoreCase = true) || entryDay.equals(shortDay, ignoreCase = true)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activityStatus: StateFlow<ActivityStatus> = combine(todayTimetable, userProfile) { timetable, profile ->
        val now = LocalTime.now()
        val slots = classSlots(timetable)
        val currentClass = slots.find { !now.isBefore(it.start) && now.isBefore(it.end) }
        val nextClass = slots.filter { it.start.isAfter(now) }.minByOrNull { it.start }

        ActivityStatus(
            currentActivity = currentClass?.subject,
            nextActivity = nextClass?.subject ?: "Sleep",
            nextActivityTime = (nextClass?.start ?: profile.sleepTime).toString(),
            isOngoing = currentClass != null
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
        val timetable: TimetableEntry?,
        val profile: UserProfile
    )

    val state: StateFlow<DashboardState> = combine(
        combine(
            behavioralSessionDao.getAllSessions(),
            interventionLogDao.getAllLogs(),
            monitoredApps,
            todayTimetable,
            userProfile
        ) { sessions, logs, monitored, timetable, profile ->
            Inputs(sessions, logs, monitored, timetable, profile)
        },
        minuteTicker
    ) { inputs, _ -> compute(inputs) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardState())

    fun getGreeting(): String {
        return when (LocalDateTime.now().hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

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

        // --- Comparison against the user's own baseline, up to the same time of day ---------
        val history = (1..28).mapNotNull { daysAgo ->
            val date = today.minusDays(daysAgo.toLong())
            val daySessions = byDate[date] ?: return@mapNotNull null
            val cutoff = date.atStartOfDay(zone).toInstant().toEpochMilli() + nowMin * 60_000L
            val minutesByNow = daySessions.filter { it.timestamp <= cutoff }.sumOf { it.elapsedDuration }
            date to minutesByNow
        }
        val sameWeekday = history.filter { it.first.dayOfWeek == today.dayOfWeek }
        val comparison = when {
            sameWeekday.size >= 2 -> TodayComparison(
                baselineMinutes = sameWeekday.map { it.second }.average(),
                label = "your usual ${today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)}"
            )
            history.size >= 3 -> TodayComparison(
                baselineMinutes = history.map { it.second }.average(),
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
            classSlots(input.timetable).map { TimeBlock(it.start.toMinuteOfDay(), it.end.toMinuteOfDay()) }
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
        val daysWithData = weekly.count { it.totalMinutes > 0 }

        return DashboardState(
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
            weeklyDailyAverage = if (daysWithData > 0) weeklyTotal / daysWithData else 0.0,
            monitoredCount = input.monitored.size
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

    private fun classSlots(timetable: TimetableEntry?): List<SlotInfo> {
        if (timetable == null) return emptyList()
        return listOf(
            SlotInfo(LocalTime.of(8, 15), LocalTime.of(9, 15), timetable.slot1),
            SlotInfo(LocalTime.of(9, 15), LocalTime.of(10, 15), timetable.slot2),
            SlotInfo(LocalTime.of(10, 15), LocalTime.of(11, 15), timetable.slot3),
            SlotInfo(LocalTime.of(11, 15), LocalTime.of(12, 15), timetable.slot4),
            SlotInfo(LocalTime.of(12, 15), LocalTime.of(13, 15), timetable.slot5),
            SlotInfo(LocalTime.of(13, 15), LocalTime.of(14, 15), timetable.slot6),
            SlotInfo(LocalTime.of(14, 15), LocalTime.of(15, 15), timetable.slot7),
            SlotInfo(LocalTime.of(15, 15), LocalTime.of(16, 15), timetable.slot8),
            SlotInfo(LocalTime.of(16, 15), LocalTime.of(17, 15), timetable.slot9)
        ).filter { it.subject != "-" && it.subject.isNotBlank() }
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