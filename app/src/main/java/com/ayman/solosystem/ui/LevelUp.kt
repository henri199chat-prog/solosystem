package com.ayman.solosystem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.random.Random

private val Cyan get() = SystemTheme.c(0xFF00E5FF, 0xFFC86BFF)
private val LvBg get() = SystemTheme.c(0xE6050A15, 0xE60C0516)
private val Vein get() = SystemTheme.c(0xFF1E5BFF, 0xFF8B2BFF)
private val VeinHi get() = SystemTheme.c(0xFF7FB2FF, 0xFFE0A8FF)

/** Normalised vein polylines for the left screen edge (mirrored for the right). */
private val VEINS: List<List<Offset>> = run {
    val r = Random(3141)
    val out = mutableListOf<List<Offset>>()
    fun branch(x0: Float, y0: Float, dx: Float, dy: Float, depth: Int) {
        val pts = mutableListOf(Offset(x0, y0)); var x = x0; var y = y0
        val n = 6 + r.nextInt(6)
        repeat(n) { x += dx * (0.5f + r.nextFloat()) + (r.nextFloat() - 0.5f) * 0.03f; y += dy * (0.5f + r.nextFloat()) + (r.nextFloat() - 0.5f) * 0.04f; pts += Offset(x.coerceIn(-0.05f, 0.15f), y) }
        out += pts
        if (depth < 1) for (i in 1 until pts.size step 3) if (r.nextFloat() < 0.5f) branch(pts[i].x, pts[i].y, dx * 0.6f, (r.nextFloat() - 0.5f) * 0.06f, depth + 1)
    }
    for (k in 0 until 7) branch(-0.03f, 0.05f + k * 0.15f + r.nextFloat() * 0.05f, 0.014f, (r.nextFloat() - 0.5f) * 0.05f, 0)
    out
}

/**
 * Anime LEVEL-UP notification: 0–0.25 s a bright cyan line strikes across the centre (ping), 0.25–0.5 s it expands into the window;
 * screen dims (rgba 0,5,15,.8) with deep-blue energy veins on the far left/right edges. Body: "لقد أكملت" + "[المهمة اليومية]",
 * then from ~1.5 s one "ارتفع مستواك!" per level gained every 0.4 s with a blip. Closes in reverse on tap or 2.5 s after the last line.
 */
@Composable
fun LevelUpWindow(e: UiEffect.LevelUp, dismiss: () -> Unit) {
    val ar = LocalArabic.current
    val reduced = LocalReducedMotion.current
    val ctl = LocalWindowCtl.current
    val gained = (e.to - e.from).coerceAtLeast(1)
    val shown = gained.coerceAtMost(8)
    val line = remember { Animatable(if (reduced) 1f else 0f) }     // line strike width
    val open = remember { Animatable(if (reduced) 1f else 0f) }     // vertical expansion
    var lines by remember { mutableIntStateOf(if (reduced) shown else 0) }
    var info by remember { mutableStateOf(reduced) }
    val clock = rememberInfiniteTransition(label = "veins").animateFloat(0f, 1f, infiniteRepeatable(tween(3000, easing = LinearEasing)), label = "v")
    val closing = ctl?.closing?.value == true
    LaunchedEffect(Unit) {
        Sfx.play(Sfx.S.PING)   // Ayman's original anime level-up clip — kept as-is
        if (reduced) { delay(1700); Sfx.play(Sfx.S.LEVELUP_TAIL); return@LaunchedEffect }
        line.animateTo(1f, tween(250, easing = FastOutSlowInEasing))
        open.animateTo(1f, tween(250, easing = LinearOutSlowInEasing))
        delay(1000)
        for (i in 1..shown) { lines = i; Sfx.play(Sfx.S.BLIP); delay(400) }
        info = true
        Sfx.play(Sfx.S.LEVELUP_TAIL)   // subtle synthesized shimmer tail layered after the original clip
        delay(2500)
        dismiss()
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        Sfx.play(Sfx.S.WIN_CLOSE)
        if (!reduced) { open.animateTo(0f, tween(180, easing = FastOutLinearInEasing)); line.animateTo(0f, tween(160)) }
        ctl?.onClosed?.invoke()
    }
    BackHandler(!closing) { dismiss() }
    val o = open.value; val l = line.value
    Box(Modifier.fillMaxSize()
        .drawBehind {
            val dim = (0.8f * maxOf(o, l * 0.4f))
            drawRect(SystemTheme.c(0xFF00050F, 0xFF07000F).copy(alpha = dim))
            // energy veins on the far left & right edges
            val t = clock.value
            VEINS.forEachIndexed { i, v ->
                val pulse = 0.35f + 0.65f * (0.5f + 0.5f * kotlin.math.sin((t * 6.283f) + i * 0.9f))
                val a = o * pulse
                if (a < 0.02f) return@forEachIndexed
                for (side in 0..1) {
                    val p = Path()
                    v.forEachIndexed { j, pt ->
                        val x = if (side == 0) pt.x * size.width else size.width - pt.x * size.width
                        val y = pt.y * size.height
                        if (j == 0) p.moveTo(x, y) else p.lineTo(x, y)
                    }
                    drawPath(p, Vein.copy(alpha = 0.07f * a), style = Stroke(8.dp.toPx()), blendMode = BlendMode.Plus)
                    drawPath(p, Vein.copy(alpha = 0.25f * a), style = Stroke(2.5.dp.toPx()), blendMode = BlendMode.Plus)
                    drawPath(p, VeinHi.copy(alpha = 0.45f * a), style = Stroke(0.8.dp.toPx()), blendMode = BlendMode.Plus)
                }
            }
            // the cyan strike line across the centre
            if (o < 0.98f && l > 0f) {
                val w = size.width * 0.92f * l; val y = size.height / 2; val x0 = (size.width - w) / 2
                val fade = 1f - o
                listOf(16f to 0.10f, 8f to 0.25f, 3f to 0.9f).forEach { (sw, a) -> drawLine(Cyan.copy(alpha = a * fade), Offset(x0, y), Offset(x0 + w, y), sw.dp.toPx(), StrokeCap.Round, blendMode = BlendMode.Plus) }
                drawLine(Color.White.copy(alpha = 0.9f * fade), Offset(x0, y), Offset(x0 + w, y), 1.2.dp.toPx(), blendMode = BlendMode.Plus)
            }
        }
        .clickable(remember { MutableInteractionSource() }, null) { if (!closing) dismiss() }, contentAlignment = Alignment.Center) {
        if (o > 0.01f) Column(Modifier.widthIn(max = 430.dp).fillMaxWidth().padding(horizontal = 26.dp)
            .graphicsLayer { scaleY = o.coerceAtLeast(0.01f); alpha = (o * 1.6f).coerceAtMost(1f) }
            .drawBehind {
                drawRect(LvBg)
                listOf(10f to 0.08f, 5f to 0.18f).forEach { (w, a) -> drawRect(Cyan.copy(alpha = a), style = Stroke(w.dp.toPx())) }
                drawRect(Cyan.copy(alpha = 0.85f), style = Stroke(1.dp.toPx()))
            }.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) { OctagonBang(30) }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f).height(44.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) {
                        GlowText(tr("إشعار", "NOTIFICATION"), body(20.sp, FontWeight.Bold), glow = SysWhite)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            GlowText(tr("لقد أكملت", "You have completed"), body(17.sp, FontWeight.Medium), Modifier.fillMaxWidth(), glow = SysWhite, align = TextAlign.Center)
            GlowText("[" + (e.source?.get(ar) ?: tr("المهمة اليومية", "Daily Quest")) + "]", body(18.sp, FontWeight.Bold), Modifier.fillMaxWidth(), glow = SysWhite, align = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.heightIn(min = (shown * 30).dp), horizontalAlignment = Alignment.CenterHorizontally) {
                for (i in 1..lines) key(i) { LevelLine(tr("ارتفع مستواك!", "You have leveled up!"), reduced) }
                if (gained > shown && lines == shown) Label("+${gained - shown}", color = SysWhite, size = 14, align = TextAlign.Center)
            }
            if (info) {
                Spacer(Modifier.height(10.dp))
                Label(tr("المستوى ${e.from} ← ${e.to}", "Level ${e.from} → ${e.to}"), color = Cyan, size = 14, align = TextAlign.Center, w = FontWeight.Bold)
                e.newTitle?.let { Label(tr("لقب جديد: ${it.ar}", "New title: ${it.en}"), color = LocalPalette.current.secondary, size = 13, align = TextAlign.Center) }
                if (e.points > 0) Label(tr("+${e.points} نقاط سمات متاحة", "+${e.points} attribute points available"), color = Sys.green, size = 13, align = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun LevelLine(text: String, reduced: Boolean) {
    val f = remember { Animatable(if (reduced) 0f else 1f) }
    LaunchedEffect(Unit) { if (!reduced) f.animateTo(0f, tween(260)) }
    Box(Modifier.padding(vertical = 3.dp).graphicsLayer { val v = f.value; scaleX = 1f + 0.06f * v; scaleY = 1f + 0.06f * v; alpha = 1f - 0.4f * v }, contentAlignment = Alignment.Center) {
        GlowText(text, body(18.sp, FontWeight.Bold, Color.White), glow = if (f.value > 0.3f) Cyan else SysWhite, align = TextAlign.Center)
    }
}
