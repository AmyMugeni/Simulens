package com.simulens.app.domain.rules.intervention

import java.time.LocalDateTime
import java.time.LocalTime

/** Why an intervention fired. [evidenceTag] is what the evidence cards will key on later. */
enum class InterventionType(val evidenceTag: String) {
    SCHEDULE_CONFLICT("class"),
    SLEEP_WINDOW("sleep"),
    WIND_DOWN("sleep"),
    MORNING("morning"),
    HIGH_SESSION_DEVIATION("baseline"),
    HIGH_DAILY_DEVIATION("baseline")
}

data class Intervention(
    val type: InterventionType,
    val title: String,
    val message: String,
    /** Short machine-friendly detail, e.g. "Calculus until 10:15". */
    val detail: String
) {
    /** Stored in the intervention log. */
    val reason: String get() = "${type.name}: $detail"
}

data class ClassBlock(val start: LocalTime, val end: LocalTime, val subject: String)

data class SleepSchedule(
    val wake: LocalTime,
    val sleep: LocalTime,
    /** Minutes BEFORE sleep time that count as wind-down. */
    val windDownMinutes: Long = 30,
    /** Minutes AFTER waking that count as the protected morning window. */
    val morningMinutes: Long = 30
)

data class SessionContext(
    val packageName: String,
    val appLabel: String,
    /** Length of the session so far (0 when it has just started). */
    val sessionMins: Double,
    /** Today's total INCLUDING the current session. */
    val todayMinutes: Double,
    val now: LocalDateTime
)

data class InterventionConfig(
    /** A visit is "high" when it exceeds this many times the user's mean visit... */
    val sessionDeviationMultiplier: Double = 2.5,
    /** ...and is at least this long, so a 1-minute mean doesn't nag at 2.5 minutes. */
    val minSessionMinsForDeviation: Double = 5.0,
    /** Today is "high" when usage by now is this many times the user's typical... */
    val dailyDeviationRatio: Double = 1.5,
    /** ...and exceeds the typical by at least this many minutes. */
    val minDailyExcessMins: Double = 20.0,
    /** Baseline rules stay silent until there is this much history. */
    val minHistoryDays: Int = 7,
    val minHistorySessions: Int = 20,
    /** Minimum gap between two interventions of the same type. */
    val cooldownMins: Map<InterventionType, Long> = mapOf(
        InterventionType.SCHEDULE_CONFLICT to 5L,
        InterventionType.SLEEP_WINDOW to 5L,
        InterventionType.WIND_DOWN to 10L,
        InterventionType.MORNING to 10L,
        InterventionType.HIGH_SESSION_DEVIATION to 15L,
        InterventionType.HIGH_DAILY_DEVIATION to 45L
    ),
    val maxPerDay: Int = 12
)