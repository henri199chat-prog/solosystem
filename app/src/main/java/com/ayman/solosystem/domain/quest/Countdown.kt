package com.ayman.solosystem.domain.quest

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.training.PlanGenerator
import kotlin.math.ceil

/**
 * Segmented countdown ring for the daily quest. Pure maths on real clock values so it is correct after reopening.
 * Window = moment the day's quest was issued to the player (persisted) → configured deadline, so it always starts full.
 */
object Countdown {
    const val SEGMENTS = 24
    const val WARN_FRACTION = 0.10

    data class Ring(val lit: Int, val partial: Float, val fraction: Double, val remainingMs: Long, val expired: Boolean, val warn: Boolean)

    fun deadlineMs(dayStartMs: Long, deadlineMinutes: Int) = dayStartMs + deadlineMinutes.coerceIn(60, 1440) * 60_000L

    /**
     * Ring window start for [day]: the moment the daily quest was issued to the player (first app open of the day or
     * the morning "quest arrived" notification), persisted in state. Falls back to [fallbackMs] (default local midnight).
     */
    fun windowStart(s: AppState, day: Long, dayStartMs: Long, fallbackMs: Long = dayStartMs): Long {
        val end = deadlineMs(dayStartMs, s.settings.deadlineMinutes)
        return (s.questIssuedAt[day] ?: fallbackMs).coerceIn(dayStartMs, end)
    }

    /** Marks today's quest as issued at [nowMs] if it wasn't yet (idempotent; keeps 21 days of history). */
    fun markIssued(s: AppState, day: Long, nowMs: Long): AppState =
        if (s.questIssuedAt.containsKey(day)) s else s.copy(questIssuedAt = s.questIssuedAt.filterKeys { it >= day - 21 } + (day to nowMs))

    /** fraction remaining in [0,1] of the window start → end. */
    fun fraction(startMs: Long, endMs: Long, nowMs: Long): Double {
        val total = (endMs - startMs).toDouble()
        if (total <= 0.0) return if (nowMs < endMs) 1.0 else 0.0
        return ((endMs - nowMs) / total).coerceIn(0.0, 1.0)
    }

    /** Number of lit segments (the current one may be partially faded) and its alpha. */
    fun segments(fraction: Double, total: Int = SEGMENTS): Pair<Int, Float> {
        if (fraction <= 0.0) return 0 to 0f
        val x = fraction * total
        val lit = ceil(x - 1e-9).toInt().coerceIn(1, total)
        val partial = (x - (lit - 1)).toFloat().coerceIn(0f, 1f)
        return lit to partial
    }

    fun ring(startMs: Long, endMs: Long, nowMs: Long): Ring {
        val f = fraction(startMs, endMs, nowMs)
        val (lit, partial) = segments(f)
        return Ring(lit, partial, f, (endMs - nowMs).coerceAtLeast(0), f <= 0.0, f <= WARN_FRACTION)
    }
    /** Window from local midnight (legacy / tests). */
    fun ring(dayStartMs: Long, nowMs: Long, deadlineMinutes: Int): Ring = ring(dayStartMs, deadlineMs(dayStartMs, deadlineMinutes), nowMs)

    /** Times (ms) at which reminders should fire: 50% left, 10% left, expiry — of the issued window. Only future ones. */
    fun alertTimes(startMs: Long, endMs: Long, nowMs: Long): List<Pair<String, Long>> {
        val total = (endMs - startMs).coerceAtLeast(0)
        return listOf("half" to endMs - total / 2, "ten" to endMs - total / 10, "end" to endMs).filter { it.second > nowMs }
    }
    fun alertTimes(dayStartMs: Long, nowMs: Long, deadlineMinutes: Int) = alertTimes(dayStartMs, deadlineMs(dayStartMs, deadlineMinutes), nowMs)

    private fun required(s: AppState, day: Long) = s.quests.filter { it.day == day && it.type == QuestType.DAILY && !it.optional }

    fun allDone(s: AppState, day: Long) = required(s, day).let { it.isNotEmpty() && it.all { q -> q.status == QuestStatus.COMPLETED } }

    /** The ring only threatens a penalty on normal training days with unfinished, unexcused required quests. */
    fun armed(s: AppState, day: Long): Boolean {
        val plan = s.plan ?: return false
        val req = required(s, day)
        if (req.isEmpty() || allDone(s, day)) return false
        if (!PlanGenerator.dayPlan(plan, day).isTraining) return false
        if (req.all { it.status == QuestStatus.EXCUSED || it.status == QuestStatus.COMPLETED || it.status == QuestStatus.POSTPONED }) return false
        return true
    }

    fun expiryKey(day: Long) = "expired:$day"

    /**
     * Called whenever the clock passes the deadline. Idempotent (keyed by day). Marks today's unfinished required quests
     * MISSED and runs the penalty sync as if the day had ended. Returns the new state and an issued penalty quest, if any.
     */
    fun expire(s0: AppState, day: Long, dayStartMs: Long, nowMs: Long): Pair<AppState, Quest?> {
        if (expiryKey(day) in s0.grantedKeys) return s0 to null
        if (nowMs < deadlineMs(dayStartMs, s0.settings.deadlineMinutes)) return s0 to null
        if (!armed(s0, day)) return s0.copy(grantedKeys = s0.grantedKeys + expiryKey(day)) to null
        var s = s0.copy(grantedKeys = s0.grantedKeys + expiryKey(day),
            quests = s0.quests.map { q -> if (q.day == day && q.type == QuestType.DAILY && !q.optional && q.status == QuestStatus.PENDING) q.copy(status = QuestStatus.MISSED) else q })
        val (s2, issued) = Penalty.sync(s, day + 1)
        s = s2
        return s to issued
    }
}
