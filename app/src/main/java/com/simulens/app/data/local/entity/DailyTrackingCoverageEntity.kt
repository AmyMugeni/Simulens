package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_tracking_coverage")
data class DailyTrackingCoverageEntity(
    @PrimaryKey
    val date: String, // Local date in ISO format: yyyy-MM-dd

    val firstObservedAtEpochMs: Long,
    val lastSuccessfulPollAtEpochMs: Long,

    // Milliseconds during which tracking was successfully observed.
    val coveredDurationMs: Long,

    val successfulPollCount: Int,
    val failedPollCount: Int,

    // Set only after the day's coverage has been assessed.
    val assessed: Boolean = false,
    val trackingReliable: Boolean = false
)
