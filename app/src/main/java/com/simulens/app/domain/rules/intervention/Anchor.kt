package com.simulens.app.domain.rules.intervention

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class PeriodStats(
    val days: Int,
    val activeDays: Int,
    val medianDailyMins: Double,
    val medianDailySessions: Double,
    val medianSessionMins: Double,
    val lateNightShare: Double
)

enum class Trajectory { IMPROVING, STEADY, SLIPPING }

data class AnchorComparison(
    val minsPct: Double?,
    val sessionsPct: Double?,
    val lengthPct: Double?,
    val lateShareDelta: Double,   // percentage points
    val bsmasDelta: Int?,
    val trajectory: Trajectory
)

object AnchorCalculator {
    const val WINDOW_DAYS = 28
    const val MIN_ACTIVE_DAYS = 7
    private const val MIN_SESSION_MINS = 2.0 / 60.0

    /** Days with no sessions count as zero usage; sessions belong to their start date. */
    fun periodStats(
        sessions: List<HistoricalSession>,
        from: LocalDate,
        toExclusive: LocalDate,
        zone: ZoneId
    ): PeriodStats? {
        class S(val date: LocalDate, val hour: Int, val mins: Double)
        val inRange = sessions
            .filter { it.durationMins.isFinite() && it.durationMins >= MIN_SESSION_MINS }
            .map {
                val start = Instant.ofEpochMilli(it.endEpochMs - (it.durationMins * 60_000).toLong()).atZone(zone)
                S(start.toLocalDate(), start.hour, it.durationMins)
            }
            .filter { it.date >= from && it.date < toExclusive }
        if (inRange.isEmpty()) return null

        val byDate = inRange.groupBy { it.date }
        val dates = generateSequence(from) { it.plusDays(1) }.takeWhile { it < toExclusive }.toList()
        val totals = dates.map { d -> byDate[d].orEmpty().sumOf { it.mins } }
        val counts = dates.map { d -> byDate[d].orEmpty().size.toDouble() }
        val allMins = inRange.sumOf { it.mins }
        val late = inRange.filter { it.hour >= 22 || it.hour < 5 }.sumOf { it.mins }

        return PeriodStats(
            days = dates.size,
            activeDays = byDate.size,
            medianDailyMins = median(totals),
            medianDailySessions = median(counts),
            medianSessionMins = median(inRange.map { it.mins }),
            lateNightShare = if (allMins > 0) late / allMins else 0.0
        )
    }

    fun compare(anchor: PeriodStats, recent: PeriodStats, bsmasAnchor: Int?, bsmasNow: Int?): AnchorComparison {
        fun pct(base: Double, cur: Double) = if (base > 0) (cur - base) / base * 100.0 else null
        val minsPct = pct(anchor.medianDailyMins, recent.medianDailyMins)
        val bsmasDelta = if (bsmasAnchor != null && bsmasNow != null) bsmasNow - bsmasAnchor else null
        val trajectory = when {
            (minsPct != null && minsPct >= 10.0) || (bsmasDelta != null && bsmasDelta >= 3) -> Trajectory.SLIPPING
            minsPct != null && minsPct <= -10.0 && (bsmasDelta == null || bsmasDelta <= 0) -> Trajectory.IMPROVING
            else -> Trajectory.STEADY
        }
        return AnchorComparison(
            minsPct = minsPct,
            sessionsPct = pct(anchor.medianDailySessions, recent.medianDailySessions),
            lengthPct = pct(anchor.medianSessionMins, recent.medianSessionMins),
            lateShareDelta = (recent.lateNightShare - anchor.lateNightShare) * 100.0,
            bsmasDelta = bsmasDelta,
            trajectory = trajectory
        )
    }

    private fun median(v: List<Double>): Double {
        if (v.isEmpty()) return 0.0
        val s = v.sorted(); val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2 else s[m]
    }
}