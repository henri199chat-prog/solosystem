package com.ayman.solosystem.domain.progression

import com.ayman.solosystem.domain.model.*
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Transparent XP + level rules.
 *
 *  Quest XP      = round(rankBase × durationFactor × typeMultiplier)
 *                  rankBase: E10 D20 C35 B55 A80 S120
 *                  durationFactor: ≤5min 0.75, ≤20min 1.0, ≤45min 1.25, else 1.5
 *                  type: DAILY 1.0, EMERGENCY 1.5, PENALTY 1.0, MAIN 3.0
 *  XP to next    = round(95 × L^0.45)  (L1→2 = 95 XP, L100→101 ≈ 751 XP, L299→300 ≈ 1235 XP)
 *  Max level     = 300.  Total XP for 300 ≈ 255k → ~4.7 years at ~150 XP/day (full daily adherence).
 *  Level is always derived from the XP ledger, so it can never drift or be double-counted.
 */
object Leveling {
    const val MAX_LEVEL = 300
    /** Reference daily XP for a fully-adherent player (6 daily quests + training + occasional main/emergency). */
    const val FULL_ADHERENCE_DAILY_XP = 150

    fun durationFactor(min: Int): Double = when {
        min <= 5 -> 0.75; min <= 20 -> 1.0; min <= 45 -> 1.25; else -> 1.5
    }
    fun typeMultiplier(t: QuestType): Double = when (t) {
        QuestType.DAILY -> 1.0; QuestType.EMERGENCY -> 1.5; QuestType.PENALTY -> 1.0; QuestType.MAIN -> 3.0
    }
    fun questXp(rank: Rank, durationMin: Int, type: QuestType): Int =
        (rank.baseXp * durationFactor(durationMin) * typeMultiplier(type)).roundToInt()

    fun goldFor(xp: Int) = xp / 2

    fun xpToNext(level: Int): Int = if (level >= MAX_LEVEL) 0 else (95.0 * level.toDouble().pow(0.45)).roundToInt()

    private val cumulative: IntArray by lazy {
        val arr = IntArray(MAX_LEVEL + 1)
        for (l in 2..MAX_LEVEL) arr[l] = arr[l - 1] + xpToNext(l - 1)
        arr
    }
    /** Total XP required to reach [level] from 0. */
    fun totalXpFor(level: Int): Int = cumulative[level.coerceIn(1, MAX_LEVEL)]

    data class LevelInfo(val level: Int, val totalXp: Int, val xpInto: Int, val xpNeeded: Int, val isMax: Boolean) {
        val fraction: Float get() = if (isMax) 1f else if (xpNeeded == 0) 0f else xpInto.toFloat() / xpNeeded
    }

    fun levelFor(totalXp: Int): LevelInfo {
        val xp = totalXp.coerceAtLeast(0)
        if (xp >= totalXpFor(MAX_LEVEL)) return LevelInfo(MAX_LEVEL, xp, 0, 0, true)
        var lo = 1; var hi = MAX_LEVEL
        while (lo < hi) { val mid = (lo + hi + 1) / 2; if (totalXpFor(mid) <= xp) lo = mid else hi = mid - 1 }
        return LevelInfo(lo, xp, xp - totalXpFor(lo), xpToNext(lo), false)
    }

    fun yearsToMax(dailyXp: Int = FULL_ADHERENCE_DAILY_XP): Double = totalXpFor(MAX_LEVEL).toDouble() / dailyXp / 365.0

    /** Player rank by level (game label only). */
    enum class PlayerRank(val label: String, val ar: String, val minLevel: Int) {
        E("E", "E", 1), D("D", "D", 25), C("C", "C", 50), B("B", "B", 75), A("A", "A", 100),
        S("S", "S", 150), NATIONAL("NATIONAL", "وطني", 200), MONARCH("MONARCH", "ملك", 250)
    }
    fun rankFor(level: Int): PlayerRank = PlayerRank.entries.last { level >= it.minLevel }

    private val titles = listOf(
        1 to Txt("لاعب جديد", "New Player"),
        2 to Txt("الخطوات الأولى", "First Steps"),
        5 to Txt("لاعب منضبط", "Disciplined Player"),
        10 to Txt("لاعب نخبة", "Elite Player"),
        20 to Txt("لاعب متقدم", "Advanced Player"),
        50 to Txt("صياد متمرّس", "Veteran Hunter"),
        100 to Txt("صياد الرتبة العليا", "High-Rank Hunter"),
        150 to Txt("فارس الظلال", "Shadow Knight"),
        200 to Txt("قائد الظلال", "Shadow Commander"),
        250 to Txt("سيّد الظلال", "Shadow Lord"),
        300 to Txt("الملك الظلّ", "SHADOW MONARCH")
    )
    fun titleFor(level: Int): Txt = titles.last { level >= it.first }.second
    fun titleMilestones() = titles

    /** Attribute points: +1 per level gained, +2 bonus on every 5th level, +5 bonus on every 25th, +10 at 300. */
    fun pointsEarned(level: Int): Int {
        var p = 0
        for (l in 2..level.coerceAtMost(MAX_LEVEL)) {
            p += 1
            if (l % 5 == 0) p += 2
            if (l % 25 == 0) p += 5
            if (l == MAX_LEVEL) p += 10
        }
        return p
    }
}

/** Derived game numbers computed from the state (never stored, so they can't desync). */
object Ledger {
    fun totalXp(s: AppState) = s.ledger.sumOf { it.xp }
    fun level(s: AppState) = Leveling.levelFor(totalXp(s))
    fun goldEarned(s: AppState) = s.ledger.sumOf { it.gold }
    fun goldSpent(s: AppState) = s.purchases.sumOf { it.cost }
    fun goldBalance(s: AppState) = goldEarned(s) - goldSpent(s)
    fun pointsAvailable(s: AppState) = Leveling.pointsEarned(level(s).level) + s.bonusPoints - s.allocated.total
    const val BASE_ATTRIBUTE = 5
}
