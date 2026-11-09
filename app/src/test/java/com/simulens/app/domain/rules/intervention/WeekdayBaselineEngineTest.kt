package com.simulens.app.domain.rules.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class WeekdayBaselineEngineTest {
    private val zone = ZoneId.of("UTC")
    private val engine = WeekdayBaselineEngine(weeks = 6, zone = zone)
    private val today = LocalDate.of(2026, 10, 28)          // Wednesday; this week's Monday = Oct 26
    private val now = today.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()

    private fun session(date: LocalDate, hour: Int, mins: Double) = HistoricalSession(
        endEpochMs = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli() + (mins * 60_000).toLong(),
        durationMins = mins
    )

    private fun mondays(n: Int) = (1..n).map { LocalDate.of(2026, 10, 26).minusWeeks(it.toLong()) }

    @Test fun flagsWellAboveBaseline() {
        val hist = mondays(6).flatMap { d -> List(3) { session(d, 9 + it, 5.0) } } +
                List(6) { session(LocalDate.of(2026, 10, 26), 9 + it, 5.0) }
        val v = engine.compute(hist, emptyList(), today, now)
            .getValue(DayOfWeek.MONDAY).metrics.getValue(BaselineMetric.SESSION_COUNT)
        assertEquals(3.0, v.baseline!!.median, 0.001)
        assertEquals(Confidence.ESTABLISHED, v.baseline!!.confidence)
        assertEquals(DeviationLevel.WELL_ABOVE, v.deviation!!.level)
    }

    @Test fun unreliableDaysAreExcluded() {
        val hist = mondays(6).flatMap { d -> List(3) { session(d, 9 + it, 5.0) } }
        val bad = mondays(6).take(2).map { DailyTrackingRecord(it, trackingReliable = false) }
        val v = engine.compute(hist, bad, today, now)
            .getValue(DayOfWeek.MONDAY).metrics.getValue(BaselineMetric.SESSION_COUNT)
        assertEquals(4, v.baseline!!.sampleDays)
    }

    @Test fun midnightCrossingSessionIsSplit() {
        val oct13 = LocalDate.of(2026, 10, 13)   // Tuesday
        val oct20 = LocalDate.of(2026, 10, 20)   // Tuesday
        val crossing = HistoricalSession(
            oct20.plusDays(1).atTime(0, 30).atZone(zone).toInstant().toEpochMilli(), 60.0
        )
        val tue = engine.compute(listOf(session(oct13, 12, 5.0), crossing), emptyList(), today, now)
            .getValue(DayOfWeek.TUESDAY)
        val total = tue.metrics.getValue(BaselineMetric.TOTAL_TIME).baseline!!
        assertEquals(30.0, total.samples.first { it.date == oct20 }.value, 0.01)  // 23:30-24:00 only
        assertEquals(15.0, tue.baselineMinsByBucket.getValue(TimeBucket.LATE_NIGHT), 0.01)
        assertNotNull(tue.metrics.getValue(BaselineMetric.SESSION_COUNT).baseline)
    }
}
