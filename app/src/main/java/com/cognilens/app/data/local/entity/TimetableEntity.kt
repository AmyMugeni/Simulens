package com.cognilens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetable_entries")
data class TimetableEntry(
    @PrimaryKey val day: String, // "Monday", "Tuesday", etc.
    val slot1: String, // 8.15 - 9.15
    val slot2: String, // 9.15 – 10.15
    val slot3: String, // 10.15 – 11.15
    val slot4: String, // 11.15 – 12.15
    val slot5: String, // 12.15 - 1.15
    val slot6: String, // 1.15 - 2.15
    val slot7: String, // 2.15 – 3.15
    val slot8: String, // 3.15 – 4.15
    val slot9: String  // 4.15 - 5.15
)