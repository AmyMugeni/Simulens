package com.cognilens.app.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

data class UserProfile(
    val username: String = "",
    val wakeUpTime: LocalTime = LocalTime.of(7, 0),
    val sleepTime: LocalTime = LocalTime.of(23, 0),
    val morningBufferMinutes: Long = 30L,
    val bedtimeBufferMinutes: Long = 30L,
    val workDays: Set<DayOfWeek> = setOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY
    ),
    val workStartTime: LocalTime = LocalTime.of(9, 0),
    val workEndTime: LocalTime = LocalTime.of(17, 0),
    val bsmasScore: Int = 18,
    val isTimetableConfigured: Boolean = false,
    val isSleepConfigured: Boolean = false // Track explicit user confirmation of sleep schedule
)