package com.ayman.solosystem.domain.nutrition

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.training.PlanGenerator
import kotlin.math.roundToInt

/**
 * "Healing Elixir / ترياق الشفاء" — lean-bulk nutrition coach.
 * Pure functions only. Completely separate from quests, XP, levels and penalties:
 * nothing here grants XP or restricts food; "strict" = firm reminders + a compliance score.
 * All values are estimates (Mifflin-St Jeor + activity factors), not medical advice.
 */
object Nutrition {
    const val MIN_SURPLUS = 150          // never below maintenance for a gain goal
    const val BASE_SURPLUS = 350         // lean bulk: +300–500
    const val MAX_SURPLUS = 700          // no extreme surplus
    const val TARGET_LOW = 0.25          // % bodyweight / week
    const val TARGET_HIGH = 0.5
    const val REVIEW_DAYS = 14

    data class Inputs(val kg: Double, val cm: Double, val age: Int, val sex: Sex, val activity: ActivityLevel,
                      val trainingDays: Int, val sessionMin: Int, val planLevel: Int)

    data class Targets(val bmr: Int, val tdee: Int, val exerciseKcalPerDay: Int, val surplus: Int, val kcal: Int,
                       val protein: Int, val fat: Int, val carbs: Int, val waterMl: Int, val weeklyGainKg: Pair<Double, Double>,
                       val proteinPerKg: Double, val fatPerKg: Double, val runKcal: Int = 0)

    fun bmr(kg: Double, cm: Double, age: Int, sex: Sex): Double = 10 * kg + 6.25 * cm - 5 * age + if (sex == Sex.MALE) 5.0 else -161.0

    /** Resistance-training energy (MET ~4.5–6 by plan level) averaged over the week. */
    fun exerciseKcalPerDay(kg: Double, days: Int, minutes: Int, level: Int): Double {
        val met = 4.5 + 0.25 * level.coerceIn(0, 6)
        return met * 3.5 * kg / 200.0 * minutes * days.coerceIn(0, 7) / 7.0
    }

    fun tdee(i: Inputs): Double = bmr(i.kg, i.cm, i.age, i.sex) * i.activity.factor + exerciseKcalPerDay(i.kg, i.trainingDays, i.sessionMin, i.planLevel)

    fun clampSurplus(base: Int, adjust: Int): Int = (base + adjust).coerceIn(MIN_SURPLUS, MAX_SURPLUS)

    fun targets(i: Inputs, kcalAdjust: Int = 0, trainingToday: Boolean = false): Targets {
        val b = bmr(i.kg, i.cm, i.age, i.sex)
        val ex = exerciseKcalPerDay(i.kg, i.trainingDays, i.sessionMin, i.planLevel)
        val maint = b * i.activity.factor + ex
        val base = if (i.age < 18) 300 else BASE_SURPLUS
        val surplus = clampSurplus(base, kcalAdjust)
        val kcal = (((maint + surplus) / 10).roundToInt() * 10)
        val ppk = when { i.trainingDays >= 5 -> 2.0; i.trainingDays >= 3 -> 1.8; else -> 1.6 }.coerceIn(1.6, 2.2)
        val protein = (ppk * i.kg).roundToInt()
        var fat = (0.9 * i.kg).roundToInt()
        fat = fat.coerceIn((kcal * 0.20 / 9).roundToInt().coerceAtLeast((0.8 * i.kg).roundToInt()), (kcal * 0.35 / 9).roundToInt())
        val carbs = ((kcal - protein * 4 - fat * 9) / 4.0).roundToInt().coerceAtLeast(0)
        val water = ((35 * i.kg + if (trainingToday) 500 else 0) / 50).roundToInt() * 50
        val wk = Pair(i.kg * TARGET_LOW / 100, i.kg * TARGET_HIGH / 100)
        return Targets(b.roundToInt(), maint.roundToInt(), ex.roundToInt(), surplus, kcal, protein, fat, carbs, water, wk, ppk, fat / i.kg)
    }

    fun currentWeight(s: AppState): Double? = s.bodyWeights.maxByOrNull { it.day }?.kg ?: s.profile?.weightKg?.toDouble()

    fun inputs(s: AppState): Inputs? {
        val p = s.profile ?: return null
        val kg = currentWeight(s) ?: return null
        val cm = p.heightCm?.toDouble() ?: return null
        val plan = s.plan
        val days = plan?.week?.count { it.isTraining } ?: p.daysPerWeek
        return Inputs(kg, cm, p.age, p.sex, p.activity, days, p.minutesPerSession, plan?.difficultyLevel ?: 1)
    }

    fun isTrainingDay(s: AppState, day: Long): Boolean {
        val dow = PlanGenerator.dayOfWeek(day)
        return s.plan?.week?.firstOrNull { it.dayOfWeek == dow }?.isTraining ?: false
    }

    /** Base targets without the day's running add-back. */
    fun baseTargetsFor(s: AppState, day: Long): Targets? = inputs(s)?.let { targets(it, s.nutrition.kcalAdjust, isTrainingDay(s, day)) }

    /** Day targets: base + the GOAL run's energy (≈1 kcal/kg/km) added back so running never eats the lean-bulk surplus. */
    fun targetsFor(s: AppState, day: Long): Targets? = baseTargetsFor(s, day)?.let { t ->
        val add = runAddBack(s, day)
        if (add <= 0) t else t.copy(kcal = t.kcal + ((add / 10.0).roundToInt() * 10), carbs = t.carbs + (add / 4.0).roundToInt(), runKcal = add)
    }

    const val RUN_KCAL_PER_KG_KM = 1.0
    fun runKcal(kg: Double, meters: Int): Int = (kg * meters / 1000.0 * RUN_KCAL_PER_KG_KM).roundToInt()

    /** Running energy of [day]'s GOAL run: max(planned, logged) in base-equivalent metres (stairs/sprints via the equivalence factor). */
    fun runAddBack(s: AppState, day: Long): Int {
        val q = s.quests.firstOrNull { it.day == day && it.templateId == "goal:run" && it.verify == Verify.COUNT } ?: return 0
        if (q.status == QuestStatus.EXCUSED || (q.status == QuestStatus.MISSED && q.progress == 0)) return 0
        val v = com.ayman.solosystem.domain.quest.GoalPlan.variant(com.ayman.solosystem.domain.quest.GoalPlan.Row.RUN, q.variant)
        val meters = com.ayman.solosystem.domain.quest.GoalPlan.intensity(v, maxOf(q.target, q.progress)).roundToInt()
        return runKcal(currentWeight(s) ?: return 0, meters)
    }

    /** Average logged kcal ÷ base target over the last 7 days that have ≥3 logged meals… null if not enough logging. */
    fun intakeRatio(s: AppState, today: Long): Double? {
        val t = baseTargetsFor(s, today) ?: return null
        val days = (1L..7L).map { today - it }.filter { d -> s.nutrition.meals.count { it.day == d } >= 3 }
        if (days.size < 3) return null
        return days.map { totals(s, it).kcal.toDouble() / (t.kcal + runAddBack(s, it)).coerceAtLeast(1) }.average()
    }

    /** Plausibility warnings (never blocks, just advises). */
    fun warnings(i: Inputs?, p: PlayerProfile?): List<Txt> {
        val w = mutableListOf<Txt>()
        if (p == null) return w
        if (p.heightCm == null || (p.weightKg == null)) w += Txt("أضف الطول والوزن في الملف الشخصي لحساب احتياجاتك.", "Add height and weight in your profile to compute your needs.")
        if (i == null) return w
        val bmi = i.kg / ((i.cm / 100) * (i.cm / 100))
        if (i.kg < 35 || i.kg > 200 || i.cm < 135 || i.cm > 220) w += Txt("القيم المدخلة تبدو غير معقولة — تحقّق من الوزن والطول.", "Inputs look implausible — check weight and height.")
        if (bmi < 17) w += Txt("مؤشر كتلة الجسم منخفض جدًا (${"%.1f".format(bmi)}). يُنصح بمراجعة طبيب قبل البدء.", "BMI is very low (${"%.1f".format(bmi)}). See a doctor before starting.")
        if (bmi >= 30) w += Txt("مؤشر كتلة الجسم ${"%.1f".format(bmi)} — زيادة الوزن قد لا تناسبك، استشر أخصائي تغذية.", "BMI ${"%.1f".format(bmi)} — weight gain may not suit you; consult a dietitian.")
        if (i.age < 18) w += Txt("أقل من 18 سنة: فائض معتدل فقط، والأفضل بإشراف مختص.", "Under 18: moderate surplus only, ideally supervised.")
        return w
    }

    // ---------------- adaptive review ----------------
    /** Least-squares weekly change (% bodyweight / week) over the last [days]; null if not enough data. */
    fun weeklyRatePct(weights: List<WeightEntry>, today: Long, days: Int = REVIEW_DAYS): Double? {
        val pts = weights.filter { it.day in (today - days)..today }.sortedBy { it.day }
        if (pts.size < 3 || pts.last().day - pts.first().day < days - 3) return null
        val mx = pts.map { it.day.toDouble() }.average(); val my = pts.map { it.kg }.average()
        val den = pts.sumOf { (it.day - mx) * (it.day - mx) }
        if (den == 0.0) return null
        val slope = pts.sumOf { (it.day - mx) * (it.kg - my) } / den
        return slope * 7 / my * 100
    }

    /** Kcal step from a 2-week trend: +150/+200 if too slow, −150/−200 if too fast, 0 if on target. */
    fun adjustmentFor(ratePct: Double): Int = when {
        ratePct < 0.0 -> 200
        ratePct < TARGET_LOW -> 150
        ratePct > TARGET_HIGH + 0.25 -> -200
        ratePct > TARGET_HIGH -> -150
        else -> 0
    }

    /** Run at most once every [REVIEW_DAYS]; returns updated state (only NutritionState changes). */
    fun review(s: AppState, today: Long): AppState {
        val n = s.nutrition
        if (n.lastReviewDay != null && today - n.lastReviewDay < REVIEW_DAYS) return s
        val rate = weeklyRatePct(s.bodyWeights, today) ?: return s
        val step = adjustmentFor(rate)
        val base = if ((s.profile?.age ?: 30) < 18) 300 else BASE_SURPLUS
        val newAdj = (clampSurplus(base, n.kcalAdjust + step) - base)
        val delta = newAdj - n.kcalAdjust
        val r = "%.2f".format(java.util.Locale.US, rate)
        val reason = when {
            step > 0 && delta == 0 -> Txt("الزيادة بطيئة ($r٪/أسبوع) لكن الفائض وصل الحد الأعلى الآمن. راجع الالتزام والنوم.", "Gain is slow ($r%/wk) but the surplus is at its safe maximum. Check adherence and sleep.")
            step < 0 && delta == 0 -> Txt("الزيادة سريعة ($r٪/أسبوع) والفائض في حده الأدنى فوق الصيانة.", "Gain is fast ($r%/wk) and the surplus is already at its minimum above maintenance.")
            delta > 0 -> Txt("زيادة الوزن أبطأ من الهدف ($r٪/أسبوع مقابل 0.25–0.5٪). رفع السعرات +$delta.", "Gain slower than target ($r%/wk vs 0.25–0.5%). Calories +$delta.")
            delta < 0 -> Txt("زيادة الوزن أسرع من الهدف ($r٪/أسبوع) — غالبًا دهون. خفض السعرات ${delta}.", "Gain faster than target ($r%/wk) — likely fat. Calories $delta.")
            else -> Txt("الوتيرة ضمن الهدف ($r٪/أسبوع). لا تغيير.", "Rate on target ($r%/wk). No change.")
        }
        return s.copy(nutrition = n.copy(kcalAdjust = newAdj, lastReviewDay = today,
            adjustments = (n.adjustments + KcalAdjustment(today, delta, rate, reason)).takeLast(26)))
    }

    // ---------------- meal timing ----------------
    enum class Kind(val ar: String, val en: String, val share: Double) {
        BREAKFAST("فطور", "Breakfast", 1.0), MEAL("وجبة رئيسية", "Main meal", 1.25), SNACK("وجبة خفيفة", "Snack", 0.6),
        PRE("وجبة ما قبل التمرين", "Pre-workout meal", 1.0), PRE_SNACK("سناك خفيف قبل التمرين", "Light pre-workout snack", 0.4),
        ELIXIR("الترياق — وجبة التعافي", "Elixir — recovery meal", 1.2), LAST("وجبة المساء", "Evening meal", 0.75)
    }
    data class Order(val minute: Int, val kind: Kind, val kcal: Int, val protein: Int, val carbs: Int, val hint: Txt)

    private val hints = mapOf(
        Kind.BREAKFAST to Txt("بيض + خبز + حليب أو رايب + تمر", "Eggs + bread + milk/rayeb + dates"),
        Kind.MEAL to Txt("دجاج/تن/لحم + كسكسي أو أرز أو مقرونة + خضر + زيت زيتون", "Chicken/tuna/meat + couscous/rice/pasta + veg + olive oil"),
        Kind.SNACK to Txt("ياغرت + تمر أو موز + حفنة لوز", "Yogurt + dates or banana + almonds"),
        Kind.PRE to Txt("كربوهيدرات + بروتين معتدل، دهون قليلة (أرز/مقرونة + دجاج)", "Carbs + moderate protein, low fat (rice/pasta + chicken)"),
        Kind.PRE_SNACK to Txt("خفيف وسهل الهضم: موز أو 3 تمرات + ياغرت", "Light & easy: banana or 3 dates + yogurt"),
        Kind.ELIXIR to Txt("بروتين + كربوهيدرات خلال 1–2 ساعة: دجاج/تن + أرز أو خبز + حليب", "Protein + carbs within 1–2 h: chicken/tuna + rice or bread + milk"),
        Kind.LAST to Txt("بروتين بطيء: رايب/حليب + بيض أو لبلابي خفيف", "Slow protein: rayeb/milk + eggs or light lablabi")
    )

    /**
     * Daily orders. wake/train/bed in minutes from midnight. Rules: pre-meal 2–3 h before (else a light snack 45–60 min before),
     * Elixir 1–2 h after, nothing heavy in the 90 min before training, protein spread over 4–5 meals, gaps ≤ ~4 h.
     */
    fun schedule(t: Targets, wake: Int, bed: Int, trainStart: Int?, sessionMin: Int, trainedEndMinute: Int? = null): List<Order> {
        val slots = mutableListOf<Pair<Int, Kind>>()
        val first = wake + 30
        val last = (bed - 75).coerceAtLeast(first + 360)
        slots += first to Kind.BREAKFAST
        if (trainStart != null) {
            val end = trainedEndMinute ?: (trainStart + sessionMin)
            if (trainStart - 150 >= first + 150) slots += (trainStart - 150) to Kind.PRE
            else if (trainStart - 50 > first + 60) slots += (trainStart - 50) to Kind.PRE_SNACK
            else if (trainStart - first < 150) { slots.clear(); slots += maxOf(wake + 15, trainStart - 60) to Kind.PRE_SNACK }  // early session: light snack, real breakfast = Elixir
            val elixir = end + 45
            if (elixir < last - 60) { slots += elixir to Kind.ELIXIR; slots += last to Kind.LAST } else slots += elixir.coerceAtMost(bed - 30) to Kind.ELIXIR
        } else slots += last to Kind.LAST
        val noHeavy = trainStart?.let { (it - 90) until (trainedEndMinute ?: (it + sessionMin)) }
        var guard = 0
        while (slots.size < 5 && guard++ < 6) {
            val sorted = slots.sortedBy { it.first }
            val gap = sorted.zipWithNext().maxByOrNull { it.second.first - it.first.first } ?: break
            val len = gap.second.first - gap.first.first
            if (len < 230) break
            var m = (gap.first.first + gap.second.first) / 2
            if (noHeavy != null && m in noHeavy) m = noHeavy.first - 15
            if (m - gap.first.first < 120 || gap.second.first - m < 60) break
            slots += m to (if (len >= 330) Kind.MEAL else Kind.SNACK)
        }
        val sorted = slots.sortedBy { it.first }.map { (m, k) -> if (noHeavy != null && m in noHeavy && k == Kind.MEAL) m to Kind.SNACK else m to k }
        val sum = sorted.sumOf { it.second.share }
        // protein spread evenly (Elixir slightly more), kcal/carbs by share
        val pw = sorted.map { if (it.second == Kind.ELIXIR) 1.3 else if (it.second == Kind.PRE_SNACK) 0.5 else 1.0 }
        val pSum = pw.sum()
        return sorted.mapIndexed { i, (m, k) ->
            Order(m, k, (t.kcal * k.share / sum).roundToInt(), (t.protein * pw[i] / pSum).roundToInt(), (t.carbs * k.share / sum).roundToInt(), hints.getValue(k))
        }
    }

    fun ordersFor(s: AppState, day: Long, nowMs: Long, dayStartMs: Long): List<Order> {
        val t = targetsFor(s, day) ?: return emptyList()
        val p = s.profile ?: return emptyList()
        val wake = s.settings.morningHour * 60 + s.settings.morningMinute
        val bed = (wake + 24 * 60 - (p.sleepHours * 60).roundToInt()).coerceIn(wake + 12 * 60, wake + 20 * 60)
        val train = if (isTrainingDay(s, day)) s.nutrition.trainHour * 60 + s.nutrition.trainMinute else null
        val done = s.sessions.lastOrNull { it.day == day && it.finishedMs != null }?.finishedMs?.let { ((it - dayStartMs) / 60000).toInt() }
        val start = if (done != null && train == null) done - p.minutesPerSession else train
        return schedule(t, wake, bed, start, p.minutesPerSession, done)
    }

    /** Elixir recovery window after a completed session today: (startMs, endMs) = finish+0 .. finish+2h. */
    fun elixirWindow(s: AppState, day: Long): Pair<Long, Long>? =
        s.sessions.lastOrNull { it.day == day && it.finishedMs != null }?.finishedMs?.let { it to it + 2 * 3600_000L }

    // ---------------- tracking ----------------
    data class DayTotals(val kcal: Int, val protein: Double, val carbs: Double, val fat: Double, val meals: Int)
    fun totals(s: AppState, day: Long): DayTotals {
        val m = s.nutrition.meals.filter { it.day == day }
        return DayTotals(m.sumOf { it.kcal }, m.sumOf { it.protein }, m.sumOf { it.carbs }, m.sumOf { it.fat }, m.size)
    }

    /** Compliance 0–100: calories 50 (reaching target; overshoot >15 % loses a little), protein 35, meal count 15. Never negative. */
    fun compliance(tot: DayTotals, t: Targets, plannedMeals: Int): Int {
        val k = tot.kcal.toDouble() / t.kcal
        val kScore = if (k <= 1.0) k else (1.0 - ((k - 1.15).coerceAtLeast(0.0)) * 2).coerceAtLeast(0.6)
        val p = (tot.protein / t.protein).coerceAtMost(1.0)
        val m = (tot.meals.toDouble() / plannedMeals.coerceAtLeast(1)).coerceAtMost(1.0)
        return ((50 * kScore.coerceIn(0.0, 1.0) + 35 * p + 15 * m)).roundToInt().coerceIn(0, 100)
    }

    fun dayMet(tot: DayTotals, t: Targets) = tot.kcal >= t.kcal * 0.9 && tot.protein >= t.protein * 0.9

    /** Consecutive days meeting ≥90 % of kcal and protein, ending today (if met) or yesterday. */
    fun streak(s: AppState, today: Long): Int {
        val t = targetsFor(s, today) ?: return 0
        var d = if (dayMet(totals(s, today), t)) today else today - 1
        var n = 0
        while (n < 400 && dayMet(totals(s, d), targetsFor(s, d) ?: t)) { n++; d-- }
        return n
    }

    fun addMeal(s: AppState, m: MealLog): AppState {
        if (m.kcal !in 0..5000 || m.protein !in 0.0..400.0) return s
        val n = s.nutrition.copy(meals = (s.nutrition.meals + m).filter { it.day > m.day - 120 })
        val st = s.copy(nutrition = n)
        val best = maxOf(n.bestStreak, streak(st, m.day))
        return st.copy(nutrition = n.copy(bestStreak = best))
    }
    /** v1.2.2: log a database food by household portion × quantity — kcal/protein/carbs/fat computed, nothing typed. */
    fun logFood(s: AppState, item: FoodDb.Item, portion: FoodDb.Portion, qty: Double, day: Long, ms: Long, id: String = "m$ms"): AppState {
        val c = FoodDb.calc(item, portion, qty)
        return addMeal(s, MealLog(id, day, ms, item.name, c.kcal, c.protein, c.carbs, c.fat, item.id, qty, portion.name, c.grams))
    }

    /** Most recent distinct database meals (food + portion + qty), newest first. */
    fun recents(s: AppState, limit: Int = 8): List<MealLog> =
        s.nutrition.meals.filter { FoodDb.get(it.foodId) != null }.sortedByDescending { it.ms }
            .distinctBy { Triple(it.foodId, it.portion, it.qty) }.take(limit)

    fun mealsOn(s: AppState, day: Long) = s.nutrition.meals.filter { it.day == day }.sortedBy { it.ms }

    /** Re-log meals (e.g. yesterday's) on [day] with fresh ids; values copied exactly (works for legacy and manual meals too). */
    fun repeatMeals(s: AppState, meals: List<MealLog>, day: Long, now: Long): AppState =
        meals.foldIndexed(s) { i, st, m -> addMeal(st, m.copy(id = "m${now}_$i", day = day, ms = now + i)) }

    fun toggleFavorite(s: AppState, foodId: String): AppState {
        val f = s.nutrition.favorites
        return s.copy(nutrition = s.nutrition.copy(favorites = if (foodId in f) f - foodId else (listOf(foodId) + f).take(30)))
    }

    fun removeMeal(s: AppState, id: String) = s.copy(nutrition = s.nutrition.copy(meals = s.nutrition.meals.filterNot { it.id == id }))
    fun addWater(s: AppState, day: Long, ml: Int) = s.copy(nutrition = s.nutrition.copy(waterMl = (s.nutrition.waterMl + (day to ((s.nutrition.waterMl[day] ?: 0) + ml).coerceIn(0, 8000))).filterKeys { it > day - 60 }))

    fun daysSinceWeighIn(s: AppState, today: Long): Long? = s.bodyWeights.maxByOrNull { it.day }?.let { today - it.day }

    fun fmtTime(min: Int): String { val m = ((min % 1440) + 1440) % 1440; return "%02d:%02d".format(m / 60, m % 60) }
}
