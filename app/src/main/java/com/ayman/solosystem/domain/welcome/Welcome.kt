package com.ayman.solosystem.domain.welcome

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.quest.Penalty
import kotlin.random.Random

/**
 * v1.2.1 "living System" welcome: a short system notice on app open, chosen from a large Arabic pool by context,
 * never repeating a recently shown line. Pure JVM (unit-tested); the UI only supplies [Ctx] and persists [Memory].
 */
object Welcome {
    /** Minimum time away before a resume shows the welcome again. */
    const val RESUME_AFTER_MS = 30 * 60_000L
    const val HISTORY = 12

    enum class TimeOfDay(val ar: String) { DAWN("فجر"), MORNING("صباح"), NOON("ظهر"), EVENING("مساء"), NIGHT("ليل") }
    fun timeOfDay(hour: Int): TimeOfDay = when (hour) { in 4..5 -> TimeOfDay.DAWN; in 6..11 -> TimeOfDay.MORNING; in 12..14 -> TimeOfDay.NOON; in 15..19 -> TimeOfDay.EVENING; else -> TimeOfDay.NIGHT }

    enum class QuestState { NONE, NOT_STARTED, IN_PROGRESS, COMPLETED, SECRET }

    /** Everything the selector needs, gathered off the main thread. */
    data class Ctx(
        val firstEver: Boolean, val hour: Int, val opensToday: Int, val awayMs: Long?, val streak: Int,
        val quest: QuestState, val questPct: Int, val minutesLeft: Int, val penaltyPending: Boolean, val restDay: Boolean,
        val sick: Boolean, val levelUpTo: Int?, val rankUp: String?, val level: Int, val monarch: Boolean, val name: String?
    )

    enum class Cat {
        FIRST_EVER, SICK, PENALTY, ABSENCE_LONG, ABSENCE_DAY, LEVEL_UP, RANK_UP, SECRET, COMPLETED, STREAK_MILESTONE,
        MONARCH, REST, RETURN_MANY, RETURN_AGAIN, IN_PROGRESS_LATE, IN_PROGRESS, NOT_STARTED_LATE, NOT_STARTED_DAWN,
        NOT_STARTED_MORNING, NOT_STARTED_NOON, NOT_STARTED_EVENING, NOT_STARTED_NIGHT, STREAK
    }

    val MILESTONES = setOf(3, 7, 14, 21, 30, 50, 60, 90, 100, 150, 200, 365)

    /** Highest-priority matching context wins. */
    fun category(c: Ctx): Cat {
        val days = (c.awayMs ?: 0L) / 86_400_000L
        val questOpen = c.quest == QuestState.NOT_STARTED || c.quest == QuestState.IN_PROGRESS
        return when {
            c.firstEver -> Cat.FIRST_EVER
            c.sick -> Cat.SICK
            c.penaltyPending -> Cat.PENALTY
            days >= 2 -> Cat.ABSENCE_LONG
            days >= 1 -> Cat.ABSENCE_DAY
            c.rankUp != null -> Cat.RANK_UP
            c.levelUpTo != null -> Cat.LEVEL_UP
            c.quest == QuestState.SECRET -> Cat.SECRET
            c.quest == QuestState.COMPLETED && c.streak in MILESTONES -> Cat.STREAK_MILESTONE
            c.quest == QuestState.COMPLETED -> if (c.monarch) Cat.MONARCH else Cat.COMPLETED
            c.restDay -> Cat.REST
            questOpen && c.opensToday >= 3 -> Cat.RETURN_MANY
            questOpen && c.opensToday == 2 -> Cat.RETURN_AGAIN
            c.monarch -> Cat.MONARCH
            c.quest == QuestState.IN_PROGRESS && c.minutesLeft in 0..180 -> Cat.IN_PROGRESS_LATE
            c.quest == QuestState.IN_PROGRESS -> Cat.IN_PROGRESS
            questOpen && c.minutesLeft in 0..180 -> Cat.NOT_STARTED_LATE
            questOpen -> when (timeOfDay(c.hour)) {
                TimeOfDay.DAWN -> Cat.NOT_STARTED_DAWN; TimeOfDay.MORNING -> Cat.NOT_STARTED_MORNING; TimeOfDay.NOON -> Cat.NOT_STARTED_NOON
                TimeOfDay.EVENING -> Cat.NOT_STARTED_EVENING; TimeOfDay.NIGHT -> Cat.NOT_STARTED_NIGHT
            }
            else -> Cat.STREAK
        }
    }

    /** Arabic system-voice pool (Fusha, cold). Placeholders: {pct} {left} {days} {streak} {level} {rank}. */
    val POOL: Map<Cat, List<String>> = mapOf(
        Cat.FIRST_EVER to listOf(
            "لقد اختارك النظام. من هذه اللحظة، لم تعد شخصًا عاديًا.",
            "تم تسجيلك لاعبًا. النظام لا يعرف الرحمة، ولا يقبل الأعذار.",
            "أهلًا بك في النظام. الضعفاء يتوقفون هنا. فهل أنت منهم؟"),
        Cat.SICK to listOf(
            "جسدك يحتاج إلى التعافي. خفّف النظام مهامك اليوم. استرح جيدًا.",
            "حتى أقوى الصيادين يتوقفون ليتعافوا. اشرب الماء ونم مبكرًا.",
            "تم رصد حالة صحية. لا عقوبة اليوم، فقط تعافَ وعُد أقوى.",
            "النظام يراقب تعافيك. لا تُجبر جسدك، فالعودة أهم من الاندفاع.",
            "الراحة اليوم جزء من التدريب. اعتنِ بنفسك أيها اللاعب."),
        Cat.PENALTY to listOf(
            "مهمة العقاب بانتظارك. الهروب ليس خيارًا.",
            "لقد فشلت سابقًا، والنظام لا ينسى. أكمل العقاب.",
            "العقاب لم يُنفَّذ بعد. كل دقيقة تأخير تُسجَّل ضدك.",
            "تذكّر لماذا عوقبت. ثم انهض وأكمل ما بدأته.",
            "النظام لا يطلب منك الكمال، بل يطلب منك الوفاء. العقاب ينتظر."),
        Cat.ABSENCE_LONG to listOf(
            "لقد غبت {days} أيام... ظننت أن النظام سينساك؟",
            "{days} أيام من الغياب. العضلات التي لا تُستعمل تذبل.",
            "عدت بعد {days} أيام. النظام سجّل كل يوم منها.",
            "غياب {days} أيام. الضعف يتسلل إلى من يتوقف. ابدأ الآن.",
            "أخيرًا عدت بعد {days} أيام. لا تجعلها عادة."),
        Cat.ABSENCE_DAY to listOf(
            "يوم كامل دون أن تفتح النظام. هل نسيت من أنت؟",
            "مرّ يوم على آخر ظهور لك. المهمة لا تنتظر أحدًا.",
            "غبت يومًا كاملًا. النظام ما زال يراقب."),
        Cat.LEVEL_UP to listOf(
            "لقد ارتفع مستواك إلى {level}. لا تتوقف، فالمستوى التالي أصعب.",
            "المستوى {level}. أنت أقوى من الأمس، لكنك لست قويًا بما يكفي بعد.",
            "تم تسجيل الارتقاء إلى المستوى {level}. استمر في الصعود."),
        Cat.RANK_UP to listOf(
            "رتبتك الآن {rank}. العالم يبدأ بملاحظتك.",
            "تمت ترقيتك إلى الرتبة {rank}. المسؤولية تكبر مع القوة."),
        Cat.SECRET to listOf(
            "لقد حققت شرطًا خفيًا اليوم. النظام يعترف بقوتك.",
            "المهمة السرية اكتملت. القليلون فقط يصلون إلى هنا.",
            "شرط خفي تحقق. تجاوزت ما طُلب منك، وهذا ما يصنع الصيادين."),
        Cat.COMPLETED to listOf(
            "المهمة اليومية مكتملة. استرح، فغدًا سيكون أصعب.",
            "أحسنت. لكن تذكّر: الانضباط يُبنى كل يوم من جديد.",
            "اكتملت مهمة اليوم. سلسلتك الآن {streak} أيام.",
            "لقد أوفيت بوعدك اليوم. النظام سجّل ذلك.",
            "مهمة اليوم انتهت. تناول طعامك ونم جيدًا، فالنمو يحدث أثناء الراحة."),
        Cat.STREAK_MILESTONE to listOf(
            "{streak} يومًا متتاليًا. هذا ليس حظًا، هذا انضباط.",
            "سلسلة {streak} يومًا. أنت تتحول ببطء إلى شخص آخر.",
            "إنجاز: {streak} يومًا دون انقطاع. النظام يراك."),
        Cat.MONARCH to listOf(
            "أيها الملك... الظلال بانتظار أوامرك.",
            "مرحبًا بعودتك أيها الملك. حتى الملوك يتدربون كل يوم.",
            "أيها الملك، العرش لا يُحفظ إلا بالقوة. المهمة بانتظارك.",
            "الظلال تنحني لعودتك أيها الملك. لا تدعها ترى ضعفًا.",
            "أيها الملك، القوة التي بلغتها تُحفظ بالانضباط لا بالذكرى."),
        Cat.REST to listOf(
            "اليوم يوم راحة. التعافي جزء من القوة، لا هروب منها.",
            "يوم راحة. مهامك أخف اليوم، لكنها ليست صفرًا.",
            "استرح اليوم بحكمة. غدًا يعود الضغط.",
            "العضلات تُبنى أثناء الراحة. نم جيدًا وتناول البروتين."),
        Cat.RETURN_MANY to listOf(
            "عدت مرة أخرى؟ الهدف لم يكتمل بعد ({pct}%).",
            "فتح التطبيق لا يرفع مستواك. التمرين وحده يفعل.",
            "هذه زيارتك رقم {opens} اليوم. والمهمة ما زالت عند {pct}%.",
            "النظر إلى المهمة لن ينجزها. انهض.",
            "تكرار الدخول دون إنجاز يُسجَّل أيضًا أيها اللاعب."),
        Cat.RETURN_AGAIN to listOf(
            "عدت مجددًا. هل أنت هنا لتُكمل المهمة؟",
            "مرحبًا مرة أخرى. التقدم الحالي {pct}%.",
            "زيارة ثانية اليوم. اجعلها زيارة إنجاز."),
        Cat.IN_PROGRESS_LATE to listOf(
            "تبقى {left} فقط. التقدم {pct}%. لا مجال للتأخير.",
            "الوقت ينفد: {left} متبقية والمهمة عند {pct}%.",
            "{pct}% فقط، و{left} حتى انتهاء المهلة. العقاب يقترب.",
            "اقتربت المهلة من نهايتها. أكمل ما تبقى قبل أن يحكم النظام."),
        Cat.IN_PROGRESS to listOf(
            "المهمة اليومية عند {pct}%. أكمل الباقي.",
            "بدأت جيدًا ({pct}%). البداية لا تكفي.",
            "التقدم {pct}%. قسّم ما تبقى إلى مجموعات وأنهِه.",
            "نصف الطريق لا يُحتسب. التقدم الحالي {pct}%.",
            "تبقى {left} حتى المهلة. التقدم {pct}%."),
        Cat.NOT_STARTED_LATE to listOf(
            "لم تبدأ بعد، وتبقى {left} فقط. تحرك الآن.",
            "المهلة توشك على الانتهاء ولم تبدأ. العقاب ينتظر المتكاسلين.",
            "{left} متبقية ولا شيء منجز. هل تريد العقاب؟"),
        Cat.NOT_STARTED_DAWN to listOf(
            "الفجر. الجميع نائمون، وأنت تستطيع أن تسبقهم.",
            "استيقظت قبل الشمس. ابدأ مهمتك قبل أن يبدأ العالم.",
            "وقت الفجر هو وقت الأقوياء. المهمة جاهزة."),
        Cat.NOT_STARTED_MORNING to listOf(
            "صباح جديد ومهمة جديدة. لم تبدأ بعد.",
            "بدأ اليوم. المهمة اليومية لم تُلمس بعد.",
            "الصباح أفضل وقت للمجموعة الأولى. ابدأ الآن.",
            "استيقظت؟ إذن ابدأ. خمسون ضغطة لن تنجز نفسها."),
        Cat.NOT_STARTED_NOON to listOf(
            "منتصف النهار ولم تبدأ بعد. الوقت لا يرحم.",
            "الظهر. نصف اليوم مضى والمهمة عند الصفر.",
            "استراحة الظهر تكفي لمجموعة أو مجموعتين. استغلها."),
        Cat.NOT_STARTED_EVENING to listOf(
            "المساء حلّ والمهمة لم تبدأ. هل تنتظر العقاب؟",
            "ما زال أمامك وقت هذا المساء. لكن ليس كثيرًا.",
            "المساء. اليوم يقترب من نهايته، والمهمة عند الصفر."),
        Cat.NOT_STARTED_NIGHT to listOf(
            "الليل. والمهمة اليومية لم تبدأ بعد.",
            "الظلام حلّ. الأعذار لا تُقبل بعد منتصف الليل.",
            "لم تبدأ بعد؟ المهلة تنتهي عند منتصف الليل."),
        Cat.STREAK to listOf(
            "النظام يعمل. استعد.",
            "سلسلتك الحالية {streak} أيام. حافظ عليها.",
            "كل يوم تختار: أن تكون أقوى، أو أن تبقى كما أنت.")
    )

    val EN: Map<Cat, String> = mapOf(
        Cat.FIRST_EVER to "The System has chosen you. You are no longer ordinary.",
        Cat.SICK to "Your body needs recovery. The System lightened today. Rest well.",
        Cat.PENALTY to "A penalty quest awaits. Escape is not an option.",
        Cat.ABSENCE_LONG to "You were gone {days} days... did you think the System would forget?",
        Cat.ABSENCE_DAY to "A full day away. The quest waits for no one.",
        Cat.LEVEL_UP to "Level {level}. Stronger than yesterday — not strong enough yet.",
        Cat.RANK_UP to "Your rank is now {rank}.",
        Cat.SECRET to "A hidden condition was met today. The System acknowledges you.",
        Cat.COMPLETED to "Daily quest complete. Tomorrow will be harder.",
        Cat.STREAK_MILESTONE to "{streak} days in a row. That is discipline, not luck.",
        Cat.MONARCH to "Welcome back, my King. The shadows await your command.",
        Cat.REST to "Rest day. Recovery is part of strength.",
        Cat.RETURN_MANY to "Back again? The goal is still at {pct}%.",
        Cat.RETURN_AGAIN to "Back again. Here to finish the quest?",
        Cat.IN_PROGRESS_LATE to "{left} left at {pct}%. No room for delay.",
        Cat.IN_PROGRESS to "Daily quest at {pct}%. Finish the rest.",
        Cat.NOT_STARTED_LATE to "Not started and only {left} left. Move.",
        Cat.NOT_STARTED_DAWN to "Dawn. Everyone sleeps — you can get ahead.",
        Cat.NOT_STARTED_MORNING to "New morning, new quest. Not started yet.",
        Cat.NOT_STARTED_NOON to "Midday and not started. Time is merciless.",
        Cat.NOT_STARTED_EVENING to "Evening, and the quest hasn't begun.",
        Cat.NOT_STARTED_NIGHT to "Night. The daily quest hasn't started.",
        Cat.STREAK to "The System is running. Prepare."
    )

    fun totalLines() = POOL.values.sumOf { it.size }

    /** Persisted between opens (by the UI, in SharedPreferences). [recent] = line ids "CAT#i", newest last. */
    data class Memory(val lastSeenMs: Long? = null, val day: Long = -1, val opensToday: Int = 0, val recent: List<String> = emptyList(),
                      val lastLevel: Int? = null, val lastRank: String? = null)

    data class Shown(val id: String, val cat: Cat, val greetingAr: String, val greetingEn: String, val lineAr: String, val lineEn: String)

    /** Picks a line for [c] excluding the recent history (never the last shown one, even if the pool is exhausted). */
    fun pick(c: Ctx, recent: List<String>, rnd: Random = Random.Default): Shown {
        val cat = category(c)
        val lines = POOL.getValue(cat)
        val ids = lines.indices.map { "${cat.name}#$it" }
        var ok = ids.filter { it !in recent }
        if (ok.isEmpty()) ok = ids.filter { it != recent.lastOrNull() }.ifEmpty { ids }
        val id = ok[rnd.nextInt(ok.size)]
        val i = id.substringAfter('#').toInt()
        val (gAr, gEn) = greeting(c, rnd)
        return Shown(id, cat, gAr, gEn, fill(lines[i], c), fill(EN.getValue(cat), c))
    }

    private fun greeting(c: Ctx, rnd: Random): Pair<String, String> {
        val who = if (c.monarch) "أيها الملك" else if (!c.name.isNullOrBlank()) "أيها اللاعب ${c.name}" else "أيها اللاعب"
        val whoEn = if (c.monarch) "my King" else if (!c.name.isNullOrBlank()) "Player ${c.name}" else "Player"
        if (c.firstEver) return "مرحبًا $who." to "Welcome, $whoEn."
        if ((c.awayMs ?: 0) >= 86_400_000L) return "مرحبًا بعودتك $who." to "Welcome back, $whoEn."
        val g = when (timeOfDay(c.hour)) {
            TimeOfDay.DAWN, TimeOfDay.MORNING -> listOf("صباح الخير $who.", "مرحبًا $who.")
            TimeOfDay.NOON -> listOf("مرحبًا $who.", "طاب يومك $who.")
            TimeOfDay.EVENING -> listOf("مساء الخير $who.", "مرحبًا $who.")
            TimeOfDay.NIGHT -> listOf("مرحبًا $who.", "ما زلت مستيقظًا $who؟")
        }
        return g[rnd.nextInt(g.size)] to "Welcome, $whoEn."
    }

    fun fmtLeft(min: Int): String { val m = min.coerceAtLeast(0); val h = m / 60; val r = m % 60
        return when { h == 0 -> "$r دقيقة"; r == 0 -> "$h ساعة"; else -> "$h س و$r د" } }
    fun fmtLeftEn(min: Int): String { val m = min.coerceAtLeast(0); return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m" }

    private fun fill(t: String, c: Ctx) = t.replace("{pct}", "${c.questPct}").replace("{left}", fmtLeft(c.minutesLeft))
        .replace("{days}", "${((c.awayMs ?: 0L) / 86_400_000L)}").replace("{streak}", "${c.streak}").replace("{level}", "${c.level}")
        .replace("{rank}", c.rankUp ?: "").replace("{opens}", "${c.opensToday}")

    /** Next memory after showing [shown] (history trimmed to [HISTORY]). */
    fun remember(m: Memory, shown: Shown, today: Long, nowMs: Long, level: Int, rank: String): Memory =
        m.copy(lastSeenMs = nowMs, day = today, opensToday = if (m.day == today) m.opensToday + 1 else 1,
            recent = (m.recent + shown.id).takeLast(HISTORY), lastLevel = level, lastRank = rank)

    /** Builds the context from game state + memory. [minuteOfDay] local. */
    fun context(s: AppState, m: Memory, today: Long, nowMs: Long, minuteOfDay: Int): Ctx {
        val lvl = Ledger.level(s).level
        val rank = Leveling.rankFor(lvl)
        val goals = s.quests.filter { it.day == today && GoalPlan.isGoal(it) }
        val pct = if (goals.isEmpty()) 0 else (goals.sumOf { (it.progress.toDouble() / it.target.coerceAtLeast(1)).coerceIn(0.0, 1.0) } / goals.size * 100).toInt()
        val qs = when {
            today in s.secretDays -> QuestState.SECRET
            goals.isEmpty() -> QuestState.NONE
            goals.all { it.status == QuestStatus.COMPLETED } -> QuestState.COMPLETED
            goals.any { it.progress > 0 || it.status == QuestStatus.COMPLETED } -> QuestState.IN_PROGRESS
            else -> QuestState.NOT_STARTED
        }
        val sick = s.quests.any { it.day in (today - 1)..today && (it.reason == MissReason.SICK.name || it.reason == MissReason.INJURY.name) } ||
            goals.any { it.lowered }
        val penalty = s.quests.any { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING && it.day <= today }
        val opens = if (m.day == today) m.opensToday + 1 else 1
        return Ctx(
            firstEver = m.lastSeenMs == null && s.quests.isEmpty(), hour = minuteOfDay / 60, opensToday = opens,
            awayMs = m.lastSeenMs?.let { (nowMs - it).coerceAtLeast(0) }, streak = Stats.streak(s, today), quest = qs, questPct = pct,
            minutesLeft = s.settings.deadlineMinutes - minuteOfDay, penaltyPending = penalty,
            restDay = Penalty.isRestDay(s, today), sick = sick,
            levelUpTo = m.lastLevel?.let { if (lvl > it) lvl else null },
            rankUp = m.lastRank?.let { if (it != rank.name) (rank.ar) else null },
            level = lvl, monarch = lvl >= 100 && s.monarchAwakened,
            name = s.profile?.name?.takeIf { it.isNotBlank() })
    }
}
