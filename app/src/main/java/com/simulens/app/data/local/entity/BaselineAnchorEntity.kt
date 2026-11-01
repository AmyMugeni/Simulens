package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "baseline_anchors")
data class BaselineAnchorEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAtEpochMs: Long,
    val windowStart: String,          // ISO date, inclusive
    val windowEndExclusive: String,   // ISO date
    val activeDays: Int,
    val medianDailyMins: Double,
    val medianDailySessions: Double,
    val medianSessionMins: Double,
    val lateNightShare: Double,
    val bsmasTotal: Int?,
    val bsmasTakenAtEpochMs: Long?
)