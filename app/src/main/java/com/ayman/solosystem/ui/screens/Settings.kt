package com.ayman.solosystem.ui.screens

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.notify.Reminders
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.*
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun SettingsScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    val st = s.settings
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = repo.today()
    fun upd(n: Settings) = repo.apply { GameEngine.updateSettings(it, n, today, repo.now()) }
    var granted by remember { mutableStateOf(Reminders.permissionGranted(ctx)) }
    var asked by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) { granted = Reminders.permissionGranted(ctx); onPauseOrDispose { } }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = Reminders.permissionGranted(ctx); asked = true
        if (ok) { upd(st.copy(remindersEnabled = true)); Reminders.reschedule(ctx, repo.state.value, force = true) }
    }
    var msg by remember { mutableStateOf<String?>(null) }
    val okExport = tr("تم التصدير.", "Exported."); val failExport = tr("فشل التصدير.", "Export failed.")
    val okImport = tr("تم الاستيراد.", "Imported."); val failImport = tr("الملف غير صالح — لم يتغير شيء.", "Invalid file — nothing changed.")
    val exportL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(repo.exportJson().toByteArray()) } }.isSuccess }
            msg = if (ok) okExport else failExport
        }
    }
    val importL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() } }.getOrNull() }
            msg = if (text != null && text.length < 20_000_000 && repo.importJson(text)) okImport else failImport
        }
    }
    var confirmReset by remember { mutableIntStateOf(0) }
    Screen(tr("الإعدادات", "Settings"), nav) {
        msg?.let { SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false, contentKey = it) { Label(it, Modifier.fillMaxWidth(), Sys.green, 14, TextAlign.Center) } }
        SysPanel(Modifier.fillMaxWidth(), contentKey = granted) {
            TitleBox(tr("الإشعارات", "NOTIFICATIONS"))
            Spacer(Modifier.height(6.dp))
            KeyValue(tr("إذن النظام", "System permission"), if (granted) tr("مسموح", "Granted") else tr("غير مسموح", "Not granted"), if (granted) Sys.green else Sys.red)
            KeyValue(tr("التذكيرات", "Reminders"), if (Reminders.active(ctx, s)) tr("نشطة", "Active") else tr("متوقفة", "Off"), if (Reminders.active(ctx, s)) Sys.green else Sys.dim)
            if (!granted) {
                SysButton(tr("السماح بالإشعارات", "Allow notifications"), Modifier.fillMaxWidth(), small = true) {
                    if (Build.VERSION.SDK_INT >= 33 && !asked) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                if (asked) Label(tr("إن رفضت سابقًا، فعّلها من إعدادات التطبيق.", "If you declined before, enable it in app settings."), size = 12)
            }
            Toggle(tr("تفعيل التذكيرات", "Enable reminders"), st.remindersEnabled) {
                val on = !st.remindersEnabled
                if (on && !granted && Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                else { upd(st.copy(remindersEnabled = on)); Reminders.reschedule(ctx, repo.state.value, force = true) }
            }
            TimeStep(tr("تذكير الصباح", "Morning reminder"), st.morningHour, st.morningMinute) { h, m -> upd(st.copy(morningHour = h, morningMinute = m)) }
            TimeStep(tr("تذكير المساء", "Evening reminder"), st.eveningHour, st.eveningMinute) { h, m -> upd(st.copy(eveningHour = h, eveningMinute = m)) }
            Toggle(tr("صوت إشعارات النظام (تنبيه/ارتفع مستواك)", "System notification sound (alert / level up)"), st.systemSounds) { upd(st.copy(systemSounds = !st.systemSounds, uiSounds = st.uiSoundsOn)) }
            Label(tr("تحترم وضع الصامت دائمًا. تنبيهات النظام بصوت أقوى، والإشعارات العادية بصوت أهدأ.", "Always respects silent mode. System alerts are louder; regular notices are quieter."), size = 11)
            Toggle(tr("الاهتزاز", "Vibration"), st.vibration) { upd(st.copy(vibration = !st.vibration)) }
            Toggle(tr("ترحيب النظام عند فتح التطبيق", "System welcome on app open"), st.welcome) { upd(st.copy(welcome = !st.welcome)) }
            Label(tr("يُرسل أيضًا تنبيه عند 50% و10% من مهلة المهمة اليومية وعند انتهائها.", "Also alerts at 50% and 10% of the daily deadline and at expiry."), size = 12)
            SysButton(tr("إشعار تجريبي", "Test notification"), Modifier.fillMaxWidth(), small = true, enabled = granted) { Reminders.test(ctx, repo.state.value) }
        }
        SoundSettings(st) { upd(it) }
        if (s.monarchAwakened) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("سمة النظام", "System theme"))
            FlowRowCompat(Modifier.fillMaxWidth()) {
                Chip(tr("الملك الظلّ (بنفسجي)", "Shadow Monarch (violet)"), !st.classicTheme) { upd(st.copy(classicTheme = false)) }
                Chip(tr("الكلاسيكي (أزرق)", "Classic (blue)"), st.classicTheme) { upd(st.copy(classicTheme = true)) }
            }
            Label(tr("فُتحت عند المستوى 100 — تطوّر النظام.", "Unlocked at level 100 — the System evolved."), size = 12)
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("المهام والعقاب", "Quests & penalties"))
            Stepper(tr("مهلة المهمة اليومية", "Daily deadline"), if (st.deadlineMinutes >= 1440) tr("منتصف الليل", "Midnight") else "%02d:%02d".format(Locale.US, st.deadlineMinutes / 60, st.deadlineMinutes % 60),
                onMinus = { upd(st.copy(deadlineMinutes = (st.deadlineMinutes - 30).coerceAtLeast(17 * 60))) }, onPlus = { upd(st.copy(deadlineMinutes = (st.deadlineMinutes + 30).coerceAtMost(1440))) })
            Label(tr("الصعوبة", "Difficulty"), color = Sys.text, size = 14)
            FlowRowCompat(Modifier.fillMaxWidth()) { Difficulty.entries.forEach { d -> Chip(tr(d.ar, d.en), st.difficulty == d) { upd(st.copy(difficulty = d, graceDaysPerWeek = d.defaultGrace)) } } }
            Stepper(tr("أيام السماح أسبوعيًا", "Grace days / week"), "${st.graceDaysPerWeek}", onMinus = { upd(st.copy(graceDaysPerWeek = (st.graceDaysPerWeek - 1).coerceAtLeast(0))) }, onPlus = { upd(st.copy(graceDaysPerWeek = (st.graceDaysPerWeek + 1).coerceAtMost(3))) })
            Stepper(tr("أسبوع تخفيف كل", "Deload every"), "${st.deloadEvery} " + tr("أسابيع", "weeks"), onMinus = { upd(st.copy(deloadEvery = (st.deloadEvery - 1).coerceAtLeast(4))) }, onPlus = { upd(st.copy(deloadEvery = (st.deloadEvery + 1).coerceAtMost(8))) })
            Label(tr("صرامة العقاب", "Penalty strictness"), color = Sys.text, size = 14)
            FlowRowCompat(Modifier.fillMaxWidth()) { PenaltyMode.entries.forEach { m -> Chip(tr(m.ar, m.en), st.penaltyMode == m) { upd(st.copy(penaltyMode = m)) } } }
            Toggle(tr("مهام طارئة عشوائية", "Random emergency quests"), st.emergencyQuests) { upd(st.copy(emergencyQuests = !st.emergencyQuests)) }
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("العرض", "Display"))
            Label(tr("تأثيرات الخلفية المتحركة", "Animated background FX"), color = Sys.text, size = 14)
            FlowRowCompat(Modifier.fillMaxWidth()) { BgFx.entries.forEach { m -> Chip(tr(m.ar, m.en), st.bgFx == m) { upd(st.copy(bgFx = m)) } } }
            Label(tr("عالٍ: ضباب، شبكة بلورية، نبضات كهربائية وجسيمات. منخفض: أخف على البطارية. إيقاف: خلفية ثابتة.", "High: fog, crystal web, electric pulses and motes. Low: lighter on battery. Off: static backdrop."), size = 12)
            Label(tr("اللغة", "Language"), color = Sys.text, size = 14)
            Row { Chip("العربية", st.arabic) { upd(st.copy(arabic = true)) }; Chip("English", !st.arabic) { upd(st.copy(arabic = false)) } }
            Label(tr("الوحدات", "Units"), color = Sys.text, size = 14)
            Row { Chip("kg", st.units == Units.KG) { upd(st.copy(units = Units.KG)) }; Chip("lb", st.units == Units.LB) { upd(st.copy(units = Units.LB)) } }
            Label(tr("لتقليل الحركة: فعّل «إزالة الرسوم المتحركة» في إعدادات الهاتف — سيعرض النظام توهجًا ثابتًا.", "Reduced motion: enable 'Remove animations' in phone settings — the System shows static glows."), size = 12)
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("حساب الخبرة", "XP math"))
            Label(tr("الحد الأقصى 300. الخبرة اللازمة للمستوى التالي = 95 × المستوى^0.45. بالالتزام الكامل يحتاج الوصول إلى 300 نحو ", "Max level 300. XP to next = 95 × level^0.45. With full adherence reaching 300 takes about ") +
                "%.1f".format(Locale.US, Leveling.yearsToMax()) + tr(" سنوات.", " years."), size = 12)
            Label(tr("الخبرة تُمنح مرة واحدة لكل مهمة (لا تكرار)، والخبرة المكتسبة لا تُحذف أبدًا كعقاب.", "XP is granted once per quest (idempotent) and earned XP is never removed as a penalty."), size = 12)
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("البيانات", "Data"))
            Label(tr("كل البيانات محلية على هاتفك فقط. لا حساب ولا خادم ولا تتبع.", "All data stays on your phone. No account, no server, no tracking."), size = 12)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton(tr("تصدير JSON", "Export JSON"), Modifier.weight(1f), small = true) { exportL.launch("solo_system_${java.time.LocalDate.now()}.json") }
                SysButton(tr("استيراد JSON", "Import JSON"), Modifier.weight(1f), small = true) { importL.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            }
            Spacer(Modifier.height(8.dp))
            SysButton(when (confirmReset) { 0 -> tr("إعادة ضبط كل شيء", "Reset everything"); 1 -> tr("هل أنت متأكد؟ اضغط مجددًا", "Are you sure? Tap again"); else -> tr("تأكيد نهائي: حذف كل البيانات", "Final: delete all data") },
                Modifier.fillMaxWidth(), small = true, accent = Sys.red) {
                if (confirmReset < 2) confirmReset++ else { repo.reset(); Reminders.reschedule(ctx, repo.state.value, force = true); nav.go("status") }
            }
            if (confirmReset > 0) SysButton(tr("إلغاء", "Cancel"), Modifier.fillMaxWidth(), small = true, accent = Sys.dim) { confirmReset = 0 }
        }
        Label("SOLO SYSTEM v" + runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull(), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 11)
    }
}

@Composable
fun Toggle(label: String, on: Boolean, onToggle: () -> Unit) {
    val snd = resolveTap(if (on) com.ayman.solosystem.notify.Sfx.S.TOGGLE_OFF else com.ayman.solosystem.notify.Sfx.S.TOGGLE_ON, LocalSoftSfx.current, false)
    Row(Modifier.fillMaxWidth().clickable { snd?.let { com.ayman.solosystem.notify.Sfx.play(it) }; onToggle() }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f), Sys.text, 14)
        SysCheck(on, onToggle = onToggle)
    }
}

@Composable
private fun TimeStep(label: String, h: Int, m: Int, onChange: (Int, Int) -> Unit) {
    val total = h * 60 + m
    Stepper(label, "%02d:%02d".format(Locale.US, h, m),
        onMinus = { val t = Math.floorMod(total - 15, 1440); onChange(t / 60, t % 60) },
        onPlus = { val t = Math.floorMod(total + 15, 1440); onChange(t / 60, t % 60) })
}

@Composable
fun ProfileEditScreen(repo: Repository, nav: Nav) {
    val s by repo.state.collectAsState()
    Screen(tr("الملف الشخصي", "Profile"), nav) {
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            Label(tr("تغيير المعدات أو الأيام أو القيود يعيد بناء الخطة ويحافظ على تقدمك وخبرتك.", "Changing equipment, days or limitations rebuilds the plan and keeps your progress and XP."), size = 12)
        }
        ProfileForm(s.profile, tr("حفظ", "Save")) { p -> repo.apply { GameEngine.updateProfile(it, p, repo.today(), repo.now()) }; nav.back() }
    }
}

/** v1.2 SOUND panel: in-app sounds on/off + persisted volume slider (applied live) + test sounds. */
@Composable
fun SoundSettings(st: Settings, upd: (Settings) -> Unit) {
    var vol by remember(st.sfxVolume) { mutableIntStateOf(st.sfxVolume) }
    SysPanel(Modifier.fillMaxWidth(), contentKey = st.uiSoundsOn) {
        TitleBox(tr("الصوت", "SOUND"))
        Spacer(Modifier.height(6.dp))
        Toggle(tr("أصوات الواجهة (الأزرار، النوافذ، التنقل، المكافآت)", "Interface sounds (buttons, windows, navigation, rewards)"), st.uiSoundsOn) {
            val on = !st.uiSoundsOn
            Sfx.enabled = on
            upd(st.copy(uiSounds = on))
            if (on) Sfx.preview(Sfx.S.TOGGLE_ON, vol / 100f)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label(tr("مستوى الصوت", "Volume"), Modifier.weight(1f), Sys.text, 14)
            Label("$vol%", color = if (st.uiSoundsOn) LocalPalette.current.secondary else Sys.dim, size = 14, w = FontWeight.Bold)
        }
        SysSlider(vol, label = tr("مستوى صوت الواجهة", "Interface volume"), onChange = { vol = it; Sfx.volume = it / 100f }) { v ->
            vol = v; Sfx.volume = v / 100f
            if (v != st.sfxVolume) upd(st.copy(sfxVolume = v))
            Sfx.preview(Sfx.S.TAP, v / 100f)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SysButton(tr("تجربة: نافذة", "Test: window"), Modifier.weight(1f), small = true, sfx = null, enabled = st.uiSoundsOn && vol > 0) { Sfx.preview(Sfx.S.WIN_OPEN, vol / 100f) }
            SysButton(tr("تجربة: مكافأة", "Test: reward"), Modifier.weight(1f), small = true, sfx = null, enabled = st.uiSoundsOn && vol > 0) { Sfx.preview(Sfx.S.QUEST_COMPLETE, vol / 100f) }
        }
        Label(tr("يتبع مستوى صوت الوسائط في الهاتف ويصمت في وضع الصامت/الاهتزاز. أصوات ترياق الشفاء أهدأ.", "Follows the phone's media volume and stays silent in silent/vibrate mode. Nutrition sounds are softer."), size = 11)
    }
}
