package com.ayman.solosystem.domain.quest

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.training.Exercise
import com.ayman.solosystem.domain.training.ExerciseLibrary
import com.ayman.solosystem.domain.training.PlanGenerator
import com.ayman.solosystem.domain.training.Readiness
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Safe escalating PENALTY QUESTS.
 * - Tier 1..5 rises by one for each day with an UNEXCUSED miss (strict mode: +1 more if a training session was missed),
 *   and decays by one after N clean days. Excused misses never escalate. Tier is derived from quest history, so
 *   excusing a miss later automatically de-escalates.
 * - Volume = player's own recent best session volume for that movement × (0.2 + 0.1 × (tier−1))  → tier1 +20% … tier5 +60%.
 *   Hard caps: penalty ≤ 0.6 × best; (today's normal volume + penalty) ≤ 1.6 × best; whole quest ≤ 35 min;
 *   sets stay submaximal (~60% of best single set), bodyweight only, never loaded, never to failure.
 * - Never on rest days or deload weeks (moved to the next normal training day). On low readiness / pain it is
 *   converted into a recovery-reflection quest. Stopping a penalty for pain is never a failure.
 */
object Penalty {
    const val MAX_TIER = 5
    const val MAX_MINUTES = 35
    const val CAP_MULTIPLIER = 1.6

    fun factor(tier: Int): Double = if (tier <= 0) 0.0 else 0.2 + 0.1 * (tier.coerceAtMost(MAX_TIER) - 1)

    private fun escalating(q: Quest) = (q.type == QuestType.DAILY || q.type == QuestType.PENALTY) && !q.optional && q.status == QuestStatus.MISSED

    /** Rest days never trigger penalties: their daily quests are recovery habits; missing them only breaks the day, not the rules. */
    fun isRestDay(s: AppState, day: Long): Boolean = s.plan?.let { !PlanGenerator.dayPlan(it, day).isTraining } ?: false

    private fun counts(s: AppState, q: Quest) = escalating(q) && (q.type == QuestType.PENALTY || !isRestDay(s, q.day))

    fun missDays(s: AppState, before: Long): List<Long> = s.quests.filter { counts(s, it) && it.day < before }.map { it.day }.distinct().sorted()

    /** Pure tier step for one day. */
    fun step(tier: Int, clean: Int, missedToday: Boolean, trainingMissed: Boolean, mode: PenaltyMode): Pair<Int, Int> {
        if (mode == PenaltyMode.OFF) return 0 to 0
        if (missedToday) {
            val inc = 1 + if (mode.extraOnTrainingMiss && trainingMissed) 1 else 0
            return min(mode.maxTier, tier + inc) to 0
        }
        val c = clean + 1
        return if (tier > 0 && c >= mode.decayAfterCleanDays) (tier - 1) to 0 else tier to c
    }

    /** Derived tier using all days strictly before [today]. */
    fun tier(s: AppState, today: Long): Int {
        val mode = s.settings.penaltyMode
        val days = s.quests.map { it.day }.filter { it < today }
        if (days.isEmpty()) return 0
        var t = 0; var c = 0
        for (d in days.min()..(today - 1)) {
            val qs = s.quests.filter { it.day == d && counts(s, it) }
            val hasAny = s.quests.any { it.day == d && it.type == QuestType.DAILY }
            if (!hasAny && qs.isEmpty()) continue
            val r = step(t, c, qs.isNotEmpty(), qs.any { it.category == QuestCategory.TRAINING }, mode)
            t = r.first; c = r.second
        }
        return t
    }

    /** Next day ≥ from that is a normal (non-deload) training day. */
    fun scheduleDay(plan: TrainingPlan, from: Long): Long {
        var d = from
        repeat(28) {
            if (PlanGenerator.dayPlan(plan, d).isTraining && PlanGenerator.dayPlan(plan, d).type != DayType.CONDITIONING && !PlanGenerator.isDeloadWeek(plan, d)) return d
            d++
        }
        var e = from
        repeat(28) { if (PlanGenerator.dayPlan(plan, e).isTraining && !PlanGenerator.isDeloadWeek(plan, e)) return e; e++ }
        return from + 1
    }

    fun unsafeToday(s: AppState, today: Long): Boolean {
        val c = s.checkIns.lastOrNull { it.day == today }
        if (c != null && (Readiness.score(c) < Readiness.LOW || c.pain >= 2)) return true
        val recentPain = s.sessions.any { it.day >= today - 2 && (it.painReports.isNotEmpty() || it.stoppedForSafety) }
        return recentPain
    }

    // ---- Volume from the player's own logged capacity ----
    data class Capacity(val bestSessionVolume: Int, val bestSet: Int, val fromLogs: Boolean)

    fun capacity(s: AppState, exerciseId: String, today: Long, fallbackSets: Int, fallbackReps: Int): Capacity {
        val ses = s.sessions.filter { it.finished && it.day in (today - 28)..today }
        var bestVol = 0; var bestSet = 0
        for (x in ses) {
            val sets = x.entries.filter { it.exerciseId == exerciseId }.flatMap { it.sets }.filter { it.done && it.reps > 0 }
            if (sets.isEmpty()) continue
            bestVol = max(bestVol, sets.sumOf { it.reps }); bestSet = max(bestSet, sets.maxOf { it.reps })
        }
        return if (bestVol > 0) Capacity(bestVol, bestSet, true) else Capacity(fallbackSets * fallbackReps, fallbackReps, false)
    }

    /** GOAL row whose completed full-day volume also proves capacity for this exercise (1.2.1). Easier variations are excluded. */
    fun goalRowFor(exerciseId: String): GoalPlan.Row? = when (exerciseId) { "pushup" -> GoalPlan.Row.PUSH; "bw_squat", "box_squat" -> GoalPlan.Row.SQUAT; else -> null }

    /** Best COMPLETED, non-lowered GOAL day (last 28 days, base-unit target) for the matching row. */
    fun goalBest(s: AppState, exerciseId: String, today: Long): Int {
        val r = goalRowFor(exerciseId) ?: return 0
        return s.quests.filter { it.templateId == "goal:${r.id}" && it.status == QuestStatus.COMPLETED && !it.lowered && it.day in (today - 28)..today }
            .maxOfOrNull { kotlin.math.ceil(GoalPlan.intensity(GoalPlan.variant(r, it.variant), it.target)).toInt() } ?: 0
    }

    /**
     * Since 1.2.1 the GOAL is 50–100 reps/day, so proven GOAL days count as capacity: the penalty scales with what he really does,
     * still under all caps (≤ 0.6 × best, ≤ 1.6 × best incl. today's work, ≤ 35 min, ~60 % sets; a GOAL day is spread over sets,
     * so its "best set" is taken as a quarter of it).
     */
    fun withGoal(s: AppState, exerciseId: String, today: Long, c: Capacity): Capacity {
        val g = goalBest(s, exerciseId, today)
        return if (g > c.bestSessionVolume) Capacity(g, max(c.bestSet, g / 4), true) else c
    }

    private fun firstSafe(ids: List<String>, p: PlayerProfile, plan: TrainingPlan?): Exercise? {
        val inPlan = plan?.week?.flatMap { it.items }?.map { it.exerciseId }?.toSet() ?: emptySet()
        val ordered = ids.filter { it in inPlan } + ids.filter { it !in inPlan } // prefer the variation he already trains
        return ordered.mapNotNull { ExerciseLibrary.get(it) }
        .firstOrNull { ExerciseLibrary.available(it, p.equipment) && ExerciseLibrary.safeFor(it, p.limitations) && it.id !in p.avoidExerciseIds }
    }

    private fun planPrescription(plan: TrainingPlan?, id: String) = plan?.week?.flatMap { it.items }?.firstOrNull { it.exerciseId == id }

    /** Today's normally prescribed volume for an exercise (0 if not in today's session). */
    fun normalVolumeOn(plan: TrainingPlan?, day: Long, id: String): Int =
        plan?.let { PlanGenerator.dayPlan(it, day).items.filter { pr -> pr.exerciseId == id }.sumOf { pr -> pr.sets * pr.repMin } } ?: 0

    /** Penalty amount for one movement, with all caps applied. Exposed for tests. */
    fun amount(tier: Int, cap: Capacity, normalToday: Int): Int {
        val raw = (cap.bestSessionVolume * factor(tier)).roundToInt()
        val c1 = floor(0.6 * cap.bestSessionVolume).toInt()
        val c2 = floor(CAP_MULTIPLIER * cap.bestSessionVolume - normalToday).toInt()
        return max(0, minOf(raw, c1, c2))
    }

    fun buildItems(s: AppState, tier: Int, day: Long): List<PenaltyItem> {
        val p = s.profile ?: return emptyList()
        val plan = s.plan
        val newbie = PlanGenerator.isNewToTraining(p)
        val items = mutableListOf<PenaltyItem>()
        fun addReps(ex: Exercise?, defSets: Int, defReps: Int) {
            ex ?: return
            val timed = ex.timed
            val pr = planPrescription(plan, ex.id)
            val cap = withGoal(s, ex.id, day, capacity(s, ex.id, day, pr?.sets ?: defSets, pr?.repMin ?: defReps))
            val amt = amount(tier, cap, normalVolumeOn(plan, day, ex.id))
            if (amt <= 0) return
            val perSet = max(if (timed) 10 else 3, (cap.bestSet * 0.6).roundToInt())
            val sets = min(6, max(1, ceil(amt.toDouble() / perSet).toInt()))
            items += PenaltyItem(ex.id, amt, if (timed) PenaltyUnit.SECONDS else PenaltyUnit.REPS, sets, if (timed) 45 else 60)
        }
        addReps(firstSafe(listOf("pushup", "knee_pushup", "incline_pushup", "band_chest_press"), p, plan), if (newbie) 2 else 3, if (newbie) 8 else 10)
        addReps(firstSafe(listOf("bw_squat", "box_squat", "glute_bridge"), p, plan), if (newbie) 2 else 3, if (newbie) 10 else 15)
        addReps(firstSafe(listOf("plank", "side_plank", "dead_bug", "bird_dog"), p, plan), 2, if (newbie) 20 else 30)
        // brisk walk minutes (never running, never high impact)
        var walk = min(20, 5 + 3 * tier)
        fun minutes(list: List<PenaltyItem>, w: Int) = list.sumOf { i ->
            val work = if (i.unit == PenaltyUnit.SECONDS) i.target else i.target * 3
            work + (i.sets - 1) * i.restSec
        } / 60.0 + w
        while (minutes(items, walk) > MAX_MINUTES && walk > 5) walk--
        var scaled = items.toList()
        var guard = 0
        while (minutes(scaled, walk) > MAX_MINUTES && guard++ < 20) {
            scaled = scaled.map { it.copy(target = max(1, (it.target * 0.85).roundToInt()), sets = max(1, it.sets - if (it.sets > 2) 1 else 0)) }
        }
        return scaled + PenaltyItem("brisk_walk", walk, PenaltyUnit.MINUTES, 1, 0)
    }

    fun totalMinutes(items: List<PenaltyItem>): Double = items.sumOf { i ->
        when (i.unit) { PenaltyUnit.MINUTES -> i.target * 60; PenaltyUnit.SECONDS -> i.target + (i.sets - 1) * i.restSec; PenaltyUnit.REPS -> i.target * 3 + (i.sets - 1) * i.restSec }
    } / 60.0

    fun rankFor(tier: Int) = when (tier) { 1 -> Rank.D; 2 -> Rank.C; 3 -> Rank.B; 4 -> Rank.A; else -> Rank.S }

    fun penaltyQuest(s: AppState, tier: Int, day: Long, redeems: List<Long>): Quest {
        val items = buildItems(s, tier, day)
        val mins = ceil(totalMinutes(items)).toInt()
        val rank = rankFor(tier)
        return Quest(
            id = "p:$day:${redeems.joinToString("-")}", templateId = "penalty", type = QuestType.PENALTY, category = QuestCategory.TRAINING,
            title = Txt("مهمة العقاب: النجاة — المستوى $tier", "Penalty Quest: Survival — Tier $tier"),
            objective = Txt("أكمل كل البنود بتقنية جيدة وتوقف قبل الفشل بـ2–3 تكرارات.", "Complete every line with good form, stopping 2–3 reps short of failure."),
            description = Txt("فشلت في إكمال مهام سابقة دون عذر. هذا العقاب محسوب من أدائك المسجّل (+${(factor(tier) * 100).roundToInt()}% من حجمك المعتاد) ولا يتجاوز $MAX_MINUTES دقيقة. إكماله يعيد درع السلسلة. إن شعرت بألم، استخدم زر الإيقاف — لن يُحسب فشلًا.",
                "You failed earlier quests without an excuse. This penalty is scaled from your logged performance (+${(factor(tier) * 100).roundToInt()}% of your usual volume) and never exceeds $MAX_MINUTES min. Completing it restores your streak shield. If anything hurts, use the stop button — it never counts as failure."),
            rank = rank, durationMin = mins, xp = Leveling.questXp(rank, mins, QuestType.PENALTY), day = day,
            deadlineDay = day, items = items, penaltyTier = tier, redeemsDays = redeems
        )
    }

    fun reflectionQuest(day: Long, redeems: List<Long>, converted: Boolean): Quest {
        val rank = Rank.E
        return Quest(
            id = "r:$day:${redeems.joinToString("-")}", templateId = "reflection", type = QuestType.PENALTY, category = QuestCategory.RECOVERY,
            title = if (converted) Txt("مهمة تعافٍ بديلة", "Recovery replacement quest") else Txt("مهمة تأمل وتخطيط", "Reflection & planning quest"),
            objective = Txt("10 دقائق: مشي هادئ أو تنفس وتمدد خفيف، ثم اكتب سبب التعثر وخطة واقعية للغد.", "10 min: easy walk or breathing & light stretching, then write why you slipped and a realistic plan for tomorrow."),
            description = if (converted) Txt("جاهزيتك منخفضة أو أبلغت عن ألم، لذلك تحوّل العقاب البدني إلى تعافٍ. صحتك أولًا.", "Your readiness is low or you reported pain, so the physical penalty became recovery. Health first.")
            else Txt("وضع العقاب البدني متوقف في الإعدادات. هذه مهمة محاسبة بدون تمرين إضافي.", "Physical penalties are off in settings. This is an accountability task without extra exercise."),
            rank = rank, durationMin = 10, xp = Leveling.questXp(rank, 10, QuestType.PENALTY), day = day, deadlineDay = day, redeemsDays = redeems, optional = true
        )
    }

    /** Unredeemed, unexcused miss days not yet covered by a completed penalty, within the last 14 days. */
    fun openTriggers(s: AppState, today: Long): List<Long> {
        val covered = s.quests.filter { it.type == QuestType.PENALTY && it.status == QuestStatus.COMPLETED }.flatMap { it.redeemsDays }.toSet()
        return missDays(s, today).filter { it >= today - 14 && it !in covered && it !in s.redeemedDays }
    }

    /** Keep penalty quests in sync with the current miss history. Returns new state + newly issued quest (for the notification). */
    fun sync(s0: AppState, today: Long): Pair<AppState, Quest?> {
        var s = s0
        val plan = s.plan ?: return s to null
        val triggers = openTriggers(s, today)
        val open = s.quests.filter { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING }
        if (triggers.isEmpty()) {
            if (open.isEmpty()) return s to null
            // all triggers excused → cancel (no consequence)
            s = s.copy(quests = s.quests.map { q -> if (q in open && q.items.none { it.done }) q.copy(status = QuestStatus.EXCUSED, reason = "cancelled:excused", reasonReviewed = true) else q })
            return s to null
        }
        val mode = s.settings.penaltyMode
        val tier = max(1, tier(s, today))
        val existing = open.firstOrNull()
        val unsafe = unsafeToday(s, today)
        val day = if (existing != null && existing.day >= today) existing.day else scheduleDay(plan, today)
        val wantReflection = mode == PenaltyMode.OFF || (day == today && unsafe)
        if (existing != null) {
            val started = existing.items.any { it.done }
            if (existing.day == today && unsafe && existing.templateId == "penalty" && !started) {
                // convert to recovery quest
                val conv = reflectionQuest(today, existing.redeemsDays.union(triggers).sorted(), converted = true)
                s = s.copy(quests = s.quests.map { if (it.id == existing.id) it.copy(status = QuestStatus.EXCUSED, reason = "converted:readiness", reasonReviewed = true) else it } + conv)
                return s to conv
            }
            if (!started && existing.templateId == "penalty" && (existing.penaltyTier != tier || existing.redeemsDays.toSet() != triggers.toSet())) {
                val upd = penaltyQuest(s, tier, existing.day, triggers).copy(id = existing.id)
                s = s.copy(quests = s.quests.map { if (it.id == existing.id) upd else it })
            }
            return s to null
        }
        val q = if (wantReflection) reflectionQuest(day, triggers, converted = mode != PenaltyMode.OFF) else penaltyQuest(s, tier, day, triggers)
        if (s.quests.any { it.id == q.id }) return s to null
        s = s.copy(quests = s.quests + q)
        return s to q
    }
}
