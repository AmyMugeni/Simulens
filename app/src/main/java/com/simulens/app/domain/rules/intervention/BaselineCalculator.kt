
package com.simulens.app.domain.rules.intervention

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object BaselineCalculator {

    private const val MIN_VALID_DAYS = 28
    private const val MIN_SAME_WEEKDAY_SAMPLES = 2

    fun compute(
        history: List<HistoricalSession>,
        trackingRecords: List<DailyTrackingRecord> = emptyList(),
        now: LocalDateTime,
        zone: ZoneId = ZoneId.systemDefault(),
        lookbackDays: Int = 28
    ): UsageSnapshot {
        require(lookbackDays > 0) {
            "lookbackDays must be greater than zero"
        }

        val today = now.toLocalDate()
        val nowEpochMs = now.atZone(zone).toInstant().toEpochMilli()
        val dayStart = today.atStartOfDay(zone)
            .toInstant().toEpochMilli()

        // Ignore invalid sessions and sessions that end in the future.
        val validHistory = history.filter {
            it.durationMins.isFinite() &&
                    it.durationMins > 0.0 &&
                    it.endEpochMs <= nowEpochMs
        }

        // Index sessions by their local end date.
        val sessionsByDate = validHistory.groupBy {
            Instant.ofEpochMilli(it.endEpochMs)
                .atZone(zone)
                .toLocalDate()
        }

        // Only explicitly reliable dates can be used as evidence.
        val reliabilityByDate = trackingRecords
            .groupBy { it.date }
            .mapValues { (_, records) ->
                records.any { it.trackingReliable }
            }

        // Use preceding calendar days up to lookbackDays, excluding today.
        val candidateDates = (1..lookbackDays)
            .map { today.minusDays(it.toLong()) }

        // Determine earliest date of observed activity (session or coverage record)
        val earliestSessionDate = sessionsByDate.keys.filter { it < today }.minOrNull()
        val earliestCoverageDate = trackingRecords
            .map { it.date }
            .filter { it < today }
            .minOrNull()

        val earliestDate = listOfNotNull(earliestSessionDate, earliestCoverageDate).minOrNull()

        val validDates = if (earliestDate != null) {
            val maxLookbackDate = today.minusDays(lookbackDays.toLong())
            val startDate = if (earliestDate.isBefore(maxLookbackDate)) maxLookbackDate else earliestDate

            candidateDates.filter { date ->
                !date.isBefore(startDate) && reliabilityByDate[date] != false
            }
        } else {
            emptyList()
        }

        val dailyMinutes = validDates.associateWith { date ->
            sessionsByDate[date].orEmpty().sumOf {
                it.durationMins
            }
        }

        val dailyCounts = validDates.associateWith { date ->
            sessionsByDate[date].orEmpty().size
        }

        val historicalSessions = validDates.flatMap { date ->
            sessionsByDate[date].orEmpty()
        }

        val dailyMinuteValues = dailyMinutes.values.toList()
        val dailyCountValues = dailyCounts.values

        val medianDailyMinutes =
            dailyMinuteValues.takeIf { it.isNotEmpty() }?.median()

        val meanDailyMinutes =
            dailyMinuteValues.takeIf { it.isNotEmpty() }?.average()

        val medianSessionsPerDay =
            dailyCountValues.map { it.toDouble() }
                .takeIf { it.isNotEmpty() }
                ?.median()

        val medianSessionMinutes =
            historicalSessions.map { it.durationMins }
                .takeIf { it.isNotEmpty() }
                ?.median()

        val currentMinuteOfDay = now.hour * 60 + now.minute

        val sameWeekdayDates = validDates.filter {
            it.dayOfWeek == today.dayOfWeek
        }

        // Use weekday-matched observations when sufficiently numerous.
        // Otherwise, fall back to all valid days if there are any.
        val comparisonDates = when {
            sameWeekdayDates.size >= MIN_SAME_WEEKDAY_SAMPLES ->
                sameWeekdayDates

            validDates.isNotEmpty() ->
                validDates

            else -> emptyList()
        }

        val typicalMinutesByNow =
            comparisonDates.map { date ->
                minutesByTime(
                    date = date,
                    sessions = sessionsByDate[date].orEmpty(),
                    cutoffMinute = currentMinuteOfDay,
                    zone = zone
                )
            }.takeIf { it.isNotEmpty() }?.average()

        val meanSessionMinsValue = historicalSessions.map { it.durationMins }
            .takeIf { it.isNotEmpty() }
            ?.average() ?: 0.0

        val baselineReliable = validDates.size >= MIN_VALID_DAYS

        val baseline = UsageBaseline(
            calculatedAtEpochMs = nowEpochMs,
            lookbackDays = lookbackDays,
            validDays = validDates.size,
            validSessionCount = historicalSessions.size,
            isReliable = baselineReliable,
            medianDailyMinutes = medianDailyMinutes,
            meanDailyMinutes = meanDailyMinutes,
            medianSessionsPerDay = medianSessionsPerDay,
            medianSessionMinutes = medianSessionMinutes,
            medianMinutesByCurrentTime = typicalMinutesByNow,
            sameWeekdaySampleCount = sameWeekdayDates.size,
            daysOfData = validDates.size,
            sampleSessions = historicalSessions.size,
            meanSessionMins = meanSessionMinsValue,
            typicalMinutesByNow = typicalMinutesByNow
        )

        // Compatibility note: this attributes a session to its end date.
        // Sessions crossing midnight need explicit start/end overlap
        // allocation for fully accurate daily totals.
        val todaySessions = sessionsByDate[today].orEmpty()
        val todayCompletedMinutes = todaySessions.sumOf {
            it.durationMins
        }

        val todayTrackingReliable =
            reliabilityByDate[today] == true

        return UsageSnapshot(
            baseline = baseline,
            current = CurrentUsageSnapshot(
                date = today,
                completedUsageMinutes = todayCompletedMinutes,
                completedSessionCount = todaySessions.size,
                trackingReliable = todayTrackingReliable
            )
        )
    }

    /**
     * Estimate how much of each session occurred before the
     * specified local-time cutoff.
     *
     * With the current schema, start time is inferred from duration.
     * Explicit session start timestamps are preferable.
     */
    private fun minutesByTime(
        date: LocalDate,
        sessions: List<HistoricalSession>,
        cutoffMinute: Int,
        zone: ZoneId
    ): Double {
        val dayStart = date.atStartOfDay(zone)
            .toInstant().toEpochMilli()

        val cutoff = date.atStartOfDay(zone)
            .plusMinutes(cutoffMinute.toLong())
            .toInstant().toEpochMilli()

        return sessions.sumOf { session ->
            val end = session.endEpochMs
            val durationMs = (session.durationMins * 60_000.0)
                .toLong()
            val start = end - durationMs

            val overlapStart = maxOf(start, dayStart)
            val overlapEnd = minOf(end, cutoff)

            if (overlapEnd > overlapStart) {
                (overlapEnd - overlapStart) / 60_000.0
            } else {
                0.0
            }
        }
    }

    private fun List<Double>.medianOrNull(): Double? =
        if (isEmpty()) null else median()

    private fun List<Double>.median(): Double {
        require(isNotEmpty())

        val sorted = this.sorted()
        val middle = sorted.size / 2

        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2.0
        } else {
            sorted[middle]
        }
    }
}
