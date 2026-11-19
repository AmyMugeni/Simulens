package com.simulens.app.data.remote

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    private val okHttpClient by lazy {
        HttpLoggingInterceptor().run {
            level = HttpLoggingInterceptor.Level.BODY
            OkHttpClient.Builder()
                .addInterceptor(this)
                .connectTimeout(100, TimeUnit.SECONDS)
                .readTimeout(100, TimeUnit.SECONDS)
                .writeTimeout(100, TimeUnit.SECONDS)
                .callTimeout(100, TimeUnit.SECONDS)
                .build()
        }
    }

    private val googleCalendarRetrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/calendar/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val googleCalendarApiService: GoogleCalendarApiService by lazy {
        googleCalendarRetrofit.create(GoogleCalendarApiService::class.java)
    }
}