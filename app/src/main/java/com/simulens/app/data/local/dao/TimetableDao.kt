package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simulens.app.data.local.entity.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: List<TimetableEntry>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: TimetableEntry)

    @Query("SELECT * FROM timetable_entries")
    fun getTimetableFlow(): Flow<List<TimetableEntry>>

    @Query("SELECT * FROM timetable_entries")
    suspend fun getAllEntries(): List<TimetableEntry>

    @Query("DELETE FROM timetable_entries WHERE id = :id")
    suspend fun deleteEntryById(id: String)

    @Query("DELETE FROM timetable_entries")
    suspend fun clearTimetable()
}