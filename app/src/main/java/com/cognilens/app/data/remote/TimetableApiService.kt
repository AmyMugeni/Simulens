package com.cognilens.app.data.remote

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface TimetableApiService {
    @Multipart
    @POST("parse-timetable")
    suspend fun uploadTimetable(
        @Part file: MultipartBody.Part
    ): Response<TimetableResponse>
}
