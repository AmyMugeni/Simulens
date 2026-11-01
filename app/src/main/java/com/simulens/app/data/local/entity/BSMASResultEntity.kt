package com.simulens.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "bsmas_results")
data class BsmasResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val takenAtEpochMs: Long,
    val item1: Int,
    val item2: Int,
    val item3: Int,
    val item4: Int,
    val item5: Int,
    val item6: Int,
    val total: Int
) {
    fun items(): List<Int> = listOf(item1, item2, item3, item4, item5, item6)

    companion object {
        fun from(answers: IntArray, takenAt: Long) = BsmasResultEntity(
            takenAtEpochMs = takenAt,
            item1 = answers[0], item2 = answers[1], item3 = answers[2],
            item4 = answers[3], item5 = answers[4], item6 = answers[5],
            total = answers.sum()
        )
    }
}