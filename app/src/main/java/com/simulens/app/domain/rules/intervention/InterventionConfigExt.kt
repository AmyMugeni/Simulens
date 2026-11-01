package com.simulens.app.domain.rules.intervention

import com.simulens.app.domain.bsmas.BsmasResult

/** Scales the two deviation thresholds by BSMAS band. Your defaults stay the reference point. */
fun InterventionConfig.withBsmas(result: BsmasResult?): InterventionConfig {
    if (result == null) return this
    val scale = result.nudgeMultiplier / 2.0   // 1.0, 0.875 or 0.75
    return copy(
        sessionDeviationMultiplier = (sessionDeviationMultiplier * scale).coerceAtLeast(1.25),
        dailyDeviationRatio = (dailyDeviationRatio * scale).coerceAtLeast(1.15)
    )
}