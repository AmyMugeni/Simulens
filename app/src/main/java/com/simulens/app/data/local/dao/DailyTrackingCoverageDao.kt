
package com.simulens.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.simulens.app.data.local.entity.DailyTrackingCoverageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyTrackingCoverageDao {

    @Query("""
        SELECT * FROM daily_tracking_coverage
        WHERE date = :date
        LIMIT 1
    """)
    suspend fun getByDate(
        date: String
    ): DailyTrackingCoverageEntity?

    @Query("""
        SELECT * FROM daily_tracking_coverage
        WHERE date >= :startDate AND date <= :endDate
        ORDER BY date ASC
    """)
    suspend fun getBetweenDates(
        startDate: String,
        endDate: String
    ): List<DailyTrackingCoverageEntity>

    @Query("""
        SELECT * FROM daily_tracking_coverage
        WHERE date = :date
        LIMIT 1
    """)
    fun observeDate(
        date: String
    ): Flow<DailyTrackingCoverageEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(
        record: DailyTrackingCoverageEntity
    )
    @Query("""
    SELECT * FROM daily_tracking_coverage
    ORDER BY date ASC
""")
    suspend fun getAllRecords(): List<DailyTrackingCoverageEntity>
    @Query("""
        UPDATE daily_tracking_coverage
        SET assessed = 1,
            trackingReliable = :reliable
        WHERE date = :date
    """)
    suspend fun markAssessed(
        date: String,
        reliable: Boolean
    )

    @Query("""
        DELETE FROM daily_tracking_coverage
        WHERE date < :cutoffDate
    """)
    suspend fun deleteBefore(
        cutoffDate: String
    )
}
