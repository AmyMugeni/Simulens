package com.simulens.app.domain.rules.intervention

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class InterventionEngineTest {

    private val engine = InterventionEngine()
    private val day = LocalDate.of(2026, 10, 16) // a Friday

    private val sleep = SleepSchedule(wake = LocalTime.of(7, 0), sleep = LocalTime.of(23, 0))
    private val maths = ClassBlock(LocalTime.of(10, 0), LocalTime.of(11, 0), "Calculus")
    private val warm = UsageSnapshot(
        baseline = UsageBaseline(daysOfData = 10, sampleSessions = 60, meanSessionMins = 4.0, typicalMinutesByNow = 30.0),
        todayCompletedMins = 0.0
    )

    private fun ctx(h: Int, m: Int, sessionMins: Double = 0.0, today: Double = sessionMins) = SessionContext(
        packageName = "com.instagram.android",
        appLabel = "Instagram",
        sessionMins = sessionMins,
        todayMinutes = today,
        now = LocalDateTime.of(day, LocalTime.of(h, m))
    )

    private fun eval(c: SessionContext, snap: UsageSnapshot = UsageSnapshot.EMPTY, classes: List<ClassBlock> = emptyList(), s: SleepSchedule = sleep) =
        engine.evaluate(c, s, classes, snap)

    // --- Schedule conflict -------------------------------------------------------------------

    @Test fun classConflictFiresInsideClass() {
        val r = eval(ctx(10, 30), classes = listOf(maths))
        assertEquals(InterventionType.SCHEDULE_CONFLICT, r?.type)
        assertTrue(r!!.message.contains("Calculus"))
        assertTrue(r.message.contains("11:00"))
    }

    @Test fun noClassConflictOutsideClassOrAtEndBoundary() {
        assertNull(eval(ctx(9, 59), classes = listOf(maths)))
        assertNull(eval(ctx(11, 0), classes = listOf(maths))) // end is exclusive
        assertEquals(InterventionType.SCHEDULE_CONFLICT, eval(ctx(10, 0), classes = listOf(maths))?.type) // start inclusive
    }

    // --- Sleep window, wind-down, morning ---------------------------------------------------

    @Test fun sleepWindowCoversNight() {
        assertEquals(InterventionType.SLEEP_WINDOW, eval(ctx(23, 0))?.type)
        assertEquals(InterventionType.SLEEP_WINDOW, eval(ctx(2, 0))?.type)
        assertEquals(InterventionType.SLEEP_WINDOW, eval(ctx(6, 59))?.type)
    }

    @Test fun windDownIsThirtyMinutesBeforeSleep() {
        assertNull(eval(ctx(22, 29)))
        assertEquals(InterventionType.WIND_DOWN, eval(ctx(22, 30))?.type)
        assertEquals(InterventionType.WIND_DOWN, eval(ctx(22, 59))?.type)
    }

    @Test fun morningIsThirtyMinutesAfterWake() {
        assertEquals(InterventionType.MORNING, eval(ctx(7, 0))?.type)
        assertEquals(InterventionType.MORNING, eval(ctx(7, 29))?.type)
        assertNull(eval(ctx(7, 30)))
    }

    @Test fun sleepAfterMidnightStillWorks() {
        val late = SleepSchedule(wake = LocalTime.of(8, 0), sleep = LocalTime.of(1, 0))
        assertNull(eval(ctx(23, 0), s = late))
        assertEquals(InterventionType.WIND_DOWN, eval(ctx(0, 40), s = late)?.type)
        assertEquals(InterventionType.SLEEP_WINDOW, eval(ctx(3, 0), s = late)?.type)
        assertEquals(InterventionType.MORNING, eval(ctx(8, 10), s = late)?.type)
    }

    @Test fun customBuffersAreRespected() {
        val s = SleepSchedule(LocalTime.of(7, 0), LocalTime.of(23, 0), windDownMinutes = 60, morningMinutes = 10)
        assertEquals(InterventionType.WIND_DOWN, eval(ctx(22, 5), s = s)?.type)
        assertNull(eval(ctx(7, 10), s = s))
    }

    @Test fun middayWithNothingElseDoesNotFire() {
        assertNull(eval(ctx(14, 0, sessionMins = 2.0), snap = warm))
    }

    // --- Baseline deviation ------------------------------------------------------------------

    @Test fun longVisitFiresOnceAboveMultiplierAndFloor() {
        // mean 4.0 -> threshold max(5, 10) = 10
        assertNull(eval(ctx(14, 0, sessionMins = 9.9), snap = warm))
        val r = eval(ctx(14, 0, sessionMins = 12.0), snap = warm)
        assertEquals(InterventionType.HIGH_SESSION_DEVIATION, r?.type)
        assertTrue(r!!.message.contains("12 min"))
    }

    @Test fun floorStopsTinyMeansFromNagging() {
        val tiny = warm.copy(baseline = warm.baseline.copy(meanSessionMins = 0.5))
        assertNull(eval(ctx(14, 0, sessionMins = 4.0), snap = tiny))   // 8x mean but under 5 min floor
        assertNotNull(eval(ctx(14, 0, sessionMins = 5.0), snap = tiny))
    }

    @Test fun baselineRulesAreSilentOnColdStart() {
        assertNull(eval(ctx(14, 0, sessionMins = 90.0, today = 200.0), snap = UsageSnapshot.EMPTY))
        val fewSessions = warm.copy(baseline = warm.baseline.copy(sampleSessions = 5))
        assertNull(eval(ctx(14, 0, sessionMins = 90.0, today = 200.0), snap = fewSessions))
    }

    @Test fun dailyDeviationNeedsRatioAndAbsoluteExcess() {
        // typical 30: needs >= 45 (1.5x) AND excess >= 20 (i.e. >= 50)
        assertNull(eval(ctx(14, 0, sessionMins = 1.0, today = 49.0), snap = warm))
        val r = eval(ctx(14, 0, sessionMins = 1.0, today = 60.0), snap = warm)
        assertEquals(InterventionType.HIGH_DAILY_DEVIATION, r?.type)
        assertTrue(r!!.message.contains("100% above"))
    }

    @Test fun dailyDeviationWithZeroTypicalUsesPlainWording() {
        val rare = warm.copy(baseline = warm.baseline.copy(typicalMinutesByNow = 0.0))
        val r = eval(ctx(14, 0, sessionMins = 1.0, today = 25.0), snap = rare)
        assertEquals(InterventionType.HIGH_DAILY_DEVIATION, r?.type)
        assertTrue(r!!.message.contains("far more"))
    }

    @Test fun sessionDeviationOutranksDaily() {
        val r = eval(ctx(14, 0, sessionMins = 15.0, today = 90.0), snap = warm)
        assertEquals(InterventionType.HIGH_SESSION_DEVIATION, r?.type)
    }

    // --- Priority --------------------------------------------------------------------------------

    @Test fun classBeatsWindDownAndDeviation() {
        val lateClass = ClassBlock(LocalTime.of(22, 0), LocalTime.of(23, 0), "Night lab")
        val r = eval(ctx(22, 40, sessionMins = 30.0, today = 100.0), snap = warm, classes = listOf(lateClass))
        assertEquals(InterventionType.SCHEDULE_CONFLICT, r?.type)
    }

    @Test fun sleepWindowBeatsDeviation() {
        val r = eval(ctx(1, 0, sessionMins = 30.0, today = 100.0), snap = warm)
        assertEquals(InterventionType.SLEEP_WINDOW, r?.type)
    }

    @Test fun isInClassHelper() {
        assertTrue(engine.isInClass(LocalTime.of(10, 30), listOf(maths)))
        assertFalse(engine.isInClass(LocalTime.of(12, 0), listOf(maths)))
    }

    // --- Policy ------------------------------------------------------------------------------------

    private val sample = Intervention(InterventionType.WIND_DOWN, "t", "m", "d")

    @Test fun cooldownBlocksSameTypeThenAllows() {
        val p = InterventionPolicy()
        val t0 = 1_000_000L
        assertTrue(p.tryFire(sample, t0, day))
        assertFalse(p.tryFire(sample, t0 + 9 * 60_000L, day))      // wind-down cooldown is 10 min
        assertTrue(p.tryFire(sample, t0 + 10 * 60_000L, day))
    }

    @Test fun differentTypesDoNotShareCooldown() {
        val p = InterventionPolicy()
        assertTrue(p.tryFire(sample, 0L, day))
        assertTrue(p.tryFire(sample.copy(type = InterventionType.MORNING), 1_000L, day))
    }

    @Test fun dailyCapResetsNextDay() {
        val p = InterventionPolicy(InterventionConfig(maxPerDay = 2, cooldownMins = emptyMap()))
        val big = 24 * 60 * 60_000L
        // cooldown default for unknown types is 10 min, so space the calls out
        assertTrue(p.tryFire(sample, 0L, day))
        assertTrue(p.tryFire(sample, 11 * 60_000L, day))
        assertFalse(p.tryFire(sample, 22 * 60_000L, day))
        assertTrue(p.tryFire(sample, big, day.plusDays(1)))
    }

    // --- Baseline calculator -------------------------------------------------------------------------

    private val utc = ZoneId.of("UTC")
    private fun ms(date: LocalDate, h: Int, m: Int = 0) =
        date.atTime(h, m).atZone(utc).toInstant().toEpochMilli()

    private val now = LocalDateTime.of(day, LocalTime.of(12, 0))

    @Test fun calculatorUsesSameWeekdayUpToNow() {
        val fri1 = day.minusDays(7)
        val fri2 = day.minusDays(14)
        val history = listOf(
            HistoricalSession(ms(fri1, 10), 20.0),
            HistoricalSession(ms(fri1, 18), 15.0),   // after 12:00, excluded from "by now"
            HistoricalSession(ms(fri2, 10), 10.0),
            HistoricalSession(ms(day, 11), 5.0)      // today
        )
        val snap = BaselineCalculator.compute(history = history, now = now, zone = utc)
        assertEquals(5.0, snap.todayCompletedMins, 1e-9)
        assertEquals(14, snap.baseline.daysOfData)
        assertEquals(3, snap.baseline.sampleSessions)
        assertEquals(15.0, snap.baseline.meanSessionMins, 1e-9)
        assertEquals(15.0, snap.baseline.typicalMinutesByNow!!, 1e-9) // (20 + 10) / 2
    }

    @Test fun calculatorCountsZeroDays() {
        val history = listOf(HistoricalSession(ms(day.minusDays(7), 10), 20.0))
        val snap = BaselineCalculator.compute(history = history, now = now, zone = utc)
        assertEquals(7, snap.baseline.daysOfData)
        assertEquals(20.0 / 7, snap.baseline.typicalMinutesByNow!!, 1e-9)
    }

    @Test fun calculatorColdStart() {
        val snap = BaselineCalculator.compute(history = listOf(HistoricalSession(ms(day, 9), 3.0)), now = now, zone = utc)
        assertEquals(0, snap.baseline.daysOfData)
        assertNull(snap.baseline.typicalMinutesByNow)
        assertEquals(3.0, snap.todayCompletedMins, 1e-9)
    }
}