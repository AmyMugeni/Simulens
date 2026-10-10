package com.cognilens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.OnConflictStrategy
import com.cognilens.app.data.local.entity.TimetableEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface TimetableDao {

    // Insert new entries, overwriting the old schedule table if a match exists
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimetable(timetable: List<TimetableEntry>)

    // Read the complete timetable live as a stream to feed into Jetpack Compose
    @Query("SELECT * FROM timetable_entries")
    fun getTimetableFlow(): Flow<List<TimetableEntry>>

    // Clear the timetable if the user decides to upload a new version
    @Query("DELETE FROM timetable_entries")
    suspend fun clearTimetable()
}