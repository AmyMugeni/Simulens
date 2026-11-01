package com.simulens.app.data.export

import android.content.Context
import android.util.Log
import com.simulens.app.data.local.dao.BehavioralSessionDao
import com.simulens.app.data.local.dao.BsmasResultDao
import com.simulens.app.data.local.dao.DailyTrackingCoverageDao
import com.simulens.app.data.repositories.UserProfileRepository
import com.simulens.app.domain.bsmas.BsmasScoring
import kotlinx.coroutines.flow.first
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object RoomCsvExporter {

    private const val TAG = "RoomCsvExporter"

    private fun csv(value: Any?): String {
        val text = value?.toString() ?: ""
        return "\"" + text.replace("\"", "\"\"") + "\""
    }

    private fun writeCsv(
        file: File,
        headers: List<String>,
        rows: List<List<Any?>>
    ) {
        file.bufferedWriter().use { writer ->
            writer.appendLine(headers.joinToString(",") { csv(it) })
            rows.forEach { row ->
                writer.appendLine(row.joinToString(",") { csv(it) })
            }
        }
    }

    private fun getAnonymousUserId(username: String): String {
        val name = username.trim().ifBlank { "simulens_user" }
        val hash = name.toByteArray(Charsets.UTF_8).fold(0) { acc, byte -> (acc * 31) + byte.toInt() }
        val hex = Math.abs(hash).toString(16).padStart(8, '0')
        return "anon_$hex"
    }

    private fun epochMsToDateString(epochMs: Long): String {
        return runCatching {
            Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }.getOrDefault("1970-01-01")
    }

    private fun epochMsToDateTimeString(epochMs: Long): String {
        return runCatching {
            Instant.ofEpochMilli(epochMs)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        }.getOrDefault("")
    }

    suspend fun export(
        context: Context,
        sessionDao: BehavioralSessionDao,
        coverageDao: DailyTrackingCoverageDao,
        bsmasDao: BsmasResultDao,
        userProfileRepository: UserProfileRepository
    ): List<File> {

        val directory = File(context.filesDir, "ml_export")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create export directory"
        }

        val profile = userProfileRepository.userProfileFlow.first()
        val anonUserId = getAnonymousUserId(profile.username)
        val sessions = sessionDao.getAllSessions().first()
        val coverageRecords = coverageDao.getAllRecords()
        val bsmasList = bsmasDao.observeAll().first().sortedBy { it.takenAtEpochMs }

        // 1. Export behavioral sessions with anonymous user ID and BSMAS scores
        val sessionFile = File(directory, "behavioral_sessions.csv")
        writeCsv(
            file = sessionFile,
            headers = listOf(
                "anonymousUserId",
                "id",
                "timestamp",
                "dateTime",
                "packageName",
                "elapsedDuration",
                "previousSessionGap",
                "sessionsToday",
                "dailySocialMediaDuration",
                "timeOfDay",
                "dayOfWeek",
                "scheduleConflict",
                "minutesSinceWake",
                "minutesUntilSleep",
                "morningSocialMedia",
                "bedtimeSocialMedia",
                "baselineDurationRatio",
                "baselineFrequencyRatio",
                "label",
                "bsmasScore",
                "bsmasBand"
            ),
            rows = sessions.map { session ->
                val bsmasAtSession = bsmasList.lastOrNull { it.takenAtEpochMs <= session.timestamp } ?: bsmasList.firstOrNull()
                val score = bsmasAtSession?.total ?: profile.bsmasScore
                val band = bsmasAtSession?.let { BsmasScoring.score(it.items())?.band?.name } ?: when {
                    score <= 12 -> "LOWER"
                    score <= 18 -> "MODERATE"
                    else -> "ELEVATED"
                }
                listOf(
                    anonUserId,
                    session.id,
                    session.timestamp,
                    epochMsToDateTimeString(session.timestamp),
                    session.packageName,
                    session.elapsedDuration,
                    session.previousSessionGap,
                    session.sessionsToday,
                    session.dailySocialMediaDuration,
                    session.timeOfDay,
                    session.dayOfWeek,
                    session.scheduleConflict,
                    session.minutesSinceWake,
                    session.minutesUntilSleep,
                    session.morningSocialMedia,
                    session.bedtimeSocialMedia,
                    session.baselineDurationRatio,
                    session.baselineFrequencyRatio,
                    session.label,
                    score,
                    band
                )
            }
        )

        // 2. Export Daily Usage Statistics Summary
        val dailyStatsFile = File(directory, "daily_statistics.csv")
        val sessionsByDate = sessions.groupBy { epochMsToDateString(it.timestamp) }
        val coverageByDate = coverageRecords.associateBy { it.date }
        val allDates = (sessionsByDate.keys + coverageByDate.keys).filter { it.isNotBlank() }.sorted()

        writeCsv(
            file = dailyStatsFile,
            headers = listOf(
                "anonymousUserId",
                "date",
                "totalScreenTimeMins",
                "totalSessionsCount",
                "socialMediaDurationMins",
                "compulsiveSessionsCount",
                "intentionalSessionsCount",
                "firstSessionTime",
                "lastSessionTime",
                "coveredDurationMs",
                "trackingReliable",
                "bsmasScore",
                "bsmasBand"
            ),
            rows = allDates.map { dateStr ->
                val daySessions = sessionsByDate[dateStr] ?: emptyList()
                val dayCoverage = coverageByDate[dateStr]

                val totalScreenTimeMins = daySessions.sumOf { it.elapsedDuration }
                val totalSessionsCount = daySessions.size
                val socialMediaMins = daySessions.maxOfOrNull { it.dailySocialMediaDuration } ?: totalScreenTimeMins
                val compulsiveCount = daySessions.count { it.label != 0 }
                val intentionalCount = daySessions.count { it.label == 0 }

                val firstTs = daySessions.minOfOrNull { it.timestamp }
                val lastTs = daySessions.maxOfOrNull { it.timestamp }

                val dayEndEpoch = runCatching {
                    LocalDate.parse(dateStr).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                }.getOrDefault(System.currentTimeMillis())

                val bsmasForDay = bsmasList.lastOrNull { it.takenAtEpochMs <= dayEndEpoch } ?: bsmasList.firstOrNull()
                val score = bsmasForDay?.total ?: profile.bsmasScore
                val band = bsmasForDay?.let { BsmasScoring.score(it.items())?.band?.name } ?: when {
                    score <= 12 -> "LOWER"
                    score <= 18 -> "MODERATE"
                    else -> "ELEVATED"
                }

                listOf(
                    anonUserId,
                    dateStr,
                    String.format(Locale.US, "%.2f", totalScreenTimeMins),
                    totalSessionsCount,
                    String.format(Locale.US, "%.2f", socialMediaMins),
                    compulsiveCount,
                    intentionalCount,
                    firstTs?.let { epochMsToDateTimeString(it) } ?: "",
                    lastTs?.let { epochMsToDateTimeString(it) } ?: "",
                    dayCoverage?.coveredDurationMs ?: 0,
                    dayCoverage?.trackingReliable ?: false,
                    score,
                    band
                )
            }
        )

        // 3. Export BSMAS Assessment History
        val bsmasFile = File(directory, "bsmas_history.csv")
        writeCsv(
            file = bsmasFile,
            headers = listOf(
                "anonymousUserId",
                "id",
                "takenAtEpochMs",
                "takenAtDate",
                "item1_salience",
                "item2_tolerance",
                "item3_mood",
                "item4_relapse",
                "item5_withdrawal",
                "item6_conflict",
                "totalScore",
                "riskBand"
            ),
            rows = bsmasList.map { entity ->
                val resultObj = BsmasScoring.score(entity.items())
                val bandStr = resultObj?.band?.name ?: when {
                    entity.total <= 12 -> "LOWER"
                    entity.total <= 18 -> "MODERATE"
                    else -> "ELEVATED"
                }
                listOf(
                    anonUserId,
                    entity.id,
                    entity.takenAtEpochMs,
                    epochMsToDateTimeString(entity.takenAtEpochMs),
                    entity.item1,
                    entity.item2,
                    entity.item3,
                    entity.item4,
                    entity.item5,
                    entity.item6,
                    entity.total,
                    bandStr
                )
            }
        )

        // 4. Export Daily Tracking Coverage
        val coverageFile = File(directory, "daily_tracking_coverage.csv")
        writeCsv(
            file = coverageFile,
            headers = listOf(
                "anonymousUserId",
                "date",
                "firstObservedAtEpochMs",
                "lastSuccessfulPollAtEpochMs",
                "coveredDurationMs",
                "successfulPollCount",
                "failedPollCount",
                "assessed",
                "trackingReliable"
            ),
            rows = coverageRecords.map { record ->
                listOf(
                    anonUserId,
                    record.date,
                    record.firstObservedAtEpochMs,
                    record.lastSuccessfulPollAtEpochMs,
                    record.coveredDurationMs,
                    record.successfulPollCount,
                    record.failedPollCount,
                    record.assessed,
                    record.trackingReliable
                )
            }
        )

        Log.i(
            TAG,
            "Export complete for user $anonUserId: ${sessions.size} sessions, " +
                    "${coverageRecords.size} coverage records, ${bsmasList.size} BSMAS records. " +
                    "Directory: ${directory.absolutePath}"
        )

        return listOf(sessionFile, dailyStatsFile, bsmasFile, coverageFile)
    }
}
