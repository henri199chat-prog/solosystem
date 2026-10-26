package com.ayman.solosystem.domain.quest

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.training.PlanGenerator
import kotlin.math.roundToInt

/**
 * The anime "GOAL" daily quest: Push-ups / Sit-ups / Squats / Running with [current/target].
 * 1.2.1 "merciless" rule: push-ups / sit-ups / squats start at the rank-E FLOOR (50 / 50 / 60) no matter how low the measured
 * capacity is (capacity can only raise the start), split into sets across the day, and ramp +15 per consistent pain-free week
 * up to the anime 100 / 100 / 100 (squats 3 weeks, push-ups / sit-ups 4 weeks). Running keeps its weight/intake-driven logic.
 * Rest days / deload / pain / illness → lighter (unchanged safety).
 */
object GoalPlan {
    enum class Row(val id: String, val ar: String, val en: String, val cap: Int, val unitAr: String, val unitEn: String) {
        PUSH("push", "ضغط", "Push-ups", 100, "", ""), SITUP("situp", "بطن", "Sit-ups", 100, "", ""),
        SQUAT("squat", "سكوات", "Squats", 100, "", ""), RUN("run", "جري", "Running", 10_000, "كم", "km")
    }
    enum class GUnit(val ar: String, val en: String) { REPS("", ""), SEC("ث", "s"), M("كم", "km"), FLOORS("طابق", "fl") }

    /**
     * A movement variant of a GOAL row. [factor] = base units one variant unit is worth (difficulty equivalence):
     * 1 diamond push-up ≈ 1.4 push-ups, 1 s plank ≈ 0.5 sit-up, 1 stair floor ≈ 80 m running, sprint metres ≈ 2.5× easy metres.
     * [tier] = minimum tier (0..3, from fitness level and player level) to be rotated in automatically.
     */
    data class Variant(val id: String, val row: Row, val ar: String, val en: String, val factor: Double, val tier: Int, val unit: GUnit,
                       val avoid: Set<Limitation> = emptySet(), val noteAr: String = "", val noteEn: String = "")

    private val IMPACT = setOf(Limitation.KNEE, Limitation.ANKLE, Limitation.HIGH_IMPACT)
    val VARIANTS: List<Variant> = listOf(
        Variant("", Row.PUSH, "ضغط", "Push-ups", 1.0, 0, GUnit.REPS),
        Variant("wide", Row.PUSH, "ضغط واسع", "Wide push-ups", 1.1, 0, GUnit.REPS, setOf(Limitation.SHOULDER)),
        Variant("decline", Row.PUSH, "ضغط مائل للأسفل", "Decline push-ups", 1.3, 1, GUnit.REPS, setOf(Limitation.SHOULDER, Limitation.WRIST)),
        Variant("diamond", Row.PUSH, "ضغط ماسي", "Diamond push-ups", 1.4, 1, GUnit.REPS, setOf(Limitation.WRIST, Limitation.ELBOW)),
        Variant("archer", Row.PUSH, "ضغط الرامي", "Archer push-ups", 2.0, 2, GUnit.REPS, setOf(Limitation.SHOULDER, Limitation.WRIST, Limitation.ELBOW), "عدّ كل جانب تكرارًا.", "Each side counts as one rep."),
        Variant("", Row.SITUP, "بطن", "Sit-ups", 1.0, 0, GUnit.REPS),
        Variant("crunch", Row.SITUP, "كرنش", "Crunches", 0.8, 0, GUnit.REPS),
        Variant("plank", Row.SITUP, "بلانك (ثوانٍ)", "Plank (seconds)", 0.5, 0, GUnit.SEC, emptySet(), "اجمع الثواني على عدة مرات.", "Sum the seconds over several holds."),
        Variant("legraise", Row.SITUP, "رفع الساقين", "Leg raises", 1.5, 1, GUnit.REPS, setOf(Limitation.LOWER_BACK)),
        Variant("", Row.SQUAT, "سكوات", "Squats", 1.0, 0, GUnit.REPS),
        Variant("lunge", Row.SQUAT, "طعنات", "Lunges", 1.2, 0, GUnit.REPS, setOf(Limitation.KNEE)),
        Variant("split", Row.SQUAT, "سكوات منقسم", "Split squats", 1.4, 1, GUnit.REPS, setOf(Limitation.KNEE)),
        Variant("jump", Row.SQUAT, "سكوات قفز", "Jump squats", 1.6, 2, GUnit.REPS, IMPACT + Limitation.HIP),
        Variant("", Row.RUN, "جري", "Running", 1.0, 0, GUnit.M),
        Variant("intervals", Row.RUN, "جري متقطع", "Intervals", 1.25, 1, GUnit.M, IMPACT, "دقيقة سريعة / دقيقتان هادئتان.", "1 min fast / 2 min easy."),
        Variant("stairs", Row.RUN, "صعود الدرج", "Stairs", 80.0, 1, GUnit.FLOORS, setOf(Limitation.KNEE), "اصعد طابقًا وانزل مشيًا = طابق واحد.", "Up one floor, walk down = 1 floor."),
        Variant("sprints", Row.RUN, "سرعات قصيرة", "Sprints", 2.5, 2, GUnit.M, IMPACT + Limitation.HIP, "مسافة السرعات فقط؛ إحماء 5 دقائق أولًا.", "Sprint distance only; 5-min warm-up first."),
    )
    fun variants(r: Row) = VARIANTS.filter { it.row == r }
    fun variant(r: Row, id: String): Variant = variants(r).firstOrNull { it.id == id } ?: variants(r).first()
    fun variantOf(q: Quest): Variant? = row(q.templateId)?.let { variant(it, q.variant) }

    /** 0..3 tier from fitness and player level. */
    fun tier(s: AppState): Int = maxOf(s.profile?.fitness?.ordinal ?: 0, (com.ayman.solosystem.domain.progression.Ledger.level(s).level / 10)).coerceIn(0, 3)

    /** Converts a base-unit amount into variant units, rounding UP so the work is never easier than the base. */
    fun toVariant(v: Variant, base: Int): Int {
        val x = base / v.factor
        return when (v.unit) {
            GUnit.M -> (kotlin.math.ceil(x / 100.0) * 100).toInt().coerceAtLeast(100)
            GUnit.SEC -> (kotlin.math.ceil(x / 5.0) * 5).toInt().coerceAtLeast(10)
            else -> kotlin.math.ceil(x - 1e-9).toInt().coerceAtLeast(1)
        }
    }
    /** Intensity of an amount in base-equivalent units. */
    fun intensity(v: Variant, amount: Int) = amount * v.factor

    /** Variety: on ~1 day in 3 per row (staggered so rows differ), rotate to an equivalent-difficulty variant allowed by tier and limitations.
     *  Never in the first week, nor on rest, deload or pain days (base movement only). */
    fun rotatedVariant(s: AppState, r: Row, day: Long): Variant {
        val base = variants(r).first()
        if (lightDay(s, day) != 1.0) return base
        if (day - (s.profile?.createdDay ?: 0L) < 7) return base      // first week: learn the base movements
        if (Math.floorMod(day + r.ordinal, 3L) != 1L) return base
        val lim = s.profile?.limitations?.toSet() ?: emptySet()
        val ok = variants(r).drop(1).filter { it.tier <= tier(s) && it.avoid.none { a -> a in lim } }
        if (ok.isEmpty()) return base
        return ok[Math.floorMod(day / 3 + r.ordinal, ok.size.toLong()).toInt()]
    }

    fun fmtV(v: Variant, amount: Int) = if (v.unit == GUnit.M) fmtKm(amount) else "$amount"

    const val OVER_FACTOR = 2           // [200/100] → overachievement bonus
    fun row(templateId: String): Row? = Row.entries.firstOrNull { "goal:${it.id}" == templateId }
    fun isGoal(q: Quest) = q.verify == Verify.COUNT && q.templateId.startsWith("goal:")

    private fun r5(x: Double) = (((x / 5).roundToInt()) * 5).coerceAtLeast(5)

    /** Rank-E starting floor (base units) for the strength rows; RUN has none (weight/intake logic). */
    fun floor(r: Row): Int = when (r) { Row.PUSH -> 50; Row.SITUP -> 50; Row.SQUAT -> 60; Row.RUN -> 0 }
    /** Weekly ramp step for the strength rows while below the 100 cap. */
    const val FAST_STEP = 15

    /** Raises existing strength tracks to the floor (never lowers anything, keeps the ramp clock). Used by the 1.2.1 migration. */
    fun liftToFloor(s: AppState): AppState {
        if (s.goalTracks.isEmpty()) return s
        val m = s.goalTracks.mapValues { (id, t) ->
            val r = Row.entries.firstOrNull { it.id == id } ?: return@mapValues t
            val f = floor(r)
            if (r == Row.RUN || t.current >= f) t else t.copy(current = f)
        }
        return if (m == s.goalTracks) s else s.copy(goalTracks = m)
    }

    /** Starting full-day total: the rank-E [floor] (50/50/60), or more if the measured capacity (~2× a max set) is higher. */
    fun initial(s: AppState, r: Row): Int = if (r == Row.RUN) capacityStart(s, r) else maxOf(floor(r), capacityStart(s, r))

    /** Start from real capacity only (pre-1.2.1 rule): ~2× a max set. */
    fun capacityStart(s: AppState, r: Row): Int {
        val p = s.profile
        val a = s.assessments.maxByOrNull { it.day }
        val fit = p?.fitness ?: FitnessLevel.BEGINNER
        val lvl = fit.ordinal // 0..3
        return when (r) {
            Row.PUSH -> r5(2.0 * (a?.pushReps ?: listOf(4, 8, 15, 25)[lvl])).coerceAtMost(r.cap)
            Row.SQUAT -> r5(2.0 * (a?.squatReps ?: listOf(10, 15, 25, 40)[lvl])).coerceAtMost(r.cap)
            Row.SITUP -> r5(a?.plankSec?.let { it / 2.0 } ?: listOf(10.0, 20.0, 30.0, 50.0)[lvl]).coerceAtMost(r.cap)
            Row.RUN -> listOf(1000, 1500, 2500, 4000)[lvl] - (if (p?.limitations?.any { it == Limitation.KNEE || it == Limitation.ANKLE || it == Limitation.HIGH_IMPACT } == true) 500 else 0)
        }.coerceAtLeast(if (r == Row.RUN) 500 else 5)
    }

    // ---------------- running energy budget (lean bulk) ----------------
    /**
     * Max daily GOAL-run metres so its energy can be eaten back on top of the surplus:
     *  eating ≥90 % of target → run burn ≤ 2× surplus (≤800 kcal); under-eating (<90 %) → ≤ 0.5× surplus; not enough logs → ≤ 1× surplus.
     * Floor 500 m (walk/jog), never above the 10 km cap. null when weight/height are unknown (capacity rules only).
     */
    fun runEnergyCapM(s: AppState, day: Long): Int? {
        val t = com.ayman.solosystem.domain.nutrition.Nutrition.baseTargetsFor(s, day) ?: return null
        val kg = com.ayman.solosystem.domain.nutrition.Nutrition.currentWeight(s) ?: return null
        val ratio = com.ayman.solosystem.domain.nutrition.Nutrition.intakeRatio(s, day)
        val maxKcal = when { ratio == null -> t.surplus.toDouble(); ratio >= 0.9 -> minOf(2.0 * t.surplus, 800.0); else -> 0.5 * t.surplus }
        val m = (maxKcal / (kg * com.ayman.solosystem.domain.nutrition.Nutrition.RUN_KCAL_PER_KG_KM) * 1000 / 100).toInt() * 100
        return m.coerceIn(500, Row.RUN.cap)
    }

    fun runCapReason(s: AppState, day: Long): Txt? {
        val ratio = com.ayman.solosystem.domain.nutrition.Nutrition.intakeRatio(s, day)
        return when {
            ratio == null -> Txt("سقف الطاقة: سجّل وجباتك في الترياق ليرتفع سقف الجري (حرق الجري ≤ الفائض).", "Energy cap: log meals in the Elixir to raise the run cap (run burn ≤ surplus).")
            ratio < 0.9 -> Txt("أنت تأكل أقل من هدفك (${(ratio * 100).roundToInt()}%) — خُفّض سقف الجري حتى لا يأكل الفائض.", "You're eating below target (${(ratio * 100).roundToInt()}%) — run cap lowered so it doesn't eat the surplus.")
            else -> null
        }
    }

    /** Weekly step: RUN 500 m; strength rows +[FAST_STEP] (never past the cap). */
    fun step(r: Row, current: Int): Int = if (r == Row.RUN) 500 else maxOf(FAST_STEP, r5(current * 0.10))

    fun track(s: AppState, r: Row, today: Long): GoalTrack {
        val t = s.goalTracks[r.id] ?: return initial(s, r).let { GoalTrack(it, it, today) }
        return if (r != Row.RUN && t.current < floor(r)) t.copy(current = floor(r)) else t   // floor always holds
    }

    /** Exact schedule of full-day targets per consistent week (week 0 = start) from [start] to the cap. */
    fun schedule(r: Row, start: Int = floor(r)): List<Int> {
        val out = mutableListOf(start); var c = start
        while (c < r.cap && r != Row.RUN) { c = (c + step(r, c)).coerceAtMost(r.cap); out += c }
        return out
    }

    private fun painRecently(s: AppState, today: Long) =
        s.checkIns.any { it.day in (today - 3)..today && it.pain >= 2 } || s.sessions.any { it.day in (today - 3)..today && (it.painReports.isNotEmpty() || it.stoppedForSafety) }

    /** Weekly ramp: +step (15) if ≥5 of the last 7 days were completed and there was no pain; otherwise hold. Never above cap. */
    fun ramp(s: AppState, today: Long): AppState {
        if (s.profile == null) return s
        val m = s.goalTracks.toMutableMap()
        Row.entries.forEach { r ->
            val t = track(s, r, today)
            var nt = t
            if (today - t.lastRampDay >= 7) {
                // only days done at (at least half of) the CURRENT level count — e.g. old 5-rep days from before the 1.2.1 floor don't
                // trigger an immediate jump after the migration; light (rest 60 % / pain 50 %) days still count.
                val done = s.quests.count { it.templateId == "goal:${r.id}" && it.day in (today - 7) until today && it.status == QuestStatus.COMPLETED &&
                    (r == Row.RUN || intensity(variant(r, it.variant), it.target) >= 0.5 * t.current - 1e-6) }
                val ok = done >= 5 && !painRecently(s, today)
                nt = if (r != Row.RUN) t.copy(current = if (ok) (t.current + step(r, t.current)).coerceAtMost(r.cap) else t.current, lastRampDay = today)
                else rampRun(s, t, ok, today)
            }
            m[r.id] = nt
        }
        return s.copy(goalTracks = m)
    }

    /**
     * Running grows only when the weight trend is on target (≥0.25 %/week, 14-day fit) and the week was consistent & pain-free;
     * a stalled/losing trend (<0.1 %/week) steps it DOWN; no trend data → hold. The reason is stored and shown.
     */
    fun rampRun(s: AppState, t: GoalTrack, okWeek: Boolean, today: Long): GoalTrack {
        val rate = com.ayman.solosystem.domain.nutrition.Nutrition.weeklyRatePct(s.bodyWeights, today)
        val st = step(Row.RUN, t.current)
        val pct = rate?.let { "%.2f".format(java.util.Locale.US, it) }
        return when {
            rate == null -> t.copy(lastRampDay = today, note = Txt("ثابت: لا يوجد اتجاه وزن كافٍ بعد — سجّل وزنك أسبوعيًا.", "Held: not enough weight trend yet — weigh in weekly."))
            rate < 0.1 -> t.copy(current = (t.current - st).coerceAtLeast(500), lastRampDay = today,
                note = Txt("خُفّض ${fmtKm(st)} كم: زيادة الوزن متوقفة ($pct%/أسبوع) — الجري لا يجب أن يأكل الفائض.", "Reduced ${fmtKm(st)} km: weight gain stalled ($pct%/wk) — running must not eat the surplus."))
            rate < 0.25 -> t.copy(lastRampDay = today, note = Txt("ثابت: الزيادة أبطأ من الهدف ($pct%/أسبوع).", "Held: gain below target ($pct%/wk)."))
            !okWeek -> t.copy(lastRampDay = today, note = Txt("ثابت: أسبوع غير مكتمل أو ألم.", "Held: incomplete week or pain."))
            else -> t.copy(current = (t.current + st).coerceAtMost(Row.RUN.cap), lastRampDay = today,
                note = Txt("+${fmtKm(st)} كم: الوزن على المسار ($pct%/أسبوع).", "+${fmtKm(st)} km: weight on track ($pct%/wk)."))
        }
    }

    /** Today's target: full on training days, ~60 % on rest days / deload weeks, 50 % after recent pain. */
    fun target(s: AppState, r: Row, day: Long): Int {
        val full = track(s, r, day).current
        val f = lightDay(s, day)
        // Cursed-box curse: +10 % on a normal day only, never above the row cap / run energy cap
        val c = if (day in s.curseDays && f >= 1.0) CURSE_FACTOR else 1.0
        return if (r == Row.RUN) {
            val t = (((full * f * c) / 100).roundToInt() * 100).coerceAtLeast(500).let { if (c > 1.0) minOf(it, maxOf(full, r.cap)) else it }
            runEnergyCapM(s, day)?.let { minOf(t, it) } ?: t
        } else if (c > 1.0) minOf(r5(full * c), maxOf(r5(full.toDouble()), r.cap)) else r5(full * f)
    }

    const val CURSE_FACTOR = 1.1

    /** 1.0 normal, 0.6 rest/deload, 0.5 after pain. */
    fun lightDay(s: AppState, day: Long): Double {
        val plan = s.plan
        val rest = plan != null && !PlanGenerator.dayPlan(plan, day).isTraining
        val deload = plan != null && PlanGenerator.isDeloadWeek(plan, day)
        return when { painRecently(s, day) -> 0.5; rest || deload -> 0.6; else -> 1.0 }
    }

    fun setsHint(r: Row, target: Int): Txt {
        if (r == Row.RUN) {
            val km = fmtKm(target)
            return if (target < 3000) Txt("$km كم مشي/هرولة متقطعة بوتيرة مريحة (تتحول إلى جري تدريجيًا).", "$km km walk/jog intervals at an easy pace (becomes running gradually).")
            else Txt("$km كم بوتيرة محادثة — يمكن تقسيمها صباحًا ومساءً.", "$km km at conversational pace — can be split morning/evening.")
        }
        val sets = sets(target)
        val per = (target + sets - 1) / sets
        return Txt("يمكن تقسيمها إلى مجموعات خلال اليوم: $sets × ~$per (صباحًا، ظهرًا، مساءً). المهم أن تكتمل قبل منتصف الليل. توقف قبل الفشل الفني بتكرار أو اثنين.",
            "Can be split into sets through the day: $sets × ~$per (morning, noon, evening). It just has to be done before midnight. Stop 1–2 reps before technical failure.")
    }

    /** Suggested number of sets for a day's total (≈10–13 reps per set). */
    fun sets(target: Int): Int = when { target <= 20 -> 2; target <= 40 -> 3; target <= 60 -> 5; target <= 80 -> 6; else -> 8 }
    /** Short always-visible hint for the quest row. */
    fun splitHint(target: Int): Txt { val n = sets(target); val per = (target + n - 1) / n
        return Txt("يمكن تقسيمها إلى مجموعات · $n×~$per", "can be split into sets · $n×~$per") }

    fun variantNote(s: AppState, r: Row): Txt? {
        val l = s.profile?.limitations ?: return null
        return when {
            r == Row.PUSH && (Limitation.WRIST in l || Limitation.SHOULDER in l || Limitation.ELBOW in l) -> Txt("استخدم ضغطًا مائلًا على طاولة أو قبضات محايدة.", "Use incline push-ups on a table or neutral grips.")
            r == Row.SQUAT && (Limitation.KNEE in l || Limitation.HIP in l) -> Txt("سكوات إلى كرسي بمدى مريح بلا ألم.", "Box squats to a chair, pain-free range.")
            r == Row.SITUP && (Limitation.LOWER_BACK in l || Limitation.NECK in l) -> Txt("استبدلها بـ dead bug أو كرنش جزئي.", "Swap for dead bugs or partial crunches.")
            r == Row.RUN && (Limitation.KNEE in l || Limitation.ANKLE in l || Limitation.HIGH_IMPACT in l) -> Txt("مشي سريع بدل الجري.", "Brisk walking instead of running.")
            else -> null
        }
    }

    fun fmtKm(m: Int): String = if (m % 1000 == 0) "${m / 1000}" else "%.1f".format(java.util.Locale.US, m / 1000.0)
    fun fmt(r: Row, v: Int) = if (r == Row.RUN) fmtKm(v) else "$v"

    /** "[⁦0 / 1⁩ كم]" — numbers bidi-isolated, NBSP before the unit so the bracket never wraps or reorders in RTL (v1.2.2). */
    fun objective(v: Variant, p: Int, t: Int) = Txt("[\u2066$p / ${fmtV(v, t)}\u2069${v.unit.ar.let { if (it.isEmpty()) "" else "\u00A0$it" }}]",
        "[$p / ${fmtV(v, t)}${v.unit.en.let { if (it.isEmpty()) "" else "\u00A0$it" }}]")
    fun hintFor(v: Variant, baseTarget: Int, t: Int): Txt {
        if (v.id.isEmpty()) return setsHint(v.row, baseTarget)
        val h = if (v.row == Row.RUN && v.unit == GUnit.M) setsHint(v.row, t) else {
            val sets = sets(t)
            Txt("$sets مجموعات × ~${(t + sets - 1) / sets}${v.unit.ar.let { if (it.isEmpty()) "" else " $it" }}.", "$sets sets × ~${(t + sets - 1) / sets}${v.unit.en.let { if (it.isEmpty()) "" else " $it" }}.")
        }
        val eq = Txt("تنويع بصعوبة مكافئة لـ ${fmt(v.row, baseTarget)}${if (v.row == Row.RUN) " كم" else ""} ${v.row.ar}. ", "Variety at equivalent difficulty to ${fmt(v.row, baseTarget)}${v.row.unitEn} ${v.row.en.lowercase()}. ")
        return Txt(eq.ar + h.ar + (if (v.noteAr.isNotEmpty()) " " + v.noteAr else ""), eq.en + h.en + (if (v.noteEn.isNotEmpty()) " " + v.noteEn else ""))
    }

    fun quests(s: AppState, day: Long): List<Quest> = Row.entries.map { r ->
        val base = target(s, r, day)
        val v = rotatedVariant(s, r, day)
        val t = if (v.id.isEmpty()) base else toVariant(v, base)
        val min = if (r == Row.RUN) (base / 1000.0 * 8).roundToInt().coerceAtLeast(6) else (base / 8).coerceAtLeast(4)
        val rank = Rank.D
        Quest("d:goal_${r.id}:$day", "goal:${r.id}", QuestType.DAILY, QuestCategory.TRAINING, Txt(v.ar, v.en),
            objective(v, 0, t), hintFor(v, base, t), rank, min,
            Leveling.questXp(rank, min, QuestType.DAILY), day, verify = Verify.COUNT, target = t, deadlineDay = day, variant = v.id)
    }

    /** Player edit result. */
    sealed interface Edit { data class Ok(val q: Quest) : Edit; data object Lower : Edit; data object Invalid : Edit }

    /**
     * Player may only RAISE intensity: a higher amount and/or a variant whose factor is ≥ the current one with ≥ the current intensity.
     * The original requirement ([Quest.target], converted into the new variant's units, rounded up) stays the completion bar;
     * the new amount is stored as [Quest.stretch].
     */
    fun edit(q: Quest, newVariant: String, newAmount: Int): Edit {
        val r = row(q.templateId) ?: return Edit.Invalid
        if (variants(r).none { it.id == newVariant }) return Edit.Invalid
        val cur = variant(r, q.variant); val nv = variant(r, newVariant)
        val curAmount = maxOf(q.stretch, q.target)
        if (nv.factor < cur.factor - 1e-9) return Edit.Lower
        if (intensity(nv, newAmount) < intensity(cur, curAmount) - 1e-6) return Edit.Lower
        if (nv.id == cur.id && newAmount == curAmount) return Edit.Invalid
        val target = if (nv.id == cur.id) q.target else toVariant(nv, kotlin.math.ceil(intensity(cur, q.target)).toInt())
        val conv = if (nv.id == cur.id) q.progress else kotlin.math.floor(intensity(cur, q.progress) / nv.factor + 1e-9).toInt()
        val progress = if (q.progress >= q.target) maxOf(conv, target) else conv   // an already-met goal stays met
        val stretch = if (newAmount > target) newAmount else 0
        return Edit.Ok(q.copy(variant = nv.id, target = target, progress = progress, stretch = stretch, title = Txt(nv.ar, nv.en),
            objective = objective(nv, progress, target)))
    }

    /** Bonus XP for a player-raised amount once reached: proportional to the extra intensity, capped at +100 % of the quest XP. */
    fun stretchXp(q: Quest): Int {
        if (q.stretch <= q.target) return 0
        val extra = ((q.stretch - q.target).toDouble() / q.target).coerceAtMost(1.0)
        return (q.xp * extra).roundToInt().coerceAtLeast(2)
    }

    /** System-only lowering after pain / illness / injury: back to the base movement at the reduced (pain) target. Never raises. */
    fun lowered(s: AppState, q: Quest, day: Long): Quest {
        val r = row(q.templateId) ?: return q
        val cur = variant(r, q.variant)
        val curBase = kotlin.math.ceil(intensity(cur, q.target)).toInt()
        val nt = minOf(curBase, r5(curBase * 0.5).let { if (r == Row.RUN) ((curBase * 0.5 / 100).roundToInt() * 100).coerceAtLeast(500) else it })
        val base = variants(r).first()
        val np = kotlin.math.floor(intensity(cur, q.progress)).toInt()
        return q.copy(lowered = true, variant = "", target = nt, stretch = 0, progress = np, title = Txt(base.ar, base.en), objective = objective(base, np, nt),
            description = Txt("خففها النظام بسبب الألم/المرض. " + setsHint(r, nt).ar, "Lowered by the System due to pain/illness. " + setsHint(r, nt).en))
    }
}
