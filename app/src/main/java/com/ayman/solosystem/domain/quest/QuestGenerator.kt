package com.ayman.solosystem.domain.quest

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.training.PlanGenerator
import com.ayman.solosystem.domain.training.Tiers
import java.time.LocalDate

object QuestGenerator {

    private fun q(id: String, tmpl: String, type: QuestType, cat: QuestCategory, title: Txt, obj: Txt, desc: Txt, rank: Rank, min: Int, day: Long,
                  verify: Verify = Verify.MANUAL, target: Int = 1, prereq: String? = null, optional: Boolean = false, deadline: Long? = day) =
        Quest(id, tmpl, type, cat, title, obj, desc, rank, min, Leveling.questXp(rank, min, type), day, verify = verify, target = target,
            prerequisiteId = prereq, optional = optional, deadlineDay = if (type == QuestType.MAIN) null else deadline)

    fun trainingRank(min: Int, deload: Boolean) = when { deload -> Rank.D; min <= 30 -> Rank.D; min <= 60 -> Rank.C; else -> Rank.B }

    fun daily(s: AppState, day: Long): List<Quest> {
        val p = s.profile ?: return emptyList(); val plan = s.plan ?: return emptyList()
        val dp = PlanGenerator.dayPlan(plan, day)
        val deload = PlanGenerator.isDeloadWeek(plan, day)
        val dow = LocalDate.ofEpochDay(day).dayOfWeek.value
        val out = mutableListOf<Quest>()
        fun id(t: String) = "d:$t:$day"
        out += q(id("checkin"), "checkin", QuestType.DAILY, QuestCategory.DISCIPLINE,
            Txt("تسجيل الجاهزية", "Readiness check-in"), Txt("سجّل نومك وطاقتك والألم العضلي وأي ألم.", "Log sleep, energy, soreness and any pain."),
            Txt("النظام يكيّف التدريب حسب جاهزيتك المسجلة.", "The system adapts training to your logged readiness."), Rank.E, 2, day, Verify.CHECKIN)
        if (dp.isTraining) {
            val r = trainingRank(p.minutesPerSession, deload)
            out += q(id("train"), "train:${dp.type.name}", QuestType.DAILY, QuestCategory.TRAINING,
                Txt("حصة التدريب: ${dp.type.ar}${if (deload) " (تخفيف)" else ""}", "Training session: ${dp.type.en}${if (deload) " (deload)" else ""}"),
                Txt("أكمل وسجّل حصة اليوم (${dp.items.size} تمارين).", "Complete and log today's session (${dp.items.size} exercises)."),
                Txt("تكتمل تلقائيًا عند إنهاء الحصة في شاشة التدريب — لا يمكن تأشيرها يدويًا.", "Completes automatically when you finish the session on the Training screen — can't be ticked manually."),
                r, p.minutesPerSession, day, Verify.WORKOUT)
        }
        out += q(id("mobility"), "mobility", QuestType.DAILY, QuestCategory.RECOVERY, Txt("روتين الحركة والتمدد", "Mobility & stretch routine"),
            Txt("10 دقائق حركة لطيفة للورك والكتفين والعمود.", "10 minutes of gentle hip, shoulder and spine mobility."), Txt("بدون ألم، تنفس ببطء.", "Pain-free, slow breathing."), Rank.E, 10, day)
        out += q(id("sleep"), "sleep", QuestType.DAILY, QuestCategory.RECOVERY, Txt("روتين النوم", "Sleep routine"),
            Txt("ابتعد عن الشاشات 30 دقيقة قبل النوم وامنح نفسك 7–9 ساعات فرصة نوم.", "No screens 30 min before bed; give yourself a 7–9 h sleep window."),
            Txt("النوم الجيد يدعم التعافي والأداء.", "Good sleep supports recovery and performance."), Rank.D, 30, day)
        // nutrition habit moved to the Healing Elixir section
        if (dow == 7) out += q(id("review"), "review", QuestType.DAILY, QuestCategory.DISCIPLINE, Txt("المراجعة الأسبوعية", "Weekly review"),
            Txt("سجّل وزنك وقياساتك، راجع الملخص الأسبوعي وحدد هدفًا واحدًا للأسبوع القادم.", "Log your weight and measurements, review the weekly summary and set one goal for next week."),
            Txt("الانضباط يُبنى بالمراجعة الصادقة.", "Discipline is built through honest review."), Rank.C, 15, day)
        // the anime GOAL block (required, drives ring + penalty) + secondary side tasks (optional, XP only, no penalty)
        return GoalPlan.quests(s, day) + out.map { it.copy(optional = true) }
    }

    data class MainDef(val id: String, val title: Txt, val obj: Txt, val rank: Rank, val target: Int, val prereq: String?, val metric: (AppState, Long) -> Int)

    val mains = listOf(
        MainDef("m:first_workout", Txt("الخطوات الأولى", "First Steps"), Txt("أكمل أول حصة تدريب مسجلة.", "Complete your first logged workout."), Rank.E, 1, null) { s, _ -> s.sessions.count { it.finished && !it.stoppedForSafety } },
        MainDef("m:first_pr", Txt("أول رقم قياسي", "First personal record"), Txt("حقق أول رقم قياسي شخصي من بياناتك.", "Set your first personal record from logged data."), Rank.D, 1, "m:first_workout") { s, _ -> minOf(1, s.prs.size) },
        MainDef("m:full_week", Txt("أسبوع كامل", "A complete week"), Txt("أكمل كل الحصص المخططة في أسبوع واحد.", "Complete every planned session in one week."), Rank.D, 1, "m:first_workout") { s, t ->
            if (Stats.weeks(s, t, 52).any { it.sessionsPlanned > 0 && it.sessionsDone >= it.sessionsPlanned }) 1 else 0 },
        MainDef("m:workouts_12", Txt("12 حصة", "12 sessions"), Txt("أكمل 12 حصة تدريب.", "Complete 12 training sessions."), Rank.C, 12, "m:first_workout") { s, _ -> s.sessions.count { it.finished && !it.stoppedForSafety } },
        MainDef("m:checkins_30", Txt("30 يومًا من الوعي", "30 days of awareness"), Txt("سجّل الجاهزية 30 مرة.", "Log readiness 30 times."), Rank.C, 30, null) { s, _ -> s.checkIns.size },
        MainDef("m:streak_14", Txt("سلسلة 14 يومًا", "14-day streak"), Txt("حافظ على سلسلة نجاح 14 يومًا.", "Keep a 14-day success streak."), Rank.B, 14, "m:full_week") { s, t -> Stats.bestStreak(s, t) },
        MainDef("m:workouts_36", Txt("36 حصة", "36 sessions"), Txt("أكمل 36 حصة تدريب.", "Complete 36 training sessions."), Rank.A, 36, "m:workouts_12") { s, _ -> s.sessions.count { it.finished && !it.stoppedForSafety } },
        MainDef("m:workouts_100", Txt("مئة حصة", "One hundred sessions"), Txt("أكمل 100 حصة تدريب.", "Complete 100 training sessions."), Rank.S, 100, "m:workouts_36") { s, _ -> s.sessions.count { it.finished && !it.stoppedForSafety } }
    )

    fun mainQuest(d: MainDef, day: Long) = q(d.id, d.id, QuestType.MAIN, if (d.id.contains("checkin")) QuestCategory.DISCIPLINE else QuestCategory.TRAINING,
        d.title, d.obj, Txt("مهمة رئيسية: تكتمل تلقائيًا من بياناتك المسجلة.", "Main quest: completes automatically from your logged data."), d.rank, 30, day, Verify.MILESTONE, d.target, d.prereq)

    fun assessmentQuest(tier: Int, day: Long): Quest {
        val t = Tiers.all[tier]
        return q("m:assess:$tier", "assess", QuestType.MAIN, QuestCategory.TRAINING,
            Txt("اختبار الرتبة ${t.ar}", "Rank ${t.label} assessment"),
            Txt("حقق معايير الدخول المسجلة (ضغط ${t.push}، قرفصاء ${t.squat}، بلانك ${t.plankSec} ث) في حصصك أو في الاختبار.", "Meet the entry benchmarks (push-ups ${t.push}, squats ${t.squat}, plank ${t.plankSec}s) in your sessions or the assessment."),
            Txt("المستوى يفتح الرتبة فقط؛ تفعيلها يتطلب أداءً حقيقيًا مسجلًا. توقف قبل الفشل الفني بتكرار أو اثنين.", "Level only unlocks the tier; activating it needs real logged performance. Stop 1–2 reps before technical failure."),
            Penalty.rankFor(minOf(5, tier)), 20, day, Verify.MILESTONE)
    }

    private val emergencyPool = listOf(
        Triple("tidy", Txt("رتّب مكان تدريبك", "Tidy your training space"), Txt("10 دقائق لتجهيز مكان آمن ومرتب للتمرين.", "10 minutes to make a safe, tidy training spot.")),
        Triple("eve_walk", Txt("مشي بعد العشاء", "After-dinner walk"), Txt("امشِ 10 دقائق بعد العشاء.", "Walk 10 minutes after dinner.")),
        Triple("breath", Txt("تنفس هادئ", "Calm breathing"), Txt("5 دقائق تنفس بطيء (4 ث شهيق، 6 ث زفير).", "5 minutes of slow breathing (4 s in, 6 s out).")),
        Triple("buddy", Txt("شريك التدريب", "Training buddy"), Txt("راسل صديقًا واتفقا على حصة مشتركة.", "Message a friend and agree on a shared session.")),
        Triple("prep", Txt("تحضير الغد", "Prepare for tomorrow"), Txt("جهّز ملابس التمرين ووجبة الغد.", "Lay out training clothes and tomorrow's meal.")),
        Triple("posture", Txt("استراحة الجلوس", "Sitting break"), Txt("قف وتحرك دقيقتين كل ساعة لثلاث ساعات.", "Stand and move 2 minutes every hour for three hours."))
    )

    /** Deterministic ~1-in-4 chance per day. Never offered when readiness is low or pain was reported. */
    fun emergency(s: AppState, day: Long, unsafe: Boolean, chancePct: Int = 25, force: Int = -1): Quest? {
        if (!s.settings.emergencyQuests || unsafe) return null
        val h = ((day * 2654435761L) ushr 7) and 0x7fffffff
        if (force < 0 && h % 100L >= chancePct) return null
        val pick = emergencyPool[(((h / 100) + maxOf(0, force)) % emergencyPool.size).toInt()]
        return q("e:${pick.first}:$day" + (if (force >= 0) ":t$force" else ""), "e:${pick.first}", QuestType.EMERGENCY, QuestCategory.DISCIPLINE,
            Txt("مهمة طارئة: ${pick.second.ar}", "Emergency quest: ${pick.second.en}"), pick.third,
            Txt("اختيارية: يمكنك القبول أو التأجيل ليوم واحد أو الرفض بدون أي عقاب.", "Optional: accept, postpone one day or decline with no penalty."),
            Rank.D, 10, day, optional = true).copy(status = QuestStatus.OFFERED)
    }
}
