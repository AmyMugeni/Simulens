package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simulens.app.data.local.entity.InterventionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionLogDao {

    @Insert
    suspend fun insertLog(log: InterventionLogEntity): Long

    @Query("UPDATE intervention_logs SET wasDismissed = :dismissed WHERE id = :id")
    suspend fun setDismissed(id: Long, dismissed: Boolean)

    @Query("SELECT * FROM intervention_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<InterventionLogEntity>>

    @Query("SELECT COUNT(*) FROM intervention_logs WHERE timestamp >= :startTime")
    suspend fun getInterventionCountSince(startTime: Long): Int

    @Query("DELETE FROM intervention_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM intervention_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}