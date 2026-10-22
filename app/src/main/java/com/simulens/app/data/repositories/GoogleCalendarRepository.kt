package com.simulens.app.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.simulens.app.data.local.dao.TimetableDao
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.local.entity.distinctWeeklySchedule
import com.simulens.app.data.remote.EventDateTime
import com.simulens.app.data.remote.GoogleCalendarApiService
import com.simulens.app.data.remote.GoogleCalendarEventItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

private val Context.calendarAuthDataStore: DataStore<Preferences> by preferencesDataStore(name = "google_calendar_auth_prefs")

class GoogleCalendarRepository(
    private val context: Context,
    private val apiService: GoogleCalendarApiService,
    private val timetableDao: TimetableDao
) {

    companion object {
        private val OAUTH_TOKEN = stringPreferencesKey("oauth_access_token")
        private val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        private val ISO_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
    }

    val savedTimetable: Flow<List<TimetableEntry>> = timetableDao.getTimetableFlow()

    val accountEmailFlow: Flow<String?> = context.calendarAuthDataStore.data.map { prefs ->
        prefs[ACCOUNT_EMAIL]
    }

    val isConnectedFlow: Flow<Boolean> = context.calendarAuthDataStore.data.map { prefs ->
        !prefs[OAUTH_TOKEN].isNull_or_blank()
    }

    private fun String?.isNull_or_blank(): Boolean = this == null || this.trim().isEmpty()

    suspend fun saveAuthToken(token: String, email: String) {
        context.calendarAuthDataStore.edit { prefs ->
            prefs[OAUTH_TOKEN] = token
            prefs[ACCOUNT_EMAIL] = email
        }
    }

    suspend fun clearAuthToken() {
        context.calendarAuthDataStore.edit { prefs ->
            prefs.remove(OAUTH_TOKEN)
            prefs.remove(ACCOUNT_EMAIL)
        }
        timetableDao.clearTimetable()
    }

    suspend fun getAccessToken(): String? {
        return context.calendarAuthDataStore.data.map { it[OAUTH_TOKEN] }.first()
    }

    /**
     * Fetches events from Google Calendar API and updates Room DB cache.
     */
    suspend fun syncCalendarEvents(): Result<List<TimetableEntry>> {
        val token = getAccessToken()
        if (token.isNullOrBlank()) {
            return Result.failure(Exception("Google Account not connected"))
        }

        return try {
            val zone = ZoneId.systemDefault()
            val timeMin = LocalDate.now().minusDays(1).atStartOfDay(zone).format(ISO_FORMATTER)
            val timeMax = LocalDate.now().plusDays(14).atStartOfDay(zone).format(ISO_FORMATTER)

            val response = apiService.getEvents(
                authHeader = "Bearer $token",
                timeMin = timeMin,
                timeMax = timeMax
            )

            if (response.isSuccessful && response.body() != null) {
                val apiEvents = response.body()!!.items
                val dbEntries = apiEvents.mapNotNull { convertToTimetableEntry(it) }
                val distinctEntries = dbEntries.distinctWeeklySchedule()

                timetableDao.clearTimetable()
                timetableDao.insertTimetable(distinctEntries)

                Result.success(distinctEntries)
            } else {
                Result.failure(Exception("Sync failed: ${response.message()} (${response.code()})"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Creates a new class/event on Google Calendar API and saves to Room DB.
     */
    suspend fun createClassEvent(
        summary: String,
        description: String = "",
        location: String = "",
        dayOfWeek: DayOfWeek,
        startTime: LocalTime,
        endTime: LocalTime
    ): Result<TimetableEntry> {
        val token = getAccessToken()
        val zone = ZoneId.systemDefault()
        val targetDate = LocalDate.now().with(dayOfWeek)
        val startDateTime = ZonedDateTime.of(targetDate, startTime, zone)
        val endDateTime = ZonedDateTime.of(targetDate, endTime, zone)

        val localEntry = TimetableEntry(
            id = "local_" + UUID.randomUUID().toString(),
            summary = summary,
            description = description,
            location = location,
            dayOfWeek = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            startTime = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            endTime = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            startIso = startDateTime.format(ISO_FORMATTER),
            endIso = endDateTime.format(ISO_FORMATTER)
        )

        // Save locally first for instant UI response
        timetableDao.insertEntry(localEntry)

        if (token.isNullOrBlank()) {
            return Result.success(localEntry)
        }

        return try {
            val requestItem = GoogleCalendarEventItem(
                summary = summary,
                description = description,
                location = location,
                start = EventDateTime(dateTime = startDateTime.format(ISO_FORMATTER)),
                end = EventDateTime(dateTime = endDateTime.format(ISO_FORMATTER))
            )

            val response = apiService.createEvent("Bearer $token", event = requestItem)
            if (response.isSuccessful && response.body() != null) {
                val syncedItem = response.body()!!
                val syncedEntry = convertToTimetableEntry(syncedItem) ?: localEntry
                timetableDao.deleteEntryById(localEntry.id)
                timetableDao.insertEntry(syncedEntry)
                Result.success(syncedEntry)
            } else {
                Result.success(localEntry)
            }
        } catch (e: Exception) {
            Result.success(localEntry)
        }
    }

    /**
     * Updates an existing event on Google Calendar API and local Room DB.
     */
    suspend fun updateClassEvent(entry: TimetableEntry): Result<TimetableEntry> {
        timetableDao.insertEntry(entry)

        val token = getAccessToken()
        if (token.isNullOrBlank() || entry.id.startsWith("local_")) {
            return Result.success(entry)
        }

        return try {
            val requestItem = GoogleCalendarEventItem(
                id = entry.id,
                summary = entry.summary,
                description = entry.description,
                location = entry.location,
                start = EventDateTime(dateTime = entry.startIso.ifBlank { null }),
                end = EventDateTime(dateTime = entry.endIso.ifBlank { null })
            )

            val response = apiService.updateEvent("Bearer $token", eventId = entry.id, event = requestItem)
            if (response.isSuccessful && response.body() != null) {
                val updatedEntry = convertToTimetableEntry(response.body()!!) ?: entry
                timetableDao.insertEntry(updatedEntry)
                Result.success(updatedEntry)
            } else {
                Result.success(entry)
            }
        } catch (e: Exception) {
            Result.success(entry)
        }
    }

    /**
     * Deletes an event from Google Calendar API and local Room DB.
     */
    suspend fun deleteClassEvent(id: String): Result<Unit> {
        timetableDao.deleteEntryById(id)

        val token = getAccessToken()
        if (token.isNullOrBlank() || id.startsWith("local_")) {
            return Result.success(Unit)
        }

        return try {
            apiService.deleteEvent("Bearer $token", eventId = id)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    private fun convertToTimetableEntry(item: GoogleCalendarEventItem): TimetableEntry? {
        val id = item.id ?: return null
        val summary = item.summary.orEmpty().ifBlank { "Untitled Event" }

        val startStr = item.start?.dateTime ?: item.start?.date
        val endStr = item.end?.dateTime ?: item.end?.date

        var dayOfWeekStr = "Monday"
        var startTimeStr = "08:00"
        var endTimeStr = "09:00"

        if (!startStr.isNullOrBlank()) {
            try {
                val zdt = ZonedDateTime.parse(startStr)
                dayOfWeekStr = zdt.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
                startTimeStr = zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                // Ignore parse errors for all-day events
            }
        }

        if (!endStr.isNullOrBlank()) {
            try {
                val zdt = ZonedDateTime.parse(endStr)
                endTimeStr = zdt.format(DateTimeFormatter.ofPattern("HH:mm"))
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        return TimetableEntry(
            id = id,
            summary = summary,
            description = item.description.orEmpty(),
            location = item.location.orEmpty(),
            dayOfWeek = dayOfWeekStr,
            startTime = startTimeStr,
            endTime = endTimeStr,
            startIso = startStr.orEmpty(),
            endIso = endStr.orEmpty(),
            isRecurring = !item.recurrence.isNullOrEmpty()
        )
    }
}