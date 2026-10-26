package com.ayman.solosystem.domain.training

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.model.Pattern.*
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Rule-based, deterministic plan generator. Same profile + tier = same plan. */
object PlanGenerator {

    fun trainingDays(days: Int): List<Int> = when (days.coerceIn(2, 6)) {
        2 -> listOf(1, 4); 3 -> listOf(1, 3, 5); 4 -> listOf(1, 2, 4, 5); 5 -> listOf(1, 2, 3, 5, 6); else -> listOf(1, 2, 3, 4, 5, 6)
    }

    fun isNewToTraining(p: PlayerProfile) = p.fitness == FitnessLevel.BEGINNER || p.experience == Experience.NONE

    fun chooseSplit(p: PlayerProfile): Split = when {
        p.daysPerWeek <= 3 -> Split.FULL_BODY
        isNewToTraining(p) -> Split.FULL_BODY      // extra days become light conditioning days
        p.daysPerWeek == 4 -> Split.UPPER_LOWER
        p.daysPerWeek == 5 -> Split.PPL_UL
        else -> Split.PPL
    }

    fun dayTypes(p: PlayerProfile, split: Split): List<DayType> {
        val n = trainingDays(p.daysPerWeek).size
        return when (split) {
            Split.FULL_BODY -> if (n <= 3) List(n) { if (it % 2 == 0) DayType.FULL_A else DayType.FULL_B }
            else when (n) { // beginner with 4–6 days: max 3 full-body strength days, rest are light conditioning
                4 -> listOf(DayType.FULL_A, DayType.CONDITIONING, DayType.FULL_B, DayType.CONDITIONING)
                5 -> listOf(DayType.FULL_A, DayType.CONDITIONING, DayType.FULL_B, DayType.FULL_A, DayType.CONDITIONING)
                else -> listOf(DayType.FULL_A, DayType.CONDITIONING, DayType.FULL_B, DayType.CONDITIONING, DayType.FULL_A, DayType.CONDITIONING)
            }
            Split.UPPER_LOWER -> listOf(DayType.UPPER, DayType.LOWER, DayType.UPPER, DayType.LOWER)
            Split.PPL_UL -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.UPPER, DayType.LOWER)
            Split.PPL -> listOf(DayType.PUSH, DayType.PULL, DayType.LEGS, DayType.PUSH, DayType.PULL, DayType.LEGS)
        }
    }

    fun slots(type: DayType): List<Pattern> = when (type) {
        DayType.FULL_A -> listOf(SQUAT, H_PUSH, H_PULL, HINGE, CORE, CALF)
        DayType.FULL_B -> listOf(HINGE, V_PUSH, V_PULL, LUNGE, CORE, BICEPS)
        DayType.UPPER -> listOf(H_PUSH, H_PULL, V_PUSH, V_PULL, TRICEPS, BICEPS, CORE)
        DayType.LOWER -> listOf(SQUAT, HINGE, LUNGE, CALF, CORE)
        DayType.PUSH -> listOf(H_PUSH, V_PUSH, H_PUSH, SHOULDER_ACC, TRICEPS)
        DayType.PULL -> listOf(V_PULL, H_PULL, H_PULL, BICEPS, CORE)
        DayType.LEGS -> listOf(SQUAT, HINGE, LUNGE, CALF, CORE)
        DayType.CONDITIONING -> listOf(CONDITIONING, CORE, CARRY, MOBILITY)
        DayType.REST -> emptyList()
    }

    private val fallback = mapOf(
        V_PULL to H_PULL, SHOULDER_ACC to H_PULL, BICEPS to H_PULL, TRICEPS to H_PUSH,
        CALF to CORE, CARRY to CORE, V_PUSH to H_PUSH, LUNGE to SQUAT
    )

    fun baseDifficulty(p: PlayerProfile): Int {
        val b = when (p.fitness) { FitnessLevel.BEGINNER -> 1; FitnessLevel.NOVICE -> 2; FitnessLevel.INTERMEDIATE -> 3; FitnessLevel.ADVANCED -> 4 }
        val capped = if (p.experience == Experience.NONE) min(b, 2) else b
        return if (p.age >= 60) min(capped, 2) else capped
    }

    /** Difficulty used for exercise selection: base + bonus from the ACTIVE (assessment-passed) training tier. */
    fun targetDifficulty(p: PlayerProfile, activeTier: Int) = min(5, baseDifficulty(p) + activeTier / 2)

    fun candidates(pattern: Pattern, p: PlayerProfile): List<Exercise> =
        ExerciseLibrary.all.filter {
            it.pattern == pattern && ExerciseLibrary.available(it, p.equipment) && ExerciseLibrary.safeFor(it, p.limitations) && it.id !in p.avoidExerciseIds
        }

    fun pick(pattern: Pattern, p: PlayerProfile, difficulty: Int, used: Set<String>): Exercise? {
        var pat: Pattern? = pattern
        val tried = mutableSetOf<Pattern>()
        while (pat != null && tried.add(pat)) {
            val c = candidates(pat, p).filter { it.id !in used }
            if (c.isNotEmpty()) {
                val preferLoad = p.goal == Goal.STRENGTH || p.goal == Goal.HYPERTROPHY
                val within = c.filter { it.level <= difficulty }
                return if (within.isNotEmpty()) within.sortedWith(compareByDescending<Exercise> { it.level }.thenByDescending { if (preferLoad && it.loadable) 1 else 0 }).first()
                else c.minByOrNull { it.level }
            }
            pat = fallback[pat]
        }
        return null
    }

    data class Scheme(val repMin: Int, val repMax: Int, val rest: Int)

    fun scheme(goal: Goal, e: Exercise, main: Boolean, p: PlayerProfile): Scheme {
        if (e.timed) {
            if (e.pattern == CONDITIONING && (e.id == "brisk_walk")) return Scheme(600, 1200, 60)
            if (e.pattern == MOBILITY) return Scheme(300, 480, 30)
            return if (isNewToTraining(p)) Scheme(20, 40, 45) else if (goal == Goal.ENDURANCE) Scheme(45, 90, 45) else Scheme(30, 60, 60)
        }
        return when (goal) {
            Goal.STRENGTH -> if (e.loadable) (if (main) Scheme(4, 6, 150) else Scheme(6, 10, 90)) else Scheme(6, 12, 90)
            Goal.HYPERTROPHY -> if (e.loadable) Scheme(8, 12, if (main) 90 else 75) else Scheme(10, 15, 60)
            Goal.ENDURANCE -> if (e.loadable) Scheme(12, 20, 45) else Scheme(15, 25, 45)
            Goal.GENERAL -> if (e.loadable) Scheme(8, 12, 75) else Scheme(10, 15, 60)
        }
    }

    fun mainSets(p: PlayerProfile, activeTier: Int): Int {
        val base = when (p.fitness) { FitnessLevel.BEGINNER -> 2; FitnessLevel.NOVICE, FitnessLevel.INTERMEDIATE -> 3; FitnessLevel.ADVANCED -> 4 }
        val bonus = (if (activeTier >= 3) 1 else 0) + (if (activeTier >= 6) 1 else 0)
        return min(5, base + bonus)
    }

    fun estimateSeconds(items: List<Prescription>): Int = items.sumOf { pr ->
        val work = if (pr.timed) pr.repMax else pr.repMax * 4
        pr.sets * (work + pr.restSec) + 60
    }

    fun buildDay(dayOfWeek: Int, type: DayType, p: PlayerProfile, activeTier: Int): DayPlan {
        if (type == DayType.REST) return DayPlan(dayOfWeek, type)
        val diff = targetDifficulty(p, activeTier)
        val budget = (p.minutesPerSession - 15).coerceAtLeast(10) * 60
        val pats = slots(type).toMutableList()
        if (p.goal == Goal.ENDURANCE && type != DayType.CONDITIONING) pats.add(CONDITIONING)
        val used = mutableSetOf<String>()
        val out = mutableListOf<Prescription>()
        val ms = mainSets(p, activeTier)
        for ((i, pat) in pats.withIndex()) {
            val e = pick(pat, p, if (type == DayType.CONDITIONING) min(diff, 2) else diff, used) ?: continue
            val main = i < 2 && type != DayType.CONDITIONING
            val sc = scheme(p.goal, e, main, p)
            val sets = when {
                e.pattern == MOBILITY || e.id == "brisk_walk" -> 1
                main -> ms
                else -> max(2, ms - 1)
            }
            val rpe = if (isNewToTraining(p)) 7 else 8
            val pr = Prescription(out.size, e.id, sets, sc.repMin, sc.repMax, sc.rest, e.timed, rpe)
            if (out.size >= 3 && estimateSeconds(out + pr) > budget) break
            used += e.id; out += pr
        }
        // If even 3 exercises overflow a short session, trim sets (never below 1).
        var trimmed = out.toList()
        while (estimateSeconds(trimmed) > budget && trimmed.any { it.sets > 1 }) {
            val idx = trimmed.indexOfLast { it.sets > 1 }
            trimmed = trimmed.toMutableList().also { it[idx] = it[idx].copy(sets = it[idx].sets - 1) }
        }
        return DayPlan(dayOfWeek, type, trimmed)
    }

    fun generate(p: PlayerProfile, startDay: Long, deloadEvery: Int, activeTier: Int = 0): TrainingPlan {
        val split = chooseSplit(p)
        val days = trainingDays(p.daysPerWeek)
        val types = dayTypes(p, split)
        val week = (1..7).map { dow ->
            val idx = days.indexOf(dow)
            buildDay(dow, if (idx >= 0) types[idx] else DayType.REST, p, activeTier)
        }
        val de = if (p.age >= 40) min(deloadEvery, 5) else deloadEvery
        return TrainingPlan(
            split = split, startDay = startDay, week = week, difficultyLevel = targetDifficulty(p, activeTier), deloadEvery = de,
            rationale = rationale(p, split, days.size, activeTier), excludedNotes = exclusions(p),
            warmup = warmup(p), cooldown = cooldown(), recoveryTips = recoveryTips(p)
        )
    }

    fun rationale(p: PlayerProfile, split: Split, n: Int, tier: Int): List<Txt> {
        val r = mutableListOf<Txt>()
        r += when (split) {
            Split.FULL_BODY -> if (n <= 3) Txt("$n أيام متاحة ← برنامج جسم كامل: كل عضلة تتدرب $n مرات أسبوعيًا مع يوم راحة بين الحصص.", "$n days available → full-body plan: each muscle trained $n×/week with rest days between.")
            else Txt("بما أنك جديد على التدريب: 3 حصص قوة كحد أقصى للجسم كامل، والأيام الإضافية حركة خفيفة للتحمل والتعافي.", "New to training: at most 3 full-body strength days; extra days are light conditioning for recovery.")
            Split.UPPER_LOWER -> Txt("4 أيام ← تقسيم علوي/سفلي: كل عضلة مرتين أسبوعيًا مع حجم مناسب.", "4 days → upper/lower split: each muscle twice a week with adequate volume.")
            Split.PPL_UL -> Txt("5 أيام ← دفع/سحب/أرجل ثم علوي/سفلي: تكرار ~2 مرة أسبوعيًا لكل عضلة.", "5 days → push/pull/legs then upper/lower: ~2×/week per muscle.")
            Split.PPL -> Txt("6 أيام ← دفع/سحب/أرجل مرتين: مناسب لذوي الخبرة مع يوم راحة كامل.", "6 days → PPL twice: suited to experienced lifters, with one full rest day.")
        }
        r += when (p.goal) {
            Goal.STRENGTH -> Txt("هدف القوة: تكرارات أقل (4–6 للتمارين المحمّلة) وراحة أطول 2–3 دقائق.", "Strength goal: lower reps (4–6 on loaded lifts) and longer 2–3 min rests.")
            Goal.HYPERTROPHY -> Txt("هدف بناء العضلات: 8–12 تكرارًا قريبًا من الفشل بهامش 2–3 تكرارات، وراحة 60–90 ثانية.", "Hypertrophy goal: 8–12 reps leaving 2–3 in reserve, 60–90 s rest.")
            Goal.ENDURANCE -> Txt("هدف التحمل: تكرارات أعلى وراحة قصيرة مع تمرين تحمّل في نهاية الحصة.", "Endurance goal: higher reps, short rests, plus a conditioning finisher.")
            Goal.GENERAL -> Txt("لياقة عامة: نطاق متوسط 8–12 مع توازن بين القوة والحركة.", "General fitness: moderate 8–12 range balancing strength and movement.")
        }
        r += Txt("الصعوبة المبدئية ${targetDifficulty(p, tier)}/5 حسب مستواك وخبرتك. ابدأ بحمل يترك 2–3 تكرارات احتياطية.", "Starting difficulty ${targetDifficulty(p, tier)}/5 from your level and experience. Start with a load that leaves 2–3 reps in reserve.")
        r += Txt("التدرج المزدوج: عندما تصل لأعلى نطاق التكرارات في كل المجموعات بجهد ≤8 وبدون ألم لحصتين متتاليتين، يقترح النظام زيادة صغيرة.", "Double progression: hit the top of the rep range on all sets at RPE ≤8 with no pain for 2 sessions → the system suggests a small increase.")
        r += Txt("مدة الحصة محسوبة لتناسب ${p.minutesPerSession} دقيقة شاملة الإحماء والتهدئة.", "Session length fitted to ${p.minutesPerSession} min including warm-up and cool-down.")
        r += Txt("ملاحظة: هذه إرشادات تدريب عامة وليست نصيحة طبية. التقدّم الفعلي يختلف من شخص لآخر.", "Note: general training guidance, not medical advice. Individual progress varies.")
        return r
    }

    fun exclusions(p: PlayerProfile): List<Txt> {
        if (p.limitations.isEmpty() && p.avoidExerciseIds.isEmpty()) return emptyList()
        val ex = ExerciseLibrary.all.filter { ExerciseLibrary.available(it, p.equipment) && (!ExerciseLibrary.safeFor(it, p.limitations) || it.id in p.avoidExerciseIds) }
        val areasAr = p.limitations.joinToString("، ") { it.ar }; val areasEn = p.limitations.joinToString(", ") { it.en }
        return listOf(Txt("تم استبعاد ${ex.size} تمرينًا بسبب: $areasAr", "Excluded ${ex.size} exercises because of: $areasEn")) +
            ex.take(12).map { Txt("✕ ${it.ar}", "✕ ${it.en}") }
    }

    fun warmup(p: PlayerProfile): List<Txt> = listOf(
        if (Limitation.HIGH_IMPACT in p.limitations || Limitation.KNEE in p.limitations || Limitation.ANKLE in p.limitations)
            Txt("2–3 دقائق مشي سريع أو مشي في المكان (بدون قفز)", "2–3 min brisk walk or march in place (no jumping)")
        else Txt("2–3 دقائق حركة خفيفة: مشي سريع أو قفز خفيف", "2–3 min light cardio: brisk walk or easy jumping jacks"),
        Txt("دوائر الذراعين والكتفين 10 مرات لكل اتجاه", "Arm & shoulder circles ×10 each way"),
        Txt("دوائر الورك وتأرجح الرجلين 10 مرات", "Hip circles and leg swings ×10"),
        Txt("قطة-جمل للعمود الفقري 8 مرات", "Cat-camel spine mobility ×8"),
        Txt("مجموعتا إحماء خفيفتان لأول تمرين (50% ثم 75%)", "Two light ramp-up sets of the first exercise (50% then 75%)")
    )

    fun cooldown(): List<Txt> = listOf(
        Txt("2–3 دقائق مشي هادئ", "2–3 min easy walking"),
        Txt("تنفس بطيء: شهيق 4 ثوانٍ، زفير 6 ثوانٍ × 6", "Slow breathing: 4 s in, 6 s out ×6"),
        Txt("تمدد لطيف للعضلات المستخدمة 20–30 ثانية بدون ألم", "Gentle stretches for worked muscles, 20–30 s, pain-free")
    )

    fun recoveryTips(p: PlayerProfile): List<Txt> {
        val t = mutableListOf(
            Txt("استهدف 7–9 ساعات نوم بانتظام؛ النوم جزء من التدريب.", "Aim for 7–9 h of regular sleep; sleep is part of training."),
            Txt("أيام الراحة للتعافي: مشي خفيف وحركة وتمدد، بدون تمارين شاقة.", "Rest days are for recovery: light walking, mobility and stretching, no hard training."),
            Txt("وزّع البروتين على وجباتك واشرب الماء حسب العطش والنشاط.", "Spread protein across meals and drink water according to thirst and activity.")
        )
        if (p.sleepHours < 6.5f || p.sleepQuality == SleepQuality.POOR)
            t += Txt("نومك الحالي قليل: لهذا بدأ النظام بحجم تدريب محافظ ومهام نوم يومية.", "Your sleep is currently low: the system starts with conservative volume and daily sleep quests.")
        return t
    }

    // ---------------- Calendar helpers ----------------
    fun dayOfWeek(epochDay: Long): Int = java.time.LocalDate.ofEpochDay(epochDay).dayOfWeek.value
    fun dayPlan(plan: TrainingPlan, epochDay: Long): DayPlan = plan.week.first { it.dayOfWeek == dayOfWeek(epochDay) }
    fun weekIndex(plan: TrainingPlan, epochDay: Long): Int = ((epochDay - plan.startDay).coerceAtLeast(0) / 7).toInt() + 1
    fun isDeloadWeek(plan: TrainingPlan, epochDay: Long): Boolean = weekIndex(plan, epochDay) % plan.deloadEvery == 0

    fun phase(plan: TrainingPlan, epochDay: Long, readinessDeload: Boolean): Txt {
        val w = weekIndex(plan, epochDay)
        return when {
            isDeloadWeek(plan, epochDay) || readinessDeload -> Txt("أسبوع التخفيف (تعافٍ مخطط)", "Deload week (planned recovery)")
            w <= 2 -> Txt("مرحلة التأسيس والتقنية", "Foundation & technique")
            else -> Txt("مرحلة البناء — الدورة ${(w - 1) / plan.deloadEvery + 1}", "Build phase — cycle ${(w - 1) / plan.deloadEvery + 1}")
        }
    }

    /** Deload: ~40% less volume and easier effort; loads ~90% of target. */
    fun deload(pr: Prescription) = pr.copy(sets = max(1, ceil(pr.sets * 0.6).toInt()), rpeTarget = 6)
    fun reduced(pr: Prescription) = pr.copy(sets = max(1, pr.sets - 1), rpeTarget = min(pr.rpeTarget, 7))
}
