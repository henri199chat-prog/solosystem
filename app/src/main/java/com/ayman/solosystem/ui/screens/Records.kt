package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.DayOutcome
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.training.Tiers
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HistoryScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    var filter by remember { mutableIntStateOf(0) }
    Screen(tr("العقوبات والسجل", "Penalties & history"), nav) {
        val pens = s.quests.filter { it.type == QuestType.PENALTY }.sortedByDescending { it.day }
        SysPanel(Modifier.fillMaxWidth(), accent = Sys.red, contentKey = s.penaltyTier) {
            TitleBox(tr("منطقة العقاب", "PENALTY ZONE"), accent = Sys.red)
            Spacer(Modifier.height(6.dp))
            TierIndicator(s.penaltyTier)
            Label(tr("المستوى الحالي للعقاب: ", "Current penalty tier: ") + s.penaltyTier + " • " + tr("أيام نظيفة: ", "clean days: ") + s.cleanDays + " • " + (if (ar) s.settings.penaltyMode.ar else s.settings.penaltyMode.en), Modifier.fillMaxWidth(), Sys.text, 13, TextAlign.Center)
            Label(tr("العقاب تمارين آمنة من قدرتك المسجلة فقط، ولا يُصدر في أيام الراحة أو التخفيف، والأعذار لا تصعّده. لا حرمان من طعام أو ماء أو نوم أو مال، ولا تُحذف خبرة مكتسبة.", "Penalties are safe exercises from your logged capacity only, never on rest or deload days, and excuses never escalate them. No food, water, sleep or money penalties; earned XP is never removed."), size = 12)
            val done = pens.count { it.status == QuestStatus.COMPLETED }; val stopped = pens.count { it.status == QuestStatus.EXCUSED }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Stat(tr("صدرت", "Issued"), "${pens.size}", Sys.red); Stat(tr("نُجّيت", "Cleared"), "$done", Sys.green); Stat(tr("أوقفت للألم", "Pain stops"), "$stopped", Sys.amber)
            }
            pens.take(10).forEach { q ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Label(fmtDay(q.day, ar), Modifier.width(100.dp), Sys.text, 13)
                    Label(q.title.get(ar) + (if (q.penaltyTier > 0) " • T${q.penaltyTier}" else ""), Modifier.weight(1f), size = 12)
                    Label(statusText(q.status), color = statusColor(q.status), size = 12)
                }
            }
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false, contentKey = filter) {
            SectionHeader(tr("السجل", "Log"))
            val groups = listOf(tr("الكل", "All"), tr("خبرة", "XP"), tr("مهام", "Quests"), tr("تدريب", "Training"), tr("مكافآت", "Rewards"))
            Tabs(groups, filter) { filter = it }
            val tab = com.ayman.solosystem.domain.progression.HistoryFilter.Tab.entries[filter]
            val list = com.ayman.solosystem.domain.progression.HistoryFilter.filter(s.history.asReversed(), tab).take(150)
            if (list.isEmpty()) EmptyState(tr("لا توجد سجلات بعد", "No records yet"), when (tab) {
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.XP -> tr("لم تُكتسب أي خبرة بعد. أكمل مهمة ليبدأ السجل.", "No XP earned yet. Complete a quest to start the log.")
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.QUESTS -> tr("لا مهام مسجّلة بعد. النظام ينتظر أول إنجاز لك.", "No quests logged yet. The System awaits your first clear.")
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.TRAINING -> tr("لا حصص تدريب مسجّلة بعد. ابدأ حصة من تبويب التدريب.", "No training logged yet. Start a session from the Training tab.")
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.REWARDS -> tr("لا مكافآت بعد. الشارات والصناديق تُكتسب بالانضباط.", "No rewards yet. Badges and boxes are earned through discipline.")
                else -> tr("السجل فارغ. كل ما تفعله من الآن سيُسجَّل.", "The log is empty. Everything you do from now on is recorded.")
            }, icon = when (tab) { com.ayman.solosystem.domain.progression.HistoryFilter.Tab.TRAINING -> com.ayman.solosystem.ui.comp.Ico.TRAINING
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.REWARDS -> com.ayman.solosystem.ui.comp.Ico.BOX
                com.ayman.solosystem.domain.progression.HistoryFilter.Tab.QUESTS -> com.ayman.solosystem.ui.comp.Ico.QUESTS
                else -> com.ayman.solosystem.ui.comp.Ico.HISTORY })
            list.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Label(fmtDay(h.day, ar), Modifier.width(96.dp), size = 12)
                    Label((if (h.kind == HistoryKind.SECRET) "◆ " else "") + h.text.get(ar), Modifier.weight(1f), if (h.kind == HistoryKind.SECRET) com.ayman.solosystem.ui.SecretPink else Sys.text, 13)
                    if (h.xpDelta != 0) BasicText((if (h.xpDelta > 0) "+" else "") + h.xpDelta, style = tech(13.sp, if (h.xpDelta > 0) Sys.green else Sys.amber))
                }
            }
        }
    }
}

@Composable private fun Stat(k: String, v: String, c: Color) { Column(horizontalAlignment = Alignment.CenterHorizontally) { GlowText(v, tech(20.sp), glow = c); Label(k, size = 11) } }

@Composable fun statusText(st: QuestStatus) = when (st) {
    QuestStatus.COMPLETED -> tr("مكتملة", "Done"); QuestStatus.MISSED -> tr("فائتة", "Missed"); QuestStatus.EXCUSED -> tr("معذورة", "Excused")
    QuestStatus.POSTPONED -> tr("مؤجلة", "Postponed"); QuestStatus.PENDING -> tr("قيد الانتظار", "Pending"); QuestStatus.DECLINED -> tr("مرفوضة", "Declined")
    QuestStatus.OFFERED -> tr("معروضة", "Offered")
}
fun statusColor(st: QuestStatus) = when (st) { QuestStatus.COMPLETED -> Sys.green; QuestStatus.MISSED -> Sys.red; QuestStatus.PENDING -> Sys.amber; else -> Sys.dim }

@Composable
fun CalendarScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    var month by remember { mutableStateOf(YearMonth.from(LocalDate.ofEpochDay(today))) }
    var sel by remember { mutableStateOf<Long?>(today) }
    val loc = if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH
    Screen(tr("التقويم", "Calendar"), nav) {
        SysPanel(Modifier.fillMaxWidth(), contentKey = month) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SysButton("‹", small = true) { month = month.minusMonths(1) }
                GlowText(month.month.getDisplayName(TextStyle.FULL, loc) + " " + month.year, body(18.sp, FontWeight.Bold), Modifier.weight(1f), align = TextAlign.Center)
                SysButton("›", small = true, enabled = month < YearMonth.from(LocalDate.ofEpochDay(today)).plusMonths(1)) { month = month.plusMonths(1) }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth()) { (1..7).forEach { d -> Label(java.time.DayOfWeek.of(d).getDisplayName(TextStyle.SHORT, loc), Modifier.weight(1f), size = 11, align = TextAlign.Center) } }
            val first = month.atDay(1); val offset = first.dayOfWeek.value - 1
            val cells = offset + month.lengthOfMonth(); val rows = (cells + 6) / 7
            val pal = LocalPalette.current
            for (r in 0 until rows) Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - offset
                    if (idx < 0 || idx >= month.lengthOfMonth()) Spacer(Modifier.weight(1f).height(44.dp))
                    else {
                        val day = month.atDay(idx + 1).toEpochDay()
                        val out = if (day > today || s.profile == null) DayOutcome.NONE else Stats.outcome(s, day, today)
                        val col = when (out) { DayOutcome.SUCCESS -> Sys.green; DayOutcome.FAIL -> if (day in s.shieldedDays) Sys.amber else Sys.red; DayOutcome.BRIDGE -> Sys.amber; DayOutcome.PENDING -> pal.secondary; else -> Sys.dim.copy(alpha = 0.4f) }
                        val isSel = sel == day
                        val secret = day in s.secretDays
                        Box(Modifier.weight(1f).height(44.dp).padding(2.dp).drawBehind {
                            drawRect(col.copy(alpha = if (out == DayOutcome.NONE) 0.06f else 0.22f))
                            drawRect(if (isSel) Color.White else col.copy(alpha = 0.7f), style = Stroke(if (isSel || day == today) 2.dp.toPx() else 1f))
                            if (secret) { drawRect(com.ayman.solosystem.ui.SecretViolet.copy(alpha = 0.35f)); drawRect(com.ayman.solosystem.ui.SecretPink, style = Stroke(2.dp.toPx())) }
                        }.clickable { sel = day }, contentAlignment = Alignment.Center) {
                            BasicText("${idx + 1}", style = tech(15.sp, if (day == today) Color.White else Sys.text))
                        }
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRowCompat(Modifier.fillMaxWidth()) {
                Legend(Sys.green, tr("ناجح", "Success")); Legend(Sys.red, tr("فشل", "Failed")); Legend(Sys.amber, tr("سماح/درع", "Grace/shield")); Legend(LocalPalette.current.secondary, tr("اليوم", "Today")); Legend(com.ayman.solosystem.ui.SecretPink, tr("مهمة سرّية", "Secret quest"))
            }
        }
        sel?.let { d ->
            val qs = s.quests.filter { it.day == d }
            SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false, contentKey = d) {
                SectionHeader(fmtDay(d, ar) + " • " + Stats.dailyPercent(s, d) + "%")
                if (d in s.secretDays) Label(tr("◆ مهمة سرّية", "◆ SECRET QUEST"), color = com.ayman.solosystem.ui.SecretPink, size = 14, w = FontWeight.Bold)
                s.plan?.let { val dp = com.ayman.solosystem.domain.training.PlanGenerator.dayPlan(it, d); Label(tr("الخطة: ", "Plan: ") + (if (ar) dp.type.ar else dp.type.en), size = 13) }
                if (qs.isEmpty()) Label(if (d > today) tr("سيتم إنشاء المهام في يومها.", "Quests are generated on the day.") else tr("لا مهام مسجلة.", "No quests recorded."))
                qs.forEach { q -> Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) { Label(q.title.get(ar), Modifier.weight(1f), Sys.text, 13); Label(statusText(q.status), color = statusColor(q.status), size = 12) } }
                s.sessions.filter { it.day == d && it.finished }.forEach { w -> Label("• " + tr("حصة: ", "Session: ") + (if (ar) w.dayType.ar else w.dayType.en) + " (${w.entries.sumOf { e -> e.sets.count { it.done } }} " + tr("مجموعة)", "sets)"), color = Sys.green, size = 12) }
                s.checkIns.firstOrNull { it.day == d }?.let { Label(tr("الجاهزية: ", "Readiness: ") + com.ayman.solosystem.domain.training.Readiness.score(it), size = 12) }
            }
        }
    }
}

@Composable private fun Legend(c: Color, t: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 10.dp)) { Box(Modifier.size(10.dp).drawBehind { drawRect(c) }); Spacer(Modifier.width(4.dp)); Label(t, size = 11) }
}

@Composable
fun TiersScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    val p = s.profile ?: return
    val level = Ledger.level(s).level
    val bests = remember(s) { Tiers.bests(s, today) }
    Screen(tr("رتب التدريب", "Training tiers"), nav) {
        SysPanel(Modifier.fillMaxWidth(), contentKey = s.activeTier) {
            TitleBox(tr("الرتبة", "RANK"))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) { RankBadge(Tiers.all[s.activeTier].label.take(1), size = 56.dp) }
            GlowText(tr("رتبة التدريب النشطة: ", "Active training tier: ") + Tiers.all[s.activeTier].let { if (ar) it.ar else it.label }, body(16.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center)
            Label(tr("المستوى يفتح الرتبة فقط. التفعيل يحتاج اجتياز معايير أداء حقيقية — حتى لا يزيد الحمل بسبب الخبرة وحدها.", "Level only unlocks a tier. Activation needs real benchmarks — so load never rises from XP alone."), size = 12, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        Tiers.all.forEach { t ->
            val unlocked = level >= t.unlockLevel
            val active = t.index == s.activeTier
            val passed = t.index <= s.activeTier
            SysPanel(Modifier.fillMaxWidth(), accent = if (active) Sys.green else if (passed) LocalPalette.current.secondary else Sys.dim, brackets = false, flares = false, padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RankBadge(t.label.take(1), size = 34.dp, color = if (unlocked) LocalPalette.current.secondary else Sys.dim)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        BasicText(if (ar) t.ar else t.label, style = body(16.sp, FontWeight.Bold))
                        Label(tr("يُفتح عند المستوى ", "Unlocks at level ") + t.unlockLevel + if (active) tr(" • نشطة", " • active") else if (passed) tr(" • مجتازة", " • passed") else if (!unlocked) tr(" • مقفلة", " • locked") else "", size = 12, color = if (active) Sys.green else Sys.dim)
                    }
                }
                if (t.index == s.activeTier + 1 || (t.index > 0 && !passed && unlocked && t.index == s.activeTier + 1)) {
                    Tiers.checks(t, bests, p).forEach { c ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Label(c.name.get(ar), Modifier.weight(1f), Sys.text, 13)
                            Label(if (c.skipped) tr("متجاوز (قيد)", "skipped (limitation)") else "${c.current}/${c.required}", color = if (c.met) Sys.green else Sys.amber, size = 13)
                        }
                    }
                }
            }
        }
        AssessmentForm(s, repo, today)
    }
}

@Composable
private fun AssessmentForm(s: AppState, repo: Repository, today: Long) {
    var push by remember { mutableStateOf("") }; var squat by remember { mutableStateOf("") }
    var plank by remember { mutableStateOf("") }; var pull by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val bad = tr("أدخل قيمة واحدة على الأقل (0–500)", "Enter at least one value (0–500)")
    SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
        SectionHeader(tr("اختبار الرتبة", "Rank assessment"))
        Label(tr("بعد إحماء جيد، سجّل أفضل مجموعة واحدة بأداء صحيح. اترك الحقل فارغًا إن لم تختبره. لا تختبر وأنت متألم.", "After a proper warm-up, log your best single set with good form. Leave blank if not tested. Never test while in pain."), size = 12)
        TextIn(push, { push = it.filter(Char::isDigit).take(3); err = null }, tr("ضغط (تكرارات)", "Push-ups (reps)"), Modifier.fillMaxWidth(), number = true)
        TextIn(squat, { squat = it.filter(Char::isDigit).take(3); err = null }, tr("قرفصاء (تكرارات)", "Squats (reps)"), Modifier.fillMaxWidth(), number = true)
        TextIn(plank, { plank = it.filter(Char::isDigit).take(3); err = null }, tr("بلانك (ثوانٍ)", "Plank (seconds)"), Modifier.fillMaxWidth(), number = true)
        TextIn(pull, { pull = it.filter(Char::isDigit).take(3); err = null }, tr("عقلة (تكرارات)", "Pull-ups (reps)"), Modifier.fillMaxWidth(), number = true, error = err)
        SysButton(tr("سجّل الاختبار", "Record assessment"), Modifier.fillMaxWidth(), small = true) {
            val v = listOf(push, squat, plank, pull).map { it.toIntOrNull()?.takeIf { n -> n in 0..500 } }
            if (v.all { it == null }) err = bad
            else { repo.apply { st -> GameEngine.recordAssessment(st, Assessment(today, v[0], v[1], v[2], v[3]), today, repo.now()) }; push = ""; squat = ""; plank = ""; pull = "" }
        }
        s.assessments.takeLast(5).reversed().forEach { a -> Label(fmtDay(a.day, LocalArabic.current) + " • " + listOfNotNull(a.pushReps?.let { "P $it" }, a.squatReps?.let { "S $it" }, a.plankSec?.let { "PL ${it}s" }, a.pullReps?.let { "PU $it" }).joinToString(" • "), size = 12) }
    }
}
