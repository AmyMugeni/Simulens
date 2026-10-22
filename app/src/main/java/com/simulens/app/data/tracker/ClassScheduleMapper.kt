package com.simulens.app.data.tracker

import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.domain.rules.intervention.ClassBlock
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Turns stored calendar entries into concrete class blocks for a given date. */
object ClassScheduleMapper {

    fun blocksFor(date: LocalDate, entries: List<TimetableEntry>): List<ClassBlock> {
        if (entries.isEmpty()) return emptyList()

        val fullDay = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
        val shortDay = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

        val dayEntries = entries.filter { entry ->
            val day = entry.dayOfWeek.trim()
            val matchesDay = day.isBlank() || day.equals(fullDay, ignoreCase = true) || day.equals(shortDay, ignoreCase = true)

            val matchesIsoDate = if (entry.startIso.isNotBlank()) {
                try {
                    val zdt = ZonedDateTime.parse(entry.startIso)
                    zdt.toLocalDate() == date
                } catch (e: Exception) {
                    false
                }
            } else false

            matchesDay || matchesIsoDate
        }

        val blocks = dayEntries.mapNotNull { entry ->
            val start = parseTime(entry.startTime)
            val end = parseTime(entry.endTime)
            if (start != null && end != null && start.isBefore(end)) {
                ClassBlock(start, end, entry.summary)
            } else null
        }

        // Merge adjacent or overlapping slots of the same subject
        val sorted = blocks.sortedBy { it.start }
        val merged = mutableListOf<ClassBlock>()

        for (b in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.subject == b.subject && !b.start.isAfter(last.end)) {
                val maxEnd = if (b.end.isAfter(last.end)) b.end else last.end
                merged[merged.lastIndex] = last.copy(end = maxEnd)
            } else {
                merged.add(b)
            }
        }

        return merged
    }

    private fun parseTime(timeStr: String): LocalTime? {
        if (timeStr.isBlank()) return null
        return try {
            val parts = timeStr.trim().split(":")
            if (parts.size >= 2) {
                LocalTime.of(parts[0].toInt(), parts[1].toInt())
            } else null
        } catch (e: Exception) {
            null
        }
    }
}