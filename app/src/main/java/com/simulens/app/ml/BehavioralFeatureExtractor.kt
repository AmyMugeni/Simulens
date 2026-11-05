package com.simulens.app.ml

import com.simulens.app.data.local.entity.BehavioralSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.sqrt

object BehavioralFeatureExtractor {

    /**
     * Extracts the 29 behavioural features from a list of 28-day [BehavioralSession] records
     * matching the exact schema expected by RandomForestPredictor and simulens_rf_champion.onnx.
     */
    fun extractFeatures(
        sessions: List<BehavioralSession>,
        nowMs: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, Double?> {
        if (sessions.isEmpty()) {
            return emptyMap()
        }

        val sessionsByDate = sessions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }

        val dailyDurations = sessionsByDate.mapValues { (_, list) -> list.sumOf { it.elapsedDuration } }
        val dailySessions = sessionsByDate.mapValues { (_, list) -> list.size.toDouble() }

        val durationValues = dailyDurations.values.toList()
        val sessionCountValues = dailySessions.values.toList()
        val sessionDurations = sessions.map { it.elapsedDuration }
        val sessionGaps = sessions.map { it.previousSessionGap }

        val activeDays = dailyDurations.size.toDouble()
        val totalSessions = sessions.size.toDouble()

        // 14-day early vs late split for progression/forecasting
        val fourteenDaysAgoMs = nowMs - 14L * 24 * 60 * 60 * 1000
        val earlySessions = sessions.filter { it.timestamp < fourteenDaysAgoMs }
        val lateSessions = sessions.filter { it.timestamp >= fourteenDaysAgoMs }

        val earlyDailyDurations = earlySessions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.elapsedDuration } }.values.toList()

        val lateDailyDurations = lateSessions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.sumOf { it.elapsedDuration } }.values.toList()

        val earlyDailySessions = earlySessions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.size.toDouble() }.values.toList()

        val lateDailySessions = lateSessions.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }.mapValues { (_, list) -> list.size.toDouble() }.values.toList()

        val earlyAvgDuration = earlyDailyDurations.averageOrNull() ?: 0.0
        val lateAvgDuration = lateDailyDurations.averageOrNull() ?: 0.0
        val earlyAvgSessions = earlyDailySessions.averageOrNull() ?: 0.0
        val lateAvgSessions = lateDailySessions.averageOrNull() ?: 0.0

        val durationChange = lateAvgDuration - earlyAvgDuration
        val sessionChange = lateAvgSessions - earlyAvgSessions

        val stdDailyDuration = durationValues.stdDevOrNull() ?: 0.0
        val stdDailySessions = sessionCountValues.stdDevOrNull() ?: 0.0

        return mapOf(
            "avg_daily_duration" to durationValues.averageOrNull(),
            "max_daily_duration" to durationValues.maxOrNull(),
            "std_daily_duration" to stdDailyDuration,
            "avg_daily_sessions" to sessionCountValues.averageOrNull(),
            "max_daily_sessions" to sessionCountValues.maxOrNull(),
            "std_daily_sessions" to stdDailySessions,
            "active_days" to activeDays,
            "total_sessions" to totalSessions,
            "avg_session_duration" to sessionDurations.averageOrNull(),
            "median_session_duration" to sessionDurations.medianOrNull(),
            "std_session_duration" to sessionDurations.stdDevOrNull(),
            "avg_session_gap" to sessionGaps.averageOrNull(),
            "avg_sessions_recorded" to (if (activeDays > 0) totalSessions / activeDays else 0.0),
            "schedule_conflict_rate" to (if (totalSessions > 0) sessions.count { it.scheduleConflict == 1 } / totalSessions else 0.0),
            "morning_usage_rate" to (if (totalSessions > 0) sessions.count { it.morningSocialMedia == 1 } / totalSessions else 0.0),
            "bedtime_usage_rate" to (if (totalSessions > 0) sessions.count { it.bedtimeSocialMedia == 1 } / totalSessions else 0.0),
            "avg_minutes_since_wake" to sessions.map { it.minutesSinceWake.toDouble() }.averageOrNull(),
            "avg_minutes_until_sleep" to sessions.map { it.minutesUntilSleep.toDouble() }.averageOrNull(),
            "avg_baseline_duration_ratio" to sessions.map { it.baselineDurationRatio }.averageOrNull(),
            "avg_baseline_frequency_ratio" to sessions.map { it.baselineFrequencyRatio }.averageOrNull(),
            "unique_apps" to sessions.map { it.packageName }.distinct().size.toDouble(),
            "early_avg_duration" to earlyAvgDuration,
            "late_avg_duration" to lateAvgDuration,
            "early_avg_sessions" to earlyAvgSessions,
            "late_avg_sessions" to lateAvgSessions,
            "duration_change" to durationChange,
            "session_change" to sessionChange,
            "weekly_duration_variability" to stdDailyDuration,
            "weekly_session_variability" to stdDailySessions
        )
    }

    private fun List<Double>.averageOrNull(): Double? = if (isEmpty()) null else average()

    private fun List<Double>.medianOrNull(): Double? {
        if (isEmpty()) return null
        val sorted = sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[mid - 1] + sorted[mid]) / 2.0 else sorted[mid]
    }

    private fun List<Double>.stdDevOrNull(): Double? {
        if (size < 2) return 0.0
        val avg = average()
        val sumOfSq = sumOf { (it - avg) * (it - avg) }
        return sqrt(sumOfSq / (size - 1))
    }
}
