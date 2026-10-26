package com.ayman.solosystem.domain.progression

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.training.PlanGenerator
import java.time.LocalDate
import kotlin.math.roundToInt

enum class DayOutcome { SUCCESS, FAIL, BRIDGE, NONE, PENDING }

object Stats {
    private fun counted(q: Quest) = (q.type == QuestType.DAILY || q.type == QuestType.PENALTY) && !q.optional

    fun outcome(s: AppState, day: Long, today: Long): DayOutcome {
        val qs = s.quests.filter { it.day == day && counted(it) }
        if (qs.isEmpty()) return DayOutcome.NONE
        if (day in s.redeemedDays || day in s.shieldedDays) return DayOutcome.BRIDGE
        val done = qs.count { it.status == QuestStatus.COMPLETED }
        val missed = qs.count { it.status == QuestStatus.MISSED }
        val pending = qs.count { it.status == QuestStatus.PENDING }
        if (day == today && pending > 0) {
            val thr = s.settings.difficulty.dayThreshold
            return if (done.toDouble() / qs.size >= thr && qs.none { it.category == QuestCategory.TRAINING && it.status != QuestStatus.COMPLETED }) DayOutcome.SUCCESS else DayOutcome.PENDING
        }
        val decided = done + missed
        if (decided == 0) return DayOutcome.BRIDGE // all excused / postponed
        val trainingMissed = qs.any { it.category == QuestCategory.TRAINING && it.status == QuestStatus.MISSED }
        return if (!trainingMissed && done.toDouble() / decided >= s.settings.difficulty.dayThreshold) DayOutcome.SUCCESS else DayOutcome.FAIL
    }

    /** Streak of successful days. Grace days per ISO week can bridge a failed day. Excused / redeemed days bridge. */
    fun streak(s: AppState, today: Long): Int {
        var d = today
        if (outcome(s, today, today) == DayOutcome.PENDING) d = today - 1
        var count = 0
        val graceUsed = mutableMapOf<Long, Int>()
        var guard = 0
        while (guard++ < 2000) {
            when (outcome(s, d, today)) {
                DayOutcome.SUCCESS -> count++
                DayOutcome.BRIDGE, DayOutcome.PENDING -> {}
                DayOutcome.NONE -> return count
                DayOutcome.FAIL -> {
                    val wk = weekStart(d)
                    val used = graceUsed[wk] ?: 0
                    if (used < s.settings.graceDaysPerWeek + com.ayman.solosystem.domain.rewards.Bonuses.shield(s)) graceUsed[wk] = used + 1 else return count
                }
            }
            d--
        }
        return count
    }

    fun bestStreak(s: AppState, today: Long): Int {
        val days = s.quests.map { it.day }.distinct().sorted()
        if (days.isEmpty()) return 0
        var best = 0; var cur = 0
        for (d in days.first()..today) {
            when (outcome(s, d, today)) {
                DayOutcome.SUCCESS -> { cur++; best = maxOf(best, cur) }
                DayOutcome.FAIL -> cur = 0
                else -> {}
            }
        }
        return best
    }

    /** % of decided (completed+missed) counted quests completed in the last 28 days. Excused/postponed/declined excluded. */
    fun consistency(s: AppState, today: Long): Int? {
        val qs = s.quests.filter { counted(it) && it.day in (today - 27)..today }
        val done = qs.count { it.status == QuestStatus.COMPLETED }
        val missed = qs.count { it.status == QuestStatus.MISSED }
        if (done + missed == 0) return null
        return (100.0 * done / (done + missed)).roundToInt()
    }

    fun dailyPercent(s: AppState, day: Long): Int {
        val qs = s.quests.filter { it.day == day && it.type == QuestType.DAILY && !it.optional && it.status != QuestStatus.EXCUSED && it.status != QuestStatus.POSTPONED }
        if (qs.isEmpty()) return 0
        return (100.0 * qs.count { it.status == QuestStatus.COMPLETED } / qs.size).roundToInt()
    }

    fun weekStart(day: Long): Long = day - (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)

    data class Week(val start: Long, val sessionsDone: Int, val sessionsPlanned: Int, val questsDone: Int, val questsMissed: Int, val questsExcused: Int, val xp: Int, val prs: Int)

    fun week(s: AppState, start: Long): Week {
        val range = start..(start + 6)
        val planned = s.plan?.let { p -> range.count { d -> d >= p.startDay && PlanGenerator.dayPlan(p, d).isTraining } } ?: 0
        val qs = s.quests.filter { it.day in range && it.type != QuestType.MAIN }
        return Week(start, s.sessions.count { it.finished && it.day in range }, planned,
            qs.count { it.status == QuestStatus.COMPLETED }, qs.count { it.status == QuestStatus.MISSED }, qs.count { it.status == QuestStatus.EXCUSED },
            s.ledger.filter { it.day in range }.sumOf { it.xp }, s.prs.count { it.day in range })
    }

    fun weeks(s: AppState, today: Long, count: Int = 8): List<Week> {
        val first = s.profile?.createdDay ?: today
        val out = mutableListOf<Week>()
        var ws = weekStart(today)
        while (out.size < count && ws + 6 >= first) { out += week(s, ws); ws -= 7 }
        return out
    }

    fun weeklyCompletion(s: AppState, today: Long): Pair<Int, Int> { val w = week(s, weekStart(today)); return w.sessionsDone to w.sessionsPlanned }

    fun bonusPending(s: AppState) = s.bonus.sumOf { it.xp }.coerceAtLeast(0)
}
