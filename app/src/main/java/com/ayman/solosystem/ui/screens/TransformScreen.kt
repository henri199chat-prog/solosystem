package com.ayman.solosystem.ui.screens

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.model.TransformPhoto
import com.ayman.solosystem.domain.transform.Transform
import com.ayman.solosystem.media.BeforeAfterVideo
import com.ayman.solosystem.media.PrivateFiles
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Session unlock for the private photos (relocks when the app process goes away or the user leaves the screen). */
object TransformLock { var unlocked = false }

private fun Context.activity(): Activity? { var c: Context? = this; while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }; return null }
fun deviceSecure(ctx: Context) = (ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isDeviceSecure

@Composable
fun TransformScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ctx = LocalContext.current
    val ar = LocalArabic.current
    val needLock = st.transform.lock && deviceSecure(ctx)
    var unlocked by remember { mutableStateOf(!needLock || TransformLock.unlocked) }
    DisposableEffect(Unit) { onDispose { TransformLock.unlocked = false } }
    if (!unlocked) {
        LockGate(onOk = { TransformLock.unlocked = true; unlocked = true }, nav = nav)
        return
    }
    TransformContent(repo, nav, st.transform.photos, ar)
}

@Composable
private fun LockGate(onOk: () -> Unit, nav: Nav) {
    val ctx = LocalContext.current
    var err by remember { mutableStateOf<String?>(null) }
    val confirm = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> if (r.resultCode == Activity.RESULT_OK) onOk() else err = "cancel" }
    val title = tr("صور التحوّل الخاصة", "Private transformation photos")
    val sub = tr("تأكيد الهوية", "Confirm it's you")
    fun ask() {
        Sfx.play(Sfx.S.KEY)
        if (Build.VERSION.SDK_INT >= 29) {
            val act = ctx.activity() ?: return
            val b = BiometricPrompt.Builder(act).setTitle(title).setSubtitle(sub)
            if (Build.VERSION.SDK_INT >= 30) b.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            else @Suppress("DEPRECATION") b.setDeviceCredentialAllowed(true)
            b.build().authenticate(CancellationSignal(), act.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { onOk() }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) { err = errString?.toString() ?: "error" }
            })
        } else {
            val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
            @Suppress("DEPRECATION") val i = km.createConfirmDeviceCredentialIntent(title, null)
            if (i == null) onOk() else confirm.launch(i)
        }
    }
    LaunchedEffect(Unit) { ask() }
    Screen(tr("التحوّل", "TRANSFORMATION"), nav) {
        SysPanel(Modifier.fillMaxWidth(), accent = Sys.amber, pulse = true) {
            TitleBox(tr("مقفل", "LOCKED"), accent = Sys.amber)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { SysIcon(Ico.LOCK, Sys.amber, size = 64.dp) }
            Spacer(Modifier.height(10.dp))
            Label(tr("صورك الخاصة محمية ببصمة/قفل الهاتف.", "Your private photos are protected by the phone's biometric/screen lock."), Modifier.fillMaxWidth(), color = Sys.text, size = 14, align = TextAlign.Center)
            if (err != null) Label(tr("لم يتم التأكيد.", "Not confirmed."), Modifier.fillMaxWidth(), color = Sys.red, size = 12, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            SysButton(tr("فتح", "Unlock"), Modifier.fillMaxWidth(), sfx = null) { err = null; ask() }
        }
    }
}

@Composable
private fun TransformContent(repo: Repository, nav: Nav, photos: List<TransformPhoto>, ar: Boolean) {
    val st by repo.state.collectAsState()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val today = repo.today()
    var beforeId by remember { mutableStateOf<String?>(null) }
    var afterId by remember { mutableStateOf<String?>(null) }
    val before = photos.firstOrNull { it.id == beforeId } ?: Transform.first(st)
    val after = photos.firstOrNull { it.id == afterId } ?: Transform.latest(st)
    var viewing by remember { mutableStateOf<TransformPhoto?>(null) }
    var confirmDel by remember { mutableStateOf<TransformPhoto?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var video by remember { mutableStateOf<File?>(null) }
    var videoErr by remember { mutableStateOf(false) }
    var camTarget by remember { mutableStateOf<Pair<String, File>?>(null) }

    fun addFrom(id: String, open: () -> java.io.InputStream?, cleanup: (() -> Unit)? = null) = scope.launch {
        val rel = withContext(Dispatchers.IO) { PrivateFiles.importImage(ctx, open, "transform", id).also { cleanup?.invoke() } }
        if (rel != null) { repo.set { Transform.add(it, id, rel, today, repo.now()) }; Sfx.play(Sfx.S.QUEST_TICK) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) addFrom("p${System.currentTimeMillis()}", { ctx.contentResolver.openInputStream(uri) })
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val t = camTarget; camTarget = null
        if (t != null) { if (ok && t.second.length() > 0) addFrom(t.first, { t.second.inputStream() }) { t.second.delete() } else t.second.delete() }
    }
    val canCamera = remember { ctx.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_CAMERA_ANY) }

    Screen(tr("التحوّل", "TRANSFORMATION"), nav) {
        SysPanel(Modifier.fillMaxWidth()) {
            TitleBox(tr("سجل التحوّل الخاص", "PRIVATE TRANSFORMATION LOG"))
            Spacer(Modifier.height(6.dp))
            Label(tr("الصور تُحفظ داخل التطبيق فقط (ليست في المعرض ولا في النسخ الاحتياطي). لا شيء يُشارك إلا بضغطك.", "Photos are stored inside the app only (not in the gallery, not in backups). Nothing is shared unless you tap Share."), Modifier.fillMaxWidth(), size = 12, align = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canCamera) SysButton(tr("التقاط صورة", "Take photo"), Modifier.weight(1f), small = true, sfx = Sfx.S.SELECT) {
                    val id = "p${System.currentTimeMillis()}"
                    val (f, uri) = PrivateFiles.newCameraTarget(ctx, id)
                    camTarget = id to f
                    runCatching { camera.launch(uri) }.onFailure { camTarget = null; f.delete() }
                }
                SysButton(tr("من المعرض", "From gallery"), Modifier.weight(1f), small = true, sfx = Sfx.S.SELECT) { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
            }
            Label(tr("نصيحة: نفس المكان، نفس الإضاءة، نفس الوضعية كل أسبوع.", "Tip: same place, same light, same pose every week."), Modifier.fillMaxWidth().padding(top = 6.dp), size = 11, align = TextAlign.Center)
        }
        if (photos.isEmpty()) {
            EmptyState(tr("لا صور بعد", "No photos yet"), tr("أضف صورتك الأولى — ستكون صورة \"قبل\".", "Add your first photo — it becomes your \"before\"."), icon = Ico.FRAME)
        } else {
            if (before != null && after != null && before.id != after.id) {
                SysPanel(Modifier.fillMaxWidth(), accent = ShadowViolet) {
                    TitleBox(tr("قبل / بعد", "BEFORE / AFTER"))
                    Spacer(Modifier.height(8.dp))
                    CompareSlider(before, after, ar)
                    Spacer(Modifier.height(6.dp))
                    // the slider is drawn left=before / right=after in both languages, so pin this caption row to LTR too
                    androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides androidx.compose.ui.unit.LayoutDirection.Ltr) { Row(Modifier.fillMaxWidth()) {
                        Label("◀ ${fmtDay(before.day, ar)}${before.weightKg?.let { " • %.1f".format(java.util.Locale.US, it) + tr("كغ", "kg") } ?: ""}", Modifier.weight(1f), size = 12)
                        Label("${fmtDay(after.day, ar)}${after.weightKg?.let { " • %.1f".format(java.util.Locale.US, it) + tr("كغ", "kg") } ?: ""} ▶", Modifier.weight(1f), size = 12, align = TextAlign.End)
                    } }
                    Label(deltaText(before, after, ar), Modifier.fillMaxWidth(), color = Sys.text, size = 14, align = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    if (busy) {
                        StatBar(tr("تصيير الفيديو", "Rendering video"), progress * 100, 100f, LocalPalette.current.secondary, valueText = "${(progress * 100).toInt()}%")
                    } else {
                        SysButton(tr("إنشاء فيديو قبل/بعد", "Make before/after video"), Modifier.fillMaxWidth(), sfx = Sfx.S.WIN_OPEN) {
                            busy = true; progress = 0f; videoErr = false
                            scope.launch {
                                val f = withContext(Dispatchers.Default) {
                                    runCatching {
                                        val b = PrivateFiles.load(ctx, before.file, 1400)!!; val a = PrivateFiles.load(ctx, after.file, 1400)!!
                                        BeforeAfterVideo.render(ctx, BeforeAfterVideo.Info(b, a,
                                            fmtDay(before.day, ar) + (before.weightKg?.let { " • %.1f".format(java.util.Locale.US, it) + (if (ar) " كغ" else " kg") } ?: ""),
                                            fmtDay(after.day, ar) + (after.weightKg?.let { " • %.1f".format(java.util.Locale.US, it) + (if (ar) " كغ" else " kg") } ?: ""),
                                            if (ar) "سجل التحوّل" else "TRANSFORMATION", deltaText(before, after, ar), ar),
                                            PrivateFiles.shareTarget(ctx, "transformation.mp4")) { p -> progress = p }
                                    }.getOrNull()
                                }
                                busy = false; video = f; videoErr = f == null
                                Sfx.play(if (f != null) Sfx.S.REWARD_RARE else Sfx.S.ERROR)
                            }
                        }
                    }
                    if (videoErr) Label(tr("تعذّر إنشاء الفيديو على هذا الجهاز.", "Couldn't create the video on this device."), Modifier.fillMaxWidth(), color = Sys.red, size = 12, align = TextAlign.Center)
                    video?.takeIf { it.exists() }?.let { f ->
                        Spacer(Modifier.height(8.dp))
                        AndroidView({ c -> android.widget.VideoView(c).apply { setVideoPath(f.absolutePath); setOnPreparedListener { mp -> mp.isLooping = true; mp.setVolume(0.6f, 0.6f); start() } } },
                            Modifier.fillMaxWidth().aspectRatio(9f / 16f).clipToBounds())
                        Spacer(Modifier.height(6.dp))
                        SysButton(tr("مشاركة الفيديو", "Share video"), Modifier.fillMaxWidth(), sfx = Sfx.S.YES) {
                            runCatching { PrivateFiles.share(ctx, f, "video/mp4", if (ar) "مشاركة فيديو التحوّل" else "Share transformation video") }
                        }
                    }
                }
            } else Label(tr("أضف صورة ثانية لاحقًا لترى المقارنة والفيديو.", "Add a second photo later to unlock the comparison and video."), Modifier.fillMaxWidth(), size = 13, align = TextAlign.Center)

            SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
                SectionHeader(tr("الصور (${photos.size})", "Photos (${photos.size})"))
                photos.sortedByDescending { it.day }.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { p ->
                            Thumb(p, Modifier.weight(1f), when (p.id) { before?.id -> tr("قبل", "BEFORE"); after?.id -> tr("بعد", "AFTER"); else -> null }) { viewing = p }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
        SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("الخصوصية والتذكير", "Privacy & reminder"))
            // re-check on every resume: the user may come back from Settings after setting a screen lock
            var secure by remember { mutableStateOf(deviceSecure(ctx)) }
            val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) secure = deviceSecure(ctx) }
                owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Label(tr("قفل بالبصمة / قفل الشاشة", "Lock with biometric / screen lock"), color = Sys.text, size = 14)
                    if (!secure) Label(tr("فعّل قفل شاشة للهاتف أولًا.", "Set a phone screen lock first."), size = 11, color = Sys.amber)
                }
                SysCheck(st.transform.lock, enabled = secure || st.transform.lock) { val on = !st.transform.lock; repo.set { Transform.setLock(it, on) }; if (on) TransformLock.unlocked = true }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Label(tr("تذكير أسبوعي (الاثنين صباحًا)", "Weekly reminder (Monday morning)"), Modifier.weight(1f), color = Sys.text, size = 14)
                SysCheck(st.transform.reminder) { val on = !st.transform.reminder; repo.set { Transform.setReminder(it, on) } }
            }
        }
    }

    viewing?.let { p ->
        FullScreenDialog({ viewing = null }) {
            var bmp by remember(p.id) { mutableStateOf<Bitmap?>(null) }
            LaunchedEffect(p.id) { bmp = withContext(Dispatchers.IO) { PrivateFiles.load(ctx, p.file, 1600) } }
            Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                }
                Label(fmtDay(p.day, ar) + (p.weightKg?.let { " • %.1f".format(java.util.Locale.US, it) + tr(" كغ", " kg") } ?: ""), color = Sys.text, size = 14)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton(tr("اجعلها \"قبل\"", "Set as BEFORE"), Modifier.weight(1f), small = true) { beforeId = p.id; viewing = null }
                    SysButton(tr("اجعلها \"بعد\"", "Set as AFTER"), Modifier.weight(1f), small = true) { afterId = p.id; viewing = null }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton(tr("حذف", "Delete"), Modifier.weight(1f), small = true, accent = Sys.red) { confirmDel = p }
                    SysButton(tr("إغلاق", "Close"), Modifier.weight(1f), small = true, accent = Sys.dim, sfx = Sfx.S.WIN_CLOSE) { viewing = null }
                }
            }
        }
    }
    confirmDel?.let { p ->
        ConfirmDialog(tr("حذف الصورة", "Delete photo"), tr("حذف هذه الصورة نهائيًا من التطبيق؟ لا يمكن التراجع.", "Permanently delete this photo from the app? This can't be undone."), tr("حذف", "Delete"),
            onYes = {
                repo.set { Transform.remove(it, p.id) }
                scope.launch(Dispatchers.IO) { PrivateFiles.file(ctx, p.file).delete() }
                if (beforeId == p.id) beforeId = null; if (afterId == p.id) afterId = null
                viewing = null; video = null
            }, onDismiss = { confirmDel = null })
    }
}

private fun deltaText(b: TransformPhoto, a: TransformPhoto, ar: Boolean): String {
    val days = (a.day - b.day).toInt()
    val kg = if (a.weightKg != null && b.weightKg != null) a.weightKg - b.weightKg else null
    val k = kg?.let { (if (it > 0) "+" else "") + "%.1f".format(java.util.Locale.US, it) + (if (ar) " كغ" else " kg") }
    return if (ar) "$days يوم" + (k?.let { " • $it" } ?: "") else "$days days" + (k?.let { " • $it" } ?: "")
}

@Composable
private fun Thumb(p: TransformPhoto, modifier: Modifier, tag: String?, onClick: () -> Unit) {
    val ctx = LocalContext.current
    var bmp by remember(p.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(p.id) { bmp = withContext(Dispatchers.IO) { PrivateFiles.load(ctx, p.file, 360) } }
    Column(modifier.thinBox(if (tag != null) LocalPalette.current.secondary else Sys.panelLine).clickable { Sfx.play(Sfx.S.SELECT); onClick() }.padding(3.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clipToBounds()) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            if (tag != null) Label(tag, Modifier.align(Alignment.TopStart).padding(3.dp), color = LocalPalette.current.secondary, size = 10)
        }
        Label(fmtDay(p.day, LocalArabic.current), Modifier.fillMaxWidth(), size = 10, align = TextAlign.Center)
    }
}

@Composable
private fun CompareSlider(b: TransformPhoto, a: TransformPhoto, ar: Boolean) {
    val ctx = LocalContext.current
    var bb by remember(b.id) { mutableStateOf<Bitmap?>(null) }
    var ab by remember(a.id) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(b.id) { bb = withContext(Dispatchers.IO) { PrivateFiles.load(ctx, b.file, 1000) } }
    LaunchedEffect(a.id) { ab = withContext(Dispatchers.IO) { PrivateFiles.load(ctx, a.file, 1000) } }
    var frac by remember { mutableFloatStateOf(0.5f) }
    val line = LocalPalette.current.secondary
    Box(Modifier.fillMaxWidth().aspectRatio(0.75f).clipToBounds().thinBox(Sys.panelLine)
        .pointerInput(Unit) {
            detectHorizontalDragGestures { ch, _ -> frac = (ch.position.x / size.width).coerceIn(0f, 1f) }
        }.pointerInput(Unit) { detectTapGestures { o -> frac = (o.x / size.width).coerceIn(0f, 1f) } }) {
        bb?.let { Image(it.asImageBitmap(), tr("قبل", "Before"), Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        ab?.let {
            Image(it.asImageBitmap(), tr("بعد", "After"), Modifier.fillMaxSize().drawWithContent { clipRect(left = size.width * frac) { this@drawWithContent.drawContent() } }, contentScale = ContentScale.Crop)
        }
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * frac
            drawLine(line.copy(alpha = 0.35f), Offset(x, 0f), Offset(x, size.height), 14f)
            drawLine(Color.White, Offset(x, 0f), Offset(x, size.height), 3f)
            drawCircle(line, 18f, Offset(x, size.height / 2))
            drawCircle(Color.White, 8f, Offset(x, size.height / 2))
        }
        Label(tr("قبل", "BEFORE"), Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopLeft).padding(6.dp).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, size = 12)
        Label(tr("بعد", "AFTER"), Modifier.align(androidx.compose.ui.AbsoluteAlignment.TopRight).padding(6.dp).background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, size = 12)
    }
}
