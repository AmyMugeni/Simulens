package com.simulens.app.domain.rules.intervention

import java.time.LocalDate

/**
 * Historical session data.
 *
 * This preserves compatibility with the current tracker.
 * Duration is in minutes; endEpochMs is the session end timestamp.
 */
data class HistoricalSession(
    val endEpochMs: Long,
    val durationMins: Double
)

/**
 * A date on which tracking coverage has been assessed.
 *
 * A reliable day with no sessions represents zero usage.
 * An unreliable day must not contribute to the baseline.
 */
data class DailyTrackingRecord(
    val date: LocalDate,
    val trackingReliable: Boolean
)

data class UsageBaseline(
    val calculatedAtEpochMs: Long = 0L,
    val lookbackDays: Int = 28,
    val validDays: Int = 0,
    val validSessionCount: Int = 0,
    val isReliable: Boolean = false,
    val medianDailyMinutes: Double? = null,
    val meanDailyMinutes: Double? = null,
    val medianSessionsPerDay: Double? = null,
    val medianSessionMinutes: Double? = null,
    val medianMinutesByCurrentTime: Double? = null,
    val sameWeekdaySampleCount: Int = 0,
    val daysOfData: Int = validDays,
    val sampleSessions: Int = validSessionCount,
    val meanSessionMins: Double = medianSessionMinutes ?: 0.0,
    val typicalMinutesByNow: Double? = medianMinutesByCurrentTime
)

data class CurrentUsageSnapshot(
    val date: LocalDate = LocalDate.now(),
    val completedUsageMinutes: Double = 0.0,
    val completedSessionCount: Int = 0,
    val trackingReliable: Boolean = false
)

data class UsageSnapshot(
    val baseline: UsageBaseline = UsageBaseline(),
    val current: CurrentUsageSnapshot = CurrentUsageSnapshot()
) {
    constructor(baseline: UsageBaseline, todayCompletedMins: Double) : this(
        baseline = baseline,
        current = CurrentUsageSnapshot(
            date = LocalDate.now(),
            completedUsageMinutes = todayCompletedMins,
            completedSessionCount = 0,
            trackingReliable = false
        )
    )

    val todayCompletedMins: Double get() = current.completedUsageMinutes

    companion object {
        val EMPTY = UsageSnapshot()
    }
}
