package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simulens.app.data.local.entity.BaselineAnchorEntity
import com.simulens.app.data.local.entity.BsmasResultEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BsmasResultDao {
    @Insert
    suspend fun insert(result: BsmasResultEntity): Long

    @Query("SELECT * FROM bsmas_results ORDER BY takenAtEpochMs DESC LIMIT 1")
    suspend fun getLatest(): BsmasResultEntity?

    @Query("SELECT * FROM bsmas_results ORDER BY takenAtEpochMs DESC LIMIT 1")
    fun observeLatest(): Flow<BsmasResultEntity?>

    @Query("SELECT * FROM bsmas_results ORDER BY takenAtEpochMs ASC")
    fun observeAll(): Flow<List<BsmasResultEntity>>

    @Query("DELETE FROM bsmas_results")
    suspend fun deleteAll()

    @Query("SELECT * FROM bsmas_results WHERE takenAtEpochMs <= :ts ORDER BY takenAtEpochMs DESC LIMIT 1")
    suspend fun getLatestBefore(ts: Long): BsmasResultEntity?

    @Query("SELECT * FROM bsmas_results ORDER BY takenAtEpochMs ASC LIMIT 1")
    suspend fun getFirst(): BsmasResultEntity?
}