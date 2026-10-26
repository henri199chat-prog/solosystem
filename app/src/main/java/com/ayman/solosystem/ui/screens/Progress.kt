package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.training.ExerciseLibrary
import com.ayman.solosystem.domain.training.Progression
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import java.util.Locale

@Composable
fun ProgressScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    val pal = LocalPalette.current
    val weeks = Stats.weeks(s, today, 8)
    Screen(tr("التقدم", "Progress"), nav) {
        val li = Ledger.level(s)
        SysPanel(Modifier.fillMaxWidth(), contentKey = li.level) {
            TitleBox(tr("التقدم", "PROGRESS"))
            Spacer(Modifier.height(8.dp))
            KeyValue(tr("المستوى", "Level"), "\u2066${li.level} / ${Leveling.MAX_LEVEL}\u2069")
            KeyValue(tr("مجموع الخبرة", "Total XP"), "${li.totalXp}")
            val toMax = (Leveling.totalXpFor(Leveling.MAX_LEVEL) - li.totalXp).coerceAtLeast(0)
            KeyValue(tr("الخبرة حتى 300", "XP to 300"), "$toMax")
            KeyValue(tr("الحصص المكتملة", "Sessions done"), "${s.sessions.count { it.finished && !it.stoppedForSafety }}")
            KeyValue(tr("الأرقام القياسية", "Records"), "${s.prs.size}")
            val (d, p) = Stats.weeklyCompletion(s, today)
            KeyValue(tr("حصص هذا الأسبوع", "Sessions this week"), "$d/$p")
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("الخبرة الأسبوعية (8 أسابيع)", "Weekly XP (8 weeks)"))
            PremiumBarChart(weeks.map { it.xp.toDouble() }, weeks.map { fmtShort(it.start) }, Modifier.fillMaxWidth().height(170.dp), pal.secondary, slots = 8,
                contentDesc = tr("الخبرة الأسبوعية", "Weekly XP"))
            Spacer(Modifier.height(8.dp))
            SectionHeader(tr("نسبة إكمال المهام", "Quest completion"))
            PremiumBarChart(weeks.map { w -> val t = w.questsDone + w.questsMissed; if (t == 0) 0.0 else w.questsDone * 100.0 / t }, weeks.map { fmtShort(it.start) },
                Modifier.fillMaxWidth().height(150.dp), SystemTheme.c(0xFF34E3C4, 0xFFC084FC), slots = 8, maxValue = 100.0, fmt = { "${it.toInt()}%" },
                contentDesc = tr("نسبة إكمال المهام", "Quest completion"))
            Label(tr("المهام المعذورة لا تُحتسب ضدك.", "Excused quests never count against you."), size = 11)
        }
        // per-exercise
        val logged = s.sessions.filter { it.finished }.flatMap { w -> w.entries.filter { e -> e.sets.any { it.done } }.map { it.exerciseId } }.distinct()
        var sel by remember { mutableStateOf<String?>(null) }
        val cur = sel?.takeIf { it in logged } ?: logged.firstOrNull()
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("التمارين", "Exercises"))
            if (logged.isEmpty()) EmptyState(tr("لا توجد سجلات بعد", "No records yet"), tr("أكمل حصة لترى الرسوم البيانية لكل تمرين.", "Finish a session to see per-exercise charts."), icon = Ico.CHART)
            else {
                FlowRowCompat(Modifier.fillMaxWidth()) { logged.forEach { id -> Chip(ExerciseLibrary.get(id)?.name(ar) ?: id, id == cur) { sel = id } } }
                cur?.let { id ->
                    val h = Progression.history(s.sessions, id)
                    val useE = h.any { it.bestE1rm != null }
                    LineChart(h.map { if (useE) (it.bestE1rm ?: 0.0) * (if (s.settings.units == Units.LB) LB else 1.0) else it.bestReps.toDouble() }, Modifier.fillMaxWidth().height(140.dp), pal.secondary)
                    Label(if (useE) tr("1RM تقديري (", "Estimated 1RM (") + (if (s.settings.units == Units.LB) UnitFmt.lb(LocalArabic.current) else UnitFmt.kg(LocalArabic.current)) + ")" else tr("أفضل تكرارات/ثوانٍ في مجموعة", "Best reps/seconds in a set"), size = 11)
                    LineChart(h.map { it.totalReps.toDouble() }, Modifier.fillMaxWidth().height(90.dp), Sys.green, bars = true)
                    Label(tr("مجموع التكرارات في كل حصة", "Total reps per session"), size = 11)
                    SysButton(tr("تفاصيل التمرين", "Exercise details"), small = true) { nav.go("exercise/$id") }
                }
            }
        }
        WeightPanel(s, repo, today)
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("الأرقام القياسية", "Personal records"))
            if (s.prs.isEmpty()) EmptyState(tr("لا توجد أرقام قياسية بعد", "No records yet"), tr("كل رقم قياسي تحطمه سيُسجَّل هنا.", "Every record you break will be logged here."), icon = Ico.BADGE)
            s.prs.sortedByDescending { it.day }.take(12).forEach { pr ->
                Row(Modifier.fillMaxWidth().clickable { nav.go("exercise/${pr.exerciseId}") }.padding(vertical = 3.dp)) {
                    Label(ExerciseLibrary.get(pr.exerciseId)?.name(ar) ?: pr.exerciseId, Modifier.weight(1f), Sys.text, 13)
                    Label(when (pr.kind) { PrKind.E1RM -> "1RM " + fmtLoad(pr.value, s.settings.units); PrKind.REPS -> "${pr.reps} " + tr("تكرار", "reps"); PrKind.SECONDS -> "${pr.reps} " + tr("ث", "s") }, color = Sys.gold, size = 13)
                    Spacer(Modifier.width(8.dp)); Label(fmtDay(pr.day, ar), size = 11)
                }
            }
        }
    }
}

private fun fmtShort(day: Long) = java.time.LocalDate.ofEpochDay(day).let { "${it.dayOfMonth}/${it.monthValue}" }

@Composable
private fun WeightPanel(s: AppState, repo: Repository, today: Long) {
    val lb = s.settings.units == Units.LB
    var txt by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val badMsg = tr("أدخل رقمًا صحيحًا", "Enter a valid number")
    SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
        SectionHeader(tr("وزن الجسم", "Body weight"))
        val list = s.bodyWeights.sortedBy { it.day }.takeLast(30)
        if (list.size >= 2) LineChart(list.map { if (lb) it.kg * LB else it.kg }, Modifier.fillMaxWidth().height(120.dp), Sys.amber)
        list.lastOrNull()?.let { KeyValue(tr("آخر قياس", "Latest"), fmtLoad(it.kg, s.settings.units) + " • " + fmtDay(it.day, LocalArabic.current)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextIn(txt, { txt = it; err = null }, tr("الوزن اليوم", "Today's weight") + if (lb) " (lb)" else " (kg)", Modifier.weight(1f), number = true, error = err)
            Spacer(Modifier.width(8.dp))
            SysButton(tr("حفظ", "Save"), small = true) {
                val v = txt.replace(',', '.').toDoubleOrNull()?.let { if (lb) it / LB else it }
                if (v == null || v !in 25.0..350.0) err = badMsg else { repo.set { GameEngine.addWeight(it, v, today) }; txt = "" }
            }
        }
        Label(tr("يُستخدم للرسم البياني ولتعديل سعرات ترياق الشفاء (قياس أسبوعي).", "Used for the chart and the Healing Elixir calorie adjustment (weekly)."), size = 11)
    }
}

@Composable
fun MoreScreen(nav: Nav) {
    Screen(tr("المزيد", "More"), null) {
        val items = listOf(
            Triple(Ico.BADGE, tr("نقابة الصيادين", "Hunter Guild"), "guild"), Triple(Ico.EMBLEM, tr("جيش الظلال", "Shadow army"), "shadows"),
            Triple(Ico.PAIN, tr("زعيم الأسبوع", "Weekly Boss"), "boss"), Triple(Ico.TIMER, tr("بوابات GPS", "GPS Dungeon"), "dungeon"),
            Triple(Ico.FRAME, tr("التحوّل (خاص)", "Transformation (private)"), "transform"),
            Triple(Ico.SKILLS, tr("المهارات", "Skills"), "skills"), Triple(Ico.INVENTORY, tr("المخزن والمتجر", "Inventory & shop"), "inventory"),
            Triple(Ico.HISTORY, tr("العقوبات والسجل", "Penalties & history"), "history"), Triple(Ico.CALENDAR, tr("التقويم", "Calendar"), "calendar"),
            Triple(Ico.BADGE, tr("رتب التدريب", "Training tiers"), "tiers"), Triple(Ico.STATUS, tr("الملف الشخصي", "Profile"), "profile"),
            Triple(Ico.POTION, tr("ترياق الشفاء (التغذية)", "Healing Elixir (nutrition)"), "nutrition"), Triple(Ico.SETTINGS, tr("الإعدادات", "Settings"), "settings")
        )
        items.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (ico, t, r) ->
                    Box(Modifier.weight(1f).heightIn(min = 96.dp).thinBox().clickable { nav.go(r) }.padding(12.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            SysIcon(ico, LocalPalette.current.secondary, size = 30.dp)
                            Spacer(Modifier.height(6.dp))
                            GlowText(t, body(14.sp, FontWeight.Bold), align = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}
