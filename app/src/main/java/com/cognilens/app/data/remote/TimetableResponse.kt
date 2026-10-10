package com.cognilens.app.data.remote

import com.google.gson.annotations.SerializedName

data class TimetableResponse(
    @SerializedName("status") val status: String,
    @SerializedName("filename") val filename: String,
    @SerializedName("timetable") val timetable: List<Map<String, String>>
)
