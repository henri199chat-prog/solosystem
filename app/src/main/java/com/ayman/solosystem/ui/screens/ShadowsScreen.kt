package com.ayman.solosystem.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.model.Shadow
import com.ayman.solosystem.domain.shadows.Shadows
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.sin
import kotlin.random.Random

val ShadowViolet = Color(0xFF8B5CFF)
private val ShadowDeep = Color(0xFF1A0B33)

@Composable
fun ShadowsScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    var arise by remember { mutableStateOf<String?>(null) }
    val pending = Shadows.pending(st, today)
    Screen(tr("جيش الظلال", "SHADOW ARMY"), nav) {
        SysPanel(Modifier.fillMaxWidth(), accent = ShadowViolet, pulse = pending.isNotEmpty()) {
            TitleBox(tr("الاستخراج", "EXTRACTION"), accent = ShadowViolet)
            Spacer(Modifier.height(6.dp))
            Label(tr("عدد محاولات الاستخراج الممكنة: ${Shadows.extractionsPossible(st, today)}/${Shadows.MAX_EXTRACTIONS}",
                "Number of extractions possible: ${Shadows.extractionsPossible(st, today)}/${Shadows.MAX_EXTRACTIONS}"), Modifier.fillMaxWidth(), color = Sys.text, size = 15, align = TextAlign.Center, w = FontWeight.Bold)
            Label(tr("سلسلة ${Shadows.EXTRACT_STREAK} يومًا متتالية على عادة واحدة تسمح باستخراج ظلّها.", "A ${Shadows.EXTRACT_STREAK}-day streak on one habit lets you extract its shadow."), Modifier.fillMaxWidth(), size = 12, align = TextAlign.Center)
            pending.take(Shadows.MAX_EXTRACTIONS).forEach { d ->
                Spacer(Modifier.height(8.dp))
                SysButton(tr("انهض! — ${d.name.ar}", "ARISE! — ${d.name.en}"), Modifier.fillMaxWidth(), accent = ShadowViolet, sfx = Sfx.S.SECRET) { arise = d.source }
            }
        }
        SysPanel(Modifier.fillMaxWidth(), accent = ShadowViolet) {
            SectionHeader(tr("السلاسل", "Streaks"))
            Shadows.defs.forEach { d ->
                val sh = st.shadows.army.firstOrNull { it.source == d.source }
                val n = Shadows.streak(st, d.source, today)
                val goal = if (sh?.asleep == true) Shadows.REVIVE_STREAK else Shadows.EXTRACT_STREAK
                val shown = if (sh?.asleep == true) Shadows.streak(st, d.source, today, sh.sleptDay) else n
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Label(d.title.get(ar), Modifier.weight(1f), color = Sys.text, size = 14)
                        Label(when { sh == null -> "${minOf(shown, goal)}/$goal"; sh.asleep -> tr("نائم ${minOf(shown, goal)}/$goal", "asleep ${minOf(shown, goal)}/$goal"); else -> tr("مُستخرج • سلسلة $n", "extracted • streak $n") },
                            color = if (sh != null && !sh.asleep) ShadowViolet else Sys.dim, size = 13)
                    }
                    if (sh == null || sh.asleep) Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                        drawRect(Sys.panelLine.copy(alpha = 0.3f))
                        drawRect(ShadowViolet, size = size.copy(width = size.width * (shown.toFloat() / goal).coerceIn(0f, 1f)))
                    }
                }
            }
        }
        if (st.shadows.army.isEmpty()) EmptyState(tr("لا ظلال بعد", "No shadows yet"), tr("حافظ على سلسلة 30 يومًا على هدف واحد (ضغط، سكوات، بطن، جري أو سجل الجاهزية).", "Keep a 30-day streak on one goal (push-ups, squats, sit-ups, running or the readiness log)."))
        st.shadows.army.forEach { sh -> ShadowCard(sh, ar) }
        if (st.shadows.army.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), accent = ShadowViolet) {
            SectionHeader(tr("قدرات الجيش (مرة في الشهر)", "Army powers (once a month)"))
            Label(tr("كل ظلّ مستيقظ: +${Shadows.XP_PCT}٪ XP على تمرينه.", "Each awake shadow: +${Shadows.XP_PCT}% XP on its exercise."), color = Sys.text, size = 13)
            Spacer(Modifier.height(6.dp))
            SysButton(tr("حارس الظل: احمِ يومًا فاشلًا", "Shadow Guard: protect a failed day"), Modifier.fillMaxWidth(), enabled = Shadows.guardReady(st, today), accent = ShadowViolet) { repo.apply { s -> Shadows.guard(s, today, repo.now()) } }
            Spacer(Modifier.height(6.dp))
            SysButton(tr("تجميد السلسلة: اليوم راحة محمية", "Streak Freeze: today is a protected rest"), Modifier.fillMaxWidth(), enabled = Shadows.freezeReady(st, today), accent = ShadowViolet) { repo.apply { s -> Shadows.freeze(s, today, repo.now()) } }
            if (Shadows.awake(st).isEmpty()) Label(tr("كل الظلال نائمة — القدرات معطّلة حتى يستيقظ ظل.", "All shadows asleep — powers are off until one revives."), color = Sys.amber, size = 12)
        }
    }
    arise?.let { src -> AriseScene(src, Shadows.extractionsPossible(st, today), ar, onArise = { repo.apply { s -> Shadows.arise(s, src, today, repo.now()) } }, dismiss = { arise = null }) }
}

@Composable
private fun ShadowCard(sh: Shadow, ar: Boolean) {
    val d = Shadows.def(sh.source)
    SysPanel(Modifier.fillMaxWidth(), accent = if (sh.asleep) Sys.dim else ShadowViolet, flares = !sh.asleep) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(64.dp)) { drawShadowFigure(this, sh.source, if (sh.asleep) 0.35f else 1f, 0f) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GlowText(d.name.get(ar), body(19.sp, FontWeight.Bold), glow = if (sh.asleep) Sys.dim else ShadowViolet)
                Label(d.title.get(ar), size = 13)
                Label(if (sh.asleep) tr("نائم — 7 أيام متتالية توقظه", "Asleep — a 7-day streak revives it") else tr("مستيقظ • +${Shadows.XP_PCT}٪ XP", "Awake • +${Shadows.XP_PCT}% XP"),
                    color = if (sh.asleep) Sys.amber else ShadowViolet, size = 12)
                Label(tr("استُخرج: ${fmtDay(sh.extractedDay, true)}", "Extracted: ${fmtDay(sh.extractedDay, false)}") + if (sh.revived > 0) tr(" • أُحيي ${sh.revived}×", " • revived ${sh.revived}×") else "", size = 11)
            }
        }
    }
}

/** Original silhouette: hooded knight/beast shape with glowing eyes, rising by [rise] (0 = fully visible, 1 = below ground). */
fun drawShadowFigure(ds: DrawScope, source: String, alpha: Float, rise: Float): Unit = with(ds) {
    val w = size.width; val h = size.height
    val dy = rise * h
    val bulk = when (source) { "squat" -> 1.25f; "situp" -> 1.1f; "run" -> 0.9f; else -> 1f }
    fun x(f: Float) = w * (0.5f + (f - 0.5f) * bulk)
    // humanoid silhouette: helmet, pauldrons, torso, tattered cape, legs
    val body = Path().apply {
        moveTo(x(.44f), h * .20f); quadraticTo(w * .5f, h * .07f, x(.56f), h * .20f)            // helmet
        lineTo(x(.55f), h * .27f); lineTo(x(.74f), h * .30f); quadraticTo(x(.82f), h * .33f, x(.80f), h * .40f) // right pauldron
        lineTo(x(.70f), h * .42f); lineTo(x(.66f), h * .60f)                                      // right flank
        lineTo(x(.84f), h * .98f); lineTo(x(.74f), h * .93f); lineTo(x(.68f), h)                 // cape tatters
        lineTo(x(.58f), h); lineTo(x(.55f), h * .70f); lineTo(x(.45f), h * .70f); lineTo(x(.42f), h) // legs
        lineTo(x(.32f), h); lineTo(x(.26f), h * .93f); lineTo(x(.16f), h * .98f); lineTo(x(.34f), h * .60f)
        lineTo(x(.30f), h * .42f); lineTo(x(.20f), h * .40f); quadraticTo(x(.18f), h * .33f, x(.26f), h * .30f)
        lineTo(x(.45f), h * .27f); close()
    }
    val extra = Path().apply {
        when (source) {
            "push" -> { // Igris: plume + greatsword
                moveTo(w * .5f, h * .08f); cubicTo(w * .62f, h * .02f, w * .72f, h * .06f, w * .80f, h * .14f); cubicTo(w * .68f, h * .10f, w * .6f, h * .11f, w * .52f, h * .13f); close()
                moveTo(x(.80f), h * .36f); lineTo(x(.83f), h * .36f); lineTo(x(.86f), h * .92f); lineTo(x(.815f), h * .98f); lineTo(x(.79f), h * .92f); close()
                moveTo(x(.74f), h * .40f); lineTo(x(.90f), h * .40f); lineTo(x(.90f), h * .42f); lineTo(x(.74f), h * .42f); close()
            }
            "squat" -> { // Tank: bear ears
                addOval(androidx.compose.ui.geometry.Rect(x(.38f), h * .09f, x(.46f), h * .16f)); addOval(androidx.compose.ui.geometry.Rect(x(.54f), h * .09f, x(.62f), h * .16f))
            }
            "situp" -> { // Iron: tower shield
                moveTo(x(.12f), h * .38f); lineTo(x(.34f), h * .36f); lineTo(x(.34f), h * .78f); quadraticTo(x(.23f), h * .88f, x(.12f), h * .78f); close()
            }
            "run" -> { // Beru: insect wings + antennae
                moveTo(x(.34f), h * .34f); cubicTo(x(.0f), h * .10f, x(-.05f), h * .45f, x(.32f), h * .52f); close()
                moveTo(x(.66f), h * .34f); cubicTo(x(1f), h * .10f, x(1.05f), h * .45f, x(.68f), h * .52f); close()
                moveTo(w * .47f, h * .12f); lineTo(w * .38f, h * .0f); lineTo(w * .395f, h * .0f); lineTo(w * .48f, h * .11f); close()
                moveTo(w * .53f, h * .12f); lineTo(w * .62f, h * .0f); lineTo(w * .605f, h * .0f); lineTo(w * .52f, h * .11f); close()
            }
            else -> { // Tusk: staff with orb
                moveTo(x(.18f), h * .2f); lineTo(x(.21f), h * .2f); lineTo(x(.21f), h); lineTo(x(.18f), h); close()
                addOval(androidx.compose.ui.geometry.Rect(x(.14f), h * .12f, x(.25f), h * .2f))
            }
        }
    }
    translate(0f, dy) {
        val fill = Brush.verticalGradient(listOf(Color(0xFF2B1255).copy(alpha = alpha), Color(0xFF05020C).copy(alpha = alpha)))
        drawPath(extra, fill); drawPath(extra, ShadowViolet.copy(alpha = 0.6f * alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.012f))
        drawPath(body, fill)
        drawPath(body, ShadowViolet.copy(alpha = 0.75f * alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.014f))
        val ey = h * .185f
        listOf(.465f, .535f).forEach { ex ->
            drawCircle(Color(0xFFB9A2FF).copy(alpha = 0.35f * alpha), w * 0.035f, Offset(w * ex, ey))
            drawCircle(Color(0xFFE6DCFF).copy(alpha = alpha), w * 0.014f, Offset(w * ex, ey))
        }
    }
}

@Composable
private fun AriseScene(source: String, possible: Int, ar: Boolean, onArise: () -> Unit, dismiss: () -> Unit) {
    val d = Shadows.def(source)
    var phase by remember { mutableIntStateOf(0) } // 0 prompt, 1 rising, 2 done
    val rise = remember { Animatable(1f) }
    val t = rememberInfiniteTransition(label = "smoke")
    val tick by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "tick")
    val parts = remember { List(36) { Triple(Random.nextFloat(), Random.nextFloat(), Random.nextFloat()) } }
    LaunchedEffect(phase) {
        if (phase == 1) {
            Sfx.play(Sfx.S.SYSTEM_AWAKEN)
            rise.animateTo(0f, tween(2200, easing = FastOutSlowInEasing))
            onArise(); phase = 2
            Sfx.play(Sfx.S.REWARD_LEGENDARY)
            delay(400)
        }
    }
    FullScreenDialog(dismiss) {
        Canvas(Modifier.fillMaxSize()) {
            // ground glow + rising smoke
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, ShadowDeep, ShadowViolet.copy(alpha = if (phase >= 1) 0.55f else 0.25f)), startY = size.height * 0.4f))
            parts.forEach { (x, s, o) ->
                val p = (tick + o) % 1f
                val y = size.height * (1f - p * (0.5f + s * 0.4f))
                val c = Offset(size.width * x + sin((p + o) * 12f) * 30f, y); val r = size.width * (0.06f + s * 0.12f) * (0.6f + p)
                drawCircle(Brush.radialGradient(listOf(ShadowViolet.copy(alpha = (1f - p) * 0.28f), Color.Transparent), c, r), r, c)
            }
            if (phase >= 1) {
                // ground crack
                drawLine(Color(0xFFE6DCFF), Offset(size.width * 0.15f, size.height * 0.78f), Offset(size.width * 0.85f, size.height * 0.78f), 6f * (1f - rise.value) + 1f)
                val fw = size.width * 0.6f; val fh = size.height * 0.45f
                translate(size.width * 0.2f, size.height * 0.78f - fh) {
                    clipRect(0f, -fh, fw, fh) { drawShadowFigureSized(this, source, fw, fh, rise.value) }
                }
            }
        }
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(60.dp))
            SysPanel(Modifier.fillMaxWidth(), accent = ShadowViolet) {
                TitleBox(tr("إشعار", "NOTIFICATION"), accent = ShadowViolet)
                Spacer(Modifier.height(6.dp))
                Label(if (phase < 2) tr("عدد محاولات الاستخراج الممكنة: $possible/${Shadows.MAX_EXTRACTIONS}", "Number of extractions possible: $possible/${Shadows.MAX_EXTRACTIONS}")
                      else tr("تم استخراج الظل: ${d.name.ar}", "Shadow extracted: ${d.name.en}"), Modifier.fillMaxWidth(), color = Sys.text, size = 16, align = TextAlign.Center, w = FontWeight.Bold)
                if (phase == 2) Label(d.title.get(ar), Modifier.fillMaxWidth(), color = ShadowViolet, size = 14, align = TextAlign.Center)
            }
            Spacer(Modifier.weight(1f))
            when (phase) {
                0 -> SysButton(tr("انهض", "ARISE"), Modifier.fillMaxWidth().heightIn(min = 64.dp), accent = ShadowViolet, sfx = null) { phase = 1 }
                2 -> SysButton(tr("تأكيد", "OK"), Modifier.fillMaxWidth(), accent = ShadowViolet) { dismiss() }
                else -> GlowText(tr("انهض…", "ARISE…"), body(34.sp, FontWeight.Bold), glow = ShadowViolet)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun drawShadowFigureSized(ds: DrawScope, source: String, w: Float, h: Float, rise: Float): Unit = with(ds) {
    // draw the figure into a w×h box
    scale(w / size.width, h / size.height, Offset.Zero) { drawShadowFigure(this, source, 1f, rise) }
}
