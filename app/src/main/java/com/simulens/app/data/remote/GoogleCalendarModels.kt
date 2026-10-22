package com.simulens.app.data.remote

import com.google.gson.annotations.SerializedName

data class GoogleCalendarEventListResponse(
    @SerializedName("items") val items: List<GoogleCalendarEventItem> = emptyList(),
    @SerializedName("nextPageToken") val nextPageToken: String? = null
)

data class GoogleCalendarEventItem(
    @SerializedName("id") val id: String? = null,
    @SerializedName("summary") val summary: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("start") val start: EventDateTime? = null,
    @SerializedName("end") val end: EventDateTime? = null,
    @SerializedName("recurrence") val recurrence: List<String>? = null,
    @SerializedName("status") val status: String? = null
)

data class EventDateTime(
    @SerializedName("dateTime") val dateTime: String? = null, // RFC3339 e.g. "2026-10-16T10:00:00+03:00"
    @SerializedName("date") val date: String? = null,         // "YYYY-MM-DD" for all-day events
    @SerializedName("timeZone") val timeZone: String? = null
)