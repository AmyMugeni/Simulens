package com.simulens.app.ml

import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.domain.rules.intervention.ClassBlock
import com.simulens.app.domain.rules.intervention.InterventionEngine
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Builds the 29 features for the random forest from RAW session start, end, duration and package.
 * It never reads the stored context columns (sessionsToday, previousSessionGap, the three flags,
 * minutesSinceWake/UntilSleep, the two baseline ratios), because those are inconsistent in the
 * app's exports. Pass the monitored-app sessions only.
 *
 * Assumptions to confirm against the training notebook (marked A1..A6):
 *  A1 days are attributed to the session END date; A2 the window is exactly 28 complete days with
 *  zero-filled gaps; A3 std is the sample std (n - 1); A4 the baseline ratios use the running
 *  total at each session against the median of the previous 14 days (the app's DAO rule);
 *  A5 the clock-time features use the session START time with plain subtraction;
 *  A6 a session counts as in class if it starts in a timetable block, or starts between 00:00 and 06:00.
 */
object BehavioralFeatureExtractor {

    const val WINDOW_DAYS = 28
    private const val HALF = 14

    private class Row(
        val startMs: Long,
        val endMs: Long,
        val minutes: Double,
        val pkg: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val start: LocalTime
    )

    /**
     * @param sessions all monitored sessions up to [windowEnd] (older ones are used for baselines)
     * @param windowEnd last COMPLETE day of the window (normally yesterday)
     * @return all 29 features, or null when the user has not been tracked for the full window
     */
    fun extractFeatures(
        sessions: List<BehavioralSession>,
        windowEnd: LocalDate,
        wake: LocalTime,
        sleep: LocalTime,
        classBlocksFor: (LocalDate) -> List<ClassBlock>,
        zone: ZoneId = ZoneId.systemDefault()
    ): Map<String, Double>? {
        if (sessions.isEmpty()) return null

        val rows = sessions.map { s ->
            val startMs = s.timestamp - (s.elapsedDuration * 60_000.0).toLong()
            val startZ = Instant.ofEpochMilli(startMs).atZone(zone)
            Row(
                startMs, s.timestamp, s.elapsedDuration, s.packageName,
                startZ.toLocalDate(),
                Instant.ofEpochMilli(s.timestamp).atZone(zone).toLocalDate(),
                startZ.toLocalTime()
            )
        }.sortedBy { it.endMs }

        val windowStart = windowEnd.minusDays((WINDOW_DAYS - 1).toLong())
        val firstDate = rows.first().endDate
        if (firstDate > windowStart) return null          // tracked for less than the full window

        val minsByDate = rows.groupBy { it.endDate }.mapValues { (_, l) -> l.sumOf { it.minutes } }
        val countByDate = rows.groupBy { it.endDate }.mapValues { (_, l) -> l.size.toDouble() }

        // Zero-filled daily series over exactly the window.
        val dates = (0 until WINDOW_DAYS).map { windowStart.plusDays(it.toLong()) }
        val dailyMins = dates.map { minsByDate[it] ?: 0.0 }
        val dailyCount = dates.map { countByDate[it] ?: 0.0 }

        fun baseline(date: LocalDate, byDate: Map<LocalDate, Double>): Double? {
            val days = (1..14).map { date.minusDays(it.toLong()) }.filter { it >= firstDate }
            return if (days.isEmpty()) null else median(days.map { byDate[it] ?: 0.0 })
        }

        val durations = ArrayList<Double>()
        val gaps = ArrayList<Double>()
        val runningCounts = ArrayList<Double>()
        val durRatios = ArrayList<Double>()
        val freqRatios = ArrayList<Double>()
        val sinceWake = ArrayList<Double>()
        val untilSleep = ArrayList<Double>()
        var conflicts = 0
        var mornings = 0
        var bedtimes = 0
        val packages = HashSet<String>()

        var runDate: LocalDate? = null
        var runCount = 0
        var runMins = 0.0

        for (i in rows.indices) {
            val r = rows[i]
            if (r.endDate < windowStart || r.endDate > windowEnd) continue

            if (r.endDate != runDate) { runDate = r.endDate; runCount = 0; runMins = 0.0 }
            runCount += 1
            runMins += r.minutes

            durations += r.minutes
            runningCounts += runCount.toDouble()
            packages += r.pkg
            gaps += if (i == 0) 60.0 else max(0.0, (r.startMs - rows[i - 1].endMs) / 60_000.0)

            val bd = baseline(r.endDate, minsByDate)
            durRatios += if (bd != null && bd > 0.0) runMins / bd else 1.0
            val bf = baseline(r.endDate, countByDate)
            freqRatios += if (bf != null && bf > 0.0) runCount / bf else 1.0

            val t = r.start
            val inClass = classBlocksFor(r.startDate).any { InterventionEngine.isInWindow(t, it.start, it.end) }
            if (inClass || t.hour in 0..5) conflicts++
            if (InterventionEngine.isInWindow(t, wake, wake.plusMinutes(30))) mornings++
            if (InterventionEngine.isInWindow(t, sleep.minusMinutes(30), wake)) bedtimes++
            sinceWake += (t.toSecondOfDay() - wake.toSecondOfDay()) / 60.0
            untilSleep += (sleep.toSecondOfDay() - t.toSecondOfDay()) / 60.0
        }
        if (durations.isEmpty()) return null

        val n = durations.size.toDouble()
        val earlyMins = dailyMins.take(HALF).average()
        val lateMins = dailyMins.takeLast(HALF).average()
        val earlyCnt = dailyCount.take(HALF).average()
        val lateCnt = dailyCount.takeLast(HALF).average()
        val stdMins = std(dailyMins)
        val stdCnt = std(dailyCount)

        val out = mapOf(
            "avg_daily_duration" to dailyMins.average(),
            "max_daily_duration" to dailyMins.max(),
            "std_daily_duration" to stdMins,
            "avg_daily_sessions" to dailyCount.average(),
            "max_daily_sessions" to dailyCount.max(),
            "std_daily_sessions" to stdCnt,
            "active_days" to dailyCount.count { it > 0 }.toDouble(),
            "total_sessions" to n,
            "avg_session_duration" to durations.average(),
            "median_session_duration" to median(durations),
            "std_session_duration" to std(durations),
            "avg_session_gap" to gaps.average(),
            "avg_sessions_recorded" to runningCounts.average(),
            "schedule_conflict_rate" to conflicts / n,
            "morning_usage_rate" to mornings / n,
            "bedtime_usage_rate" to bedtimes / n,
            "avg_minutes_since_wake" to sinceWake.average(),
            "avg_minutes_until_sleep" to untilSleep.average(),
            "avg_baseline_duration_ratio" to durRatios.average(),
            "avg_baseline_frequency_ratio" to freqRatios.average(),
            "unique_apps" to packages.size.toDouble(),
            "early_avg_duration" to earlyMins,
            "late_avg_duration" to lateMins,
            "early_avg_sessions" to earlyCnt,
            "late_avg_sessions" to lateCnt,
            "duration_change" to lateMins - earlyMins,
            "session_change" to lateCnt - earlyCnt,
            "weekly_duration_variability" to stdMins,
            "weekly_session_variability" to stdCnt
        )
        check(out.keys == RandomForestPredictor.FEATURE_NAMES.toSet()) { "Feature names differ from the model's list" }
        return out
    }

    /** Pattern score for each of the last [days] complete days, each from its own 28-day window. */
    fun scoreHistory(
        sessions: List<BehavioralSession>,
        predictor: RandomForestPredictor,
        lastDay: LocalDate,
        days: Int,
        wake: LocalTime,
        sleep: LocalTime,
        classBlocksFor: (LocalDate) -> List<ClassBlock>
    ): List<Pair<LocalDate, Double>> =
        (days - 1 downTo 0).mapNotNull { back ->
            val d = lastDay.minusDays(back.toLong())
            val f = extractFeatures(sessions, d, wake, sleep, classBlocksFor) ?: return@mapNotNull null
            predictor.predict(f)?.let { d to it.probability }
        }

    private fun median(v: List<Double>): Double {
        val s = v.sorted()
        val m = s.size / 2
        return if (s.size % 2 == 0) (s[m - 1] + s[m]) / 2.0 else s[m]
    }

    /** Sample standard deviation (n - 1). */
    private fun std(v: List<Double>): Double {
        if (v.size < 2) return 0.0
        val avg = v.average()
        return sqrt(v.sumOf { (it - avg) * (it - avg) } / (v.size - 1))
    }
}