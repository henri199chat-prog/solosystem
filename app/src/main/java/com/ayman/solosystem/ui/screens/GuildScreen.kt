package com.ayman.solosystem.ui.screens

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.guild.Guild
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.media.LicenseCard
import com.ayman.solosystem.media.PrivateFiles
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun GuildScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ar = LocalArabic.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = repo.today()
    val last = Guild.last(st)
    var photoVer by remember { mutableIntStateOf(0) }
    var confirmRemove by remember { mutableStateOf(false) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val rel = withContext(Dispatchers.IO) { PrivateFiles.importUri(ctx, uri, "guild", "license", 900) }
            if (rel != null) { repo.set { Guild.setPhoto(it, rel) }; photoVer++ }
        }
    }
    Screen(tr("نقابة الصيادين", "HUNTER GUILD"), nav) {
        if (last != null) {
            val level = Ledger.level(st).level
            val name = st.profile?.name ?: ""
            var card by remember { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(last.ms, st.guild.licensePhoto, photoVer, ar, level, name) {
                card = withContext(Dispatchers.Default) {
                    LicenseCard.render(ctx, LicenseCard.Data(name, last.rank, last.score, last.pushReps, last.plankSec, last.squatReps, last.run1kmSec, last.day,
                        Guild.licenseNo(st), level, ar, PrivateFiles.load(ctx, st.guild.licensePhoto, 900)))
                }
            }
            SysPanel(Modifier.fillMaxWidth(), accent = androidx.compose.ui.graphics.Color(LicenseCard.rankColors[last.rank])) {
                TitleBox(tr("رخصة الصياد", "HUNTER LICENSE"))
                Spacer(Modifier.height(8.dp))
                card?.let { Image(it.asImageBitmap(), tr("رخصة الصياد", "Hunter license"), Modifier.fillMaxWidth().aspectRatio(1.6f)) }
                    ?: Spacer(Modifier.fillMaxWidth().aspectRatio(1.6f))
                Spacer(Modifier.height(8.dp))
                SysButton(tr("مشاركة الرخصة (PNG)", "Share license (PNG)"), Modifier.fillMaxWidth(), sfx = Sfx.S.YES) {
                    val b = card ?: return@SysButton
                    scope.launch {
                        val f = withContext(Dispatchers.IO) { PrivateFiles.shareTarget(ctx, "hunter_license.png").also { out -> out.outputStream().use { b.compress(Bitmap.CompressFormat.PNG, 100, it) } } }
                        runCatching { PrivateFiles.share(ctx, f, "image/png", if (ar) "مشاركة رخصة الصياد" else "Share Hunter License") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton(if (st.guild.licensePhoto == null) tr("إضافة صورة", "Add photo") else tr("تغيير الصورة", "Change photo"), Modifier.weight(1f), small = true) {
                        pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                    if (st.guild.licensePhoto != null) SysButton(tr("إزالة الصورة", "Remove photo"), Modifier.weight(1f), small = true, accent = Sys.dim) { confirmRemove = true }
                }
                Label(tr("الصورة اختيارية وتبقى داخل التطبيق فقط. المشاركة تتم فقط عند الضغط.", "The photo is optional and stays inside the app. Nothing is shared unless you tap Share."), Modifier.fillMaxWidth(), size = 11, align = TextAlign.Center)
            }
        }
        AssessmentPanel(repo, today, ar)
        if (st.guild.tests.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("سجل التقييمات", "Assessment log"))
            st.guild.tests.sortedByDescending { it.ms }.take(12).forEach { t ->
                KeyValue(fmtDay(t.day, ar), "${Guild.rankLabel(t.rank)} • ${t.score}", androidx.compose.ui.graphics.Color(LicenseCard.rankColors[t.rank]))
            }
        }
    }
    if (confirmRemove) ConfirmDialog(tr("إزالة الصورة", "Remove photo"), tr("حذف صورة الرخصة من التطبيق؟ (نسخة التطبيق فقط — صورتك في المعرض لا تتأثر)", "Delete the license photo from the app? (only the app's copy — your gallery is untouched)"),
        tr("إزالة", "Remove"), onYes = {
            val rel = st.guild.licensePhoto
            repo.set { Guild.setPhoto(it, null) }
            scope.launch(Dispatchers.IO) { rel?.let { PrivateFiles.file(ctx, it).delete() } }
        }, onDismiss = { confirmRemove = false })
}

@Composable
private fun AssessmentPanel(repo: Repository, today: Long, ar: Boolean) {
    val st by repo.state.collectAsState()
    val can = Guild.canTest(st, today)
    SysPanel(Modifier.fillMaxWidth()) {
        TitleBox(tr("تقييم النقابة الشهري", "MONTHLY GUILD ASSESSMENT"))
        Spacer(Modifier.height(6.dp))
        if (!can) {
            val left = (Guild.nextDay(st) ?: today) - today
            Label(tr("التقييم القادم بعد $left يوم. التقييم مرة في الشهر يكفي لقياس التقدّم بأمان.", "Next assessment in $left days. Once a month is enough to measure progress safely."), Modifier.fillMaxWidth(), color = Sys.text, size = 14, align = TextAlign.Center)
            return@SysPanel
        }
        var started by remember { mutableStateOf(false) }
        var push by remember { mutableIntStateOf(Guild.last(st)?.pushReps ?: 10) }
        var plank by remember { mutableIntStateOf(Guild.last(st)?.plankSec ?: 30) }
        var squat by remember { mutableIntStateOf(Guild.last(st)?.squatReps ?: 20) }
        var runOn by remember { mutableStateOf(Guild.gpsKmSec(st) != null) }
        var runSec by remember { mutableIntStateOf(Guild.gpsKmSec(st) ?: 360) }
        SafetyNote(tr("إحماء 5–8 دقائق أولًا. توقّف قبل الفشل الفني بتكرار أو اثنين. لا تحبس النفس.", "Warm up 5–8 min first. Stop 1–2 reps before technical failure. Don't hold your breath."))
        SafetyNote(tr("أي ألم حاد = توقّف فورًا. لا عقاب أبدًا.", "Any sharp pain = stop immediately. Never a penalty."))
        if (!started) {
            Spacer(Modifier.height(6.dp))
            SysButton(tr("ابدأ التقييم", "Start assessment"), Modifier.fillMaxWidth(), sfx = Sfx.S.WIN_OPEN) { started = true }
            return@SysPanel
        }
        StepperRow(tr("ضغط (أقصى تكرار نظيف)", "Push-ups (max clean reps)"), push, "", { push = it })
        StepperRow(tr("بلانك (ثوانٍ)", "Plank (seconds)"), plank, tr("ث", "s"), { plank = it }, big = 15, max = 900)
        StepperRow(tr("سكوات (أقصى تكرار نظيف)", "Squats (max clean reps)"), squat, "", { squat = it })
        Row(Modifier.fillMaxWidth()) {
            Chip(tr("بدون اختبار جري", "No run test"), !runOn) { runOn = false }
            Chip(tr("جري 1 كم", "1 km run"), runOn) { runOn = true }
        }
        if (runOn) {
            StepperRow(tr("زمن 1 كم (ثوانٍ) — ${mmss(runSec)}", "1 km time (seconds) — ${mmss(runSec)}"), runSec, tr("ث", "s"), { runSec = it }, big = 15, min = 150, max = 1200)
            Guild.gpsKmSec(st)?.let { Label(tr("أفضل وتيرة من بوابة GPS: ${mmss(it)}/كم", "Best GPS gate pace: ${mmss(it)}/km"), size = 12) }
        }
        val sc = Guild.score(push, plank, squat, if (runOn) runSec else null)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Label(tr("الرتبة المتوقعة: ", "Projected rank: "), color = Sys.text, size = 15)
            GlowText("${Guild.rankLabel(Guild.rankFor(sc))} ($sc)", tech(), glow = androidx.compose.ui.graphics.Color(LicenseCard.rankColors[Guild.rankFor(sc)]))
        }
        Spacer(Modifier.height(8.dp))
        SysButton(tr("تسجيل النتيجة وإصدار الرخصة", "Record & issue license"), Modifier.fillMaxWidth(), sfx = Sfx.S.YES) {
            repo.apply { s -> Guild.record(s, push, plank, squat, if (runOn) runSec else null, today, repo.now()) }
            started = false
        }
        Spacer(Modifier.height(6.dp))
        SysButton(tr("ألم — توقّف الآن", "Pain — stop now"), Modifier.fillMaxWidth(), small = true, accent = Sys.red) {
            repo.apply { s -> Guild.painStop(s, today, repo.now()) }; started = false
        }
    }
}
