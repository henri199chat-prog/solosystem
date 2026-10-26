package com.ayman.solosystem.domain.model

import kotlinx.serialization.Serializable

/** Bilingual text helper used by the domain layer (UI picks the language). */
@Serializable
data class Txt(val ar: String, val en: String) {
    fun get(arabic: Boolean) = if (arabic) ar else en
}

@Serializable enum class Equipment(val ar: String, val en: String) {
    NONE("بدون معدات", "No equipment"),
    DUMBBELLS("دمبلز منزلية", "Home dumbbells"),
    BANDS("أحبال مقاومة", "Resistance bands"),
    PULLUP_BAR("عقلة (بار سحب)", "Pull-up bar"),
    FULL_GYM("صالة رياضية كاملة", "Full gym")
}

@Serializable enum class FitnessLevel(val ar: String, val en: String) {
    BEGINNER("مبتدئ", "Beginner"), NOVICE("أساسي", "Novice"),
    INTERMEDIATE("متوسط", "Intermediate"), ADVANCED("متقدم", "Advanced")
}

@Serializable enum class Experience(val ar: String, val en: String) {
    NONE("بدون خبرة", "None"), UNDER_6M("أقل من 6 أشهر", "< 6 months"),
    M6_TO_2Y("6 أشهر – سنتان", "6 months – 2 years"), OVER_2Y("أكثر من سنتين", "> 2 years")
}

@Serializable enum class Goal(val ar: String, val en: String) {
    STRENGTH("القوة", "Strength"), HYPERTROPHY("بناء العضلات", "Muscle growth"),
    ENDURANCE("التحمل", "Endurance"), GENERAL("لياقة عامة", "General fitness")
}

/** Body areas / conditions the user can flag. Exercises carry the same tags as contraindications. */
@Serializable enum class Limitation(val ar: String, val en: String) {
    SHOULDER("الكتف", "Shoulder"), ELBOW("المرفق", "Elbow"), WRIST("الرسغ", "Wrist"),
    LOWER_BACK("أسفل الظهر", "Lower back"), NECK("الرقبة", "Neck"), HIP("الورك", "Hip"),
    KNEE("الركبة", "Knee"), ANKLE("الكاحل", "Ankle"),
    HIGH_IMPACT("تجنب القفز/الضغط العالي", "Avoid jumping / high impact")
}

@Serializable enum class SleepQuality(val ar: String, val en: String) {
    POOR("ضعيف", "Poor"), FAIR("متوسط", "Fair"), GOOD("جيد", "Good")
}

@Serializable enum class Rank(val baseXp: Int) { E(10), D(20), C(35), B(55), A(80), S(120) }

@Serializable enum class Difficulty(val ar: String, val en: String, val dayThreshold: Double, val defaultGrace: Int) {
    RELAXED("مرن", "Relaxed", 0.5, 2), STANDARD("قياسي", "Standard", 0.7, 1), HARDCORE("صارم", "Hardcore", 0.9, 0)
}

@Serializable enum class Units { KG, LB }

@Serializable
data class PlayerProfile(
    val name: String,
    val age: Int,
    val heightCm: Float? = null,
    val weightKg: Float? = null,
    val fitness: FitnessLevel,
    val experience: Experience,
    val equipment: Set<Equipment>,
    val daysPerWeek: Int,
    val minutesPerSession: Int,
    val goal: Goal,
    val limitations: Set<Limitation> = emptySet(),
    val limitationNotes: String = "",
    val avoidExerciseIds: Set<String> = emptySet(),
    val sleepHours: Float = 7f,
    val sleepQuality: SleepQuality = SleepQuality.FAIR,
    val createdDay: Long = 0,
    val sex: Sex = Sex.MALE,
    /** Daily-life activity outside training (training load is added separately from the plan). */
    val activity: ActivityLevel = ActivityLevel.LIGHT
)

@Serializable enum class Sex(val ar: String, val en: String) { MALE("ذكر", "Male"), FEMALE("أنثى", "Female") }
/** Non-exercise activity factor (NEAT). Training sessions are added on top from the plan. */
@Serializable enum class ActivityLevel(val ar: String, val en: String, val factor: Double) {
    SEDENTARY("جلوس معظم اليوم", "Mostly sitting", 1.2), LIGHT("نشاط خفيف", "Lightly active", 1.3),
    ACTIVE("كثير الحركة", "On my feet a lot", 1.45), VERY("عمل بدني شاق", "Physical job", 1.6)
}

// ---------------- Training ----------------
@Serializable enum class Pattern { SQUAT, HINGE, LUNGE, H_PUSH, V_PUSH, H_PULL, V_PULL, CORE, CONDITIONING, CALF, SHOULDER_ACC, BICEPS, TRICEPS, CARRY, MOBILITY }

@Serializable enum class DayType(val ar: String, val en: String) {
    FULL_A("جسم كامل A", "Full body A"), FULL_B("جسم كامل B", "Full body B"),
    UPPER("الجزء العلوي", "Upper body"), LOWER("الجزء السفلي", "Lower body"),
    PUSH("دفع", "Push"), PULL("سحب", "Pull"), LEGS("أرجل", "Legs"),
    CONDITIONING("تحمّل وحركة", "Conditioning"), REST("تعافٍ", "Recovery")
}

@Serializable enum class Split(val ar: String, val en: String) {
    FULL_BODY("جسم كامل", "Full body"), UPPER_LOWER("علوي / سفلي", "Upper / Lower"),
    PPL("دفع / سحب / أرجل", "Push / Pull / Legs"), PPL_UL("دفع/سحب/أرجل + علوي/سفلي", "PPL + Upper/Lower")
}

@Serializable
data class Prescription(
    val slot: Int,
    val exerciseId: String,
    val sets: Int,
    val repMin: Int,
    val repMax: Int,
    val restSec: Int,
    val timed: Boolean = false,
    val rpeTarget: Int = 8
)

@Serializable
data class DayPlan(val dayOfWeek: Int, val type: DayType, val items: List<Prescription> = emptyList()) {
    val isTraining get() = type != DayType.REST
}

@Serializable
data class TrainingPlan(
    val split: Split,
    val startDay: Long,
    val week: List<DayPlan>,
    val difficultyLevel: Int,
    val deloadEvery: Int,
    val rationale: List<Txt>,
    val excludedNotes: List<Txt> = emptyList(),
    val warmup: List<Txt>,
    val cooldown: List<Txt>,
    val recoveryTips: List<Txt>
)

@Serializable
data class SetLog(
    val reps: Int = 0,
    val loadKg: Double? = null,
    val rpe: Int? = null,
    val done: Boolean = false
)

@Serializable enum class EntryStatus { ACTIVE, SWAPPED_OUT, STOPPED, DONE }

@Serializable
data class SessionEntry(
    val prescription: Prescription,
    val exerciseId: String,
    val sets: List<SetLog>,
    val status: EntryStatus = EntryStatus.ACTIVE,
    val pain: Boolean = false,
    val note: String = ""
)

@Serializable enum class SymptomType(val ar: String, val en: String) {
    MILD_DISCOMFORT("انزعاج خفيف", "Mild discomfort"),
    SHARP_PAIN("ألم حاد", "Sharp pain"),
    JOINT_PAIN("ألم في المفصل", "Joint pain"),
    DIZZINESS("دوخة", "Dizziness"),
    CHEST_PAIN("ألم أو ضيق في الصدر", "Chest pain / tightness"),
    BREATHLESS("ضيق تنفس غير عادي", "Unusual breathlessness")
}

@Serializable
data class PainReport(val epochMs: Long, val exerciseId: String?, val area: Limitation?, val type: SymptomType, val action: String)

@Serializable
data class WorkoutSession(
    val id: String,
    val day: Long,
    val dayType: DayType,
    val deload: Boolean,
    val reduced: Boolean = false,
    val entries: List<SessionEntry>,
    val startedMs: Long,
    val finishedMs: Long? = null,
    val stoppedForSafety: Boolean = false,
    val painReports: List<PainReport> = emptyList()
) { val finished get() = finishedMs != null }

@Serializable
data class Suggestion(val kind: SuggestionKind, val newLoadKg: Double? = null, val newRepMin: Int? = null, val newRepMax: Int? = null, val newExerciseId: String? = null)
@Serializable enum class SuggestionKind { ADD_LOAD, ADD_REPS, HARDER_VARIATION, EASIER_VARIATION, REDUCE_LOAD }

@Serializable
data class ExerciseProgress(
    val exerciseId: String,
    val targetLoadKg: Double? = null,
    val repMinOverride: Int? = null,
    val repMaxOverride: Int? = null,
    val successStreak: Int = 0,
    val struggleStreak: Int = 0,
    val suggestion: Suggestion? = null,
    val lastDay: Long? = null
)

@Serializable
data class PersonalRecord(val exerciseId: String, val day: Long, val kind: PrKind, val value: Double, val reps: Int, val loadKg: Double?)
@Serializable enum class PrKind { E1RM, REPS, SECONDS }

@Serializable
data class CheckIn(val day: Long, val sleepHours: Float, val soreness: Int, val energy: Int, val pain: Int) // soreness/energy 1..5, pain 0..3

@Serializable data class WeightEntry(val day: Long, val kg: Double)

// ---------------- Quests ----------------
@Serializable enum class QuestType { DAILY, MAIN, EMERGENCY, PENALTY }
@Serializable enum class QuestCategory(val ar: String, val en: String) {
    TRAINING("تدريب بدني", "Training"), MOVEMENT("حركة يومية", "Movement"),
    RECOVERY("تعافٍ ونوم", "Recovery & sleep"), NUTRITION("عادات غذائية", "Nutrition habits"),
    DISCIPLINE("انضباط", "Discipline"), LEARNING("تعلّم وتطوير", "Learning")
}
@Serializable enum class QuestStatus { PENDING, COMPLETED, MISSED, POSTPONED, EXCUSED, DECLINED, OFFERED }

/** How a quest gets completed: by the user ticking it, or automatically from verified data. */
/** COUNT = anime GOAL row: logged reps/metres toward [target]; may exceed it (overachievement). */
@Serializable enum class Verify { MANUAL, WORKOUT, CHECKIN, MILESTONE, COUNT }

@Serializable
data class Quest(
    val id: String,
    val templateId: String,
    val type: QuestType,
    val category: QuestCategory,
    val title: Txt,
    val objective: Txt,
    val description: Txt,
    val rank: Rank,
    val durationMin: Int,
    val xp: Int,
    val day: Long,
    val status: QuestStatus = QuestStatus.PENDING,
    val verify: Verify = Verify.MANUAL,
    val deadlineDay: Long? = null,
    val prerequisiteId: String? = null,
    val target: Int = 1,
    val progress: Int = 0,
    val reason: String? = null,
    val reasonReviewed: Boolean = false,
    val completedMs: Long? = null,
    val postponeCount: Int = 0,
    val recoveryFor: String? = null,
    val optional: Boolean = false,
    val items: List<PenaltyItem> = emptyList(),
    val penaltyTier: Int = 0,
    /** GOAL rows: rotated/edited exercise variant id ("" = base movement). */
    val variant: String = "",
    /** GOAL rows: player-raised amount (0 = none). Completion is still judged against [target]; reaching this earns bonus XP. */
    val stretch: Int = 0,
    /** GOAL rows lowered by the System (pain / illness): no further lowering, not eligible for the secret condition. */
    val lowered: Boolean = false,
    val redeemsDays: List<Long> = emptyList()
)

@Serializable enum class PenaltyUnit { REPS, SECONDS, MINUTES }

/** One line of a penalty quest, e.g. "Push-ups [0/24]". Volume is derived from the player's own logged capacity. */
@Serializable
data class PenaltyItem(val exerciseId: String, val target: Int, val unit: PenaltyUnit, val sets: Int, val restSec: Int, val done: Boolean = false)

@Serializable enum class PenaltyMode(val ar: String, val en: String, val maxTier: Int, val decayAfterCleanDays: Int, val extraOnTrainingMiss: Boolean) {
    OFF("بدون عقاب بدني (تأمل وتخطيط فقط)", "No physical penalty (reflection only)", 0, 1, false),
    GENTLE("تصاعد خفيف", "Gentle escalation", 3, 2, false),
    NORMAL("تصاعد عادي", "Normal escalation", 5, 3, false),
    STRICT("تصاعد صارم", "Strict escalation", 5, 5, true)
}

@Serializable enum class MissReason(val ar: String, val en: String, val excusable: Boolean) {
    NO_TIME("لم يكن لدي وقت", "No time", false),
    FORGOT("نسيت", "Forgot", false),
    LOW_MOTIVATION("قلة الحافز", "Low motivation", false),
    TIRED("إرهاق عادي", "Ordinary tiredness", false),
    SICK("مرض", "Illness", true),
    INJURY("إصابة أو ألم", "Injury / pain", true),
    EMERGENCY("ظرف طارئ", "Emergency", true),
    OTHER("سبب آخر", "Other", false)
}

// ---------------- Progression ----------------
@Serializable
data class XpEntry(val key: String, val xp: Int, val gold: Int, val day: Long, val label: Txt, val category: QuestCategory? = null)

@Serializable
data class Attributes(val str: Int = 0, val agi: Int = 0, val vit: Int = 0, val end: Int = 0, val disc: Int = 0) {
    val total get() = str + agi + vit + end + disc
}

@Serializable enum class Attr { STR, AGI, VIT, END, DISC }

@Serializable
data class ShopReward(val id: String, val name: String, val cost: Int, val entertainment: Boolean = true)
@Serializable
data class Purchase(val rewardId: String, val name: String, val cost: Int, val epochMs: Long)

@Serializable enum class HistoryKind { XP, XP_REVERSED, LEVEL_UP, QUEST_MISSED, QUEST_EXCUSED, QUEST_POSTPONED, QUEST_DECLINED, QUEST_RESCHEDULED, BONUS, BONUS_LOST, BONUS_BANKED, PR, WORKOUT, PAIN, ACHIEVEMENT, PLAN, PROGRESSION, PURCHASE, ATTRIBUTE, STREAK_LOST, PENALTY_ISSUED, PENALTY_DONE, PENALTY_STOPPED, SYSTEM, SECRET, RAID, GUILD, SHADOW, DUNGEON, BOSS, PHOTO }

@Serializable
data class HistoryEvent(val epochMs: Long, val day: Long, val kind: HistoryKind, val text: Txt, val xpDelta: Int = 0)

@Serializable enum class Accent { BLUE, CYAN, VIOLET, CRIMSON, GOLD }

@Serializable
data class Settings(
    val arabic: Boolean = true,
    val units: Units = Units.KG,
    val difficulty: Difficulty = Difficulty.STANDARD,
    val graceDaysPerWeek: Int = 1,
    val deloadEvery: Int = 6,
    val remindersEnabled: Boolean = false,
    val morningHour: Int = 8, val morningMinute: Int = 0,
    val eveningHour: Int = 20, val eveningMinute: Int = 30,
    val sound: Boolean = false,
    val vibration: Boolean = false,
    val emergencyQuests: Boolean = true,
    val penaltyMode: PenaltyMode = PenaltyMode.NORMAL,
    /** Daily quest deadline in minutes after local midnight (1440 = midnight at the end of the day). */
    val deadlineMinutes: Int = 1440,
    /** Animated anime-style background intensity. */
    val bgFx: BgFx = BgFx.HIGH,
    /** System sounds (anime ping/blip) in-app and on alert/notice notification channels. Silent mode always wins. */
    val systemSounds: Boolean = true,
    /** In-app interface sounds (buttons, windows, transitions, rewards). v1.2. null (1.1.0 data) = follow [systemSounds],
     *  so a player who had silenced the System keeps it silent after the update. */
    val uiSounds: Boolean? = null,
    /** In-app sound volume 0..100 (on top of the phone's media volume). v1.2. */
    val sfxVolume: Int = 80,
    /** After the level-100 awakening the player may switch back to the classic blue System. v1.2. */
    val classicTheme: Boolean = false,
    /** v1.2.1: context-aware System welcome window on app open. */
    val welcome: Boolean = true
) {
    val uiSoundsOn: Boolean get() = uiSounds ?: systemSounds
}

@Serializable enum class BgFx(val ar: String, val en: String) { HIGH("عالٍ", "High"), LOW("منخفض", "Low"), OFF("إيقاف", "Off") }

@Serializable
data class AppState(
    /** Days whose daily quest was converted into a SECRET QUEST (permanent). */
    val secretDays: Set<Long> = emptySet(),
    val schemaVersion: Int = 1,
    val profile: PlayerProfile? = null,
    val plan: TrainingPlan? = null,
    val settings: Settings = Settings(),
    /** Epoch day -> ms when that day's daily quest was first issued to the player (ring window start). Persisted. */
    val questIssuedAt: Map<Long, Long> = emptyMap(),
    val quests: List<Quest> = emptyList(),
    val ledger: List<XpEntry> = emptyList(),
    val bonusPending: Int = 0,
    val sessions: List<WorkoutSession> = emptyList(),
    val progress: Map<String, ExerciseProgress> = emptyMap(),
    val checkIns: List<CheckIn> = emptyList(),
    val prs: List<PersonalRecord> = emptyList(),
    val allocated: Attributes = Attributes(),
    val achievements: Map<String, Long> = emptyMap(),
    val rewards: List<ShopReward> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val history: List<HistoryEvent> = emptyList(),
    val lastProcessedDay: Long? = null,
    val bodyWeights: List<WeightEntry> = emptyList(),
    val highestLevelSeen: Int = 1,
    /** v1.2: the System evolved into the violet Shadow-Monarch theme (first time level >= 100). Persisted, one-time cinematic. */
    val monarchAwakened: Boolean = false,
    val penaltyTier: Int = 0,
    val cleanDays: Int = 0,
    val redeemedDays: Set<Long> = emptySet(),
    val bonus: List<BonusEntry> = emptyList(),
    val assessments: List<Assessment> = emptyList(),
    val activeTier: Int = 0,
    val inventory: Map<String, Int> = emptyMap(),
    val grantedKeys: Set<String> = emptySet(),
    val equippedTitle: String? = null,
    val equippedTheme: String = "theme_azure",
    val equippedFrame: String = "frame_standard",
    val equippedEmblem: String = "emblem_bang",
    val loot: LootState = LootState(),
    val skillLevelsSeen: Map<String, Int> = emptyMap(),
    val rerollsUsed: Map<Long, Int> = emptyMap(),
    val rewardLog: List<RewardGrant> = emptyList(),
    val shieldedDays: Set<Long> = emptySet(),
    /** Healing Elixir (nutrition) — fully separate from quests/XP/penalties. */
    val nutrition: NutritionState = NutritionState(),
    /** Anime GOAL rows (push/situp/squat/run): current full-day target per row, ramped weekly. */
    val goalTracks: Map<String, GoalTrack> = emptyMap(),
    /** Secret Quest reward selection waiting for the player (day -> claim). Granted once per day. */
    val secretClaims: Map<Long, SecretClaim> = emptyMap(),
    /** Extra attribute points from rewards (added to level points). */
    val bonusPoints: Int = 0,
    val raidKeys: List<RaidKey> = emptyList(),
    val raids: List<Raid> = emptyList(),
    /** Days carrying a Cursed-box curse: GOAL rows +10 % (always within safety caps; skipped on rest/pain days). */
    val curseDays: Set<Long> = emptySet(),
    val assessProposedDay: Long? = null,
    // ---- v1.3.0 (schema 4): all additive with defaults, so 1.1.x / 1.2.x saves decode unchanged ----
    /** Hunter Guild monthly assessment + license card. */
    val guild: GuildState = GuildState(),
    /** Shadow Army (30-day streak extractions). */
    val shadows: ShadowState = ShadowState(),
    /** GPS Dungeon runs (on-device GPS only, no network). */
    val dungeonRuns: List<DungeonRun> = emptyList(),
    /** Weekly Boss fights (one per ISO week, keyed by the Monday epoch day). */
    val bosses: List<BossFight> = emptyList(),
    /** Private transformation photos (app-private files only) + lock. */
    val transform: TransformState = TransformState()
)

/** v1.3.0 Hunter Guild. [rank] 0 = E … 5 = S. [run1kmSec] optional. */
@Serializable data class GuildTest(val day: Long, val ms: Long, val pushReps: Int, val plankSec: Int, val squatReps: Int, val run1kmSec: Int? = null, val score: Int, val rank: Int)
@Serializable data class GuildState(val tests: List<GuildTest> = emptyList(), val licensePhoto: String? = null, val painStops: Int = 0)

/** v1.3.0 Shadow extracted from a 30-day streak on [source] (GOAL row id or "checkin"). Sleeps when that streak breaks; revives after a 7-day streak. */
@Serializable data class Shadow(val id: String, val source: String, val extractedDay: Long, val asleep: Boolean = false, val sleptDay: Long? = null, val revived: Int = 0)
@Serializable data class ShadowState(val army: List<Shadow> = emptyList(), val offered: Set<String> = emptySet(),
    /** Month index (year*12+month) when the monthly perks were last used. */
    val guardMonth: Int? = null, val freezeMonth: Int? = null)

@Serializable data class DungeonRun(val id: String, val day: Long, val startMs: Long, val endMs: Long, val meters: Int, val targetM: Int, val rank: Int,
    val cleared: Boolean, val xp: Int = 0, val loggedM: Int = 0, val pain: Boolean = false)

/** [week] = Monday epoch day. Damage = XP earned that week (computed, cached in [damage]); [seenDamage] drives the hit animation. */
@Serializable data class BossFight(val week: Long, val bossId: Int, val rank: Int, val maxHp: Int, val damage: Int = 0, val seenDamage: Int = 0,
    val defeatedMs: Long? = null, val closed: Boolean = false)

@Serializable data class TransformPhoto(val id: String, val day: Long, val file: String, val weightKg: Double? = null)
@Serializable data class TransformState(val photos: List<TransformPhoto> = emptyList(), val lock: Boolean = false, val reminder: Boolean = true)

@Serializable data class SecretClaim(val day: Long, val points: Int, val claimed: Boolean = false, val choice: String? = null)
/** [rank] = raid rank index (0 = E … 5 = S) = player tier + 1 when dropped. */
@Serializable data class RaidKey(val id: String, val rank: Int, val day: Long, val expiresDay: Long, val used: Boolean = false)
@Serializable data class RaidStep(val row: String, val variant: String, val amount: Int, val sets: Int, val done: Boolean = false)
@Serializable data class Raid(val id: String, val keyId: String, val rank: Int, val day: Long, val startMs: Long, val limitMin: Int, val steps: List<RaidStep>,
                              val endMs: Long? = null, val cleared: Boolean = false, val xp: Int = 0)

/** [current] = full-day target (reps, or metres for run). */
@Serializable data class GoalTrack(val start: Int, val current: Int, val lastRampDay: Long, val note: Txt? = null)

@Serializable data class MealLog(val id: String, val day: Long, val ms: Long, val name: Txt, val kcal: Int, val protein: Double, val carbs: Double = 0.0, val fat: Double = 0.0, val foodId: String? = null, val qty: Double = 1.0,
    /** v1.2.2: household portion id (FoodDb.Portion name) and computed grams; null for legacy / manual meals. */
    val portion: String? = null, val grams: Int? = null)
@Serializable data class KcalAdjustment(val day: Long, val delta: Int, val ratePct: Double, val reason: Txt)
@Serializable data class NutritionState(
    val trainHour: Int = 18, val trainMinute: Int = 0,
    val remindersEnabled: Boolean = true,
    /** Cumulative kcal change from the 2-week trend review. */
    val kcalAdjust: Int = 0,
    val adjustments: List<KcalAdjustment> = emptyList(),
    val lastReviewDay: Long? = null,
    val meals: List<MealLog> = emptyList(),
    val waterMl: Map<Long, Int> = emptyMap(),
    val bestStreak: Int = 0,
    /** v1.2.2: favourite FoodDb ids (additive, default empty → 1.1.x/1.2.x saves decode unchanged). */
    val favorites: List<String> = emptyList()
)

@Serializable data class LootState(val opened: Int = 0, val sinceEpic: Int = 0, val sinceLegendary: Int = 0, val seed: Long = 0x5011L)

/** One delivered reward (for the reveal window and history). */
@Serializable data class RewardGrant(val key: String, val rewardId: String, val day: Long, val source: Txt, val goldInstead: Int = 0)

/** Temporary bonus XP (pending pool). Positive = daily-completion bonus, negative = small loss from a missed quest. Banked weekly into earned XP. */
@Serializable data class BonusEntry(val key: String, val day: Long, val xp: Int)

/** Self-recorded benchmark test (stop 1–2 reps before technical failure). Null = not tested / not safe. */
@Serializable data class Assessment(val day: Long, val pushReps: Int? = null, val squatReps: Int? = null, val plankSec: Int? = null, val pullReps: Int? = null)
