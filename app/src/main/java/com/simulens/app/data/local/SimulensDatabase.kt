package com.simulens.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.simulens.app.data.local.dao.BehavioralSessionDao
import com.simulens.app.data.local.dao.InterventionLogDao
import com.simulens.app.data.local.dao.TimetableDao
import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.local.entity.DailyTrackingCoverageEntity
import com.simulens.app.data.local.dao.DailyTrackingCoverageDao

@Database(
    entities = [InterventionLogEntity::class, DailyTrackingCoverageEntity::class, TimetableEntry::class, BehavioralSession::class],
    version = 7,
    exportSchema = false
)
abstract class SimulensDatabase : RoomDatabase() {

    abstract fun interventionLogDao(): InterventionLogDao
    abstract fun timetableDao(): TimetableDao
    abstract fun behavioralSessionDao(): BehavioralSessionDao
    abstract fun dailyTrackingCoverageDao(): DailyTrackingCoverageDao


    companion object {
        @Volatile
        private var INSTANCE: SimulensDatabase? = null

        fun getDatabase(context: Context): SimulensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SimulensDatabase::class.java,
                    "simulens_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}