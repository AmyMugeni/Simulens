package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "behavioral_sessions")
data class BehavioralSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val elapsedDuration: Double,
    val previousSessionGap: Double,
    val sessionsToday: Int,
    val dailySocialMediaDuration: Double,
    val timeOfDay: Int,
    val dayOfWeek: Int,
    val scheduleConflict: Int,
    val minutesSinceWake: Int,
    val minutesUntilSleep: Int,
    val morningSocialMedia: Int,
    val bedtimeSocialMedia: Int,
    val baselineDurationRatio: Double,
    val baselineFrequencyRatio: Double,
    val label: Int // 0: INTENTIONAL, 1: POTENTIALLY_COMPULSIVE, 2: UNCERTAIN
)