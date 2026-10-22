package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simulens.app.data.local.entity.InterventionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InterventionLogDao {

    @Insert
    suspend fun insertLog(log: InterventionLogEntity)

    @Query("SELECT * FROM intervention_logs ORDER BY timestamp DESC")
    fun getAllLogs(): Flow<List<InterventionLogEntity>>

    @Query("SELECT COUNT(*) FROM intervention_logs WHERE timestamp >= :startTime")
    suspend fun getInterventionCountSince(startTime: Long): Int

    @Query("DELETE FROM intervention_logs")
    suspend fun clearAllLogs()

    @Query("DELETE FROM intervention_logs WHERE id = :id")
    suspend fun deleteLogById(id: Long)
}