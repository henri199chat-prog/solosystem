package com.ayman.solosystem.domain.rewards

import com.ayman.solosystem.domain.game.Metric
import com.ayman.solosystem.domain.game.Metric.*
import com.ayman.solosystem.domain.model.QuestCategory
import com.ayman.solosystem.domain.model.Txt
import com.ayman.solosystem.domain.skills.Effect

enum class Rarity(val ar: String, val en: String, val color: Long, val dupGold: Int) {
    COMMON("عادي", "Common", 0xFFB8C7D9, 40), RARE("نادر", "Rare", 0xFF3FA9FF, 120), EPIC("ملحمي", "Epic", 0xFFB46CFF, 350), LEGENDARY("أسطوري", "Legendary", 0xFFFFC447, 900)
}
enum class RewardKind(val ar: String, val en: String) {
    TITLE("لقب", "Title"), BADGE("شارة", "Badge"), THEME("سمة اللوحات", "Panel theme"), FRAME("إطار", "Frame"), EMBLEM("شعار", "Emblem"),
    BOX("صندوق غنائم", "Loot box"), GOLD("ذهب", "Gold"), TOKEN("رمز", "Token")
}
enum class EmblemShape { BANG, SWORD, SHIELD, CROWN, EYE, FLAME, BOLT, STAR, MOON, DIAMOND, HEX, WING, FANG, SUN, KEY, GATE, SKULL, LOTUS, TRIDENT, ORB }
enum class TokenType { REROLL, SHIELD, EMERGENCY, FULL_REST }

data class RewardDef(
    val id: String, val kind: RewardKind, val name: Txt, val rarity: Rarity, val flavour: Txt,
    /** Game-only effect applied while equipped (titles) */
    val effect: Effect? = null, val category: QuestCategory? = null, val pct: Int = 0,
    val primary: Long = 0, val secondary: Long = 0, val notches: Int = 3, val doubleLine: Boolean = false,
    val shape: EmblemShape = EmblemShape.BANG, val gold: Int = 0, val token: TokenType? = null,
    val hidden: Boolean = false, val metric: Metric? = null, val threshold: Int = 0, val lootable: Boolean = true
) {
    val cosmetic get() = kind == RewardKind.TITLE || kind == RewardKind.THEME || kind == RewardKind.FRAME || kind == RewardKind.EMBLEM
    fun bonusText(arabic: Boolean): String? = when (effect) {
        Effect.XP_CATEGORY -> if (arabic) "خبرة مهام ${category?.ar} \u2066+$pct%\u2069" else "+$pct% ${category?.en} quest XP"
        Effect.GOLD_PCT -> if (arabic) "الذهب \u2066+$pct%\u2069" else "+$pct% gold"
        Effect.LOOT_LUCK -> if (arabic) "حظ الغنائم \u2066+$pct\u2069" else "Loot luck +$pct"
        Effect.BONUS_POOL -> if (arabic) "مكافأة اليوم الكامل \u2066+$pct%\u2069" else "Full-day bonus +$pct%"
        else -> null
    }
}

/** Data-driven reward content. Add rows here to extend the game; ids must stay stable (they are saved). */
object RewardCatalog {
    private val C = Rarity.COMMON; private val R = Rarity.RARE; private val E = Rarity.EPIC; private val L = Rarity.LEGENDARY
    private fun title(id: String, ar: String, en: String, r: Rarity, eff: Effect, cat: QuestCategory?, pct: Int, fAr: String, fEn: String, lootable: Boolean = true) =
        RewardDef("title_$id", RewardKind.TITLE, Txt(ar, en), r, Txt(fAr, fEn), eff, cat, pct, lootable = lootable)
    private fun theme(id: String, ar: String, en: String, r: Rarity, p: Long, s: Long) = RewardDef("theme_$id", RewardKind.THEME, Txt(ar, en), r, Txt("سمة ألوان للوحات النظام.", "A color theme for system panels."), primary = p, secondary = s)
    private fun frame(id: String, ar: String, en: String, r: Rarity, p: Long, n: Int, dbl: Boolean) = RewardDef("frame_$id", RewardKind.FRAME, Txt(ar, en), r, Txt("نمط إطار للوحات.", "A panel frame style."), primary = p, notches = n, doubleLine = dbl)
    private fun emblem(id: String, ar: String, en: String, r: Rarity, sh: EmblemShape, p: Long) = RewardDef("emblem_$id", RewardKind.EMBLEM, Txt(ar, en), r, Txt("شعار يظهر في ملفك.", "An emblem shown on your profile."), primary = p, shape = sh)
    private fun badge(id: String, ar: String, en: String, r: Rarity, m: Metric, t: Int, fAr: String, fEn: String, hidden: Boolean = false) =
        RewardDef("badge_$id", RewardKind.BADGE, Txt(ar, en), r, Txt(fAr, fEn), metric = m, threshold = t, hidden = hidden, lootable = false)

    val titles = listOf(
        title("wolf_slayer", "مغتال الذئاب", "Wolf Slayer", R, Effect.XP_CATEGORY, QuestCategory.TRAINING, 3, "لقب من يصطاد في الظلام دون خوف.", "Title of one who hunts in the dark without fear."),
        title("sloth_slayer", "قاهر الكسل", "Sloth Slayer", C, Effect.XP_CATEGORY, QuestCategory.DISCIPLINE, 2, "الكسل لم يعد خصمًا.", "Laziness is no longer a rival."),
        title("dawn_hunter", "صياد الفجر", "Dawn Hunter", C, Effect.XP_CATEGORY, QuestCategory.MOVEMENT, 3, "يبدأ قبل شروق الشمس.", "Starts before the sunrise."),
        title("sleep_warden", "حارس النوم", "Sleep Warden", C, Effect.XP_CATEGORY, QuestCategory.RECOVERY, 3, "يحرس ساعات التعافي.", "Guards the hours of recovery."),
        title("excuse_breaker", "محطم الأعذار", "Excuse Breaker", R, Effect.XP_CATEGORY, QuestCategory.DISCIPLINE, 3, "الأعذار تتحطم عند قدميه.", "Excuses shatter at his feet."),
        title("plank_lord", "سيد الثبات", "Lord of Stillness", R, Effect.GOLD_PCT, null, 3, "لا يتحرك حين يجب الثبات.", "Does not move when stillness is due."),
        title("iron_wolf", "ذئب الحديد", "Iron Wolf", E, Effect.XP_CATEGORY, QuestCategory.TRAINING, 4, "يصطاد الحديد كما يصطاد الذئب.", "Hunts iron like a wolf hunts."),
        title("shadow_gale", "عاصفة الظل", "Shadow Gale", E, Effect.XP_CATEGORY, QuestCategory.MOVEMENT, 4, "تمر كالعاصفة وتختفي.", "Passes like a storm and vanishes."),
        title("penalty_survivor", "الناجي من العقاب", "Penalty Survivor", R, Effect.GOLD_PCT, null, 3, "عاد من منطقة العقاب أقوى.", "Returned from the penalty zone stronger."),
        title("limit_seeker", "باحث الحدود", "Limit Seeker", R, Effect.XP_CATEGORY, QuestCategory.TRAINING, 3, "يقترب من الحدود بحكمة.", "Approaches limits wisely."),
        title("silent_knight", "الفارس الصامت", "Silent Knight", E, Effect.XP_CATEGORY, QuestCategory.DISCIPLINE, 4, "يعمل في صمت ويترك النتائج تتكلم.", "Works in silence, lets results speak."),
        title("routine_king", "ملك الروتين", "Routine King", R, Effect.BONUS_POOL, null, 4, "الروتين عرشه.", "Routine is his throne."),
        title("iron_thrower", "رامي الحديد", "Iron Thrower", C, Effect.GOLD_PCT, null, 2, "الحديد خفيف بين يديه.", "Iron is light in his hands."),
        title("hoarder", "جامع الكنوز", "Treasure Hoarder", R, Effect.LOOT_LUCK, null, 1, "لا يترك صندوقًا مغلقًا.", "Leaves no chest unopened."),
        title("balance", "سيد التوازن", "Balance Master", E, Effect.XP_CATEGORY, QuestCategory.RECOVERY, 4, "التدريب والراحة في ميزان واحد.", "Training and rest in one balance."),
        title("torchbearer", "حامل الشعلة", "Torchbearer", C, Effect.XP_CATEGORY, QuestCategory.LEARNING, 3, "ينير الطريق بالمعرفة.", "Lights the way with knowledge."),
        title("record_hunter", "صياد الأرقام", "Record Hunter", R, Effect.XP_CATEGORY, QuestCategory.TRAINING, 3, "كل رقم قياسي فريسة.", "Every record is prey."),
        title("eternal_student", "الطالب الأبدي", "Eternal Student", R, Effect.XP_CATEGORY, QuestCategory.LEARNING, 4, "لا يتوقف عن التعلم.", "Never stops learning."),
        title("lone_wolf", "الذئب الوحيد", "Lone Wolf", E, Effect.GOLD_PCT, null, 4, "يتدرب وحده ولا ينتظر أحدًا.", "Trains alone and waits for no one."),
        title("lionheart", "قلب الأسد", "Lionheart", E, Effect.XP_CATEGORY, QuestCategory.TRAINING, 5, "شجاعة بلا تهور.", "Courage without recklessness."),
        title("unseen", "ظل لا يُرى", "Unseen Shadow", L, Effect.XP_CATEGORY, QuestCategory.MOVEMENT, 6, "حتى الظلال لا تراه.", "Even shadows can't see him."),
        title("dawnblade", "سيف الفجر", "Dawnblade", L, Effect.XP_CATEGORY, QuestCategory.DISCIPLINE, 6, "يقطع الليل بإرادته.", "Cuts the night with his will."),
        title("oathguard", "حامي العهد", "Oathguard", E, Effect.BONUS_POOL, null, 5, "عهده لا يُكسر.", "His oath never breaks."),
        title("hawk_eye", "عين الصقر", "Hawk Eye", R, Effect.LOOT_LUCK, null, 2, "يرى الغنائم من بعيد.", "Spots loot from afar."),
        title("energy_sovereign", "سيد الطاقة", "Energy Sovereign", L, Effect.GOLD_PCT, null, 6, "طاقة لا حدود لها.", "Boundless energy."),
        title("shadow_heir", "وريث الظلال", "Shadow Heir", L, Effect.XP_CATEGORY, QuestCategory.TRAINING, 6, "الظلال تنحني له.", "Shadows bow to him."),
        title("monarch", "الملك الظلّ", "Shadow Monarch", L, Effect.BONUS_POOL, null, 10, "وصل إلى المستوى 300. لا أحد فوقه.", "Reached level 300. None stand above.", lootable = false),
        // secret-chest exclusives (never in normal boxes or the shop)
        title("secret_cipher", "فاكّ الشفرة", "Cipher Breaker", R, Effect.LOOT_LUCK, null, 2, "وجد الشرط الذي لم يُذكر.", "Found the condition that was never stated.", lootable = false),
        title("secret_limit", "كاسر الحدود", "Limit Breaker", E, Effect.XP_CATEGORY, QuestCategory.TRAINING, 5, "ضعف المطلوب، قبل أن ينطفئ العدّاد.", "Double the order, before the ring went dark.", lootable = false),
        title("secret_hidden", "صاحب المهمة الخفية", "Bearer of the Hidden Quest", E, Effect.BONUS_POOL, null, 6, "النظام يراقبك عن كثب.", "The System watches you closely.", lootable = false),
        title("secret_shadow", "ظلّ السرّ", "Shadow of the Secret", L, Effect.LOOT_LUCK, null, 4, "لا يعرف أحد كيف حصل عليه.", "No one knows how he earned it.", lootable = false)
    )
    val secretExclusives get() = titles.filter { it.id.startsWith("title_secret_") }
    val themes = listOf(
        theme("azure", "أزرق النظام", "System Azure", C, 0xFF2F8CFF, 0xFF7FD4FF), theme("frost", "صقيع سماوي", "Cyan Frost", C, 0xFF19D3E6, 0xFFB9F6FF),
        theme("violet", "هاوية بنفسجية", "Violet Abyss", R, 0xFF8B5CFF, 0xFFD3B8FF), theme("crimson", "البوابة القرمزية", "Crimson Gate", R, 0xFFFF3D5A, 0xFFFFA3B1),
        theme("emerald", "زنزانة الزمرد", "Emerald Dungeon", R, 0xFF20D98B, 0xFFA6FFD6), theme("ice", "بلّور الجليد", "Ice Crystal", E, 0xFF9AD8FF, 0xFFFFFFFF),
        theme("obsidian", "سبج", "Obsidian", E, 0xFF6F7C99, 0xFFC9D3E8), theme("rose", "مانا وردية", "Rose Mana", E, 0xFFFF5FC8, 0xFFFFC2EA),
        theme("solar", "وهج شمسي", "Solar Flare", L, 0xFFFF8A1F, 0xFFFFD38A), theme("monarch", "ذهب الملوك", "Monarch Gold", L, 0xFFFFC447, 0xFFFFF1C2)
    )
    val frames = listOf(
        frame("standard", "الإطار القياسي", "Standard", C, 0xFF2F8CFF, 3, false), frame("twin", "الخط المزدوج", "Twin Line", C, 0xFF3FA9FF, 3, true),
        frame("notched", "النخبة المسنن", "Notched Elite", R, 0xFF19D3E6, 5, false), frame("crystal", "الكريستال", "Crystal", R, 0xFF9AD8FF, 4, true),
        frame("gate", "بوابة الزنزانة", "Dungeon Gate", R, 0xFF8B5CFF, 6, false), frame("rune", "الرونية", "Runic", E, 0xFF20D98B, 7, true),
        frame("circuit", "الدارة", "Circuit", E, 0xFF19D3E6, 8, false), frame("shard", "الشظايا", "Shard", E, 0xFFFF3D5A, 4, true),
        frame("royal", "الملكي", "Royal", L, 0xFFFFC447, 6, true), frame("crown", "تاج الملك", "Monarch Crown", L, 0xFFFFF1C2, 9, true)
    )
    val emblems = listOf(
        emblem("bang", "إشعار النظام", "System Alert", C, EmblemShape.BANG, 0xFF7FD4FF), emblem("sword", "النصل", "Blade", C, EmblemShape.SWORD, 0xFFB8C7D9),
        emblem("shield", "الدرع", "Shield", C, EmblemShape.SHIELD, 0xFF3FA9FF), emblem("star", "النجم", "Star", C, EmblemShape.STAR, 0xFFB9F6FF),
        emblem("moon", "الهلال", "Crescent", C, EmblemShape.MOON, 0xFFC9D3E8), emblem("diamond", "الماسة", "Diamond", C, EmblemShape.DIAMOND, 0xFF9AD8FF),
        emblem("hex", "السداسي", "Hexcore", R, EmblemShape.HEX, 0xFF19D3E6), emblem("bolt", "الصاعقة", "Thunderbolt", R, EmblemShape.BOLT, 0xFFFFE066),
        emblem("flame", "اللهب", "Flame", R, EmblemShape.FLAME, 0xFFFF8A1F), emblem("eye", "عين النظام", "System Eye", R, EmblemShape.EYE, 0xFF8B5CFF),
        emblem("wing", "الجناح", "Wing", R, EmblemShape.WING, 0xFFB9F6FF), emblem("fang", "ناب الذئب", "Wolf Fang", R, EmblemShape.FANG, 0xFFE8EEF8),
        emblem("key", "مفتاح البوابة", "Gate Key", E, EmblemShape.KEY, 0xFFFFC447), emblem("gate", "البوابة", "The Gate", E, EmblemShape.GATE, 0xFF8B5CFF),
        emblem("sun", "الشمس", "Sun", E, EmblemShape.SUN, 0xFFFFB347), emblem("lotus", "اللوتس", "Lotus", E, EmblemShape.LOTUS, 0xFFFF5FC8),
        emblem("trident", "الرمح الثلاثي", "Trident", E, EmblemShape.TRIDENT, 0xFF20D98B), emblem("skull", "قناع الظل", "Shadow Mask", L, EmblemShape.SKULL, 0xFF6F7C99),
        emblem("orb", "جوهرة المانا", "Mana Orb", L, EmblemShape.ORB, 0xFF2F8CFF), emblem("crown", "التاج", "Crown", L, EmblemShape.CROWN, 0xFFFFC447)
    )
    val badges = listOf(
        badge("first_session", "الحصة الأولى", "First Session", C, WORKOUTS, 1, "أول حصة مسجلة.", "Your first logged session."),
        badge("sessions_10", "عشر حصص", "Ten Sessions", C, WORKOUTS, 10, "عشر حصص كاملة.", "Ten full sessions."),
        badge("sessions_50", "خمسون حصة", "Fifty Sessions", R, WORKOUTS, 50, "التزام حقيقي.", "Real commitment."),
        badge("sessions_150", "مئة وخمسون", "150 Sessions", E, WORKOUTS, 150, "سنة من العمل.", "A year of work."),
        badge("sessions_365", "حصص بعدد أيام السنة", "365 Sessions", L, WORKOUTS, 365, "أسطورة الصالة.", "Legend of training."),
        badge("streak_7", "أسبوع النار", "Week of Fire", C, BEST_STREAK, 7, "سلسلة 7 أيام.", "7-day streak."),
        badge("streak_30", "شهر الحديد", "Iron Month", R, BEST_STREAK, 30, "سلسلة 30 يومًا.", "30-day streak."),
        badge("streak_100", "مئة يوم", "Hundred Days", E, BEST_STREAK, 100, "سلسلة 100 يوم.", "100-day streak."),
        badge("streak_365", "سنة كاملة", "Full Year", L, BEST_STREAK, 365, "سلسلة سنة كاملة.", "A whole-year streak."),
        badge("pr_1", "الرقم الأول", "First Record", C, PRS, 1, "أول رقم قياسي.", "First PR."),
        badge("pr_25", "آلة الأرقام", "Record Machine", R, PRS, 25, "25 رقمًا قياسيًا.", "25 PRs."),
        badge("checkin_30", "الواعي", "The Aware", C, CHECKINS, 30, "30 تسجيل جاهزية.", "30 check-ins."),
        badge("checkin_180", "مراقب الجسد", "Body Watcher", E, CHECKINS, 180, "180 تسجيل جاهزية.", "180 check-ins."),
        badge("walker_50", "الجوال", "Wanderer", R, WALKS_DONE, 50, "50 مهمة مشي.", "50 walk quests."),
        badge("sleep_60", "سيد الليل", "Lord of Night", R, SLEEP_DONE, 60, "60 مهمة نوم.", "60 sleep quests."),
        badge("learn_50", "القارئ", "The Reader", R, LEARNING_DONE, 50, "50 مهمة تعلم.", "50 learning quests."),
        badge("nutri_60", "الطاهي", "The Cook", R, NUTRITION_DONE, 60, "60 مهمة تغذية.", "60 nutrition quests."),
        badge("level_10", "المستوى 10", "Level 10", C, LEVEL, 10, "وصلت إلى المستوى 10.", "Reached level 10."),
        badge("level_50", "المستوى 50", "Level 50", R, LEVEL, 50, "وصلت إلى المستوى 50.", "Reached level 50."),
        badge("level_100", "المستوى 100", "Level 100", E, LEVEL, 100, "وصلت إلى المستوى 100.", "Reached level 100."),
        badge("level_200", "المستوى 200", "Level 200", L, LEVEL, 200, "وصلت إلى المستوى 200.", "Reached level 200."),
        badge("tonne_10", "عشرة أطنان", "Ten Tonnes", R, VOLUME_TONNES, 10, "10 أطنان مرفوعة.", "10 tonnes lifted."),
        badge("tier_1", "الرتبة D", "Tier D", R, ACTIVE_TIER, 1, "فعّلت رتبة تدريب جديدة.", "Activated a new training tier."),
        badge("tier_4", "الرتبة A", "Tier A", E, ACTIVE_TIER, 4, "رتبة A مفعّلة بأدائك.", "Tier A activated by your performance."),
        // hidden / secret
        badge("secret_safe", "الصياد الحكيم", "The Wise Hunter", R, SAFE_STOPS, 1, "استخدمت زر الألم وتوقفت بأمان. الحكمة قوة.", "You used the pain button and stopped safely. Wisdom is strength.", true),
        badge("secret_survivor", "عائد من العقاب", "Back from Penalty", R, PENALTIES_DONE, 1, "أكملت أول مهمة عقاب.", "Cleared your first Penalty Quest.", true),
        badge("secret_perfect7", "الأسبوع المثالي", "Perfect Week", E, PERFECT_DAYS, 7, "سبعة أيام مثالية.", "Seven perfect days.", true),
        badge("secret_loot50", "مدمن الغنائم", "Loot Addict", E, LOOT_OPENED, 50, "فتحت 50 صندوقًا.", "Opened 50 boxes.", true),
        badge("secret_deload", "فن التراجع", "Art of Retreat", R, RECOVERY_RESPECTED, 3, "احترمت التخفيف 3 مرات.", "Respected deloads 3 times.", true),
        badge("secret_emergency", "رجل الطوارئ", "First Responder", R, EMERGENCIES_DONE, 10, "أكملت 10 مهام طارئة.", "Completed 10 emergency quests.", true),
        badge("secret_challenges", "صائد التحديات", "Challenge Hunter", E, CHALLENGES_DONE, 10, "أكملت 10 تحديات أسبوعية.", "Completed 10 weekly challenges.", true),
        badge("secret_monarch", "الملك", "The Monarch", L, LEVEL, 300, "المستوى الأقصى.", "Maximum level.", true)
    )
    val boxes = listOf(
        RewardDef("box_common", RewardKind.BOX, Txt("صندوق غنائم عادي", "Common loot box"), C, Txt("صندوق غنائم عشوائية.", "A random loot box."), lootable = false),
        RewardDef("box_rare", RewardKind.BOX, Txt("صندوق غنائم نادر", "Rare loot box"), R, Txt("فرص أعلى للنادر والملحمي.", "Better odds for rare and epic."), lootable = false),
        RewardDef("box_epic", RewardKind.BOX, Txt("صندوق غنائم ملحمي", "Epic loot box"), E, Txt("ملحمي مضمون أو أفضل غالبًا.", "Mostly epic or better."), lootable = false),
        RewardDef("box_legendary", RewardKind.BOX, Txt("صندوق غنائم أسطوري", "Legendary loot box"), L, Txt("صندوق الملوك.", "A monarch's chest."), lootable = false),
        RewardDef("box_blessed", RewardKind.BOX, Txt("صندوق غنائم عشوائية مبارك", "Blessed random loot box"), E, Txt("فرص جيدة وآمنة: نادر/ملحمي/أسطوري، بلا لعنات.", "Good, safe odds: rare/epic/legendary, never cursed."), lootable = false),
        RewardDef("box_cursed", RewardKind.BOX, Txt("صندوق غنائم عشوائية ملعون", "Cursed random loot box"), L, Txt("فرص أسطورية أعلى، لكن قد يحمل لعنة تحدٍّ أصعب (+10 % لصفوف GOAL ليوم واحد، ضمن حدود السلامة).", "Higher legendary odds, but may carry a harder-challenge curse (+10 % GOAL rows for one day, within safety caps)."), lootable = false),
        RewardDef("box_secret", RewardKind.BOX, Txt("الصندوق السرّي", "Secret chest"), E, Txt("من مهمة سرّية فقط: فرص أعلى بكثير للنادر/الملحمي/الأسطوري، وقد يحوي ألقابًا حصرية.", "Only from a Secret Quest: much higher RARE/EPIC/LEGENDARY odds, may hold exclusive titles."), lootable = false)
    )
    val currency = listOf(
        RewardDef("gold_s", RewardKind.GOLD, Txt("كيس ذهب صغير", "Small gold pouch"), C, Txt("ذهب للمتجر.", "Gold for the shop."), gold = 40),
        RewardDef("gold_m", RewardKind.GOLD, Txt("كيس ذهب", "Gold pouch"), R, Txt("ذهب للمتجر.", "Gold for the shop."), gold = 120),
        RewardDef("gold_l", RewardKind.GOLD, Txt("خزنة ذهب", "Gold vault"), E, Txt("ذهب للمتجر.", "Gold for the shop."), gold = 350),
        RewardDef("gold_xl", RewardKind.GOLD, Txt("كنز الملك", "Monarch's hoard"), L, Txt("ذهب للمتجر.", "Gold for the shop."), gold = 900),
        RewardDef("token_reroll", RewardKind.TOKEN, Txt("رمز إعادة السحب", "Reroll token"), C, Txt("أعد سحب مهمة يومية يدوية مرة واحدة.", "Reroll one manual daily quest once."), token = TokenType.REROLL),
        RewardDef("token_shield", RewardKind.TOKEN, Txt("درع السلسلة", "Streak shield"), R, Txt("يحمي سلسلتك ليوم فاشل واحد (لا يلغي مهمة عقاب).", "Protects your streak for one failed day (doesn't cancel a penalty)."), token = TokenType.SHIELD),
        RewardDef("token_fullheal", RewardKind.TOKEN, Txt("الشفاء الكامل", "Full Recovery"), R, Txt("داخل اللعبة فقط: يحوّل اليوم إلى يوم راحة كامل مجاني — تُعذر المهام اليومية المتبقية بلا عقاب وتُحمى السلسلة.", "In-game only: turns today into a free full rest day — remaining daily quests excused, no penalty, streak protected."), token = TokenType.FULL_REST, lootable = false),
        RewardDef("key_raid", RewardKind.TOKEN, Txt("مفتاح الغارة", "Raid Key"), L, Txt("مفتاح يفتح غارة واحدة: حصة صعبة أعلى من رتبتك الحالية بدرجة. يبقى في المخزون حتى تستخدمه، وينتهي بعد 7 أيام.", "Opens one raid: a hard session one tier above your current tier. Stays until used; expires in 7 days."), lootable = false),
        RewardDef("curse_iron", RewardKind.TOKEN, Txt("لعنة: الثقل الحديدي", "Curse: Iron Weight"), E, Txt("غدًا صفوف GOAL أصعب بـ 10 % (لا تتجاوز سقوف السلامة، وتُلغى في أيام الراحة أو الألم). لا علاقة لها بالأكل.", "Tomorrow the GOAL rows are 10 % harder (never above safety caps; cancelled on rest or pain days). Never food-related."), lootable = false),
        RewardDef("token_emergency", RewardKind.TOKEN, Txt("استدعاء طارئ", "Emergency summon"), R, Txt("استدعِ مهمة طارئة اختيارية الآن.", "Summon an optional emergency quest now."), token = TokenType.EMERGENCY)
    )

    val all: List<RewardDef> = titles + themes + frames + emblems + badges + boxes + currency
    private val byId = all.associateBy { it.id }
    fun get(id: String) = byId[id]
    val defaults = setOf("theme_azure", "frame_standard", "emblem_bang")
}
