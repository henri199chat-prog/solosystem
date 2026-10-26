package com.ayman.solosystem.domain.training

import com.ayman.solosystem.domain.model.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Double progression. Performance-driven only — game level never changes loads.
 * Success session = every prescribed set done, every set at/above top of rep range, every set RPE ≤ 8, no pain.
 * 2 consecutive successes → suggest +2.5 kg (loaded), +reps / harder variation (bodyweight), +5 s (timed).
 * 2 consecutive struggles or pain → suggest an easier option. Suggestions need the player's acceptance.
 */
object Progression {
    const val LOAD_STEP_KG = 2.5
    const val SUCCESS_SESSIONS = 2

    fun repRange(pr: Prescription, prog: ExerciseProgress?) =
        (prog?.repMinOverride ?: pr.repMin) to (prog?.repMaxOverride ?: pr.repMax)

    fun isSuccess(entry: SessionEntry, prog: ExerciseProgress?): Boolean {
        val (_, top) = repRange(entry.prescription, prog)
        val done = entry.sets.filter { it.done }
        return entry.status != EntryStatus.STOPPED && !entry.pain && done.size >= entry.prescription.sets &&
            done.all { it.reps >= top && (it.rpe ?: 99) <= 8 }
    }

    fun isStruggle(entry: SessionEntry, prog: ExerciseProgress?): Boolean {
        val (low, _) = repRange(entry.prescription, prog)
        val done = entry.sets.filter { it.done }
        return done.size < entry.prescription.sets || done.any { it.reps < low || (it.rpe ?: 0) >= 10 }
    }

    fun evaluate(entry: SessionEntry, prev: ExerciseProgress?, profile: PlayerProfile, day: Long): ExerciseProgress {
        val ex = ExerciseLibrary.get(entry.exerciseId)
        val base = prev ?: ExerciseProgress(entry.exerciseId)
        val doneSets = entry.sets.filter { it.done }
        if (doneSets.isEmpty() && !entry.pain) return base
        val maxLoad = doneSets.mapNotNull { it.loadKg }.maxOrNull()
        val success = isSuccess(entry, prev)
        val struggle = !success && isStruggle(entry, prev)
        val streak = if (success) base.successStreak + 1 else 0
        val struggleStreak = if (struggle) base.struggleStreak + 1 else 0
        var p = base.copy(
            targetLoadKg = base.targetLoadKg ?: maxLoad,
            successStreak = streak, struggleStreak = struggleStreak, lastDay = day
        )
        if (p.suggestion == null && ex != null) {
            val (lo, hi) = repRange(entry.prescription, prev)
            val sug: Suggestion? = when {
                entry.pain -> easier(ex, profile, p.targetLoadKg ?: maxLoad)
                streak >= SUCCESS_SESSIONS -> harder(ex, profile, p.targetLoadKg ?: maxLoad, lo, hi)
                struggleStreak >= SUCCESS_SESSIONS -> easier(ex, profile, p.targetLoadKg ?: maxLoad)
                else -> null
            }
            p = p.copy(suggestion = sug)
        }
        return p
    }

    fun harder(ex: Exercise, profile: PlayerProfile, load: Double?, lo: Int, hi: Int): Suggestion? {
        if (ex.loadable && load != null && load > 0) return Suggestion(SuggestionKind.ADD_LOAD, newLoadKg = load + LOAD_STEP_KG, newRepMin = lo, newRepMax = hi)
        if (ex.timed) return if (hi < 120) Suggestion(SuggestionKind.ADD_REPS, newRepMin = lo + 5, newRepMax = hi + 5) else variation(ex.progression, profile, SuggestionKind.HARDER_VARIATION)
        variation(ex.progression, profile, SuggestionKind.HARDER_VARIATION)?.let { return it }
        return if (hi < 30) Suggestion(SuggestionKind.ADD_REPS, newRepMin = lo + 2, newRepMax = hi + 2) else null
    }

    fun easier(ex: Exercise, profile: PlayerProfile, load: Double?): Suggestion? {
        if (ex.loadable && load != null && load > 0) return Suggestion(SuggestionKind.REDUCE_LOAD, newLoadKg = max(0.0, ((load * 0.9) * 2).roundToInt() / 2.0))
        return variation(ex.regression, profile, SuggestionKind.EASIER_VARIATION)
    }

    private fun variation(id: String?, profile: PlayerProfile, kind: SuggestionKind): Suggestion? {
        val v = id?.let { ExerciseLibrary.get(it) } ?: return null
        if (!ExerciseLibrary.available(v, profile.equipment) || !ExerciseLibrary.safeFor(v, profile.limitations) || v.id in profile.avoidExerciseIds) return null
        return Suggestion(kind, newExerciseId = v.id)
    }

    /** Apply an accepted suggestion: returns updated plan + progress map. */
    fun accept(plan: TrainingPlan, progress: Map<String, ExerciseProgress>, exerciseId: String): Pair<TrainingPlan, Map<String, ExerciseProgress>> {
        val p = progress[exerciseId] ?: return plan to progress
        val s = p.suggestion ?: return plan to progress
        return when (s.kind) {
            SuggestionKind.ADD_LOAD, SuggestionKind.REDUCE_LOAD ->
                plan to progress + (exerciseId to p.copy(targetLoadKg = s.newLoadKg, repMinOverride = s.newRepMin ?: p.repMinOverride, repMaxOverride = s.newRepMax ?: p.repMaxOverride, suggestion = null, successStreak = 0, struggleStreak = 0))
            SuggestionKind.ADD_REPS ->
                plan to progress + (exerciseId to p.copy(repMinOverride = s.newRepMin, repMaxOverride = s.newRepMax, suggestion = null, successStreak = 0))
            SuggestionKind.HARDER_VARIATION, SuggestionKind.EASIER_VARIATION -> {
                val nid = s.newExerciseId ?: return plan to progress
                val newPlan = plan.copy(week = plan.week.map { d -> d.copy(items = d.items.map { if (it.exerciseId == exerciseId) it.copy(exerciseId = nid) else it }) })
                newPlan to (progress + (exerciseId to p.copy(suggestion = null, successStreak = 0, struggleStreak = 0)))
            }
        }
    }

    fun dismiss(progress: Map<String, ExerciseProgress>, exerciseId: String) =
        progress[exerciseId]?.let { progress + (exerciseId to it.copy(suggestion = null, successStreak = 0, struggleStreak = 0)) } ?: progress

    // -------- Performance metrics (real data only) --------
    fun e1rm(load: Double, reps: Int): Double = if (reps <= 1) load else load * (1 + reps / 30.0)

    data class Perf(val day: Long, val bestReps: Int, val bestLoad: Double?, val bestE1rm: Double?, val volume: Double, val totalReps: Int)

    fun history(sessions: List<WorkoutSession>, exerciseId: String): List<Perf> =
        sessions.filter { it.finished }.sortedBy { it.day }.mapNotNull { s ->
            val sets = s.entries.filter { it.exerciseId == exerciseId }.flatMap { it.sets }.filter { it.done && it.reps > 0 }
            if (sets.isEmpty()) null else Perf(
                s.day, sets.maxOf { it.reps }, sets.mapNotNull { it.loadKg }.maxOrNull(),
                sets.filter { (it.loadKg ?: 0.0) > 0 }.maxOfOrNull { e1rm(it.loadKg!!, it.reps) },
                sets.sumOf { it.reps * (it.loadKg ?: 0.0) }, sets.sumOf { it.reps }
            )
        }

    fun lastPerformance(sessions: List<WorkoutSession>, exerciseId: String): SessionEntry? =
        sessions.filter { it.finished }.sortedByDescending { it.finishedMs }.firstNotNullOfOrNull { s -> s.entries.firstOrNull { it.exerciseId == exerciseId && it.sets.any { st -> st.done } } }

    /** Detect new personal records from a finished session against previous PRs. */
    fun detectPrs(session: WorkoutSession, previous: List<PersonalRecord>): List<PersonalRecord> {
        val out = mutableListOf<PersonalRecord>()
        for (e in session.entries) {
            val ex = ExerciseLibrary.get(e.exerciseId) ?: continue
            val sets = e.sets.filter { it.done && it.reps > 0 }
            if (sets.isEmpty()) continue
            val loaded = sets.filter { (it.loadKg ?: 0.0) > 0 }
            if (ex.loadable && loaded.isNotEmpty()) {
                val best = loaded.maxBy { e1rm(it.loadKg!!, it.reps) }
                val v = e1rm(best.loadKg!!, best.reps)
                val prev = previous.filter { it.exerciseId == e.exerciseId && it.kind == PrKind.E1RM }.maxOfOrNull { it.value } ?: 0.0
                if (v > prev + 1e-6) out += PersonalRecord(e.exerciseId, session.day, PrKind.E1RM, (v * 10).roundToInt() / 10.0, best.reps, best.loadKg)
            } else {
                val kind = if (ex.timed) PrKind.SECONDS else PrKind.REPS
                val best = sets.maxOf { it.reps }
                val prev = previous.filter { it.exerciseId == e.exerciseId && it.kind == kind }.maxOfOrNull { it.value } ?: 0.0
                if (best > prev) out += PersonalRecord(e.exerciseId, session.day, kind, best.toDouble(), best, null)
            }
        }
        return out
    }

    fun clampRpe(r: Int) = min(10, max(1, r))
}
