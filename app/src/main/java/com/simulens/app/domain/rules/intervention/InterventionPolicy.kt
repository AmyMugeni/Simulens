package com.simulens.app.domain.rules.intervention

import java.time.LocalDate

/**
 * Decides whether a matched intervention is actually shown, so ongoing sessions don't spam:
 * per-type cooldown plus a daily cap and global snooze.
 */
class InterventionPolicy(private val config: InterventionConfig = InterventionConfig()) {

    private val lastFiredAtMs = mutableMapOf<InterventionType, Long>()
    private var countDate: LocalDate? = null
    private var countToday = 0

    @Volatile
    private var snoozedUntilMs: Long = 0L

    @Synchronized
    fun snooze(durationMins: Long = 15L) {
        snoozedUntilMs = System.currentTimeMillis() + (durationMins * 60_000L)
    }

    fun isSnoozed(nowMs: Long = System.currentTimeMillis()): Boolean {
        return nowMs < snoozedUntilMs
    }

    /** Returns true and records the firing if allowed. */
    @Synchronized
    fun tryFire(intervention: Intervention, nowMs: Long, today: LocalDate): Boolean {
        if (isSnoozed(nowMs)) return false

        if (countDate != today) {
            countDate = today
            countToday = 0
        }
        if (countToday >= config.maxPerDay) return false

        val cooldownMs = (config.cooldownMins[intervention.type] ?: 10L) * 60_000L
        val last = lastFiredAtMs[intervention.type]
        if (last != null && nowMs - last < cooldownMs) return false

        lastFiredAtMs[intervention.type] = nowMs
        countToday++
        return true
    }
}
