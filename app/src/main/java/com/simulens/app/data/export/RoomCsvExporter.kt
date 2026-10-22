
package com.simulens.app.data.export

import android.content.Context
import android.util.Log
import com.simulens.app.data.local.dao.BehavioralSessionDao
import com.simulens.app.data.local.dao.DailyTrackingCoverageDao
import kotlinx.coroutines.flow.first
import java.io.File

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
                writer.appendLine(
                    row.joinToString(",") { csv(it) }
                )
            }
        }
    }

    suspend fun export(
        context: Context,
        sessionDao: BehavioralSessionDao,
        coverageDao: DailyTrackingCoverageDao
    ): List<File> {

        val directory = File(context.filesDir, "ml_export")
        check(directory.exists() || directory.mkdirs()) {
            "Could not create export directory"
        }

        // Export behavioral sessions.
        val sessions = sessionDao.getAllSessions().first()

        val sessionFile = File(
            directory,
            "behavioral_sessions.csv"
        )

        writeCsv(
            file = sessionFile,
            headers = listOf(
                "id",
                "timestamp",
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
                "label"
            ),
            rows = sessions.map { session ->
                listOf(
                    session.id,
                    session.timestamp,
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
                    session.label
                )
            }
        )

        // Export daily tracking coverage.
        val coverageRecords = coverageDao.getAllRecords()

        val coverageFile = File(
            directory,
            "daily_tracking_coverage.csv"
        )

        writeCsv(
            file = coverageFile,
            headers = listOf(
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
            "Export complete: ${sessions.size} sessions, " +
                    "${coverageRecords.size} coverage records. " +
                    "Directory: ${directory.absolutePath}"
        )

        return listOf(sessionFile, coverageFile)
    }
}
