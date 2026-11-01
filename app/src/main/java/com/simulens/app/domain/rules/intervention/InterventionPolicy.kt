package com.simulens.app.domain.rules.intervention

import android.content.SharedPreferences
import java.time.LocalDate

/**
 * Decides whether a matched intervention is shown: per-type cooldown, per-type and global
 * daily caps, per-type quiet periods after a user response, and a global snooze.
 * Pass SharedPreferences to survive service restarts.
 */
class InterventionPolicy(
    private val config: InterventionConfig = InterventionConfig(),
    private val prefs: SharedPreferences? = null,
    private val maxPerTypePerDay: Int = 4
) {
    private val lastFiredAtMs = mutableMapOf<InterventionType, Long>()
    private val quietUntilMs = mutableMapOf<InterventionType, Long>()
    private val countByType = mutableMapOf<InterventionType, Int>()
    private var countDate: LocalDate? = null
    private var countToday = 0

    @Volatile
    private var snoozedUntilMs = 0L

    init { restore() }

    @Synchronized
    fun snooze(durationMins: Long = 15L) {
        snoozedUntilMs = System.currentTimeMillis() + durationMins * 60_000L
        persist()
    }

    fun isSnoozed(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs < snoozedUntilMs

    /** Call when the user answers a nudge: keeps that rule quiet longer than its cooldown. */
    @Synchronized
    fun suppress(type: InterventionType, mins: Long) {
        quietUntilMs[type] = System.currentTimeMillis() + mins * 60_000L
        persist()
    }

    /** Returns true and records the firing if allowed. */
    @Synchronized
    fun tryFire(intervention: Intervention, nowMs: Long, today: LocalDate): Boolean {
        if (isSnoozed(nowMs)) return false

        if (countDate != today) {
            countDate = today
            countToday = 0
            countByType.clear()
        }
        val type = intervention.type
        if (countToday >= config.maxPerDay) return false
        if ((countByType[type] ?: 0) >= maxPerTypePerDay) return false
        if (nowMs < (quietUntilMs[type] ?: 0L)) return false

        val cooldownMs = (config.cooldownMins[type] ?: 10L) * 60_000L
        val last = lastFiredAtMs[type]
        if (last != null && nowMs - last < cooldownMs) return false

        lastFiredAtMs[type] = nowMs
        countByType[type] = (countByType[type] ?: 0) + 1
        countToday++
        persist()
        return true
    }

    private fun persist() {
        val p = prefs ?: return
        p.edit().apply {
            putLong("snooze_until", snoozedUntilMs)
            putString("count_date", countDate?.toString())
            putInt("count_today", countToday)
            InterventionType.entries.forEach { t ->
                putLong("last_${t.name}", lastFiredAtMs[t] ?: 0L)
                putLong("quiet_${t.name}", quietUntilMs[t] ?: 0L)
                putInt("cnt_${t.name}", countByType[t] ?: 0)
            }
        }.apply()
    }

    private fun restore() {
        val p = prefs ?: return
        snoozedUntilMs = p.getLong("snooze_until", 0L)
        countDate = p.getString("count_date", null)?.let(LocalDate::parse)
        countToday = p.getInt("count_today", 0)
        InterventionType.entries.forEach { t ->
            p.getLong("last_${t.name}", 0L).takeIf { it > 0 }?.let { lastFiredAtMs[t] = it }
            p.getLong("quiet_${t.name}", 0L).takeIf { it > 0 }?.let { quietUntilMs[t] = it }
            p.getInt("cnt_${t.name}", 0).takeIf { it > 0 }?.let { countByType[t] = it }
        }
    }
}