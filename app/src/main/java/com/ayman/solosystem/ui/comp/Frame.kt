package com.ayman.solosystem.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayman.solosystem.ui.theme.LocalPalette
import com.ayman.solosystem.ui.theme.LocalReducedMotion
import com.ayman.solosystem.ui.theme.Palette
import com.ayman.solosystem.ui.theme.Sys
import com.ayman.solosystem.ui.theme.SystemTheme

/** Glow by layering strokes (works on every API level, no blur dependency). */
fun DrawScope.glowPath(p: Path, c: Color, width: Float, intensity: Float = 1f) {
    listOf(10f to 0.06f, 6f to 0.12f, 3.5f to 0.22f).forEach { (w, a) -> drawPath(p, c.copy(alpha = a * intensity), style = Stroke(width * w, cap = StrokeCap.Square, join = StrokeJoin.Miter)) }
    drawPath(p, c, style = Stroke(width, join = StrokeJoin.Miter))
    drawPath(p, Color.White.copy(alpha = 0.7f * intensity), style = Stroke(width * 0.4f))
}

/** Stepped / notched bracket bar like a system window header (original geometry). */
fun DrawScope.bracketBar(y: Float, top: Boolean, pal: Palette, sw: Float, intensity: Float) {
    val w = size.width
    val d = if (top) 1f else -1f
    val h = 7.dp.toPx() * d
    val p = Path().apply {
        moveTo(-4.dp.toPx(), y + h * 1.6f)
        lineTo(w * 0.06f, y + h * 1.6f); lineTo(w * 0.09f, y + h); lineTo(w * 0.2f, y + h); lineTo(w * 0.23f, y)
        lineTo(w * 0.77f, y); lineTo(w * 0.8f, y + h); lineTo(w * 0.91f, y + h); lineTo(w * 0.94f, y + h * 1.6f)
        lineTo(w + 4.dp.toPx(), y + h * 1.6f)
    }
    glowPath(p, pal.frame, sw, intensity)
    // MONARCH: thick neon bar with magenta bloom and circuit ticks (reference: Shadow-Monarch notification frame)
    val m = SystemTheme.blend
    if (m > 0.01f) {
        val th = 3.6.dp.toPx() * m
        val band = Path().apply { moveTo(w * 0.07f, y + h * 1.45f); lineTo(w * 0.09f, y + h); lineTo(w * 0.2f, y + h); lineTo(w * 0.23f, y); lineTo(w * 0.77f, y); lineTo(w * 0.8f, y + h); lineTo(w * 0.91f, y + h); lineTo(w * 0.93f, y + h * 1.45f) }
        drawPath(band, SystemTheme.MonarchMagenta.copy(alpha = 0.10f * intensity * m), style = Stroke(th * 6f, join = StrokeJoin.Miter))
        drawPath(band, pal.frame.copy(alpha = 0.38f * intensity * m), style = Stroke(th * 2.6f, join = StrokeJoin.Miter))
        drawPath(band, pal.frame.copy(alpha = 0.95f * m), style = Stroke(th, join = StrokeJoin.Miter))
        drawPath(band, Color.White.copy(alpha = 0.6f * m * intensity), style = Stroke(th * 0.28f))
        // tech circuit ticks riding the bar
        val step = 13.dp.toPx(); var x = w * 0.27f
        while (x < w * 0.73f) { drawRect(SystemTheme.MonarchMagenta.copy(alpha = 0.75f * m * intensity), Offset(x, y - th * 0.9f * d), Size(1.5.dp.toPx(), th * 1.8f * d)); x += step }
    }
    // notch blocks
    val bw = 6.dp.toPx(); val gap = 4.dp.toPx()
    for (i in 0 until pal.notches) {
        val x1 = w * 0.25f + i * (bw + gap); val x2 = w * 0.75f - i * (bw + gap) - bw
        listOf(x1, x2).forEach { x -> drawRect(pal.frame.copy(alpha = 0.85f * intensity), Offset(x, y - (if (top) 3.dp.toPx() else 0f)), Size(bw, 3.dp.toPx())) }
    }
}

fun DrawScope.jaggedSide(x: Float, left: Boolean, pal: Palette, sw: Float, intensity: Float) {
    val h = size.height
    val o = 5.dp.toPx() * (if (left) -1f else 1f)
    val p = Path().apply {
        moveTo(x, h * 0.08f); lineTo(x, h * 0.22f); lineTo(x + o, h * 0.25f); lineTo(x + o, h * 0.38f); lineTo(x, h * 0.41f)
        lineTo(x, h * 0.6f); lineTo(x + o, h * 0.63f); lineTo(x + o, h * 0.74f); lineTo(x, h * 0.77f); lineTo(x, h * 0.92f)
    }
    glowPath(p, pal.frame, sw * 0.8f, intensity * 0.8f)
}

/**
 * The signature system window: translucent dark glass with a thin grey inner border, an electric-blue outer frame with
 * stepped bracket bars top & bottom and jagged side frames.
 */
fun Modifier.systemWindow(pal: Palette, glow: Float = 1f, accent: Color? = null, brackets: Boolean = true): Modifier = this
    .drawBehind {
        val p = if (accent != null) pal.copy(frame = accent) else pal
        drawRect(Brush.verticalGradient(listOf(SystemTheme.c(0xF00B1626, 0xF0160A26), SystemTheme.c(0xF0060B14, 0xF00A0514))))
        // diagonal scratch texture inside
        var i = 0f
        while (i < size.width + size.height) { drawLine(Color.White.copy(alpha = 0.025f), Offset(i, 0f), Offset(i - size.height * 0.6f, size.height), 1f); i += 22.dp.toPx() }
        val inset = 5.dp.toPx()
        drawRect(Sys.panelLine, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2), style = Stroke(1.dp.toPx()))
        if (p.doubleLine) drawRect(Sys.panelLine.copy(alpha = 0.25f), Offset(inset * 1.8f, inset * 1.8f), Size(size.width - inset * 3.6f, size.height - inset * 3.6f), style = Stroke(1f))
        val sw = 1.6.dp.toPx()
        glowPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)) }, p.frame, sw * 0.7f, glow * 0.7f)
        if (brackets) {
            bracketBar(-3.dp.toPx(), true, p, sw, glow); bracketBar(size.height + 3.dp.toPx(), false, p, sw, glow)
            jaggedSide(-3.dp.toPx(), true, p, sw, glow); jaggedSide(size.width + 3.dp.toPx(), false, p, sw, glow)
        }
    }

/** Stepped bracket bars + jagged side frames (decor layer on top of the shared panel FX). */
fun Modifier.brackets(pal: Palette, glow: Float = 1f, accent: Color? = null) = this.drawBehind {
    val p = if (accent != null) pal.copy(frame = accent) else pal
    val sw = 1.6.dp.toPx()
    bracketBar(-4.dp.toPx(), true, p, sw, glow); bracketBar(size.height + 4.dp.toPx(), false, p, sw, glow)
    jaggedSide(-4.dp.toPx(), true, p, sw, glow); jaggedSide(size.width + 4.dp.toPx(), false, p, sw, glow)
}

/**
 * THE shared SystemPanel used everywhere: navy glass, circuit texture, chamfered electric frame, cyan inner line,
 * scan sweep, crackling edge flares + sparks, and a brief flash when [contentKey] changes.
 */
@Composable
fun SysPanel(modifier: Modifier = Modifier, accent: Color? = null, brackets: Boolean = true, pulse: Boolean = false, padding: Dp = 16.dp,
             flares: Boolean = true, contentKey: Any? = null, content: @Composable ColumnScope.() -> Unit) {
    val pal = LocalPalette.current
    Box(modifier.padding(horizontal = 6.dp, vertical = 10.dp), propagateMinConstraints = true) {
        Column(
            Modifier
                .then(if (brackets) Modifier.brackets(pal, 1f, accent) else Modifier)
                .systemPanelStatic(pal, accent)
                .refreshBeam(contentKey)
                .padding(padding),
            content = content
        )
        PanelLiveFx(pal, accent, flares, Modifier.matchParentSize())
    }
}

/** A simple thin-bordered inner box (used for rows, title boxes). */
fun Modifier.thinBox(c: Color = Sys.panelLine, fill: Color = Color(0x55000000)) = this.drawWithContent {
    drawRect(fill); drawRect(c, style = Stroke(1.dp.toPx())); drawContent()
}
