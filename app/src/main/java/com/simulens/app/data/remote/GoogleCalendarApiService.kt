package com.simulens.app.data.remote

import retrofit2.Response
import retrofit2.http.*

interface GoogleCalendarApiService {

    @GET("calendars/{calendarId}/events")
    suspend fun getEvents(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String = "primary",
        @Query("timeMin") timeMin: String? = null,
        @Query("timeMax") timeMax: String? = null,
        @Query("singleEvents") singleEvents: Boolean = true,
        @Query("orderBy") orderBy: String = "startTime"
    ): Response<GoogleCalendarEventListResponse>

    @POST("calendars/{calendarId}/events")
    suspend fun createEvent(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String = "primary",
        @Body event: GoogleCalendarEventItem
    ): Response<GoogleCalendarEventItem>

    @PATCH("calendars/{calendarId}/events/{eventId}")
    suspend fun updateEvent(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String = "primary",
        @Path("eventId") eventId: String,
        @Body event: GoogleCalendarEventItem
    ): Response<GoogleCalendarEventItem>

    @DELETE("calendars/{calendarId}/events/{eventId}")
    suspend fun deleteEvent(
        @Header("Authorization") authHeader: String,
        @Path("calendarId") calendarId: String = "primary",
        @Path("eventId") eventId: String
    ): Response<Unit>
}