package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "intervention_logs")
data class InterventionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val packageName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val triggerReason: String,
    val sessionDurationMins: Float,
    val reopenIntervalMins: Float,
    val wasDismissed: Boolean = false
)