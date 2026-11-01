package com.simulens.app.data.tracker


import android.content.Context
import com.simulens.app.data.local.SimulensDatabase
import com.simulens.app.data.local.entity.BaselineAnchorEntity
import com.simulens.app.domain.rules.intervention.AnchorCalculator
import com.simulens.app.domain.rules.intervention.HistoricalSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

object AnchorManager {
    private const val PREFS = "simulens_anchor"
    private const val KEY_START = "start_epoch_day"

    /** Freezes the anchor once its window is complete. Safe to call repeatedly. */
    suspend fun ensureFrozen(context: Context, db: SimulensDatabase, monitored: Set<String>) {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val anchorDao = db.baselineAnchorDao()
        val bsmasDao = db.bsmasResultDao()

        val active = anchorDao.getActive()
        if (active != null) {
            // The reference BSMAS arrives late if the questionnaire was taken after the freeze.
            if (active.bsmasTotal == null) bsmasDao.getFirst()?.let {
                anchorDao.setBsmas(active.id, it.total, it.takenAtEpochMs)
            }
            return
        }

        val firstTs = db.behavioralSessionDao().getFirstTimestamp() ?: return
        val firstDate = Instant.ofEpochMilli(firstTs).atZone(zone).toLocalDate()
        val prefStart = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_START, 0L)
        val start = maxOf(firstDate, LocalDate.ofEpochDay(prefStart))
        val endExclusive = start.plusDays(AnchorCalculator.WINDOW_DAYS.toLong())
        if (today < endExclusive) return // window not finished yet

        val sinceMs = start.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val sessions = db.behavioralSessionDao().getSessionsSince(sinceMs)
            .filter { it.packageName in monitored }
            .map { HistoricalSession(it.timestamp, it.elapsedDuration) }
        val stats = AnchorCalculator.periodStats(sessions, start, endExclusive, zone) ?: return
        if (stats.activeDays < AnchorCalculator.MIN_ACTIVE_DAYS) return

        val endMs = endExclusive.atStartOfDay(zone).toInstant().toEpochMilli()
        val b = bsmasDao.getLatestBefore(endMs + 7 * 86_400_000L)
        anchorDao.insert(
            BaselineAnchorEntity(
                createdAtEpochMs = System.currentTimeMillis(),
                windowStart = start.toString(),
                windowEndExclusive = endExclusive.toString(),
                activeDays = stats.activeDays,
                medianDailyMins = stats.medianDailyMins,
                medianDailySessions = stats.medianDailySessions,
                medianSessionMins = stats.medianSessionMins,
                lateNightShare = stats.lateNightShare,
                bsmasTotal = b?.total,
                bsmasTakenAtEpochMs = b?.takenAtEpochMs
            )
        )
    }

    /** Starts a new anchor window from [from]. Old anchors stay as history. */
    fun reanchor(context: Context, from: LocalDate = LocalDate.now()) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_START, from.toEpochDay()).apply()
    }
}