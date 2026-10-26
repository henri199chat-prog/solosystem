package com.ayman.solosystem.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.ui.FlowRowCompat
import com.ayman.solosystem.ui.WarningText
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun OnboardingFlow(repo: Repository) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    androidx.activity.compose.BackHandler(step == 1) { step = 0 }
    if (step == 0) ActivationScreen(repo) { step = 1 } else SetupScreen(repo)
}

@Composable
fun ActivationScreen(repo: Repository, onAccept: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val ar = LocalArabic.current
    val full = tr("لقد استوفيت الشروط لتصبح «لاعبًا».\nهل تقبل؟", "You have acquired the qualifications to be a Player.\nWill you accept?")
    var shown by rememberSaveable(full) { mutableIntStateOf(if (reduced) full.length else 0) }
    var declined by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(full) { if (!reduced) { delay(500); while (shown < full.length) { shown++; delay(28) } } }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        GlowText("SOLO SYSTEM", tech(30.sp), align = TextAlign.Center)
        Label("REAL LIFE LEVELING SYSTEM", color = Sys.dim, size = 12, align = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        SysPanel(Modifier.fillMaxWidth(), padding = 20.dp, contentKey = declined) {
            TitleBox(tr("إشعار", "NOTIFICATION"))
            Spacer(Modifier.height(18.dp))
            GlowText(if (declined) tr("النظام سينتظر. يمكنك القبول متى شئت.", "The System will wait. You may accept whenever you're ready.") else full.take(shown),
                body(17.sp, FontWeight.Medium), Modifier.fillMaxWidth().heightIn(min = 64.dp), align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SysButton(tr("نعم", "Yes"), Modifier.weight(1f), enabled = shown >= full.length || declined) { onAccept() }
                SysButton(tr("لا", "No"), Modifier.weight(1f), accent = Sys.dim, enabled = !declined && shown >= full.length) { declined = true }
            }
        }
        Spacer(Modifier.height(16.dp))
        SysButton(if (ar) "English" else "العربية", small = true, accent = Sys.dim) { repo.set { it.copy(settings = it.settings.copy(arabic = !ar)) } }
        Spacer(Modifier.height(8.dp))
        Label(tr("تطبيق محلي بالكامل — بياناتك تبقى على هاتفك. ليس نصيحة طبية.", "Fully local app — your data stays on your phone. Not medical advice."), size = 12, align = TextAlign.Center)
    }
}

@Composable
private fun <T> ChoiceRow(label: String, options: List<T>, selected: T?, name: (T) -> String, onPick: (T) -> Unit) {
    Label(label, color = Sys.text, size = 14, w = FontWeight.Bold)
    FlowRowCompat(Modifier.fillMaxWidth()) { options.forEach { o -> Chip(name(o), o == selected) { onPick(o) } } }
    Spacer(Modifier.height(8.dp))
}

/** Profile form used by setup and by "Edit profile". Validates every field before enabling save. */
@Composable
fun ProfileForm(initial: PlayerProfile?, submitText: String, onSubmit: (PlayerProfile) -> Unit) {
    val ar = LocalArabic.current
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var age by rememberSaveable { mutableStateOf(initial?.age?.toString() ?: "") }
    var height by rememberSaveable { mutableStateOf(initial?.heightCm?.let { "%.0f".format(java.util.Locale.US, it) } ?: "") }
    var weight by rememberSaveable { mutableStateOf(initial?.weightKg?.let { "%.1f".format(java.util.Locale.US, it) } ?: "") }
    var fitness by remember { mutableStateOf(initial?.fitness) }
    var exp by remember { mutableStateOf(initial?.experience) }
    var equip by remember { mutableStateOf(initial?.equipment ?: setOf(Equipment.NONE)) }
    var days by rememberSaveable { mutableIntStateOf(initial?.daysPerWeek ?: 3) }
    var minutes by rememberSaveable { mutableIntStateOf(initial?.minutesPerSession ?: 45) }
    var goal by remember { mutableStateOf(initial?.goal) }
    var lim by remember { mutableStateOf(initial?.limitations ?: emptySet()) }
    var notes by rememberSaveable { mutableStateOf(initial?.limitationNotes ?: "") }
    var sleep by rememberSaveable { mutableFloatStateOf(initial?.sleepHours ?: 7f) }
    var sq by remember { mutableStateOf(initial?.sleepQuality ?: SleepQuality.FAIR) }
    var sex by remember { mutableStateOf(initial?.sex ?: Sex.MALE) }
    var act by remember { mutableStateOf(initial?.activity ?: ActivityLevel.LIGHT) }
    var tried by remember { mutableStateOf(false) }

    val ageN = age.trim().toIntOrNull()
    val hN = height.trim().replace(',', '.').toFloatOrNull()
    val wN = weight.trim().replace(',', '.').toFloatOrNull()
    val nameErr = if (name.trim().length !in 1..30) tr("أدخل اسمًا (1–30 حرفًا)", "Enter a name (1–30 chars)") else null
    val ageErr = if (ageN == null || ageN !in 13..90) tr("العمر بين 13 و 90", "Age must be 13–90") else null
    val hErr = if (height.isNotBlank() && (hN == null || hN !in 120f..230f)) tr("الطول بين 120 و 230 سم", "Height 120–230 cm") else null
    val wErr = if (weight.isNotBlank() && (wN == null || wN !in 30f..300f)) tr("الوزن بين 30 و 300 كغ", "Weight 30–300 kg") else null
    val choiceErr = fitness == null || exp == null || goal == null
    val valid = nameErr == null && ageErr == null && hErr == null && wErr == null && !choiceErr && equip.isNotEmpty()

    TextIn(name, { name = it.take(30) }, tr("الاسم", "Name"), error = if (tried) nameErr else null)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextIn(age, { age = it.filter(Char::isDigit).take(2) }, tr("العمر", "Age"), Modifier.weight(1f), number = true, error = if (tried) ageErr else null)
        TextIn(height, { height = it.take(5) }, tr("الطول سم (اختياري)", "Height cm (optional)"), Modifier.weight(1f), number = true, error = hErr)
        TextIn(weight, { weight = it.take(5) }, tr("الوزن كغ (اختياري)", "Weight kg (optional)"), Modifier.weight(1f), number = true, error = wErr)
    }
    Spacer(Modifier.height(8.dp))
    ChoiceRow(tr("الجنس", "Sex"), Sex.entries, sex, { if (ar) it.ar else it.en }) { sex = it }
    ChoiceRow(tr("النشاط اليومي خارج التدريب", "Daily activity outside training"), ActivityLevel.entries, act, { if (ar) it.ar else it.en }) { act = it }
    ChoiceRow(tr("مستوى اللياقة", "Fitness level"), FitnessLevel.entries, fitness, { if (ar) it.ar else it.en }) { fitness = it }
    ChoiceRow(tr("الخبرة في التدريب", "Training experience"), Experience.entries, exp, { if (ar) it.ar else it.en }) { exp = it }
    Label(tr("المعدات المتاحة", "Available equipment"), color = Sys.text, size = 14, w = FontWeight.Bold)
    FlowRowCompat(Modifier.fillMaxWidth()) {
        Equipment.entries.forEach { e ->
            Chip(if (ar) e.ar else e.en, e in equip) {
                equip = if (e == Equipment.NONE) setOf(Equipment.NONE) else (if (e in equip) equip - e else equip - Equipment.NONE + e).ifEmpty { setOf(Equipment.NONE) }
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Stepper(tr("أيام التدريب أسبوعيًا", "Training days / week"), "$days", onMinus = { days = (days - 1).coerceAtLeast(2) }, onPlus = { days = (days + 1).coerceAtMost(6) })
    Stepper(tr("مدة الحصة (دقيقة)", "Session length (min)"), "$minutes", onMinus = { minutes = (minutes - 5).coerceAtLeast(20) }, onPlus = { minutes = (minutes + 5).coerceAtMost(120) })
    ChoiceRow(tr("الهدف", "Goal"), Goal.entries, goal, { if (ar) it.ar else it.en }) { goal = it }
    Label(tr("مناطق ألم أو قيود (اختياري)", "Pain areas / limitations (optional)"), color = Sys.text, size = 14, w = FontWeight.Bold)
    FlowRowCompat(Modifier.fillMaxWidth()) { Limitation.entries.forEach { l -> Chip(if (ar) l.ar else l.en, l in lim) { lim = if (l in lim) lim - l else lim + l } } }
    TextIn(notes, { notes = it.take(200) }, tr("ملاحظات (اختياري)", "Notes (optional)"), singleLine = false)
    Stepper(tr("ساعات النوم المعتادة", "Usual sleep hours"), "%.1f".format(java.util.Locale.US, sleep), onMinus = { sleep = (sleep - 0.5f).coerceAtLeast(3f) }, onPlus = { sleep = (sleep + 0.5f).coerceAtMost(12f) })
    ChoiceRow(tr("جودة النوم", "Sleep quality"), SleepQuality.entries, sq, { if (ar) it.ar else it.en }) { sq = it }
    if (tried && !valid) Label(tr("أكمل الحقول المطلوبة: الاسم، العمر، اللياقة، الخبرة، الهدف.", "Complete the required fields: name, age, fitness, experience, goal."), color = Sys.red, size = 13)
    Spacer(Modifier.height(10.dp))
    SysButton(submitText, Modifier.fillMaxWidth()) {
        tried = true
        if (valid) onSubmit(PlayerProfile(name.trim(), ageN!!, hN, wN, fitness!!, exp!!, equip, days, minutes, goal!!, lim, notes.trim(),
            initial?.avoidExerciseIds ?: emptySet(), sleep, sq, initial?.createdDay ?: 0, sex, act))
    }
}

@Composable
fun SetupScreen(repo: Repository) {
    val s by repo.state.collectAsState()
    var difficulty by remember { mutableStateOf(Difficulty.STANDARD) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
        SysPanel(Modifier.fillMaxWidth()) {
            TitleBox(tr("تسجيل اللاعب", "PLAYER REGISTRATION"))
            Spacer(Modifier.height(10.dp))
            Label(tr("سيبني النظام خطة آمنة ومتدرجة حسب إجاباتك. يمكنك تعديلها لاحقًا.", "The System will build a safe, progressive plan from your answers. You can edit it later."), color = Sys.text)
            Spacer(Modifier.height(10.dp))
            Label(tr("صعوبة النظام", "System difficulty"), color = Sys.text, size = 14, w = FontWeight.Bold)
            FlowRowCompat(Modifier.fillMaxWidth()) { Difficulty.entries.forEach { d -> Chip(if (s.settings.arabic) d.ar else d.en, d == difficulty) { difficulty = d } } }
            Spacer(Modifier.height(8.dp))
            ProfileForm(null, tr("ابدأ", "Begin")) { p ->
                repo.apply { st -> GameEngine.onboard(p, st.settings.copy(difficulty = difficulty), repo.today(), repo.now(), repo.now()) }
                repo.refreshDay()
            }
            Spacer(Modifier.height(10.dp))
            WarningText()
        }
    }
}
