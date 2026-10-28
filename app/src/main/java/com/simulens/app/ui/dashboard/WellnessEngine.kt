package com.simulens.app.ui.dashboard

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

/** One stretch of phone use on a single day, in minutes since local midnight. */
data class UsageSpan(val startMin: Double, val endMin: Double)

enum class RiskLevel(val label: String) {
    LOW("Low risk"),
    MODERATE("Moderate risk"),
    HIGH("High risk"),
    VERY_HIGH("Very high risk");

    companion object {
        fun fromScore(score: Int): RiskLevel = when {
            score >= 80 -> LOW
            score >= 60 -> MODERATE
            score >= 40 -> HIGH
            else -> VERY_HIGH
        }
    }
}

/** Daily session count bands: up to 10 normal, over 10 high, over 20 very high. */
enum class FrequencyBand(val label: String) {
    NORMAL("Normal"),
    HIGH("High"),
    VERY_HIGH("Very high");

    companion object {
        const val HIGH_ABOVE = 10
        const val VERY_HIGH_ABOVE = 20

        fun fromCount(sessions: Int): FrequencyBand = when {
            sessions > VERY_HIGH_ABOVE -> VERY_HIGH
            sessions > HIGH_ABOVE -> HIGH
            else -> NORMAL
        }
    }
}

enum class ReasonTone { POSITIVE, NEGATIVE }

/** [rank] only decides which reason leads; it is not shown to the user. */
data class WellnessReason(val text: String, val tone: ReasonTone, val rank: Double)

data class ScorePoint(val minuteOfDay: Int, val score: Int)

/**
 * Everything the engine needs, already reduced to minutes-since-midnight spans and bands.
 * [baselineDays] are the past days to compare against (empty until there is enough history).
 */
data class WellnessInput(
    val todaySpans: List<UsageSpan>,
    val baselineDays: List<List<UsageSpan>> = emptyList(),
    val baselineLabel: String = "",
    val classBands: List<TimeBlock> = emptyList(),
    val windDownBands: List<TimeBlock> = emptyList(),
    val morningBand: TimeBlock? = null
)

data class WellnessResult(
    val score: Int = 100,
    val level: RiskLevel = RiskLevel.LOW,
    /** The single "why" sentence shown under the score. */
    val headline: String = "Fresh start. No usage yet today.",
    val headlineTone: ReasonTone? = null,
    /** Up to two supporting reasons, strongest first. */
    val reasons: List<WellnessReason> = emptyList(),
    val minutes: Double = 0.0,
    val baselineMinutes: Double? = null,
    val sessions: Int = 0,
    val baselineSessions: Double? = null,
    val sessionBand: FrequencyBand = FrequencyBand.NORMAL,
    val lectureMinutes: Double = 0.0,
    val baselineLectureMinutes: Double? = null,
    val baselineLabel: String = ""
)

// ---------------------------------------------------------------------------------------------
// Engine
//
// The score starts at 100 and loses points as the day's usage builds up:
//
//   volume      up to 18  today's minutes against the user's usual full day (12 pts at 100%)
//   pace        up to 10  running ahead of the usual pace for this time of day
//   frequency   up to 15  0 up to 10 sessions, 1 pt per session to 20, then 0.5 pt each
//   lectures    up to 20  0.5 pt per minute of use inside class bands
//   wind-down   up to 15  0.5 pt per minute of use inside the bedtime buffer / overnight
//   morning     up to 10  0.5 pt per minute of use in the morning buffer after waking
//
// Using less than usual never costs points, so a light day stays near 100. Because the pace
// term compares against what the user normally has done *by now*, taking a break lets the
// score recover a few points.
// ---------------------------------------------------------------------------------------------

object WellnessEngine {

    private const val DEFAULT_DAILY_BUDGET_MIN = 150.0
    private const val MIN_DAILY_BUDGET_MIN = 90.0
    private const val MAX_DAILY_BUDGET_MIN = 240.0

    private const val VOLUME_MAX = 18.0
    private const val VOLUME_RATIO_CAP = 1.5
    private const val PACE_MAX = 10.0
    private const val FREQUENCY_MAX = 15.0
    private const val LECTURE_PER_MIN = 0.5
    private const val LECTURE_MAX = 20.0
    private const val WIND_DOWN_PER_MIN = 0.5
    private const val WIND_DOWN_MAX = 15.0
    private const val MORNING_PER_MIN = 0.5
    private const val MORNING_MAX = 10.0

    /** Changes smaller than this (in percent) are treated as "about the same". */
    private const val NOTICEABLE_PCT = 5
    private const val HEADLINE_MIN_RANK = 2.0

    /** Score at [atMin] (minutes since midnight) from the usage recorded up to that moment. */
    fun evaluate(input: WellnessInput, atMin: Double): WellnessResult {
        val t = atMin.coerceIn(0.0, 1440.0)
        val m = measure(input, t)
        val p = penaltiesOf(m)
        val score = scoreOf(p)
        val label = input.baselineLabel.ifBlank { "your usual day" }

        val reasons = buildList {
            // Lecture hours
            val lectureBase = m.baselineLecture
            if (m.lectureMin >= 1.0) {
                if (lectureBase != null && lectureBase >= 1.0) {
                    val pct = pctChange(m.lectureMin, lectureBase)
                    if (pct >= NOTICEABLE_PCT) {
                        add(reason("$pct% increased usage during lecture hours", ReasonTone.NEGATIVE, p.lecture, 1.5))
                    } else if (pct <= -NOTICEABLE_PCT) {
                        add(reason("${-pct}% less usage during lecture hours than usual", ReasonTone.POSITIVE, -pct / 10.0, 1.0))
                    }
                } else {
                    add(reason("${formatDuration(m.lectureMin)} on your phone during lectures", ReasonTone.NEGATIVE, p.lecture, 1.5))
                }
            } else if (lectureBase != null && lectureBase >= 3.0 && input.classBands.any { it.startMin.toDouble() <= t }) {
                add(reason("No phone use during lectures today", ReasonTone.POSITIVE, 3.0, 1.0))
            }

            // Pace against the usual amount by this time of day
            val base = m.baselineMinutes
            if (base != null && base >= 5.0) {
                val pct = pctChange(m.minutes, base)
                if (pct >= NOTICEABLE_PCT) {
                    add(reason("$pct% increased usage compared with $label by now", ReasonTone.NEGATIVE, p.pace, 1.0))
                } else if (pct <= -NOTICEABLE_PCT) {
                    add(reason("${-pct}% reduced usage compared with $label by now", ReasonTone.POSITIVE, -pct / 10.0, 1.0))
                }
            }

            // Session frequency
            when (FrequencyBand.fromCount(m.sessions)) {
                FrequencyBand.HIGH ->
                    add(reason("High session frequency: ${m.sessions} sessions today", ReasonTone.NEGATIVE, p.frequency, 1.0))
                FrequencyBand.VERY_HIGH ->
                    add(reason("Very high session frequency: ${m.sessions} sessions today", ReasonTone.NEGATIVE, p.frequency, 1.0))
                FrequencyBand.NORMAL -> Unit
            }

            // Bedtime and morning windows
            if (m.windDownMin >= 1.0) {
                add(reason("${formatDuration(m.windDownMin)} during your wind-down window", ReasonTone.NEGATIVE, p.windDown, 1.3))
            }
            if (m.morningMin >= 1.0) {
                add(reason("${formatDuration(m.morningMin)} soon after waking", ReasonTone.NEGATIVE, p.morning, 1.2))
            }

            // Overall volume
            val share = m.minutes / m.budget
            if (share >= 0.75) {
                val usual = if (m.typicalDay != null) "your usual" else "a typical"
                add(reason("Already at ${(share * 100).roundToInt()}% of $usual day", ReasonTone.NEGATIVE, p.volume, 0.7))
            }
        }

        val negatives = reasons.filter { it.tone == ReasonTone.NEGATIVE }.sortedByDescending { it.rank }
        val positives = reasons.filter { it.tone == ReasonTone.POSITIVE }.sortedByDescending { it.rank }
        val primary = negatives.firstOrNull { it.rank >= HEADLINE_MIN_RANK } ?: positives.firstOrNull()
        val supporting = (negatives + positives).filter { it !== primary }.take(2)

        val headline = primary?.text ?: if (m.minutes < 1.0) {
            "Fresh start. No usage yet today."
        } else {
            "Usage is in line with your usual pattern."
        }

        return WellnessResult(
            score = score,
            level = RiskLevel.fromScore(score),
            headline = headline,
            headlineTone = primary?.tone,
            reasons = supporting,
            minutes = m.minutes,
            baselineMinutes = m.baselineMinutes,
            sessions = m.sessions,
            baselineSessions = m.baselineSessions,
            sessionBand = FrequencyBand.fromCount(m.sessions),
            lectureMinutes = m.lectureMin,
            baselineLectureMinutes = m.baselineLecture,
            baselineLabel = input.baselineLabel
        )
    }

    /**
     * The score at every [stepMin] from midnight up to now, so the dashboard can draw how the
     * day has gone. The last point is "now".
     */
    fun timeline(input: WellnessInput, nowMin: Int, stepMin: Int = 30): List<ScorePoint> {
        val points = mutableListOf<ScorePoint>()
        var m = 0
        while (m < nowMin) {
            points += ScorePoint(m, scoreAt(input, m.toDouble()))
            m += stepMin
        }
        // Include the whole current minute so a session that just ended is counted.
        points += ScorePoint(nowMin, scoreAt(input, nowMin + 1.0))
        return points
    }

    // -----------------------------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------------------------

    private class Measures(
        val minutes: Double,
        val sessions: Int,
        val lectureMin: Double,
        val windDownMin: Double,
        val morningMin: Double,
        val baselineMinutes: Double?,
        val baselineSessions: Double?,
        val baselineLecture: Double?,
        val typicalDay: Double?
    ) {
        val budget: Double
            get() = (typicalDay ?: DEFAULT_DAILY_BUDGET_MIN)
                .coerceIn(MIN_DAILY_BUDGET_MIN, MAX_DAILY_BUDGET_MIN)
    }

    private class Penalties(
        val volume: Double,
        val pace: Double,
        val frequency: Double,
        val lecture: Double,
        val windDown: Double,
        val morning: Double
    ) {
        val total: Double get() = volume + pace + frequency + lecture + windDown + morning
    }

    private fun scoreAt(input: WellnessInput, t: Double): Int =
        scoreOf(penaltiesOf(measure(input, t.coerceIn(0.0, 1440.0))))

    private fun scoreOf(p: Penalties): Int = (100.0 - p.total).roundToInt().coerceIn(0, 100)

    private fun measure(input: WellnessInput, t: Double): Measures {
        val days = input.baselineDays
        val hasBaseline = days.isNotEmpty()
        val morning = input.morningBand?.let { listOf(it) }.orEmpty()

        return Measures(
            minutes = usageUpTo(input.todaySpans, t),
            sessions = sessionsUpTo(input.todaySpans, t),
            lectureMin = overlapUpTo(input.todaySpans, input.classBands, t),
            windDownMin = overlapUpTo(input.todaySpans, input.windDownBands, t),
            morningMin = overlapUpTo(input.todaySpans, morning, t),
            baselineMinutes = if (hasBaseline) days.map { usageUpTo(it, t) }.average() else null,
            baselineSessions = if (hasBaseline) days.map { sessionsUpTo(it, t).toDouble() }.average() else null,
            baselineLecture = if (hasBaseline) days.map { overlapUpTo(it, input.classBands, t) }.average() else null,
            typicalDay = if (hasBaseline) days.map { usageUpTo(it, 1440.0) }.average() else null
        )
    }

    private fun penaltiesOf(m: Measures): Penalties {
        val volume = VOLUME_MAX * min(m.minutes / m.budget, VOLUME_RATIO_CAP) / VOLUME_RATIO_CAP

        val base = m.baselineMinutes
        val pace = if (base != null && base >= 5.0 && m.minutes > base) {
            min(PACE_MAX, (m.minutes / base - 1.0) * 20.0)
        } else {
            0.0
        }

        val frequency = when {
            m.sessions <= FrequencyBand.HIGH_ABOVE -> 0.0
            m.sessions <= FrequencyBand.VERY_HIGH_ABOVE -> (m.sessions - FrequencyBand.HIGH_ABOVE).toDouble()
            else -> min(
                FREQUENCY_MAX,
                10.0 + (m.sessions - FrequencyBand.VERY_HIGH_ABOVE) * 0.5
            )
        }

        return Penalties(
            volume = volume,
            pace = pace,
            frequency = frequency,
            lecture = min(LECTURE_MAX, m.lectureMin * LECTURE_PER_MIN),
            windDown = min(WIND_DOWN_MAX, m.windDownMin * WIND_DOWN_PER_MIN),
            morning = min(MORNING_MAX, m.morningMin * MORNING_PER_MIN)
        )
    }

    private fun reason(text: String, tone: ReasonTone, impact: Double, weight: Double) =
        WellnessReason(text = text, tone = tone, rank = impact * weight)

    private fun pctChange(value: Double, base: Double): Int =
        ((value - base) / base * 100.0).roundToInt()

    /** Minutes of use that happened before [t]. */
    private fun usageUpTo(spans: List<UsageSpan>, t: Double): Double =
        spans.sumOf { max(0.0, min(it.endMin, t) - max(it.startMin, 0.0)) }

    /** Sessions that had finished by [t]; sessions are recorded when they end. */
    private fun sessionsUpTo(spans: List<UsageSpan>, t: Double): Int =
        spans.count { it.endMin <= t }

    /** Minutes of use inside [bands] before [t]. */
    private fun overlapUpTo(spans: List<UsageSpan>, bands: List<TimeBlock>, t: Double): Double =
        spans.sumOf { s ->
            bands.sumOf { b ->
                val end = min(min(s.endMin, b.endMin.toDouble()), t)
                val start = max(s.startMin, b.startMin.toDouble())
                max(0.0, end - start)
            }
        }
}

fun formatDuration(minutes: Double): String {
    val total = minutes.roundToInt()
    if (total <= 0) return "0min"
    val h = total / 60
    val m = total % 60
    return when {
        h > 0 && m > 0 -> "${h}hr ${m}min"
        h > 0 -> "${h}hr"
        else -> "${m}min"
    }
}

fun formatDuration(minutes: Int): String = formatDuration(minutes.toDouble())



