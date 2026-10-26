package com.ayman.solosystem.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ayman.solosystem.ui.theme.LocalReducedMotion
import com.ayman.solosystem.ui.theme.Palette
import com.ayman.solosystem.ui.theme.SystemTheme
import kotlin.random.Random

/* ===================================================================================
 * Shared FX: one frame clock for every panel (every vsync, 120 Hz on capable phones, paused when the activity is not
 * RESUMED), stationary crackling energy flares with spark particles, holographic scan
 * sweep, content-change flash, and the CRT open/close used by notification windows.
 * =================================================================================== */

object FxColors {
    val Cyan get() = SystemTheme.c(0xFF00E5FF, 0xFFC86BFF)
    val Electric get() = SystemTheme.c(0xFF0055FF, 0xFF7A1FFF)
    val PanelFill get() = SystemTheme.c(0xBF0B1120, 0xBF10071C) // #0B1120 @ 75%
}

/** Seconds since start, updated by a single frame loop. Read ONLY in draw lambdas (no recomposition). */
class FxClock { val seconds = mutableFloatStateOf(0f) }
val LocalFxClock = staticCompositionLocalOf { FxClock() }

/**
 * The single FX frame loop. Writes one float per frame (read only in draw lambdas → no recomposition).
 * [maxFps] caps the update rate (Low intensity = 60). If the device can't keep up (avg frame > 45 ms, e.g. a software
 * emulator) it backs off to ~12 updates/s so input is never starved (no ANR).
 */
@Composable
fun ProvideFxClock(maxFps: Int = 120, content: @Composable () -> Unit) {
    val clock = remember { FxClock() }
    val reduced = LocalReducedMotion.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(reduced, lifecycle, maxFps) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var start = -1L; var last = -1L; var avg = 8_000_000.0
            val minGap = 1_000_000_000L / maxFps.coerceIn(10, 240) - 1_500_000L
            while (true) {
                val n = withFrameNanos { it }
                if (start < 0) start = n - (clock.seconds.floatValue * 1e9f).toLong()
                if (last > 0) avg = avg * 0.9 + (n - last) * 0.1
                if (last > 0 && n - last < minGap) continue
                last = n
                clock.seconds.floatValue = ((n - start) / 1e9).rem(3600.0).toFloat()
                if (avg > 45_000_000.0) kotlinx.coroutines.delay(80)
            }
        }
    }
    CompositionLocalProvider(LocalFxClock provides clock, content = content)
}

private fun hash(i: Long, salt: Int = 0): Float {
    var x = i * 0x9E3779B97F4A7C15uL.toLong() + salt * 0x632BE59BD9B4E019L
    x = (x xor (x ushr 31)) * 0x7FB5D329728EA185L; x = x xor (x ushr 27)
    return ((x ushr 40).toFloat() / (1L shl 24).toFloat())
}

/** Crackling flicker: stepped noise at two incommensurate rates (≈70 ms / 130 ms) → 50–150 ms random changes. */
fun flicker(t: Float, salt: Int): Pair<Float, Float> {
    val a = hash((t * 1000 / 70).toLong(), salt); val b = hash((t * 1000 / 130).toLong(), salt + 17)
    val n = (a * 0.6f + b * 0.4f)
    return (0.4f + 0.6f * n) to (0.9f + 0.2f * hash((t * 1000 / 95).toLong(), salt + 5)) // alpha 0.4..1, radius ±10%
}

private class FxCache(val frame: Path, val inner: Path, val circuit: Path, val nodes: List<Offset>)

private fun CacheDrawScope.buildFx(pal: Palette): FxCache {
    val w = size.width; val h = size.height; val ch = 12.dp.toPx(); val nt = 6.dp.toPx()
    // chamfered + notched outer frame
    val frame = Path().apply {
        moveTo(ch, 0f); lineTo(w * 0.3f, 0f); lineTo(w * 0.3f + nt, nt); lineTo(w * 0.7f - nt, nt); lineTo(w * 0.7f, 0f); lineTo(w - ch, 0f); lineTo(w, ch)
        lineTo(w, h * 0.35f); lineTo(w - nt, h * 0.35f + nt); lineTo(w - nt, h * 0.65f - nt); lineTo(w, h * 0.65f); lineTo(w, h - ch); lineTo(w - ch, h)
        lineTo(w * 0.7f, h); lineTo(w * 0.7f - nt, h - nt); lineTo(w * 0.3f + nt, h - nt); lineTo(w * 0.3f, h); lineTo(ch, h); lineTo(0f, h - ch)
        lineTo(0f, h * 0.65f); lineTo(nt, h * 0.65f - nt); lineTo(nt, h * 0.35f + nt); lineTo(0f, h * 0.35f); lineTo(0f, ch); close()
    }
    val i = 9.dp.toPx()
    val inner = Path().apply { addRect(Rect(i, i, w - i, h - i)) }
    val r = Random((w.toInt() * 31 + h.toInt()))
    val circuit = Path(); val nodes = mutableListOf<Offset>()
    repeat((w * h / 9000f).toInt().coerceIn(8, 40)) {
        var x = r.nextFloat() * w; var y = r.nextFloat() * h
        circuit.moveTo(x, y)
        repeat(3) { if (r.nextBoolean()) x = (x + (r.nextFloat() - 0.5f) * 160f).coerceIn(i, w - i) else y = (y + (r.nextFloat() - 0.5f) * 120f).coerceIn(i, h - i); circuit.lineTo(x, y) }
        nodes += Offset(x, y)
    }
    return FxCache(frame, inner, circuit, nodes)
}

/** One stationary energy flare on a vertical edge: white/cyan core + big electric-blue halo + sparks. */
private fun DrawScope.flare(c: Offset, scale: Float, t: Float, salt: Int, reduced: Boolean, outward: Float) {
    val (fa, fr) = if (reduced) 0.75f to 1f else flicker(t, salt)
    val R = 70.dp.toPx() * scale * fr
    drawCircle(Brush.radialGradient(listOf(FxColors.Electric.copy(alpha = 0.55f * fa), FxColors.Electric.copy(alpha = 0.18f * fa), Color.Transparent), c, R), R, c)
    drawCircle(Brush.radialGradient(listOf(FxColors.Cyan.copy(alpha = 0.8f * fa), Color.Transparent), c, R * 0.35f), R * 0.35f, c)
    // vertical streak along the edge + hot core
    val len = 46.dp.toPx() * scale * fr
    drawLine(Brush.verticalGradient(listOf(Color.Transparent, FxColors.Cyan.copy(alpha = fa), Color.Transparent), c.y - len, c.y + len), Offset(c.x, c.y - len), Offset(c.x, c.y + len), 3.dp.toPx() * scale)
    drawLine(Brush.verticalGradient(listOf(Color.Transparent, Color.White.copy(alpha = fa), Color.Transparent), c.y - len * 0.5f, c.y + len * 0.5f), Offset(c.x, c.y - len * 0.5f), Offset(c.x, c.y + len * 0.5f), 1.6.dp.toPx() * scale)
    drawCircle(Color.White.copy(alpha = 0.9f * fa), 3.dp.toPx() * scale, c)
    if (reduced) return
    // sparks: stateless, deterministic from time; 18 + 10 per panel (≤ 28)
    val n = if (scale > 0.8f) 18 else 10
    for (k in 0 until n) {
        val life = 1.0f + 0.5f * hash(k.toLong(), salt + 99)
        val tt = t + k * life / n
        val gen = (tt / life).toLong(); val age = (tt % life) / life
        val dx = (0.2f + hash(gen * 31 + k, salt) * 0.8f) * outward * 26.dp.toPx() * age
        val dy = -(20.dp.toPx() + hash(gen * 17 + k, salt + 3) * 50.dp.toPx()) * age
        val jx = (hash(gen * 7 + k, salt + 4) - 0.5f) * 14.dp.toPx()
        val p = Offset(c.x + dx + jx * 0.4f, c.y + (hash(gen * 5 + k, salt + 6) - 0.5f) * len + dy)
        val a = (1f - age) * (0.6f + 0.4f * hash(gen, salt + k))
        val rad = (1f + 2f * hash(gen * 3 + k, salt + 8)) * density * 0.5f + 0.5f
        drawCircle(FxColors.Cyan.copy(alpha = 0.35f * a), rad * 3f, p)
        drawCircle(Color.White.copy(alpha = a), rad, p)
    }
}

/** Panel border blue (#38BDF8) used for the thick bloomed corners. */
val PanelBorder get() = SystemTheme.c(0xFF38BDF8, 0xFFB45CFF)
private val PanelGlass get() = SystemTheme.c(0xBF0B1120, 0xBF10071C)   // #0B1120 @ 75% → the animated background shows through, tinted
private val PanelGrid get() = SystemTheme.c(0x1A1E293B, 0x1A3B1E4F)    // #1E293B @ 10%

/**
 * STATIC part of the shared SystemPanel (cached, never reads the clock): translucent navy glass, faint square grid,
 * circuit texture, chamfered/notched electric frame with layered bloom, crisp inner line and thick bloomed corners.
 */
fun Modifier.systemPanelStatic(pal: Palette, accent: Color? = null, glow: Float = 1f): Modifier {
    val frameColor = accent ?: pal.frame
    val lineColor = accent ?: FxColors.Cyan
    val cornerColor = accent ?: PanelBorder
    return this.drawWithCache {
        val fx = buildFx(pal)
        val sw = 1.5.dp.toPx()
        val grid = Path().apply { val g = 14.dp.toPx(); var x = g; while (x < size.width) { moveTo(x, 0f); lineTo(x, size.height); x += g }; var y = g; while (y < size.height) { moveTo(0f, y); lineTo(size.width, y); y += g } }
        val cl = 18.dp.toPx(); val w = size.width; val h = size.height; val ins = 2.dp.toPx()
        val corners = Path().apply {
            moveTo(ins, ins + cl); lineTo(ins, ins); lineTo(ins + cl, ins)
            moveTo(w - ins - cl, ins); lineTo(w - ins, ins); lineTo(w - ins, ins + cl)
            moveTo(w - ins, h - ins - cl); lineTo(w - ins, h - ins); lineTo(w - ins - cl, h - ins)
            moveTo(ins + cl, h - ins); lineTo(ins, h - ins); lineTo(ins, h - ins - cl)
        }
        onDrawBehind {
            drawPath(fx.frame, PanelGlass)
            clipPath(fx.frame) { drawPath(grid, PanelGrid, style = Stroke(1f)) }
            drawPath(fx.circuit, frameColor.copy(alpha = 0.07f), style = Stroke(1f))
            fx.nodes.forEach { drawCircle(frameColor.copy(alpha = 0.12f), 1.6.dp.toPx(), it) }
            listOf(9f to 0.05f, 5f to 0.1f, 2.6f to 0.22f).forEach { (k, a) -> drawPath(fx.frame, frameColor.copy(alpha = a * glow), style = Stroke(sw * k, join = StrokeJoin.Miter)) }
            drawPath(fx.frame, frameColor.copy(alpha = 0.9f * glow), style = Stroke(sw, join = StrokeJoin.Miter))
            drawPath(fx.inner, lineColor.copy(alpha = 0.85f), style = Stroke(1.2.dp.toPx()))
            // thicker corners with bloom
            listOf(7f to 0.08f, 4f to 0.16f, 2.2f to 0.35f).forEach { (k, a) -> drawPath(corners, cornerColor.copy(alpha = a * glow), style = Stroke(sw * k, cap = StrokeCap.Square)) }
            drawPath(corners, cornerColor, style = Stroke(sw * 1.9f, cap = StrokeCap.Square))
            drawPath(corners, Color.White.copy(alpha = 0.75f), style = Stroke(sw * 0.6f))
        }
    }
}

/**
 * ANIMATED part of the SystemPanel in its OWN RenderNode (graphicsLayer): holographic scan sweep and two crackling
 * edge flares with sparks. Only this small layer is re-recorded per frame; the panel content is never redrawn.
 */
@Composable
fun PanelLiveFx(pal: Palette, accent: Color?, flares: Boolean, modifier: Modifier = Modifier) {
    val clock = LocalFxClock.current
    val reduced = LocalReducedMotion.current
    val view = LocalView.current
    var visible by remember { mutableStateOf(true) }
    Spacer(modifier
        .onGloballyPositioned { val b = it.boundsInWindow(); val v = b.height > 1f && b.bottom > 0f && b.top < view.height; if (v != visible) visible = v }
        .graphicsLayer()
        .drawWithCache {
            val fx = buildFx(pal)
            onDrawBehind {
                val live = visible && !reduced
                val t = if (live) clock.seconds.floatValue else 0f
                if (live) {
                    val band = size.height * 0.28f
                    val y = (t % 3.5f) / 3.5f * (size.height + band) - band
                    clipPath(fx.frame) { drawRect(Brush.verticalGradient(listOf(Color.Transparent, SystemTheme.c(0xFFBFF6FF, 0xFFEBC8FF).copy(alpha = 0.08f), Color.Transparent), y, y + band)) }
                }
                if (flares && size.height > 60.dp.toPx()) {
                    flare(Offset(0f, size.height * 0.58f), 1f, t, 11, !live, -1f)
                    flare(Offset(size.width, size.height * 0.4f), 0.6f, t + 0.37f, 23, !live, 1f)
                }
            }
        })
}

/**
 * DATA REFRESH beam: when [key] changes, a thick white-core / cyan (#06B6D4) bloomed horizontal beam sweeps top → bottom
 * in ~0.35 s and the content is revealed in its wake. Reads the animation only in the draw phase.
 */
@Composable
fun Modifier.refreshBeam(key: Any?): Modifier {
    val reduced = LocalReducedMotion.current
    val a = remember { Animatable(1f) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(key) { if (first) { first = false; return@LaunchedEffect }; if (!reduced) { a.snapTo(0f); a.animateTo(1f, tween(360, easing = LinearEasing)) } }
    return this.drawWithContent {
        val v = a.value
        if (v >= 1f) { drawContent(); return@drawWithContent }
        val pad = 24.dp.toPx()
        val y = v * (size.height + pad * 2) - pad
        clipRect(bottom = y.coerceIn(0f, size.height)) { this@drawWithContent.drawContent() }
        val wake = 70.dp.toPx(); val bw = 20.dp.toPx()
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, BeamCyan.copy(alpha = 0.16f)), y - wake, y), Offset(0f, y - wake), Size(size.width, wake))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, BeamCyan.copy(alpha = 0.6f), Color.Transparent), y - bw, y + bw), Offset(0f, y - bw), Size(size.width, bw * 2), blendMode = BlendMode.Plus)
        drawRect(BeamCyan, Offset(0f, y - 2.5.dp.toPx()), Size(size.width, 5.dp.toPx()))
        drawRect(Color.White, Offset(0f, y - 1.dp.toPx()), Size(size.width, 2.dp.toPx()))
    }
}
val BeamCyan get() = SystemTheme.c(0xFF06B6D4, 0xFFD946EF)

/**
 * CRT open/close. [progress] 0 = gone (dot), 1 = full panel.
 * 0..0.35: dot → thin bright line (scaleX), 0.35..1: line → panel (scaleY).
 */
fun crtScale(progress: Float): Pair<Float, Float> {
    val p = progress.coerceIn(0f, 1f)
    return if (p < 0.35f) (p / 0.35f).coerceAtLeast(0.02f) to 0.012f else 1f to (0.012f + (p - 0.35f) / 0.65f * 0.988f)
}

@Composable
fun CrtLine(progress: Float, modifier: Modifier = Modifier) {
    val (sx, sy) = crtScale(progress)
    if (sy > 0.25f || progress >= 1f) return
    Canvas(modifier) {
        val w = size.width * sx; val cx = size.width / 2; val cy = size.height / 2
        val k = 1f - sy * 3f
        drawLine(FxColors.Cyan.copy(alpha = 0.35f * k), Offset(cx - w / 2, cy), Offset(cx + w / 2, cy), 10.dp.toPx(), StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.95f * k), Offset(cx - w / 2, cy), Offset(cx + w / 2, cy), 2.dp.toPx(), StrokeCap.Round)
        drawCircle(Color.White.copy(alpha = k), 4.dp.toPx() * (1f - sx * 0.6f), Offset(cx, cy))
    }
}
