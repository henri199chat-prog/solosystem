package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.training.*
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.animation.core.animateFloat
import java.util.Locale
import kotlin.math.roundToInt

const val LB = 2.2046226

@androidx.compose.runtime.Composable
fun fmtLoad(kg: Double?, units: Units): String = fmtLoadA(kg, units, com.ayman.solosystem.ui.theme.LocalArabic.current)

/** Load with bidi-isolated number and Arabic unit (كغ / رطل) — "⁦62.5⁩ كغ". */
fun fmtLoadA(kg: Double?, units: Units, ar: Boolean): String {
    if (kg == null) return "—"
    val v = if (units == Units.LB) kg * LB else kg
    val r = (v * 10).roundToInt() / 10.0
    return com.ayman.solosystem.ui.UnitFmt.num(if (r % 1.0 == 0.0) r.toInt().toString() else String.format(Locale.US, "%.1f", r), if (units == Units.LB) com.ayman.solosystem.ui.UnitFmt.lb(ar) else com.ayman.solosystem.ui.UnitFmt.kg(ar))
}
/** One load step: 2.5 kg or 5 lb (stored in kg). */
fun loadStep(units: Units) = if (units == Units.LB) 5 / LB else 2.5

/** Process-wide rest timer so it survives navigating between tabs. */
object RestTimer {
    var endMs by mutableLongStateOf(0L)
    var totalSec by mutableIntStateOf(0)
    fun start(sec: Int) { totalSec = sec; endMs = System.currentTimeMillis() + sec * 1000L }
    fun stop() { endMs = 0L }
}

@Composable
fun TrainingScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    val plan = s.plan
    val active = s.sessions.firstOrNull { !it.finished }
    Screen(tr("التدريب", "Training"), nav) {
        if (plan == null) { Label(tr("لا توجد خطة.", "No plan.")); return@Screen }
        if (active != null) { ActiveSession(s, active, repo, nav, today); return@Screen }
        val dp = PlanGenerator.dayPlan(plan, today)
        val deload = PlanGenerator.isDeloadWeek(plan, today) || Readiness.deloadRecommended(s.checkIns, today)
        val checkIn = s.checkIns.lastOrNull { it.day == today }
        val adv = Readiness.advice(checkIn)
        val todayType = GameEngine.sessionTypeFor(s, today)
        val doneToday = s.sessions.any { it.day == today && it.finished && !it.stoppedForSafety }
        SysPanel(Modifier.fillMaxWidth(), contentKey = todayType) {
            TitleBox(tr("حصة اليوم", "TODAY'S SESSION"))
            Spacer(Modifier.height(8.dp))
            GlowText(todayType?.let { if (ar) it.ar else it.en } ?: tr("يوم تعافٍ", "Recovery day"), body(20.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center)
            Label(tr("الأسبوع ", "Week ") + PlanGenerator.weekIndex(plan, today) + " • " + PlanGenerator.phase(plan, today, Readiness.deloadRecommended(s.checkIns, today)).get(ar), Modifier.fillMaxWidth(), align = TextAlign.Center)
            if (deload) Label(tr("أسبوع تخفيف: مجموعات أقل وجهد RPE 6 — لا عقاب في هذه الأيام.", "Deload: fewer sets at RPE 6 — no penalties on these days."), color = Sys.amber, size = 13)
            when {
                checkIn == null -> Label(tr("سجّل جاهزيتك أولًا لتعديل الحصة تلقائيًا.", "Log readiness first so the session adapts."), color = Sys.amber, size = 13)
                adv == ReadinessAdvice.REDUCED -> Label(tr("الجاهزية متوسطة: ستُخفَّض مجموعة من كل تمرين.", "Moderate readiness: one set less per exercise."), color = Sys.amber, size = 13)
                adv == ReadinessAdvice.RECOVERY_ONLY -> Label(tr("ألم مسجل اليوم: تعافٍ فقط.", "Pain logged today: recovery only."), color = Sys.red, size = 13)
                else -> Label(tr("الجاهزية جيدة.", "Readiness good."), color = Sys.green, size = 13)
            }
            val items = todayType?.let { t -> plan.week.firstOrNull { it.type == t }?.items }.orEmpty()
            items.forEach { pr -> PrescriptionRow(pr, s, nav) }
            Spacer(Modifier.height(8.dp))
            if (todayType != null) {
                if (doneToday) Label(tr("أكملت حصة اليوم ✓", "Today's session complete ✓"), Modifier.fillMaxWidth(), Sys.green, 14, TextAlign.Center)
                SysButton(if (doneToday) tr("حصة إضافية", "Extra session") else tr("ابدأ الحصة", "Start session"), Modifier.fillMaxWidth(), enabled = adv != ReadinessAdvice.RECOVERY_ONLY) {
                    repo.apply { st -> GameEngine.startSession(st, today, repo.now(), if (doneToday) todayType else null) }
                }
                if (checkIn == null) SysButton(tr("سجّل الجاهزية", "Log readiness"), Modifier.fillMaxWidth(), small = true, accent = Sys.amber) { nav.go("status") }
            } else {
                Label(tr("الراحة جزء من التدريب. يمكنك المشي الخفيف أو التمدد. حصة اختيارية:", "Rest is part of training. Walk or stretch. Optional session:"), size = 13)
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    plan.week.map { it.type }.filter { it != DayType.REST }.distinct().forEach { t ->
                        Chip(if (ar) t.ar else t.en, false) { repo.apply { st -> GameEngine.startSession(st, today, repo.now(), t) } }
                    }
                }
            }
        }
        Suggestions(s, repo)
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("خطة الأسبوع", "Weekly plan") + " • " + (if (ar) plan.split.ar else plan.split.en))
            val todayDow = PlanGenerator.dayOfWeek(today)
            plan.week.sortedBy { it.dayOfWeek }.forEach { d ->
                val name = java.time.DayOfWeek.of(d.dayOfWeek).getDisplayName(java.time.format.TextStyle.FULL, if (ar) Locale.forLanguageTag("ar") else Locale.ENGLISH)
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Label(name, Modifier.width(90.dp), if (d.dayOfWeek == todayDow) LocalPalette.current.secondary else Sys.text, 14, w = if (d.dayOfWeek == todayDow) FontWeight.Bold else FontWeight.Normal)
                    Label((if (ar) d.type.ar else d.type.en) + if (d.isTraining) " • ${d.items.size} " + tr("تمارين", "exercises") + " • ~${PlanGenerator.estimateSeconds(d.items) / 60} " + tr("د", "min") else "", Modifier.weight(1f), size = 13)
                }
            }
            Divider()
            Label(tr("لماذا هذه الخطة", "Why this plan"), color = Sys.text, size = 14, w = FontWeight.Bold)
            plan.rationale.forEach { Label("• " + it.get(ar), size = 12) }
            plan.excludedNotes.forEach { Label("• " + it.get(ar), color = Sys.amber, size = 12) }
            Label(tr("التقدم: زيادة التكرارات حتى أعلى النطاق ثم زيادة الحمل (تقدم مزدوج). المستوى في اللعبة لا يزيد الأحمال أبدًا.", "Progress: add reps to the top of the range, then add load (double progression). Game level never raises loads."), size = 12)
            Spacer(Modifier.height(6.dp))
            SysButton(tr("رتب التدريب والاختبارات", "Training tiers & assessments"), Modifier.fillMaxWidth(), small = true) { nav.go("tiers") }
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("الإحماء والتهدئة", "Warm-up & cool-down"))
            plan.warmup.forEach { Label("• " + it.get(ar), size = 13) }
            Divider()
            plan.cooldown.forEach { Label("• " + it.get(ar), size = 13) }
            Divider()
            plan.recoveryTips.forEach { Label("• " + it.get(ar), size = 12) }
        }
        val recent = s.sessions.filter { it.finished }.sortedByDescending { it.finishedMs }.take(8)
        if (recent.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("آخر الحصص", "Recent sessions"))
            recent.forEach { w ->
                val sets = w.entries.sumOf { e -> e.sets.count { it.done } }
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Label(fmtDay(w.day, ar), Modifier.width(100.dp), Sys.text, 13)
                    Label((if (ar) w.dayType.ar else w.dayType.en) + " • $sets " + tr("مجموعة", "sets") + (if (w.deload) tr(" • تخفيف", " • deload") else "") + (if (w.stoppedForSafety) tr(" • أوقفت للسلامة", " • stopped for safety") else ""), Modifier.weight(1f), size = 12)
                    Label("${((w.finishedMs!! - w.startedMs) / 60000).coerceAtLeast(1)} " + tr("د", "min"), size = 12)
                }
            }
        }
    }
}

@Composable
private fun PrescriptionRow(pr: Prescription, s: AppState, nav: Nav) {
    val ar = LocalArabic.current
    val ex = ExerciseLibrary.get(pr.exerciseId) ?: return
    val (lo, hi) = Progression.repRange(pr, s.progress[pr.exerciseId])
    val load = s.progress[pr.exerciseId]?.targetLoadKg
    Row(Modifier.fillMaxWidth().clickable { nav.go("exercise/${ex.id}") }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        PoseThumb(ex.pose)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            BasicText(ex.name(ar), style = body(15.sp, FontWeight.Medium))
            Label("\u2066${pr.sets} × $lo–$hi\u2069" + (if (pr.timed) "\u00A0" + tr("ث", "s") else "") + (if (load != null) " @ " + fmtLoad(load, s.settings.units) else "") + " • \u2066RPE ${pr.rpeTarget}\u2069 • " + tr("راحة", "rest") + " \u2066${pr.restSec}\u2069" + tr("ث", "s"), size = 12)
        }
        BasicText("›", style = tech(20.sp, Sys.dim))
    }
}

@Composable
fun PoseThumb(pose: Pose, size: androidx.compose.ui.unit.Dp = 36.dp) {
    val c = LocalPalette.current.secondary
    Canvas(Modifier.size(size).thinBox()) { drawPose(pose, c, 0.5f) }
}

@Composable
private fun Suggestions(s: AppState, repo: Repository) {
    val ar = LocalArabic.current
    val list = s.progress.values.filter { it.suggestion != null && ExerciseLibrary.get(it.exerciseId) != null }
    if (list.isEmpty()) return
    SysPanel(Modifier.fillMaxWidth(), accent = Sys.green, brackets = false, flares = false) {
        SectionHeader(tr("اقتراحات التقدم", "Progression suggestions"))
        list.forEach { p ->
            val ex = ExerciseLibrary.get(p.exerciseId)!!
            val sg = p.suggestion!!
            val text = when (sg.kind) {
                SuggestionKind.ADD_LOAD -> tr("زد الحمل إلى ", "Increase load to ") + fmtLoad(sg.newLoadKg, s.settings.units)
                SuggestionKind.ADD_REPS -> tr("نطاق جديد: ", "New range: ") + "${sg.newRepMin}–${sg.newRepMax}"
                SuggestionKind.HARDER_VARIATION -> tr("انتقل إلى: ", "Move up to: ") + (ExerciseLibrary.get(sg.newExerciseId ?: "")?.name(ar) ?: "")
                SuggestionKind.EASIER_VARIATION -> tr("جرّب الأسهل: ", "Try the easier: ") + (ExerciseLibrary.get(sg.newExerciseId ?: "")?.name(ar) ?: "")
                SuggestionKind.REDUCE_LOAD -> tr("خفّف الحمل إلى ", "Reduce load to ") + fmtLoad(sg.newLoadKg, s.settings.units)
            }
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Label(ex.name(ar), color = Sys.text, size = 14, w = FontWeight.Bold)
                Label(text, color = Sys.green, size = 13)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton(tr("قبول", "Accept"), Modifier.weight(1f), small = true, accent = Sys.green) { repo.set { GameEngine.acceptSuggestion(it, p.exerciseId) } }
                    SysButton(tr("ليس الآن", "Not now"), Modifier.weight(1f), small = true, accent = Sys.dim) { repo.set { GameEngine.dismissSuggestion(it, p.exerciseId) } }
                }
            }
        }
    }
}

@Composable
private fun ActiveSession(s: AppState, w: WorkoutSession, repo: Repository, nav: Nav, today: Long) {
    val ar = LocalArabic.current
    var swapFor by remember { mutableStateOf<Int?>(null) }
    var painFor by remember { mutableStateOf<Int?>(-2) } // -2 = closed, -1 = whole session, else entry
    var confirmDiscard by remember { mutableStateOf(false) }
    val units = s.settings.units
    RestTimerPanel()
    SysPanel(Modifier.fillMaxWidth(), contentKey = w.entries.sumOf { e -> e.sets.count { it.done } }) {
        TitleBox(tr("حصة جارية", "SESSION ACTIVE"))
        Spacer(Modifier.height(6.dp))
        GlowText((if (ar) w.dayType.ar else w.dayType.en) + (if (w.deload) tr(" • تخفيف", " • deload") else "") + (if (w.reduced) tr(" • مخففة", " • reduced") else ""), body(17.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center)
        val done = w.entries.sumOf { e -> e.sets.count { it.done } }; val total = w.entries.sumOf { it.sets.size }
        StatBar(tr("المجموعات", "Sets"), done.toFloat(), total.toFloat(), Sys.green, valueText = "$done/$total")
        Label(tr("سجّل الأرقام الحقيقية فقط. توقف عند أي ألم حاد.", "Log only real numbers. Stop at any sharp pain."), size = 12)
    }
    w.entries.forEachIndexed { i, e ->
        val ex = ExerciseLibrary.get(e.exerciseId)
        val (lo, hi) = Progression.repRange(e.prescription, s.progress[e.exerciseId])
        val stopped = e.status == EntryStatus.STOPPED
        SysPanel(Modifier.fillMaxWidth(), accent = if (stopped) Sys.red else if (e.sets.all { it.done }) Sys.green else null ?: LocalPalette.current.secondary, brackets = false, flares = false, padding = 12.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ex?.let { PoseThumb(it.pose, 40.dp) }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).clickable { nav.go("exercise/${e.exerciseId}") }) {
                    BasicText(ex?.name(ar) ?: e.exerciseId, style = body(16.sp, FontWeight.Bold))
                    Label("${tr("الهدف", "Target")}: \u2066${e.prescription.sets} × $lo–$hi\u2069${if (e.prescription.timed) tr(" ث", " s") else ""} • \u2066RPE ≤ ${e.prescription.rpeTarget}\u2069", size = 12)
                    Progression.lastPerformance(s.sessions, e.exerciseId)?.let { last ->
                        val d = last.sets.filter { it.done }
                        Label(tr("المرة السابقة: ", "Last time: ") + run { val arL = com.ayman.solosystem.ui.theme.LocalArabic.current; d.joinToString(" / ") { "\u2066${it.reps}\u2069" + (it.loadKg?.let { k -> " @ " + fmtLoadA(k, units, arL) } ?: "") } }, size = 11)
                    }
                }
            }
            if (stopped) Label(tr("أوقف بسبب الألم — هذا ليس فشلًا.", "Stopped for pain — not a failure."), color = Sys.red, size = 13)
            else {
                e.sets.forEachIndexed { j, set -> SetRow(j, set, e, ex, units, enabled = true) { log ->
                    repo.set { st -> GameEngine.updateSet(st, w.id, i, j, log) }
                    if (log.done && !set.done) RestTimer.start(e.prescription.restSec)
                } }
                FlowRowCompat(Modifier.fillMaxWidth()) {
                    if (e.sets.size < 10) SysButton(tr("+ مجموعة", "+ Set"), small = true) { repo.set { GameEngine.addSet(it, w.id, i) } }
                    SysButton(tr("بديل", "Swap"), small = true) { swapFor = if (swapFor == i) null else i }
                    SysButton(tr("ألم", "Pain"), small = true, accent = Sys.amber) { painFor = i }
                }
                if (swapFor == i) {
                    val opts = GameEngine.swapOptions(s, e.exerciseId).take(6)
                    if (opts.isEmpty()) Label(tr("لا بدائل مناسبة لمعداتك.", "No suitable alternatives for your equipment."), size = 12)
                    opts.forEach { o -> Row(Modifier.fillMaxWidth().clickable { repo.set { st -> GameEngine.swap(st, w.id, i, o.id) }; swapFor = null }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        PoseThumb(o.pose, 28.dp); Spacer(Modifier.width(8.dp)); Label(o.name(ar) + " • L${o.level}", color = Sys.text, size = 14)
                    } }
                }
            }
            if (painFor == i) PainForm(s, w, i, repo, today) { painFor = -2 }
        }
    }
    if (painFor == -1) SysPanel(Modifier.fillMaxWidth(), accent = Sys.amber, brackets = false, flares = false) { PainForm(s, w, null, repo, today) { painFor = -2 } }
    SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
        SysButton(tr("إنهاء الحصة", "Finish session"), Modifier.fillMaxWidth(), accent = Sys.green) { RestTimer.stop(); repo.apply { st -> GameEngine.finishSession(st, w.id, today, repo.now()) } }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton(tr("أعراض / ألم", "Symptoms / pain"), Modifier.weight(1f), small = true, accent = Sys.amber) { painFor = -1 }
            SysButton(if (confirmDiscard) tr("تأكيد الإلغاء", "Confirm discard") else tr("إلغاء الحصة", "Discard"), Modifier.weight(1f), small = true, accent = Sys.red) {
                if (confirmDiscard) { RestTimer.stop(); repo.set { GameEngine.discardSession(it, w.id) } } else confirmDiscard = true
            }
        }
        if (confirmDiscard) Label(tr("سيتم حذف هذه الحصة دون خبرة. اضغط مجددًا للتأكيد.", "This session will be deleted without XP. Tap again to confirm."), color = Sys.red, size = 12)
    }
}

@Composable
private fun SetRow(j: Int, set: SetLog, e: SessionEntry, ex: Exercise?, units: Units, enabled: Boolean, onChange: (SetLog) -> Unit) {
    val loadable = ex?.loadable == true || set.loadKg != null
    // v1.2.2: one control per line, 48dp touch targets and real spacing (S25 recording: +/- were cramped side by side)
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp).thinBox().padding(horizontal = 10.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicText("${j + 1}", style = tech(18.sp, LocalPalette.current.secondary), modifier = Modifier.width(26.dp))
            MiniStep(tr("تكرار", "Reps"), if (e.prescription.timed) "\u2066${set.reps}\u2069 " + tr("ث", "s") else "${set.reps}", Modifier.weight(1f),
                { onChange(set.copy(reps = (set.reps - (if (e.prescription.timed) 5 else 1)).coerceAtLeast(0))) },
                { onChange(set.copy(reps = (set.reps + (if (e.prescription.timed) 5 else 1)).coerceAtMost(999))) })
            Spacer(Modifier.width(12.dp))
            val canCheck = enabled && set.reps > 0
            Box(Modifier.size(48.dp).clickable(enabled = canCheck, role = androidx.compose.ui.semantics.Role.Checkbox) {
                com.ayman.solosystem.notify.Sfx.play(if (set.done) com.ayman.solosystem.notify.Sfx.S.TOGGLE_OFF else com.ayman.solosystem.notify.Sfx.S.TOGGLE_ON); onChange(set.copy(done = !set.done)) },
                contentAlignment = Alignment.Center) {
                SysCheck(set.done, enabled = enabled && set.reps > 0) { onChange(set.copy(done = !set.done)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(26.dp))
            if (loadable) MiniStep(tr("الوزن", "Load"), fmtLoad(set.loadKg ?: 0.0, units), Modifier.weight(1f),
                { val n = (set.loadKg ?: 0.0) - loadStep(units); onChange(set.copy(loadKg = if (n <= 0.01) null else n)) },
                { onChange(set.copy(loadKg = ((set.loadKg ?: 0.0) + loadStep(units)).coerceAtMost(500.0))) })
            else Label(tr("وزن الجسم", "Bodyweight"), Modifier.weight(1f).heightIn(min = 48.dp).wrapContentHeight(), size = 13)
            Spacer(Modifier.width(60.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(26.dp))
            MiniStep("RPE", set.rpe?.toString() ?: "–", Modifier.weight(1f),
                { onChange(set.copy(rpe = set.rpe?.let { if (it <= 5) null else it - 1 })) },
                { onChange(set.copy(rpe = ((set.rpe ?: 5) + 1).coerceAtMost(10))) })
            Spacer(Modifier.width(60.dp))
        }
    }
}

/** Labelled stepper: caption · [−] value [+], 48dp buttons with 10dp gaps. */
@Composable
private fun MiniStep(caption: String, value: String, modifier: Modifier, minus: () -> Unit, plus: () -> Unit) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Label(caption, Modifier.width(52.dp), Sys.dim, 12)
        SysButton("−", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, onClick = minus)
        BasicText(value, Modifier.weight(1f), style = tech(16.sp).copy(textAlign = TextAlign.Center), maxLines = 1, softWrap = false)
        SysButton("+", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, onClick = plus)
    }
}

@Composable
private fun PainForm(s: AppState, w: WorkoutSession, entry: Int?, repo: Repository, today: Long, close: () -> Unit) {
    val ar = LocalArabic.current
    var type by remember { mutableStateOf<SymptomType?>(null) }
    var area by remember { mutableStateOf<Limitation?>(null) }
    var addLim by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Label(tr("ما الذي تشعر به؟", "What do you feel?"), color = Sys.amber, size = 14, w = FontWeight.Bold)
        FlowRowCompat(Modifier.fillMaxWidth()) { SymptomType.entries.forEach { t -> Chip(if (ar) t.ar else t.en, type == t) { type = t } } }
        Label(tr("المكان (اختياري)", "Area (optional)"), size = 12)
        FlowRowCompat(Modifier.fillMaxWidth()) { Limitation.entries.filter { it != Limitation.HIGH_IMPACT }.forEach { l -> Chip(if (ar) l.ar else l.en, area == l) { area = if (area == l) null else l } } }
        if (area != null) Row(Modifier.clickable { addLim = !addLim }, verticalAlignment = Alignment.CenterVertically) { SysCheck(addLim) { addLim = !addLim }; Spacer(Modifier.width(6.dp)); Label(tr("أضفها لقيودي (تجنب التمارين المؤلمة)", "Add to my limitations (avoid painful exercises)"), color = Sys.text, size = 13) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton(tr("إرسال", "Report"), Modifier.weight(1f), small = true, enabled = type != null, accent = Sys.amber) {
                type?.let { t -> repo.apply { st -> GameEngine.reportPain(st, w.id, entry, t, area, addLim, today, repo.now()) } }; close()
            }
            SysButton(tr("إغلاق", "Close"), Modifier.weight(1f), small = true, accent = Sys.dim, onClick = close)
        }
    }
}

@Composable
private fun RestTimerPanel() {
    val end = RestTimer.endMs
    if (end == 0L) return
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(end) { while (true) { now = System.currentTimeMillis(); if (now >= end) break; delay(250) } }
    val left = ((end - now) / 1000).coerceAtLeast(0)
    SysPanel(Modifier.fillMaxWidth(), accent = if (left == 0L) Sys.green else null ?: LocalPalette.current.secondary, brackets = false, flares = false, padding = 12.dp, contentKey = left == 0L) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SysIcon(Ico.TIMER, if (left == 0L) Sys.green else LocalPalette.current.secondary)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Label(if (left == 0L) tr("انتهت الراحة — المجموعة التالية", "Rest over — next set") else tr("راحة", "Rest"), color = Sys.text, size = 14)
                StatBar("", (RestTimer.totalSec - left).toFloat(), RestTimer.totalSec.toFloat().coerceAtLeast(1f), if (left == 0L) Sys.green else LocalPalette.current.secondary, valueText = "")
            }
            Spacer(Modifier.width(10.dp))
            GlowText("%d:%02d".format(java.util.Locale.US, left / 60, left % 60), tech(26.sp))
            Spacer(Modifier.width(6.dp))
            Column {
                SysButton("+15", small = true) { RestTimer.endMs = maxOf(RestTimer.endMs, System.currentTimeMillis()) + 15_000; RestTimer.totalSec += 15 }
                SysButton("✕", small = true, accent = Sys.dim) { RestTimer.stop() }
            }
        }
    }
}

@Composable
fun ExerciseScreen(repo: Repository, nav: Nav, id: String) {
    val s by repo.state.collectAsState()
    val ar = LocalArabic.current
    val ex = ExerciseLibrary.get(id)
    Screen(ex?.name(ar) ?: tr("تمرين", "Exercise"), nav) {
        if (ex == null) { Label(tr("تمرين غير معروف.", "Unknown exercise.")); return@Screen }
        val c = LocalPalette.current.secondary
        val reduced = LocalReducedMotion.current
        val phase: State<Float> = if (reduced) remember { mutableFloatStateOf(0.5f) } else rememberInfiniteTransitionCompat()
        SysPanel(Modifier.fillMaxWidth(), contentKey = id) {
            TitleBox(ex.name(ar))
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(200.dp).thinBox()) { drawPose(ex.pose, c, phase.value) }
            Label(tr("رسم توضيحي مبسّط للحركة", "Simplified movement diagram"), Modifier.fillMaxWidth(), size = 11, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            KeyValue(tr("المستوى", "Level"), "${ex.level}/5")
            KeyValue(tr("العضلات", "Muscles"), ex.muscles.joinToString("، ") { if (ar) it.ar else it.en })
            KeyValue(tr("المعدات", "Equipment"), if (ex.needs.isEmpty()) tr("وزن الجسم", "Bodyweight") else ex.needs.joinToString(" / ") { if (ar) it.ar else it.en })
            if (ex.avoid.isNotEmpty()) KeyValue(tr("تجنب مع", "Avoid with"), ex.avoid.joinToString("، ") { if (ar) it.ar else it.en }, Sys.amber)
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("نقاط الأداء", "Coaching cues"))
            ex.cues(ar).forEachIndexed { i, cue -> Label("${i + 1}. $cue", color = Sys.text, size = 14) }
            Spacer(Modifier.height(6.dp))
            ex.regression?.let { r -> ExerciseLibrary.get(r)?.let { Row(Modifier.fillMaxWidth().clickable { nav.go("exercise/$r") }.padding(vertical = 4.dp)) { Label(tr("الأسهل: ", "Easier: "), size = 13); Label(it.name(ar), color = Sys.green, size = 13) } } }
            ex.progression?.let { r -> ExerciseLibrary.get(r)?.let { Row(Modifier.fillMaxWidth().clickable { nav.go("exercise/$r") }.padding(vertical = 4.dp)) { Label(tr("الأصعب: ", "Harder: "), size = 13); Label(it.name(ar), color = Sys.amber, size = 13) } } }
        }
        val hist = Progression.history(s.sessions, id)
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("السجل والأرقام القياسية", "History & records"))
            if (hist.isEmpty()) EmptyState(tr("لا توجد سجلات بعد", "No records yet"), tr("لم تُسجَّل هذه الحركة بعد. أدّها في حصة ليبدأ تتبعها.", "Not logged yet. Do it in a session to start tracking."), icon = Ico.TRAINING)
            else {
                val useE = hist.any { it.bestE1rm != null }
                LineChart(hist.map { (if (useE) it.bestE1rm!!.let { v -> if (s.settings.units == Units.LB) v * LB else v } else it.bestReps.toDouble()) }, Modifier.fillMaxWidth().height(140.dp), c)
                Label(if (useE) tr("أفضل 1RM تقديري", "Best estimated 1RM") else tr("أفضل تكرارات", "Best reps"), size = 11)
                hist.takeLast(8).reversed().forEach { p ->
                    Row(Modifier.fillMaxWidth()) { Label(fmtDay(p.day, ar), Modifier.width(100.dp), Sys.text, 13); Label("${p.bestReps} " + tr("تكرار", "reps") + (p.bestLoad?.let { " @ " + fmtLoad(it, s.settings.units) } ?: "") + " • " + tr("مجموع", "total") + " ${p.totalReps}", size = 12) }
                }
            }
            s.prs.filter { it.exerciseId == id }.groupBy { it.kind }.forEach { (k, l) -> val best = l.maxBy { it.value }
                KeyValue(when (k) { PrKind.E1RM -> "1RM"; PrKind.REPS -> tr("تكرارات", "Reps"); PrKind.SECONDS -> tr("ثوانٍ", "Seconds") }, if (k == PrKind.E1RM) fmtLoad(best.value, s.settings.units) else best.value.roundToInt().toString(), Sys.gold) }
        }
    }
}

@Composable
private fun rememberInfiniteTransitionCompat(): State<Float> {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "pose")
    return t.animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1600, easing = androidx.compose.animation.core.FastOutSlowInEasing), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
}

/** Minimal line chart for a series (chronological). */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier, color: Color, bars: Boolean = false, slots: Int = 0, maxValue: Double? = null) {
    Canvas(modifier.thinBox().padding(8.dp)) {
        if (values.isEmpty()) return@Canvas
        val mx = (maxValue ?: values.max()).coerceAtLeast(1e-6); val mn = if (bars) 0.0 else values.min().let { if (it == mx) 0.0 else it - (mx - it) * 0.1 }
        val n = values.size
        val stepX = if (n > 1) size.width / (n - 1) else 0f
        fun y(v: Double) = (size.height - ((v - mn) / (mx - mn).coerceAtLeast(1e-6) * size.height)).toFloat()
        for (g in 0..3) drawLine(Sys.panelLine.copy(alpha = 0.25f), androidx.compose.ui.geometry.Offset(0f, size.height * g / 3), androidx.compose.ui.geometry.Offset(size.width, size.height * g / 3), 1f)
        if (bars) {
            val sn = maxOf(n, slots); val off = sn - n
            val bw = minOf(size.width / sn * 0.6f, 26.dp.toPx())
            values.forEachIndexed { i, v ->
                val x = size.width / sn * (i + off + 0.5f); val top = y(v); val h = size.height - top
                if (h > 0.5f) {
                    drawRect(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.25f)), top, size.height), androidx.compose.ui.geometry.Offset(x - bw / 2, top), androidx.compose.ui.geometry.Size(bw, h))
                    drawLine(Color.White.copy(alpha = 0.9f), androidx.compose.ui.geometry.Offset(x - bw / 2, top), androidx.compose.ui.geometry.Offset(x + bw / 2, top), 2.dp.toPx())
                }
            }
        } else {
            val path = androidx.compose.ui.graphics.Path()
            values.forEachIndexed { i, v -> val x = if (n == 1) size.width / 2 else i * stepX; if (i == 0) path.moveTo(x, y(v)) else path.lineTo(x, y(v)) }
            drawPath(path, color.copy(alpha = 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(8f))
            drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(3f))
            values.forEachIndexed { i, v -> val x = if (n == 1) size.width / 2 else i * stepX; drawCircle(Color.White, 4f, androidx.compose.ui.geometry.Offset(x, y(v))) }
        }
    }
}
