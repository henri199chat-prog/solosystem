package com.ayman.solosystem.domain.training

import com.ayman.solosystem.domain.model.CheckIn
import kotlin.math.min
import kotlin.math.roundToInt

enum class ReadinessAdvice { FULL, REDUCED, RECOVERY_ONLY }

object Readiness {
    /** 0..100. Sleep 30, energy 30, soreness 25, pain 15. A self-reported estimate, not a medical measure. */
    fun score(c: CheckIn): Int {
        val sleep = min(c.sleepHours, 8f) / 8f * 30f
        val energy = (c.energy.coerceIn(1, 5) - 1) / 4f * 30f
        val sore = (5 - c.soreness.coerceIn(1, 5)) / 4f * 25f
        val pain = when (c.pain) { 0 -> 15f; 1 -> 8f; else -> 0f }
        return (sleep + energy + sore + pain).roundToInt().coerceIn(0, 100)
    }
    fun advice(c: CheckIn?): ReadinessAdvice = when {
        c == null -> ReadinessAdvice.FULL
        c.pain >= 2 -> ReadinessAdvice.RECOVERY_ONLY
        score(c) < 40 -> ReadinessAdvice.REDUCED
        else -> ReadinessAdvice.FULL
    }
    const val LOW = 40
    /** 3+ low check-ins within the last 7 days → recommend a deload week. */
    fun deloadRecommended(checkIns: List<CheckIn>, today: Long): Boolean =
        checkIns.filter { it.day in (today - 6)..today }.count { score(it) < LOW } >= 3
}
