package com.ayman.solosystem.domain.skills

import com.ayman.solosystem.domain.game.Metric
import com.ayman.solosystem.domain.game.Metric.*
import com.ayman.solosystem.domain.model.QuestCategory
import com.ayman.solosystem.domain.model.Rank
import com.ayman.solosystem.domain.model.Txt

enum class Branch(val ar: String, val en: String) {
    STRENGTH("القوة", "Strength"), AGILITY("الرشاقة", "Agility"), ENDURANCE("التحمل", "Endurance"),
    VITALITY("الحيوية والتعافي", "Vitality & recovery"), DISCIPLINE("الانضباط", "Discipline"), MIND("العقل والتعلم", "Mind & learning")
}

/** Game-only effects. None of them claim or change anything physical. */
enum class Effect { XP_CATEGORY, GOLD_PCT, STREAK_SHIELD, REROLL, EMERGENCY_CHANCE, LOOT_LUCK, BONUS_POOL }

data class SkillDef(
    val id: String, val branch: Branch, val name: Txt, val rank: Rank, val active: Boolean,
    val metric: Metric, val thresholds: List<Int>, val effect: Effect, val category: QuestCategory? = null, val pctPerLevel: Int = 1,
    val flavour: Txt
) {
    private fun iso(t: String) = "\u2066$t\u2069"
    fun level(value: Int) = thresholds.count { value >= it }
    fun effectText(level: Int, arabic: Boolean): String {
        val l = level.coerceAtLeast(1)
        return when (effect) {
            Effect.XP_CATEGORY -> if (arabic) "خبرة مهام ${category?.ar} ${iso("+${pctPerLevel * l}%")}" else "${category?.en} quest XP +${pctPerLevel * l}%"
            Effect.GOLD_PCT -> if (arabic) "الذهب المكتسب ${iso("+${pctPerLevel * l}%")}" else "Gold earned +${pctPerLevel * l}%"
            Effect.STREAK_SHIELD -> if (l >= 3) (if (arabic) "درع السلسلة: ${iso("+1")} يوم سماح أسبوعيًا" else "Streak shield: +1 grace day / week") else (if (arabic) "درع السلسلة يتفعل عند المستوى 3" else "Streak shield activates at Lv.3")
            Effect.REROLL -> if (arabic) "إعادة سحب مهمة يومية: ${if (l >= 4) 2 else 1} يوميًا" else "Daily quest reroll: ${if (l >= 4) 2 else 1}/day"
            Effect.EMERGENCY_CHANCE -> if (arabic) "فرصة المهام الطارئة ${iso("+${l * 3}%")}" else "Emergency quest chance +${l * 3}%"
            Effect.LOOT_LUCK -> if (arabic) "حظ الغنائم ${iso("+$l")}" else "Loot luck +$l"
            Effect.BONUS_POOL -> if (arabic) "مكافأة اليوم الكامل ${iso("+${l * 2}%")}" else "Full-day bonus +${l * 2}%"
        }
    }
}

object SkillCatalog {
    private fun s(id: String, b: Branch, ar: String, en: String, r: Rank, m: Metric, t: List<Int>, e: Effect, cat: QuestCategory? = null, pct: Int = 1,
                  fAr: String, fEn: String, active: Boolean = false) = SkillDef(id, b, Txt(ar, en), r, active, m, t, e, cat, pct, Txt(fAr, fEn))
    private val T = QuestCategory.TRAINING; private val MV = QuestCategory.MOVEMENT; private val RC = QuestCategory.RECOVERY
    private val NU = QuestCategory.NUTRITION; private val DI = QuestCategory.DISCIPLINE; private val LE = QuestCategory.LEARNING
    private val S = Branch.STRENGTH; private val A = Branch.AGILITY; private val EN = Branch.ENDURANCE; private val V = Branch.VITALITY; private val D = Branch.DISCIPLINE; private val M = Branch.MIND

    val all: List<SkillDef> = listOf(
        s("iron_fist", S, "قبضة الحديد", "Iron Fist", Rank.E, PUSH_REPS, listOf(100, 500, 1500, 4000, 10000), Effect.XP_CATEGORY, T, 1, "كل تكرار ضغط يصقل القبضة.", "Every push rep tempers the fist."),
        s("titan_legs", S, "أرجل العملاق", "Titan Legs", Rank.E, SQUAT_REPS, listOf(150, 700, 2000, 5000, 12000), Effect.XP_CATEGORY, T, 1, "الأرض ترتجف تحت خطواتك.", "The ground trembles beneath your steps."),
        s("grappler", S, "قبضة المصارع", "Grappler's Grip", Rank.D, PULL_REPS, listOf(100, 400, 1200, 3000, 8000), Effect.GOLD_PCT, null, 1, "ما تمسكه لا يفلت.", "What you grip does not escape."),
        s("record_breaker", S, "محطم الأرقام", "Record Breaker", Rank.D, PRS, listOf(1, 5, 15, 30, 60), Effect.LOOT_LUCK, null, 1, "الأمس هو خصمك الوحيد.", "Yesterday is your only rival."),
        s("heavy_hand", S, "اليد الثقيلة", "Heavy Hand", Rank.C, VOLUME_TONNES, listOf(1, 5, 20, 60, 150), Effect.XP_CATEGORY, T, 1, "أطنان من الحديد تحكي قصتك.", "Tonnes of iron tell your story."),
        s("push_master", S, "سيد الضغط", "Push Master", Rank.C, BEST_PUSH_SET, listOf(10, 20, 30, 40, 50), Effect.GOLD_PCT, null, 1, "مجموعة واحدة، بلا تنازل عن التقنية.", "One set, no compromise on form."),
        s("berserker", S, "الهائج", "Berserker", Rank.B, WORKOUTS, listOf(20, 50, 100, 200, 400), Effect.REROLL, null, 1, "مهارة نشطة: أعد سحب مهمة لا تناسب يومك.", "Active: reroll a quest that doesn't fit your day.", true),
        s("steel_core", S, "جذع فولاذي", "Steel Core", Rank.D, CORE_SECONDS, listOf(300, 1500, 4000, 10000, 25000), Effect.XP_CATEGORY, T, 1, "المركز الثابت يحمل كل شيء.", "A stable center carries everything."),
        s("immovable", S, "الثابت", "Immovable", Rank.C, BEST_PLANK, listOf(45, 60, 90, 120, 180), Effect.STREAK_SHIELD, null, 1, "لا تهزك الرياح.", "The wind cannot move you."),
        s("rank_climber", S, "متسلق الرتب", "Rank Climber", Rank.A, ACTIVE_TIER, listOf(1, 2, 3, 4, 5), Effect.XP_CATEGORY, T, 2, "كل رتبة مُفعّلة بأداء حقيقي.", "Every tier earned through real performance."),
        s("proven", S, "المُختبَر", "Proven", Rank.B, ASSESSMENTS, listOf(1, 3, 6, 10, 15), Effect.GOLD_PCT, null, 2, "الأرقام لا تكذب.", "Numbers don't lie."),

        s("shadow_step", A, "خطوة الظل", "Shadow Step", Rank.D, MOBILITY_DONE, listOf(10, 25, 50, 100, 200), Effect.XP_CATEGORY, RC, 1, "تتحرك كالظل، بلا صوت وبلا ألم.", "Move like a shadow — silent, pain-free."),
        s("swift_feet", A, "أقدام خاطفة", "Swift Feet", Rank.E, WALKS_DONE, listOf(5, 20, 50, 120, 300), Effect.XP_CATEGORY, MV, 2, "الطريق يقصر لمن يمشيه كل يوم.", "The road shortens for those who walk it daily."),
        s("flow_state", A, "حالة التدفق", "Flow State", Rank.C, MOBILITY_DONE, listOf(20, 40, 80, 160, 300), Effect.EMERGENCY_CHANCE, null, 1, "الجسد والعقل في إيقاع واحد.", "Body and mind in one rhythm."),
        s("wind_runner", A, "عدّاء الريح", "Wind Runner", Rank.C, CARDIO_SESSIONS, listOf(3, 10, 25, 50, 100), Effect.XP_CATEGORY, MV, 1, "تسبق أنفاسك خطواتك.", "Your breath outpaces your steps."),
        s("evasion", A, "المراوغة", "Evasion", Rank.B, SAFE_STOPS, listOf(1, 2, 4, 6, 10), Effect.STREAK_SHIELD, null, 1, "الصياد الذكي يتجنب الإصابة.", "A smart hunter avoids injury."),
        s("nimble", A, "الرشيق", "Nimble", Rank.D, WALKS_DONE, listOf(10, 30, 70, 150, 350), Effect.GOLD_PCT, null, 1, "خفة في كل خطوة.", "Lightness in every step."),
        s("phantom_pace", A, "إيقاع الشبح", "Phantom Pace", Rank.A, CARDIO_SESSIONS, listOf(10, 30, 60, 120, 250), Effect.LOOT_LUCK, null, 1, "لا أحد يسمعك قادمًا.", "No one hears you coming."),
        s("quick_reflex", A, "رد فعل سريع", "Quick Reflex", Rank.E, EMERGENCIES_DONE, listOf(1, 5, 12, 25, 50), Effect.EMERGENCY_CHANCE, null, 1, "مهارة نشطة: المهام الطارئة تجدك أكثر.", "Active: emergency quests find you more often.", true),
        s("sprint_spirit", A, "روح الانطلاق", "Sprint Spirit", Rank.C, PERFECT_DAYS, listOf(3, 10, 25, 50, 100), Effect.XP_CATEGORY, MV, 1, "يوم كامل، انطلاقة كاملة.", "A full day, a full start."),
        s("acrobat", A, "البهلوان", "Acrobat", Rank.S, MOBILITY_DONE, listOf(50, 100, 200, 350, 500), Effect.XP_CATEGORY, RC, 2, "المرونة قوة لا تُرى.", "Flexibility is unseen strength."),

        s("second_wind", EN, "النفس الثاني", "Second Wind", Rank.D, CARDIO_SESSIONS, listOf(5, 15, 30, 60, 120), Effect.STREAK_SHIELD, null, 1, "حين يتوقف الآخرون، تبدأ أنت.", "When others stop, you begin."),
        s("marathon_soul", EN, "روح الماراثون", "Marathon Soul", Rank.B, WALKS_DONE, listOf(30, 80, 160, 300, 500), Effect.XP_CATEGORY, MV, 2, "المسافة الطويلة تُقطع خطوة بخطوة.", "Long distance is crossed step by step."),
        s("tireless", EN, "لا يكلّ", "Tireless", Rank.C, WORKOUTS, listOf(10, 30, 60, 120, 250), Effect.XP_CATEGORY, T, 1, "الحضور هو نصف المعركة.", "Showing up is half the battle."),
        s("iron_lungs", EN, "رئتا الحديد", "Iron Lungs", Rank.C, CARDIO_SESSIONS, listOf(8, 20, 40, 80, 160), Effect.GOLD_PCT, null, 1, "كل نفس وقود.", "Every breath is fuel."),
        s("enduring_flame", EN, "اللهب الدائم", "Enduring Flame", Rank.A, STREAK, listOf(7, 14, 30, 60, 120), Effect.XP_CATEGORY, DI, 2, "شعلة لا تنطفئ.", "A flame that never dies."),
        s("survivor", EN, "الناجي", "Survivor", Rank.B, PENALTIES_DONE, listOf(1, 3, 6, 10, 20), Effect.STREAK_SHIELD, null, 1, "نجوت من مهمة العقاب.", "You survived the Penalty Quest."),
        s("long_road", EN, "الطريق الطويل", "The Long Road", Rank.S, WORKOUTS, listOf(50, 100, 200, 365, 600), Effect.LOOT_LUCK, null, 1, "التحول رحلة سنوات.", "Transformation is a journey of years."),
        s("stamina_well", EN, "بئر الطاقة", "Stamina Well", Rank.D, CORE_SECONDS, listOf(200, 800, 2000, 5000, 12000), Effect.GOLD_PCT, null, 1, "عمق لا ينضب.", "A depth that never runs dry."),
        s("relentless", EN, "العنيد", "Relentless", Rank.B, PENALTIES_DONE, listOf(2, 5, 10, 15, 25), Effect.REROLL, null, 1, "مهارة نشطة: إعادة سحب إضافية.", "Active: extra quest reroll.", true),

        s("deep_sleep", V, "النوم العميق", "Deep Sleep", Rank.E, SLEEP_DONE, listOf(5, 20, 50, 120, 300), Effect.XP_CATEGORY, RC, 2, "القوة تُبنى أثناء النوم.", "Strength is built while you sleep."),
        s("regeneration", V, "التجدد", "Regeneration", Rank.D, RECOVERY_RESPECTED, listOf(1, 3, 6, 12, 24), Effect.XP_CATEGORY, RC, 1, "التخفيف جزء من الخطة.", "Deloading is part of the plan."),
        s("healer", V, "المعالج", "Healer", Rank.C, EARLY_CHECKINS, listOf(7, 20, 50, 100, 200), Effect.STREAK_SHIELD, null, 1, "ليالٍ كاملة، صباحات أقوى.", "Full nights, stronger mornings."),
        s("iron_body", V, "جسد الحديد", "Iron Body", Rank.B, CHECKINS, listOf(10, 30, 60, 120, 250), Effect.GOLD_PCT, null, 1, "تعرف جسدك كما تعرف سلاحك.", "You know your body like your blade."),
        s("night_guard", V, "حارس الليل", "Night Guard", Rank.C, SLEEP_DONE, listOf(15, 40, 90, 180, 365), Effect.XP_CATEGORY, RC, 1, "الشاشات تنطفئ، والجسد يتعافى.", "Screens off, the body recovers."),
        s("nourished", V, "المغذّى", "Nourished", Rank.E, NUTRITION_DONE, listOf(5, 20, 50, 120, 300), Effect.XP_CATEGORY, NU, 2, "الوقود الجيد لمحرك قوي.", "Good fuel for a strong engine."),
        s("alchemist", V, "الخيميائي", "Alchemist", Rank.C, NUTRITION_DONE, listOf(15, 40, 90, 180, 365), Effect.GOLD_PCT, null, 2, "تحوّل الطعام إلى طاقة.", "You turn food into power."),
        s("wise_rest", V, "الراحة الحكيمة", "Wise Rest", Rank.B, SAFE_STOPS, listOf(1, 3, 5, 8, 12), Effect.XP_CATEGORY, RC, 1, "التوقف عند الألم حكمة لا ضعف.", "Stopping at pain is wisdom, not weakness."),
        s("self_aware", V, "الوعي الذاتي", "Self-Awareness", Rank.D, CHECKINS, listOf(5, 15, 40, 90, 180), Effect.REROLL, null, 1, "مهارة نشطة: بدّل مهمة حسب حالتك.", "Active: swap a quest to suit your state.", true),
        s("vital_aura", V, "هالة الحيوية", "Vital Aura", Rank.A, EARLY_CHECKINS, listOf(30, 60, 120, 240, 365), Effect.LOOT_LUCK, null, 1, "طاقة تشع من حولك.", "Energy radiates around you."),
        s("phoenix", V, "العنقاء", "Phoenix", Rank.S, RECOVERY_RESPECTED, listOf(5, 10, 20, 35, 50), Effect.STREAK_SHIELD, null, 1, "تنهض أقوى بعد كل راحة.", "You rise stronger after every rest."),

        s("iron_will", D, "الإرادة الحديدية", "Iron Will", Rank.C, BEST_STREAK, listOf(14, 30, 60, 120, 240), Effect.STREAK_SHIELD, null, 1, "14 يومًا دون انكسار.", "Fourteen days unbroken."),
        s("first_light", D, "الضوء الأول", "First Light", Rank.E, STREAK, listOf(3, 7, 14, 21, 30), Effect.XP_CATEGORY, DI, 1, "البداية أصعب خطوة.", "The beginning is the hardest step."),
        s("record_keeper", D, "حافظ السجل", "Record Keeper", Rank.D, CHECKINS, listOf(7, 21, 45, 90, 180), Effect.XP_CATEGORY, DI, 1, "ما يُقاس يتحسن.", "What gets measured improves."),
        s("commander", D, "القائد", "Commander", Rank.B, DISCIPLINE_DONE, listOf(20, 60, 150, 300, 600), Effect.GOLD_PCT, null, 2, "تقود يومك ولا يقودك.", "You lead your day; it doesn't lead you."),
        s("perfectionist", D, "الكمالي", "Perfectionist", Rank.A, PERFECT_DAYS, listOf(7, 20, 50, 100, 200), Effect.BONUS_POOL, null, 1, "كل المهام، كل يوم.", "Every quest, every day."),
        s("strategist", D, "الاستراتيجي", "Strategist", Rank.C, WEEKLY_REVIEWS, listOf(1, 4, 12, 26, 52), Effect.REROLL, null, 1, "خطة أسبوعية، نصر أسبوعي.", "Weekly plan, weekly victory."),
        s("unbroken", D, "غير المنكسر", "Unbroken", Rank.S, BEST_STREAK, listOf(30, 60, 100, 200, 365), Effect.XP_CATEGORY, DI, 2, "السلسلة أصبحت هويتك.", "The streak became who you are."),
        s("oath_keeper", D, "حافظ العهد", "Oath Keeper", Rank.B, MAIN_DONE, listOf(1, 3, 5, 7, 9), Effect.LOOT_LUCK, null, 1, "وعدت ووفيت.", "You promised and delivered."),
        s("quest_hunter", D, "صياد المهام", "Quest Hunter", Rank.D, QUESTS_DONE, listOf(25, 100, 300, 800, 2000), Effect.GOLD_PCT, null, 1, "لا مهمة تفلت منك.", "No quest escapes you."),
        s("monarch_resolve", D, "عزيمة الملك", "Monarch's Resolve", Rank.S, LEVEL, listOf(50, 100, 150, 200, 300), Effect.BONUS_POOL, null, 1, "عزيمة تليق بملك.", "A resolve fit for a monarch."),
        s("challenger", D, "المتحدي", "Challenger", Rank.C, CHALLENGES_DONE, listOf(1, 4, 10, 20, 40), Effect.EMERGENCY_CHANCE, null, 1, "مهارة نشطة: تحديات أكثر تقترب.", "Active: more challenges approach.", true),

        s("scholar", M, "الباحث", "Scholar", Rank.E, LEARNING_DONE, listOf(5, 20, 50, 120, 300), Effect.XP_CATEGORY, LE, 2, "المعرفة سلاح خفي.", "Knowledge is a hidden weapon."),
        s("sage", M, "الحكيم", "Sage", Rank.B, LEARNING_DONE, listOf(30, 80, 160, 300, 500), Effect.XP_CATEGORY, LE, 1, "عقل مدرّب كجسد مدرّب.", "A trained mind like a trained body."),
        s("tactician", M, "المخطِّط", "Tactician", Rank.D, WEEKLY_REVIEWS, listOf(2, 6, 15, 30, 52), Effect.XP_CATEGORY, DI, 1, "ترى المعركة قبل أن تبدأ.", "You see the battle before it begins."),
        s("insight", M, "البصيرة", "Insight", Rank.C, ASSESSMENTS, listOf(1, 2, 4, 7, 10), Effect.XP_CATEGORY, LE, 1, "تعرف أين تقف بدقة.", "You know exactly where you stand."),
        s("calm_mind", M, "العقل الهادئ", "Calm Mind", Rank.D, EMERGENCIES_DONE, listOf(2, 6, 12, 25, 50), Effect.BONUS_POOL, null, 1, "الطوارئ لا تربكك.", "Emergencies don't shake you."),
        s("treasure_sense", M, "حاسة الكنز", "Treasure Sense", Rank.C, LOOT_OPENED, listOf(3, 10, 25, 50, 100), Effect.LOOT_LUCK, null, 1, "تشم رائحة الغنائم.", "You can smell the loot."),
        s("merchant", M, "التاجر", "Merchant", Rank.D, QUESTS_DONE, listOf(50, 200, 500, 1200, 3000), Effect.GOLD_PCT, null, 2, "كل جهد له ثمن.", "Every effort has its price."),
        s("focus", M, "التركيز", "Focus", Rank.E, LEARNING_DONE, listOf(3, 10, 30, 70, 150), Effect.REROLL, null, 1, "مهارة نشطة: اختر ما يستحق انتباهك.", "Active: choose what deserves your focus.", true),
        // exclusive: grows ONLY from converted SECRET QUEST days
        s("hidden_path", M, "الطريق الخفي", "Hidden Path", Rank.S, SECRET_QUESTS, listOf(1, 3, 7, 15, 30), Effect.LOOT_LUCK, null, 1, "شرط لا يُكتب في أي مهمة.", "A condition written in no quest."),
        s("analyst", M, "المحلل", "Analyst", Rank.B, PRS, listOf(3, 10, 25, 50, 100), Effect.XP_CATEGORY, LE, 1, "البيانات ترسم الطريق.", "Data draws the path."),
        s("awakened_mind", M, "العقل المستيقظ", "Awakened Mind", Rank.A, LEVEL, listOf(10, 25, 50, 100, 200), Effect.XP_CATEGORY, LE, 2, "الاستيقاظ الحقيقي داخلي.", "True awakening is within."),
        s("shadow_archive", M, "أرشيف الظلال", "Shadow Archive", Rank.S, QUESTS_DONE, listOf(500, 1500, 3000, 6000, 10000), Effect.LOOT_LUCK, null, 1, "كل مهمة محفوظة في الظلال.", "Every quest archived in shadow.")
    )
    fun get(id: String) = all.firstOrNull { it.id == id }
}
