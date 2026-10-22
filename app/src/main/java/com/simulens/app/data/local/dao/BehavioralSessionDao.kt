package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.simulens.app.data.local.entity.BehavioralSession
import kotlinx.coroutines.flow.Flow

@Dao
interface BehavioralSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: BehavioralSession): Long

    @Query("SELECT * FROM behavioral_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<BehavioralSession>>

    @Query("SELECT * FROM behavioral_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): BehavioralSession?

    @Query("SELECT COUNT(*) FROM behavioral_sessions WHERE timestamp >= :startOfDayTimestamp")
    suspend fun getSessionCountToday(startOfDayTimestamp: Long): Int

    @Delete
    suspend fun deleteSession(session: BehavioralSession)

    @Query("DELETE FROM behavioral_sessions")
    suspend fun deleteAllSessions()

    @Query("SELECT COUNT(*) FROM behavioral_sessions")
    suspend fun getTotalSessionCount(): Int

    @Query("SELECT COALESCE(SUM(elapsedDuration), 0.0) FROM behavioral_sessions")
    suspend fun getTotalDuration(): Double

    @Query("SELECT MIN(timestamp) FROM behavioral_sessions")
    suspend fun getFirstTimestamp(): Long?

    @Query("SELECT * FROM behavioral_sessions WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getSessionsSince(since: Long): List<BehavioralSession>

    @Query(
        """
    SELECT COALESCE(SUM(elapsedDuration), 0.0)
    FROM behavioral_sessions
    WHERE timestamp >= :startOfDayTimestamp
      AND timestamp < :endOfDayTimestamp
    """
    )
    suspend fun getTotalDurationBetween(
        startOfDayTimestamp: Long,
        endOfDayTimestamp: Long
    ): Double

    @Query(
        """
    SELECT COUNT(*)
    FROM behavioral_sessions
    WHERE timestamp >= :startOfDayTimestamp
      AND timestamp < :endOfDayTimestamp
    """
    )
    suspend fun getSessionCountBetween(
        startOfDayTimestamp: Long,
        endOfDayTimestamp: Long
    ): Int



    @Transaction
    suspend fun insertSessionWithDailyTotals(
        session: BehavioralSession,
        startOfDayTimestamp: Long,
        endOfDayTimestamp: Long,
        baselineDailyMinutes: Double?,
        baselineSessionsPerDay: Double?
    ): Long {
        val previousDuration = getTotalDurationBetween(
            startOfDayTimestamp,
            endOfDayTimestamp
        )

        val previousCount = getSessionCountBetween(
            startOfDayTimestamp,
            endOfDayTimestamp
        )

        val currentSessionsToday = previousCount + 1
        val currentDurationToday =
            previousDuration + session.elapsedDuration

        val durationRatio =
            if (baselineDailyMinutes != null &&
                baselineDailyMinutes > 0.0
            ) {
                currentDurationToday / baselineDailyMinutes
            } else {
                1.0
            }

        val frequencyRatio =
            if (baselineSessionsPerDay != null &&
                baselineSessionsPerDay > 0.0
            ) {
                currentSessionsToday.toDouble() /
                        baselineSessionsPerDay
            } else {
                1.0
            }

        val updatedSession = session.copy(
            sessionsToday = currentSessionsToday,
            dailySocialMediaDuration = currentDurationToday,
            baselineDurationRatio = durationRatio,
            baselineFrequencyRatio = frequencyRatio
        )

        return insertSession(updatedSession)
    }



}