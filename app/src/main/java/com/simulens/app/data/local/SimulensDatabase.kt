package com.simulens.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.simulens.app.data.chat.ChatDao
import com.simulens.app.data.chat.ChatMessageEntity
import com.simulens.app.data.local.dao.BaselineAnchorDao
import com.simulens.app.data.local.dao.BehavioralSessionDao
import com.simulens.app.data.local.dao.BsmasResultDao
import com.simulens.app.data.local.dao.DailyTrackingCoverageDao
import com.simulens.app.data.local.dao.InterventionLogDao
import com.simulens.app.data.local.dao.TimetableDao
import com.simulens.app.data.local.entity.BehavioralSession
import com.simulens.app.data.local.entity.BsmasResultEntity
import com.simulens.app.data.local.entity.DailyTrackingCoverageEntity
import com.simulens.app.data.local.entity.InterventionLogEntity
import com.simulens.app.data.local.entity.TimetableEntry
import com.simulens.app.data.local.entity.BaselineAnchorEntity

@Database(
    entities = [
        InterventionLogEntity::class,
        DailyTrackingCoverageEntity::class,
        TimetableEntry::class,
        BehavioralSession::class,
        BsmasResultEntity::class,
        BaselineAnchorEntity::class,
        ChatMessageEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class SimulensDatabase : RoomDatabase() {

    abstract fun interventionLogDao(): InterventionLogDao
    abstract fun timetableDao(): TimetableDao
    abstract fun behavioralSessionDao(): BehavioralSessionDao
    abstract fun dailyTrackingCoverageDao(): DailyTrackingCoverageDao
    abstract fun bsmasResultDao(): BsmasResultDao
    abstract fun baselineAnchorDao(): BaselineAnchorDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile
        private var INSTANCE: SimulensDatabase? = null

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS baseline_anchors (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                createdAtEpochMs INTEGER NOT NULL,
                windowStart TEXT NOT NULL,
                windowEndExclusive TEXT NOT NULL,
                activeDays INTEGER NOT NULL,
                medianDailyMins REAL NOT NULL,
                medianDailySessions REAL NOT NULL,
                medianSessionMins REAL NOT NULL,
                lateNightShare REAL NOT NULL,
                bsmasTotal INTEGER,
                bsmasTakenAtEpochMs INTEGER)"""
                )
                db.execSQL(
                    """CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fromUser INTEGER NOT NULL,
                text TEXT NOT NULL,
                mode TEXT NOT NULL,
                sourcesJson TEXT NOT NULL,
                createdAt INTEGER NOT NULL)"""
                )
            }
        }
// .addMigrations(MIGRATION_8_9, MIGRATION_9_10)

        fun getDatabase(context: Context): SimulensDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SimulensDatabase::class.java,
                    "simulens_database"
                )
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration() // dev only; remove before release
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}