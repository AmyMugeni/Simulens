package com.simulens.app.domain.rules.intervention

import com.simulens.app.ui.dashboard.formatDuration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pure, stateless rules. Priority (first match wins):
 *   1. schedule conflict (in class)
 *   2. sleep window (sleep time -> wake time)
 *   3. wind-down (N min before sleep)
 *   4. morning (N min after waking)
 *   5. visit far longer than the user's usual
 *   6. today far above the user's usual by this time of day
 */
class InterventionEngine(private val config: InterventionConfig = InterventionConfig()) {

    fun evaluate(
        session: SessionContext,
        sleep: SleepSchedule,
        classes: List<ClassBlock>,
        snapshot: UsageSnapshot
    ): Intervention? {
        val time = session.now.toLocalTime()
        return scheduleConflict(session, time, classes)
            ?: sleepAndCircadian(session, time, sleep)
            ?: sessionDeviation(session, snapshot.baseline)
            ?: dailyDeviation(session, snapshot.baseline)
    }

    fun isInClass(time: LocalTime, classes: List<ClassBlock>): Boolean =
        classes.any { isInWindow(time, it.start, it.end) }

    // --- 1. Schedule conflict ------------------------------------------------------------------

    private fun scheduleConflict(s: SessionContext, time: LocalTime, classes: List<ClassBlock>): Intervention? {
        val current = classes.firstOrNull { isInWindow(time, it.start, it.end) } ?: return null
        val until = fmt(current.end)
        return Intervention(
            type = InterventionType.SCHEDULE_CONFLICT,
            title = "You're in class",
            message = "${current.subject} runs until $until. ${s.appLabel} can wait.",
            detail = "${current.subject} until $until"
        )
    }

    // --- 2-4. Sleep window, wind-down, morning -------------------------------------------------

    private fun sleepAndCircadian(s: SessionContext, time: LocalTime, sleep: SleepSchedule): Intervention? {
        val windDownStart = sleep.sleep.minusMinutes(sleep.windDownMinutes)
        val morningEnd = sleep.wake.plusMinutes(sleep.morningMinutes)

        return when {
            isInWindow(time, sleep.sleep, sleep.wake) -> Intervention(
                type = InterventionType.SLEEP_WINDOW,
                title = "It's sleep time",
                message = "You planned to be asleep until ${fmt(sleep.wake)}. Put ${s.appLabel} down and rest.",
                detail = "sleep window ${fmt(sleep.sleep)}-${fmt(sleep.wake)}"
            )
            isInWindow(time, windDownStart, sleep.sleep) -> Intervention(
                type = InterventionType.WIND_DOWN,
                title = "Time to wind down",
                message = "Your sleep window starts at ${fmt(sleep.sleep)}. A feed now makes it harder to switch off.",
                detail = "wind-down before ${fmt(sleep.sleep)}"
            )
            isInWindow(time, sleep.wake, morningEnd) -> Intervention(
                type = InterventionType.MORNING,
                title = "Ease into your day",
                message = "You woke at ${fmt(sleep.wake)}. The first half hour is easier without ${s.appLabel}.",
                detail = "first ${sleep.morningMinutes} min after waking"
            )
            else -> null
        }
    }

    // --- 5. Visit much longer than usual --------------------------------------------------------

    private fun sessionDeviation(s: SessionContext, b: UsageBaseline): Intervention? {
        if (!hasHistory(b) || b.meanSessionMins <= 0.0) return null

        val threshold = max(config.minSessionMinsForDeviation, b.meanSessionMins * config.sessionDeviationMultiplier)
        if (s.sessionMins < threshold) return null

        val times = String.format(Locale.US, "%.1f", s.sessionMins / b.meanSessionMins)
        val durStr = formatDuration(s.sessionMins)
        return Intervention(
            type = InterventionType.HIGH_SESSION_DEVIATION,
            title = "This visit is running long",
            message = "You've been on ${s.appLabel} for $durStr, about ${times}x your usual visit.",
            detail = "visit $durStr vs usual ${formatDuration(b.meanSessionMins)}"
        )
    }

    // --- 6. Today far above usual by this time of day --------------------------------------------

    private fun dailyDeviation(s: SessionContext, b: UsageBaseline): Intervention? {
        if (!hasHistory(b)) return null
        val typical = b.typicalMinutesByNow ?: return null

        val excess = s.todayMinutes - typical
        if (excess < config.minDailyExcessMins) return null
        if (s.todayMinutes < typical * config.dailyDeviationRatio) return null

        val todayStr = formatDuration(s.todayMinutes)
        val comparison = if (typical >= 1.0) {
            "${(excess / typical * 100).roundToInt()}% above your usual by this time"
        } else {
            "far more than you usually have by this time"
        }
        return Intervention(
            type = InterventionType.HIGH_DAILY_DEVIATION,
            title = "Heavier than usual today",
            message = "You're at $todayStr on social media today, $comparison.",
            detail = "today $todayStr vs typical ${formatDuration(typical)}"
        )
    }

    private fun hasHistory(b: UsageBaseline) =
        b.daysOfData >= config.minHistoryDays && b.sampleSessions >= config.minHistorySessions

    companion object {
        private val HHMM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        private fun fmt(t: LocalTime): String = t.format(HHMM)

        /** [start, end) with midnight wrap-around (e.g. 23:00 -> 07:00). */
        fun isInWindow(t: LocalTime, start: LocalTime, end: LocalTime): Boolean = when {
            start == end -> false
            start.isBefore(end) -> !t.isBefore(start) && t.isBefore(end)
            else -> !t.isBefore(start) || t.isBefore(end)
        }
    }
}