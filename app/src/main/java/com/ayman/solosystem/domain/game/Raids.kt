package com.ayman.solosystem.domain.game

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.quest.Penalty
import kotlin.math.ceil

/**
 * RAID (غارة): one optional hard session unlocked by a Raid Key, ONE tier above the player's current GOAL tier.
 * Volume = the player's own full-day GOAL targets × [RAID_FACTOR] using harder variants allowed by the raid rank and his
 * limitations, always clamped to the per-row safety cap and the running energy cap. Rest-day, pain and illness rules apply.
 * Failing or leaving a raid never gives a penalty.
 */
object Raids {
    const val KEY_DAYS = 7
    const val RAID_FACTOR = 1.3
    val RANKS = listOf("E", "D", "C", "B", "A", "S")
    fun rankLabel(i: Int) = RANKS[i.coerceIn(0, RANKS.size - 1)]
    /** Rank a new key/raid gets: current tier + 1. */
    fun rankFor(s: AppState) = (GoalPlan.tier(s) + 1).coerceIn(1, 5)

    fun activeKeys(s: AppState, today: Long) = s.raidKeys.filter { !it.used && today <= it.expiresDay }
    fun active(s: AppState) = s.raids.lastOrNull { it.endMs == null }
    fun clears(s: AppState) = s.raids.count { it.cleared }

    fun blockReason(s: AppState, today: Long): Txt? = when {
        active(s) != null -> Txt("[SYSTEM] أنت داخل غارة بالفعل.", "[SYSTEM] You are already inside a raid.")
        s.history.any { it.day == today && it.kind == HistoryKind.PAIN } -> Txt("[SYSTEM] سُجّل ألم اليوم. البوابة مغلقة — ارتح. المفتاح يبقى معك.", "[SYSTEM] Pain was logged today. The gate is closed — rest. You keep the key.")
        s.quests.any { it.day == today && (it.reason == MissReason.SICK.name || it.reason == MissReason.INJURY.name) } ->
            Txt("[SYSTEM] أنت مريض أو مصاب اليوم. البوابة مغلقة. المفتاح يبقى معك.", "[SYSTEM] You are sick or injured today. The gate is closed. You keep the key.")
        Penalty.unsafeToday(s, today) -> Txt("[SYSTEM] جاهزيتك منخفضة اليوم. ارتح — المفتاح يبقى معك.", "[SYSTEM] Low readiness today. Rest — you keep the key.")
        GoalPlan.lightDay(s, today) < 1.0 -> Txt("[SYSTEM] اليوم يوم راحة أو تخفيف. الغارات تُفتح في أيام التدريب فقط.", "[SYSTEM] Today is a rest/deload day. Raids open on training days only.")
        s.raids.any { it.day == today } -> Txt("[SYSTEM] غارة واحدة فقط في اليوم.", "[SYSTEM] Only one raid per day.")
        else -> null
    }

    /** Hardest variant allowed by the raid rank and limitations (stair floors excluded — unit conversion, not difficulty). */
    fun raidVariant(s: AppState, r: GoalPlan.Row, rank: Int): GoalPlan.Variant {
        val lim = s.profile?.limitations ?: emptySet()
        return GoalPlan.variants(r).filter { it.tier <= rank.coerceAtMost(2) && it.unit != GoalPlan.GUnit.FLOORS && it.avoid.none { a -> a in lim } }
            .maxByOrNull { it.factor } ?: GoalPlan.variants(r).first()
    }

    fun steps(s: AppState, rank: Int, today: Long): List<RaidStep> = GoalPlan.Row.entries.map { r ->
        val v = raidVariant(s, r, rank)
        val full = GoalPlan.track(s, r, today).current
        var cap = r.cap.toDouble()
        if (r == GoalPlan.Row.RUN) GoalPlan.runEnergyCapM(s, today)?.let { cap = minOf(cap, it.toDouble()) }
        val base = minOf(full * RAID_FACTOR, cap).toInt()
        var amount = GoalPlan.toVariant(v, base)
        val minStep = if (v.unit == GoalPlan.GUnit.M) 100 else if (v.unit == GoalPlan.GUnit.SEC) 5 else 1
        while (GoalPlan.intensity(v, amount) > cap + 1e-6 && amount > minStep) amount -= minStep
        val sets = if (v.unit == GoalPlan.GUnit.M) 1 else 4
        RaidStep(r.id, v.id, amount, sets)
    }

    fun limitMin(steps: List<RaidStep>): Int {
        val runM = steps.firstOrNull { it.row == GoalPlan.Row.RUN.id }?.amount ?: 0
        return (30 + ceil(runM / 1000.0 * 7).toInt()).coerceIn(40, 90)
    }

    fun xpFor(rank: Int) = 120 + 60 * rank
    fun goldFor(rank: Int) = 60 + 30 * rank

    fun stepName(st: RaidStep): Txt { val r = GoalPlan.Row.entries.first { it.id == st.row }; val v = GoalPlan.variant(r, st.variant); return Txt(v.ar, v.en) }
    fun stepAmount(st: RaidStep, ar: Boolean): String { val r = GoalPlan.Row.entries.first { it.id == st.row }; val v = GoalPlan.variant(r, st.variant)
        return GoalPlan.fmtV(v, st.amount) + (if (ar) v.unit.ar else v.unit.en).let { if (it.isEmpty()) "" else " $it" } }

    /** Secret Quest attribute points: 3 + 2 per tier, max 10. */
    fun secretPoints(s: AppState) = (3 + 2 * GoalPlan.tier(s)).coerceAtMost(10)
}
