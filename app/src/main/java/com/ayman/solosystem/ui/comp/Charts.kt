package com.ayman.solosystem.ui.comp

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.ui.theme.*

/**
 * v1.2.2 premium holographic bar chart: wide glowing gradient bars, staggered grow-in on appear, value labels above bars,
 * slot-aligned date labels below and dashed holographic grid lines. Chronological left → right (also in RTL, like any chart).
 * Colours come from the caller / theme so it follows blue HOLO ↔ violet MONARCH.
 */
@Composable
fun PremiumBarChart(values: List<Double>, labels: List<String>, modifier: Modifier, color: Color, slots: Int = 8, maxValue: Double? = null,
                    fmt: (Double) -> String = { it.toInt().toString() }, contentDesc: String = "") {
    val reduced = LocalReducedMotion.current
    val prog = remember(values) { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(values) { if (!reduced) prog.animateTo(1f, tween(1200, easing = LinearEasing)) }
    val tm = rememberTextMeasurer()
    val grid = LocalPalette.current.secondary
    val labelStyle = tech(10.sp, Sys.dim)
    val valueStyle = tech(11.sp, Color.White)
    Canvas(modifier.thinBox().padding(horizontal = 8.dp, vertical = 6.dp).semantics { if (contentDesc.isNotEmpty()) contentDescription = contentDesc }) {
        val n = values.size
        val topPad = 18.dp.toPx(); val bottomPad = 16.dp.toPx()
        val chartH = (size.height - topPad - bottomPad).coerceAtLeast(1f)
        val base = topPad + chartH
        // holographic grid: dashed horizontals + faint slot verticals
        val dash = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
        for (g in 0..4) { val y = topPad + chartH * g / 4f
            drawLine(grid.copy(alpha = if (g == 4) 0.45f else 0.16f), Offset(0f, y), Offset(size.width, y), if (g == 4) 1.2.dp.toPx() else 1f, pathEffect = if (g == 4) null else dash) }
        val sn = maxOf(n, slots, 1); val off = sn - n
        val slotW = size.width / sn
        for (i in 1 until sn) drawLine(grid.copy(alpha = 0.05f), Offset(slotW * i, topPad), Offset(slotW * i, base), 1f)
        if (n == 0) return@Canvas
        val mx = (maxValue ?: values.max()).coerceAtLeast(1e-6)
        val bw = minOf(slotW * 0.64f, 36.dp.toPx())
        val r = CornerRadius(3.dp.toPx(), 3.dp.toPx())
        val dur = 0.55f; val spread = 1f - dur
        values.forEachIndexed { i, v ->
            val start = if (n > 1) spread * i / (n - 1) else 0f
            val local = ((prog.value - start) / dur).coerceIn(0f, 1f)
            val e = FastOutSlowInEasing.transform(local)
            val cx = slotW * (i + off + 0.5f)
            val full = (chartH * (v / mx).coerceIn(0.0, 1.0)).toFloat()
            val h = full * e
            if (full > 0.5f) {
                val top = base - h
                // glow halo (layered, cheap — no blur)
                for (k in 3 downTo 1) {
                    val g = k * 3.dp.toPx()
                    drawRoundRect(color.copy(alpha = 0.07f * e), Offset(cx - bw / 2 - g, top - g), Size(bw + 2 * g, h + g), CornerRadius(r.x + g, r.y + g), blendMode = BlendMode.Plus)
                }
                drawRoundRect(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.95f), color, color.copy(alpha = 0.30f)), top, base), Offset(cx - bw / 2, top), Size(bw, h), r)
                // inner sheen + bright cap
                drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), cx - bw / 2, cx), Offset(cx - bw / 2, top), Size(bw / 2, h))
                drawLine(Color.White, Offset(cx - bw / 2, top), Offset(cx + bw / 2, top), 2.dp.toPx())
                if (local > 0.6f) {
                    val t = tm.measure(fmt(v), valueStyle.copy(color = Color.White.copy(alpha = ((local - 0.6f) / 0.4f).coerceIn(0f, 1f))))
                    drawText(t, topLeft = Offset((cx - t.size.width / 2f).coerceIn(0f, size.width - t.size.width), (top - t.size.height - 2.dp.toPx()).coerceAtLeast(0f)))
                }
            } else {
                drawLine(color.copy(alpha = 0.5f), Offset(cx - bw / 2, base - 1.dp.toPx()), Offset(cx + bw / 2, base - 1.dp.toPx()), 2.dp.toPx())
            }
            labels.getOrNull(i)?.let { l ->
                val t = tm.measure(l, labelStyle)
                drawText(t, topLeft = Offset((cx - t.size.width / 2f).coerceIn(0f, (size.width - t.size.width).coerceAtLeast(0f)), base + 3.dp.toPx()))
            }
        }
    }
}
