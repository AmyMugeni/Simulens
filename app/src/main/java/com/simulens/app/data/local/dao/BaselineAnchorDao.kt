package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.simulens.app.data.local.entity.BaselineAnchorEntity
import kotlinx.coroutines.flow.Flow


@Dao
interface BaselineAnchorDao {
    @Insert suspend fun insert(a: BaselineAnchorEntity): Long

    @Query("SELECT * FROM baseline_anchors ORDER BY createdAtEpochMs DESC LIMIT 1")
    suspend fun getActive(): BaselineAnchorEntity?

    @Query("SELECT * FROM baseline_anchors ORDER BY createdAtEpochMs DESC LIMIT 1")
    fun observeActive(): Flow<BaselineAnchorEntity?>

    @Query("UPDATE baseline_anchors SET bsmasTotal = :total, bsmasTakenAtEpochMs = :at WHERE id = :id")
    suspend fun setBsmas(id: Long, total: Int, at: Long)

    @Query("DELETE FROM baseline_anchors")
    suspend fun clearAll()
}