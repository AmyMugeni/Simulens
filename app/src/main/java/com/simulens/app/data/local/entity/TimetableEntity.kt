package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "timetable_entries")
data class TimetableEntry(
    @PrimaryKey val id: String, // Google Calendar Event ID or local UUID
    val summary: String,       // e.g. "Calculus Lecture"
    val description: String = "",
    val location: String = "",
    val dayOfWeek: String = "", // "Monday", "Tuesday", etc.
    val startTime: String = "",  // "HH:mm" e.g. "10:00"
    val endTime: String = "",    // "HH:mm" e.g. "11:00"
    val startIso: String = "",   // Full ISO e.g. "2026-10-16T10:00:00+03:00"
    val endIso: String = "",     // Full ISO e.g. "2026-10-16T11:00:00+03:00"
    val isRecurring: Boolean = false,
    val recurrenceRule: String = "",
    val lastUpdated: Long = System.currentTimeMillis()
)

private val dayOfWeekOrder = mapOf(
    "monday" to 1, "tuesday" to 2, "wednesday" to 3, "thursday" to 4,
    "friday" to 5, "saturday" to 6, "sunday" to 7,
    "mon" to 1, "tue" to 2, "wed" to 3, "thu" to 4, "fri" to 5, "sat" to 6, "sun" to 7
)

/** Deduplicates entries by weekly schedule key: (summary, dayOfWeek, startTime, endTime). */
fun List<TimetableEntry>.distinctWeeklySchedule(): List<TimetableEntry> {
    return this.distinctBy {
        val summaryKey = it.summary.trim().lowercase()
        val dayKey = it.dayOfWeek.trim().lowercase()
        val timeKey = "${it.startTime.trim()}-${it.endTime.trim()}"
        "$summaryKey|$dayKey|$timeKey"
    }
}

/** Sorts entries by Day of the Week (Monday to Sunday) and then by start time. */
fun List<TimetableEntry>.sortedByWeeklyOrder(): List<TimetableEntry> {
    return this.distinctWeeklySchedule().sortedWith(
        compareBy<TimetableEntry> { entry ->
            dayOfWeekOrder[entry.dayOfWeek.trim().lowercase()] ?: 8
        }.thenBy { entry ->
            entry.startTime.trim()
        }
    )
}
