package com.simulens.app.domain.rules.intervention

import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.EnumMap
import kotlin.math.ceil
import kotlin.math.floor

enum class BaselineMetric { SESSION_COUNT, SESSION_LENGTH, FREQUENCY, TOTAL_TIME }
enum class Confidence { LEARNING, ESTABLISHED }
enum class DeviationLevel { BELOW, WITHIN, ABOVE, WELL_ABOVE }

enum class TimeBucket {
    MORNING, AFTERNOON, EVENING, LATE_NIGHT;

    companion object {
        fun of(hour: Int) = when (hour) {
            in 5..11 -> MORNING
            in 12..16 -> AFTERNOON
            in 17..21 -> EVENING
            else -> LATE_NIGHT
        }
    }
}

data class DailyAggregate(
    val date: LocalDate,
    val sessionCount: Int,
    val totalMins: Double,
    val medianSessionMins: Double,
    val sessionsPerActiveHour: Double,
    val minsByBucket: Map<TimeBucket, Double>
) {
    /** Null when the metric is undefined for the day (e.g. session length with zero sessions). */
    fun value(metric: BaselineMetric): Double? = when (metric) {
        BaselineMetric.SESSION_COUNT -> sessionCount.toDouble()
        BaselineMetric.TOTAL_TIME -> totalMins
        BaselineMetric.SESSION_LENGTH -> if (sessionCount > 0) medianSessionMins else null
        BaselineMetric.FREQUENCY -> if (sessionCount > 0) sessionsPerActiveHour else null
    }
}

data class DatedValue(val date: LocalDate, val value: Double)

data class MetricBaseline(
    val median: Double,
    val q1: Double,
    val q3: Double,
    val low: Double,
    val high: Double,
    val wellAbove: Double,
    val sampleDays: Int,
    val confidence: Confidence,
    val samples: List<DatedValue>
)

data class Deviation(val pctVsMedian: Double?, val level: DeviationLevel)

data class WeekdayMetricView(
    val baseline: MetricBaseline?,
    val current: DatedValue?,
    val deviation: Deviation?,
    val isPartialDay: Boolean
)

data class WeekdayBaseline(
    val day: DayOfWeek,
    val metrics: Map<BaselineMetric, WeekdayMetricView>,
    val baselineMinsByBucket: Map<TimeBucket, Double>,
    val currentMinsByBucket: Map<TimeBucket, Double>?
)

class WeekdayBaselineEngine(
    private val weeks: Int = 6,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val requireExplicitCoverage: Boolean = false
) {
    fun compute(
        history: List<HistoricalSession>,
        trackingRecords: List<DailyTrackingRecord>,
        today: LocalDate,
        nowEpochMs: Long
    ): Map<DayOfWeek, WeekdayBaseline> {
        val sessions = history.filter {
            it.durationMins.isFinite() &&
                    it.durationMins >= MIN_SESSION_MINS &&
                    it.endEpochMs <= nowEpochMs
        }
        val aggregates = aggregate(sessions)

        val reliability = trackingRecords.groupBy { it.date }
            .mapValues { (_, r) -> r.any { it.trackingReliable } }

        val earliest = (aggregates.keys + trackingRecords.map { it.date }).minOrNull()

        fun isValid(date: LocalDate): Boolean =
            earliest != null && date >= earliest && date < today &&
                    if (requireExplicitCoverage) reliability[date] == true
                    else reliability[date] != false

        val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

        return DayOfWeek.entries.associateWith { dow ->
            val offset = (dow.value - 1).toLong()

            val baselineDates = (weeks downTo 1)
                .map { weekStart.minusWeeks(it.toLong()).plusDays(offset) }
                .filter(::isValid)
            val baselineAggs = baselineDates.map { aggregates[it] ?: emptyAggregate(it) }

            val currentDate = weekStart.plusDays(offset)
            val isPartial = currentDate == today
            val currentAgg: DailyAggregate? = when {
                currentDate > today -> null
                isPartial || isValid(currentDate) -> aggregates[currentDate] ?: emptyAggregate(currentDate)
                else -> null
            }

            val metrics = BaselineMetric.entries.associateWith { metric ->
                val samples = baselineAggs.mapNotNull { a ->
                    a.value(metric)?.let { DatedValue(a.date, it) }
                }
                val baseline = buildBaseline(samples)
                val current = currentAgg?.let { a ->
                    a.value(metric)?.let { DatedValue(a.date, it) }
                }
                WeekdayMetricView(
                    baseline = baseline,
                    current = current,
                    deviation = if (baseline != null && current != null)
                        deviation(current.value, baseline, isPartial) else null,
                    isPartialDay = isPartial
                )
            }

            WeekdayBaseline(
                day = dow,
                metrics = metrics,
                baselineMinsByBucket = TimeBucket.entries.associateWith { b ->
                    val v = baselineAggs.map { it.minsByBucket[b] ?: 0.0 }.sorted()
                    if (v.size >= 2) percentile(v, 0.5) else 0.0
                },
                currentMinsByBucket = currentAgg?.minsByBucket
            )
        }
    }

    fun deviation(value: Double, b: MetricBaseline, partialDay: Boolean = false): Deviation {
        val pct = if (b.median > 0.0) (value - b.median) / b.median * 100.0 else null
        val level = when {
            value > b.wellAbove -> DeviationLevel.WELL_ABOVE
            value > b.high -> DeviationLevel.ABOVE
            value < b.low && !partialDay -> DeviationLevel.BELOW // an unfinished day can't be "below" yet
            else -> DeviationLevel.WITHIN
        }
        return Deviation(pct, level)
    }

    private fun buildBaseline(samples: List<DatedValue>): MetricBaseline? {
        if (samples.size < 2) return null
        val v = samples.map { it.value }.sorted()
        val median = percentile(v, 0.5)
        val q1 = percentile(v, 0.25)
        val q3 = percentile(v, 0.75)
        val iqr = q3 - q1
        val fallback = v.size < 4 || iqr <= 0.0 // use +/-25% of the median
        return MetricBaseline(
            median = median, q1 = q1, q3 = q3,
            low = if (fallback) median * 0.75 else q1,
            high = if (fallback) median * 1.25 else q3,
            wellAbove = if (fallback) median * 1.5 else q3 + 1.5 * iqr,
            sampleDays = v.size,
            confidence = if (v.size >= 4) Confidence.ESTABLISHED else Confidence.LEARNING,
            samples = samples.sortedBy { it.date }
        )
    }

    private fun percentile(sorted: List<Double>, p: Double): Double {
        if (sorted.size == 1) return sorted[0]
        val idx = p * (sorted.size - 1)
        val lo = floor(idx).toInt()
        val hi = ceil(idx).toInt()
        return sorted[lo] + (sorted[hi] - sorted[lo]) * (idx - lo)
    }

    private class Acc {
        val lengths = mutableListOf<Double>()
        var firstStart = Long.MAX_VALUE
        var lastEnd = Long.MIN_VALUE
        var total = 0.0
        val buckets = EnumMap<TimeBucket, Double>(TimeBucket::class.java)
    }

    /** Count/length/frequency belong to the start date; minutes are split by overlap per hour. */
    private fun aggregate(sessions: List<HistoricalSession>): Map<LocalDate, DailyAggregate> {
        val accs = HashMap<LocalDate, Acc>()
        for (s in sessions) {
            val startMs = s.endEpochMs - (s.durationMins * 60_000.0).toLong()
            val startZ = Instant.ofEpochMilli(startMs).atZone(zone)
            val endZ = Instant.ofEpochMilli(s.endEpochMs).atZone(zone)

            val startAcc = accs.getOrPut(startZ.toLocalDate()) { Acc() }
            startAcc.lengths += s.durationMins
            startAcc.firstStart = minOf(startAcc.firstStart, startMs)
            startAcc.lastEnd = maxOf(startAcc.lastEnd, s.endEpochMs)

            var cursor = startZ
            while (cursor.isBefore(endZ)) {
                val nextHour = cursor.truncatedTo(ChronoUnit.HOURS).plusHours(1)
                val chunkEnd = if (nextHour.isBefore(endZ)) nextHour else endZ
                val mins = Duration.between(cursor, chunkEnd).toMillis() / 60_000.0
                val acc = accs.getOrPut(cursor.toLocalDate()) { Acc() }
                acc.total += mins
                val bucket = TimeBucket.of(cursor.hour)
                acc.buckets[bucket] = (acc.buckets[bucket] ?: 0.0) + mins
                cursor = chunkEnd
            }
        }
        return accs.mapValues { (date, a) ->
            val activeHours = if (a.lengths.isEmpty()) 1.0
            else maxOf(1.0, (a.lastEnd - a.firstStart) / 3_600_000.0)
            DailyAggregate(
                date = date,
                sessionCount = a.lengths.size,
                totalMins = a.total,
                medianSessionMins = if (a.lengths.isEmpty()) 0.0 else percentile(a.lengths.sorted(), 0.5),
                sessionsPerActiveHour = a.lengths.size / activeHours,
                minsByBucket = a.buckets.toMap()
            )
        }
    }

    private fun emptyAggregate(date: LocalDate) =
        DailyAggregate(date, 0, 0.0, 0.0, 0.0, emptyMap())

    private companion object {
        const val MIN_SESSION_MINS = 2.0 / 60.0 // matches the syncer's 2 s floor
    }
}