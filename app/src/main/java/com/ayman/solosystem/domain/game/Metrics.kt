package com.ayman.solosystem.domain.game

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.training.ExerciseLibrary

/** Real-behaviour metrics that drive skills, badges and challenges. Everything is computed from logged data. */
enum class Metric(val ar: String, val en: String) {
    WORKOUTS("حصص مكتملة", "Sessions completed"), BEST_STREAK("أفضل سلسلة", "Best streak"), STREAK("السلسلة الحالية", "Current streak"),
    CHECKINS("تسجيلات الجاهزية", "Readiness check-ins"), PRS("أرقام قياسية", "Personal records"),
    CARDIO_SESSIONS("حصص التحمل", "Conditioning sessions"), MOBILITY_DONE("أيام الحركة والتمدد", "Mobility days"),
    SLEEP_DONE("مهام النوم", "Sleep quests"), NUTRITION_DONE("مهام التغذية", "Nutrition quests"), LEARNING_DONE("مهام التعلم", "Learning quests"),
    DISCIPLINE_DONE("مهام الانضباط", "Discipline quests"), WALKS_DONE("مهام المشي", "Walk quests"),
    PUSH_REPS("مجموع تكرارات الدفع", "Total push reps"), SQUAT_REPS("مجموع تكرارات الأرجل", "Total leg reps"), PULL_REPS("مجموع تكرارات السحب", "Total pull reps"),
    CORE_SECONDS("ثواني الثبات", "Hold seconds"), BEST_PUSH_SET("أفضل مجموعة ضغط", "Best push-up set"), BEST_PLANK("أفضل بلانك (ث)", "Best plank (s)"),
    PENALTIES_DONE("مهام عقاب مكتملة", "Penalty quests cleared"), EMERGENCIES_DONE("مهام طارئة مكتملة", "Emergency quests done"),
    WEEKLY_REVIEWS("مراجعات أسبوعية", "Weekly reviews"), PERFECT_DAYS("أيام مثالية", "Perfect days"), LEVEL("المستوى", "Level"),
    RECOVERY_RESPECTED("حصص تخفيف/تعافٍ محترمة", "Deload/reduced sessions done"), VOLUME_TONNES("الحجم المرفوع (طن)", "Volume lifted (t)"),
    ASSESSMENTS("اختبارات الرتبة", "Rank assessments"), ACTIVE_TIER("رتبة التدريب", "Training tier"), MAIN_DONE("مهام رئيسية", "Main quests"), SECRET_QUESTS("مهام سرّية", "Secret quests"),
    LOOT_OPENED("صناديق مفتوحة", "Boxes opened"), CHALLENGES_DONE("تحديات أسبوعية", "Weekly challenges"), SAFE_STOPS("إيقافات آمنة", "Safe stops"),
    QUESTS_DONE("مهام مكتملة", "Quests completed"), EARLY_CHECKINS("أيام نوم ≥7 س", "Nights ≥7 h sleep")
}

object Metrics {
    private val pushPatterns = setOf(Pattern.H_PUSH, Pattern.V_PUSH, Pattern.TRICEPS)
    private val legPatterns = setOf(Pattern.SQUAT, Pattern.LUNGE, Pattern.HINGE, Pattern.CALF)
    private val pullPatterns = setOf(Pattern.H_PULL, Pattern.V_PULL, Pattern.BICEPS)

    fun compute(s: AppState, today: Long): Map<Metric, Int> {
        val done = s.quests.filter { it.status == QuestStatus.COMPLETED }
        fun tmpl(vararg t: String) = done.count { q -> t.any { q.templateId.startsWith(it) } }
        val fin = s.sessions.filter { it.finished && !it.stoppedForSafety }
        val sets = fin.flatMap { ses -> ses.entries.flatMap { e -> e.sets.filter { it.done }.map { e.exerciseId to it } } }
        fun repsOf(pats: Set<Pattern>) = sets.filter { (id, _) -> ExerciseLibrary.get(id)?.let { it.pattern in pats && !it.timed } == true }.sumOf { it.second.reps }
        val core = sets.filter { (id, _) -> ExerciseLibrary.get(id)?.let { it.timed && it.pattern == Pattern.CORE } == true }.sumOf { it.second.reps }
        val bestPush = sets.filter { it.first in setOf("pushup", "decline_pushup", "diamond_pushup") }.maxOfOrNull { it.second.reps } ?: 0
        val bestPlank = sets.filter { it.first == "plank" }.maxOfOrNull { it.second.reps } ?: 0
        val vol = sets.sumOf { it.second.reps * (it.second.loadKg ?: 0.0) } / 1000.0
        val cardio = fin.count { ses -> ses.dayType == DayType.CONDITIONING || ses.entries.any { e -> ExerciseLibrary.get(e.exerciseId)?.pattern == Pattern.CONDITIONING && e.sets.any { it.done } } }
        val days = s.quests.map { it.day }.distinct()
        val perfect = days.count { d -> val qs = s.quests.filter { it.day == d && it.type == QuestType.DAILY && !it.optional }; qs.isNotEmpty() && qs.all { it.status == QuestStatus.COMPLETED } }
        return mapOf(
            Metric.WORKOUTS to fin.size, Metric.BEST_STREAK to Stats.bestStreak(s, today), Metric.STREAK to Stats.streak(s, today),
            Metric.CHECKINS to s.checkIns.size, Metric.PRS to s.prs.size, Metric.CARDIO_SESSIONS to cardio,
            Metric.MOBILITY_DONE to tmpl("mobility"), Metric.SLEEP_DONE to tmpl("sleep"), Metric.NUTRITION_DONE to tmpl("nutrition"),
            Metric.LEARNING_DONE to tmpl("learn", "review"), Metric.DISCIPLINE_DONE to done.count { it.category == QuestCategory.DISCIPLINE },
            Metric.WALKS_DONE to tmpl("walk", "goal:run"), Metric.SECRET_QUESTS to s.secretDays.size, Metric.PUSH_REPS to repsOf(pushPatterns), Metric.SQUAT_REPS to repsOf(legPatterns),
            Metric.PULL_REPS to repsOf(pullPatterns), Metric.CORE_SECONDS to core, Metric.BEST_PUSH_SET to bestPush, Metric.BEST_PLANK to bestPlank,
            Metric.PENALTIES_DONE to done.count { it.type == QuestType.PENALTY }, Metric.EMERGENCIES_DONE to done.count { it.type == QuestType.EMERGENCY },
            Metric.WEEKLY_REVIEWS to tmpl("review"), Metric.PERFECT_DAYS to perfect, Metric.LEVEL to Ledger.level(s).level,
            Metric.RECOVERY_RESPECTED to fin.count { it.deload || it.reduced }, Metric.VOLUME_TONNES to vol.toInt(),
            Metric.ASSESSMENTS to s.assessments.size, Metric.ACTIVE_TIER to s.activeTier, Metric.MAIN_DONE to done.count { it.type == QuestType.MAIN },
            Metric.LOOT_OPENED to s.loot.opened, Metric.CHALLENGES_DONE to s.grantedKeys.count { it.startsWith("wc:") },
            Metric.SAFE_STOPS to s.sessions.sumOf { it.painReports.size } + s.quests.count { it.reason == "pain" },
            Metric.QUESTS_DONE to done.size, Metric.EARLY_CHECKINS to s.checkIns.count { it.sleepHours >= 7f }
        )
    }
}
