package com.ayman.solosystem.domain.shadows

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Outcome
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.DayOutcome
import com.ayman.solosystem.domain.progression.Stats
import java.time.LocalDate

/**
 * Shadow Army: a 30-day streak on one habit → ARISE extraction of a named Shadow bound to that habit.
 * Perks: +5 % XP on its GOAL row; once a month the army can guard one failed day (penalty/streak shield) and freeze today (streak freeze).
 * If the habit's streak breaks the Shadow falls asleep (perks off) until a new 7-day streak revives it. Nothing is ever deleted.
 */
object Shadows {
    const val EXTRACT_STREAK = 30
    const val REVIVE_STREAK = 7
    const val XP_PCT = 5
    const val MAX_EXTRACTIONS = 3

    data class Def(val source: String, val name: Txt, val title: Txt)
    val defs = listOf(
        Def("push", Txt("إيغريس", "Igris"), Txt("فارس الظل — الضغط", "Shadow Knight — Push-ups")),
        Def("squat", Txt("تانك", "Tank"), Txt("الدب الحديدي — السكوات", "Iron Bear — Squats")),
        Def("situp", Txt("آيرون", "Iron"), Txt("الدرع الأسود — البطن", "Black Shield — Sit-ups")),
        Def("run", Txt("بيرو", "Beru"), Txt("ملك النمل — الجري", "Ant King — Running")),
        Def("checkin", Txt("توسك", "Tusk"), Txt("الساحر الأعظم — سجل الجاهزية", "Grand Mage — Readiness log")),
    )
    fun def(source: String) = defs.first { it.source == source }

    /** Day state for one habit: true = done, false = broken, null = neutral (no quest that day, shielded/redeemed, excused, or today still open). */
    private fun dayDone(s: AppState, src: String, day: Long, today: Long, idx: Map<Long, Quest>?): Boolean? {
        if (src == "checkin") {
            if (s.checkIns.any { it.day == day }) return true
            if (day >= today || day in s.shieldedDays || day in s.redeemedDays) return null
            return false
        }
        val q = idx?.get(day) ?: return null
        if (q.status == QuestStatus.COMPLETED || q.progress >= q.target) return true
        if (day >= today || q.status == QuestStatus.EXCUSED || day in s.shieldedDays || day in s.redeemedDays) return null
        return false
    }

    private fun index(s: AppState, src: String): Map<Long, Quest>? =
        if (src == "checkin") null else s.quests.filter { it.templateId == "goal:$src" && it.type == QuestType.DAILY }.associateBy { it.day }

    private fun firstDay(s: AppState, src: String, idx: Map<Long, Quest>?): Long? =
        if (src == "checkin") s.checkIns.minOfOrNull { it.day }?.let { minOf(it, s.profile?.createdDay ?: it) } else idx?.keys?.minOrNull()

    /** Current streak (consecutive done days, neutral days skipped) ending today (if done) or before. [since] limits the walk (revive counting). */
    fun streak(s: AppState, src: String, today: Long, since: Long? = null): Int {
        val idx = index(s, src)
        val first = maxOf(firstDay(s, src, idx) ?: return 0, since ?: Long.MIN_VALUE)
        var n = 0; var d = today
        while (d >= first && today - d < 800) {
            when (dayDone(s, src, d, today, idx)) { true -> n++; false -> return n; null -> {} }
            d--
        }
        return n
    }

    fun pending(s: AppState, today: Long): List<Def> =
        defs.filter { d -> s.shadows.army.none { it.source == d.source } && streak(s, d.source, today) >= EXTRACT_STREAK }

    fun extractionsPossible(s: AppState, today: Long) = pending(s, today).size.coerceAtMost(MAX_EXTRACTIONS)

    fun awake(s: AppState) = s.shadows.army.filter { !it.asleep }

    /** +XP % for a quest bound to an awake shadow. */
    fun xpPct(s: AppState, q: Quest): Int {
        if (!q.templateId.startsWith("goal:")) return 0
        val src = q.templateId.removePrefix("goal:")
        return if (awake(s).any { it.source == src }) XP_PCT else 0
    }

    /** Called from GameEngine.post(): sleep/revive + one-time "extraction available" notice. */
    fun sync(s0: AppState, today: Long, now: Long): Pair<AppState, List<UiEffect>> {
        var s = s0
        val eff = mutableListOf<UiEffect>()
        val army = s.shadows.army.map { sh ->
            if (!sh.asleep) {
                val idx = index(s, sh.source)
                // a break after the extraction day (yesterday or before) = sleep
                val brokeRecently = (1..2).any { k -> val d = today - k; d > sh.extractedDay && dayDone(s, sh.source, d, today, idx) == false }
                if (brokeRecently && streak(s, sh.source, today) == 0) {
                    s = GameEngine.hist(s, now, today, HistoryKind.SHADOW, Txt("الظل ${def(sh.source).name.ar} نائم — انكسرت السلسلة", "Shadow ${def(sh.source).name.en} fell asleep — streak broken"))
                    eff += UiEffect.Toast(Txt("[SYSTEM] الظل ${def(sh.source).name.ar} دخل في سبات. ٧ أيام متتالية توقظه.", "[SYSTEM] Shadow ${def(sh.source).name.en} fell asleep. A 7-day streak revives it."))
                    sh.copy(asleep = true, sleptDay = today)
                } else sh
            } else if (streak(s, sh.source, today, since = sh.sleptDay) >= REVIVE_STREAK) {
                s = GameEngine.hist(s, now, today, HistoryKind.SHADOW, Txt("استيقظ الظل ${def(sh.source).name.ar}", "Shadow ${def(sh.source).name.en} revived"))
                eff += UiEffect.Toast(Txt("[SYSTEM] استيقظ الظل ${def(sh.source).name.ar}!", "[SYSTEM] Shadow ${def(sh.source).name.en} has risen again!"))
                sh.copy(asleep = false, sleptDay = null, revived = sh.revived + 1)
            } else sh
        }
        s = s.copy(shadows = s.shadows.copy(army = army))
        val newOffers = pending(s, today).filter { it.source !in s.shadows.offered }
        if (newOffers.isNotEmpty()) {
            s = s.copy(shadows = s.shadows.copy(offered = s.shadows.offered + newOffers.map { it.source }))
            eff += UiEffect.Toast(Txt("[SYSTEM] ظلّ جاهز للاستخراج. افتح جيش الظلال وقل: انهض!", "[SYSTEM] A shadow can be extracted. Open the Shadow Army and say: ARISE!"))
        }
        return s to eff
    }

    fun arise(s0: AppState, source: String, today: Long, now: Long): Outcome {
        if (pending(s0, today).none { it.source == source }) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يمكن الاستخراج: تحتاج سلسلة $EXTRACT_STREAK يومًا.", "[SYSTEM] Extraction failed: a $EXTRACT_STREAK-day streak is required."), blocked = true)))
        val d = def(source)
        var s = s0.copy(shadows = s0.shadows.copy(army = s0.shadows.army + Shadow("shadow:$source", source, today)))
        s = GameEngine.hist(s, now, today, HistoryKind.SHADOW, Txt("انهض! تم استخراج الظل ${d.name.ar}", "ARISE! Extracted shadow ${d.name.en}"))
        return Outcome(s)
    }

    fun month(day: Long): Int = LocalDate.ofEpochDay(day).let { it.year * 12 + it.monthValue - 1 }
    fun guardReady(s: AppState, today: Long) = awake(s).isNotEmpty() && s.shadows.guardMonth != month(today)
    fun freezeReady(s: AppState, today: Long) = awake(s).isNotEmpty() && s.shadows.freezeMonth != month(today)

    /** Shadow Guard (1/month): protects the latest failed day of the last 7 (it becomes a bridge day → no streak loss / penalty count). */
    fun guard(s0: AppState, today: Long, now: Long): Outcome {
        if (!guardReady(s0, today)) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] حارس الظل مستعمل هذا الشهر.", "[SYSTEM] Shadow Guard already used this month."), blocked = true)))
        val day = (today - 7 until today).lastOrNull { Stats.outcome(s0, it, today) == DayOutcome.FAIL && it !in s0.shieldedDays }
            ?: return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يوجد يوم فاشل يحتاج إلى حماية.", "[SYSTEM] No failed day needs guarding."), blocked = true)))
        val s = s0.copy(shieldedDays = s0.shieldedDays + day, shadows = s0.shadows.copy(guardMonth = month(today)))
        return GameEngine.post(s0, GameEngine.hist(s, now, today, HistoryKind.SHADOW, Txt("حارس الظل حمى يومًا فاشلًا", "Shadow Guard protected a failed day")), today, now,
            listOf(UiEffect.Toast(Txt("[SYSTEM] جيش الظلال يحمي يومك الفاشل.", "[SYSTEM] Your Shadow Army guards the failed day."))))
    }

    /** Streak Freeze (1/month): today becomes a protected rest day — today's pending daily/GOAL quests are excused, no loss. */
    fun freeze(s0: AppState, today: Long, now: Long): Outcome {
        if (!freezeReady(s0, today)) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] تجميد السلسلة مستعمل هذا الشهر.", "[SYSTEM] Streak Freeze already used this month."), blocked = true)))
        if (today in s0.shieldedDays) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] اليوم محمي بالفعل.", "[SYSTEM] Today is already protected."), blocked = true)))
        val ids = s0.quests.filter { it.day == today && it.status == QuestStatus.PENDING && it.type == QuestType.DAILY }.map { it.id }.toSet()
        val s = s0.copy(quests = s0.quests.map { if (it.id in ids) it.copy(status = QuestStatus.EXCUSED, reason = "SHADOW_FREEZE", reasonReviewed = true) else it },
            bonus = s0.bonus.filterNot { b -> ids.any { b.key == "loss:$it" } }, shieldedDays = s0.shieldedDays + today, shadows = s0.shadows.copy(freezeMonth = month(today)))
        return GameEngine.post(s0, GameEngine.hist(s, now, today, HistoryKind.SHADOW, Txt("تجميد السلسلة بقوة الظلال", "Streak frozen by the Shadow Army")), today, now,
            listOf(UiEffect.Toast(Txt("[SYSTEM] تم تجميد السلسلة. اليوم يوم راحة محمي.", "[SYSTEM] Streak frozen. Today is a protected rest day."))))
    }
}
