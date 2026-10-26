package com.ayman.solosystem.domain.training

import com.ayman.solosystem.domain.model.*
import kotlin.math.max

/**
 * Training difficulty tiers. Level only UNLOCKS a tier; it becomes ACTIVE only after the player's real logged
 * performance (sessions or an assessment) meets the tier's entry benchmarks. Loads never change because of level.
 */
object Tiers {
    data class Tier(val index: Int, val label: String, val ar: String, val unlockLevel: Int,
                    val push: Int, val squat: Int, val plankSec: Int, val pull: Int,
                    val benchBw: Double, val squatBw: Double)

    val all = listOf(
        Tier(0, "E", "E", 1, 0, 0, 0, 0, 0.0, 0.0),
        Tier(1, "D", "D", 25, 10, 25, 45, 1, 0.5, 0.75),
        Tier(2, "C", "C", 50, 20, 40, 60, 3, 0.75, 1.0),
        Tier(3, "B", "B", 75, 30, 50, 90, 6, 1.0, 1.25),
        Tier(4, "A", "A", 100, 40, 60, 120, 10, 1.15, 1.5),
        Tier(5, "S", "S", 150, 50, 75, 150, 14, 1.3, 1.75),
        Tier(6, "NATIONAL", "وطني", 200, 60, 100, 180, 18, 1.5, 2.0),
        Tier(7, "MONARCH", "ملك", 250, 70, 120, 240, 22, 1.75, 2.25)
    )

    fun unlockedTier(level: Int): Int = all.last { level >= it.unlockLevel }.index

    data class Bests(val push: Int, val squat: Int, val plank: Int, val pull: Int, val benchE1rm: Double, val squatE1rm: Double)

    private val pushIds = setOf("pushup", "decline_pushup", "diamond_pushup")
    private val squatIds = setOf("bw_squat")
    private val kneeSafeSquatIds = setOf("glute_bridge", "single_leg_bridge")
    private val plankIds = setOf("plank")
    private val pullIds = setOf("chin_up", "pullup")

    /** Best single-set values in the last [windowDays] days from finished sessions + assessments. */
    fun bests(s: AppState, today: Long, windowDays: Int = 90): Bests {
        val from = today - windowDays
        val sets = s.sessions.filter { it.finished && it.day >= from }.flatMap { ses -> ses.entries.flatMap { e -> e.sets.filter { it.done }.map { e.exerciseId to it } } }
        fun best(ids: Set<String>) = sets.filter { it.first in ids }.maxOfOrNull { it.second.reps } ?: 0
        fun e1(id: String) = sets.filter { it.first == id && (it.second.loadKg ?: 0.0) > 0 }.maxOfOrNull { Progression.e1rm(it.second.loadKg!!, it.second.reps) } ?: 0.0
        val a = s.assessments.filter { it.day >= from }
        val knee = s.profile?.limitations?.contains(Limitation.KNEE) == true
        return Bests(
            push = max(best(pushIds), a.maxOfOrNull { it.pushReps ?: 0 } ?: 0),
            squat = max(best(if (knee) kneeSafeSquatIds else squatIds), a.maxOfOrNull { it.squatReps ?: 0 } ?: 0),
            plank = max(best(plankIds), a.maxOfOrNull { it.plankSec ?: 0 } ?: 0),
            pull = max(best(pullIds), a.maxOfOrNull { it.pullReps ?: 0 } ?: 0),
            benchE1rm = e1("bench_press"), squatE1rm = e1("back_squat")
        )
    }

    data class Check(val name: Txt, val required: String, val current: String, val met: Boolean, val skipped: Boolean = false)

    /** Benchmarks that apply to this player (unsafe/unavailable ones are skipped, never forced). */
    fun checks(t: Tier, b: Bests, p: PlayerProfile): List<Check> {
        val lim = p.limitations; val eq = ExerciseLibrary.effectiveEquipment(p.equipment)
        val bw = p.weightKg?.toDouble()
        val out = mutableListOf<Check>()
        val benchOk = bw != null && b.benchE1rm >= t.benchBw * bw
        out += if (Limitation.WRIST in lim) Check(Txt("الضغط", "Push-ups"), "—", "—", true, skipped = true)
        else Check(Txt("الضغط (مجموعة واحدة)", "Push-ups (one set)"), "${t.push}", "${b.push}", b.push >= t.push || benchOk)
        val squatOk = bw != null && b.squatE1rm >= t.squatBw * bw
        out += Check(if (Limitation.KNEE in lim) Txt("جسر الأرداف (مجموعة)", "Glute bridges (one set)") else Txt("القرفصاء (مجموعة واحدة)", "Squats (one set)"),
            "${t.squat}", "${b.squat}", b.squat >= t.squat || squatOk)
        out += if (Limitation.SHOULDER in lim) Check(Txt("البلانك", "Plank"), "—", "—", true, skipped = true)
        else Check(Txt("البلانك (ثانية)", "Plank (seconds)"), "${t.plankSec}", "${b.plank}", b.plank >= t.plankSec)
        val pullPossible = (Equipment.PULLUP_BAR in eq) && Limitation.SHOULDER !in lim && Limitation.ELBOW !in lim
        if (pullPossible) out += Check(Txt("العقلة (مجموعة)", "Pull/chin-ups (one set)"), "${t.pull}", "${b.pull}", b.pull >= t.pull)
        return out
    }

    fun meets(t: Tier, b: Bests, p: PlayerProfile) = t.index == 0 || checks(t, b, p).all { it.met }

    /** Next tier the player may activate: must be unlocked by level AND benchmarks met. Tiers activate one at a time. */
    fun eligibleNext(s: AppState, level: Int, today: Long): Int? {
        val p = s.profile ?: return null
        val next = s.activeTier + 1
        if (next >= all.size || next > unlockedTier(level)) return null
        return if (meets(all[next], bests(s, today), p)) next else null
    }
}
