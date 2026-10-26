package com.ayman.solosystem.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.ui.theme.*
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.LocalSoftSfx
import com.ayman.solosystem.ui.resolveTap

@Composable
fun GlowText(text: String, style: TextStyle, modifier: Modifier = Modifier, glow: Color = LocalPalette.current.secondary, align: TextAlign? = null, maxLines: Int = Int.MAX_VALUE) {
    BasicText(text, modifier, style.copy(shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 14f), textAlign = align ?: style.textAlign), maxLines = maxLines)
}

@Composable
fun GlowTextAnnotated(text: androidx.compose.ui.text.AnnotatedString, style: TextStyle, modifier: Modifier = Modifier, glow: Color = LocalPalette.current.secondary, singleLine: Boolean = false) {
    BasicText(text, modifier, style.copy(shadow = Shadow(glow.copy(alpha = 0.85f), Offset.Zero, 14f)), softWrap = !singleLine, maxLines = if (singleLine) 1 else Int.MAX_VALUE)
}

@Composable fun Label(text: String, modifier: Modifier = Modifier, color: Color = Sys.dim, size: Int = 13, align: TextAlign? = null, w: FontWeight = FontWeight.Normal) =
    BasicText(text, modifier, body(size.sp, w, color).copy(textAlign = align ?: TextAlign.Start, lineHeight = (size * 1.55f).sp, lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center, androidx.compose.ui.text.style.LineHeightStyle.Trim.None)))

/** Title box "[إشعار]" with the separate square holding a circled '!'. */
@Composable
fun TitleBox(title: String, modifier: Modifier = Modifier, accent: Color = LocalPalette.current.secondary, bang: Boolean = true) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        if (bang) {
            Box(Modifier.size(40.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(24.dp)) {
                    drawCircle(accent.copy(alpha = 0.25f), size.minDimension / 2 + 3f, style = Stroke(6f))
                    drawCircle(Color.White, size.minDimension / 2 - 1f, style = Stroke(2.2f))
                    drawLine(Color.White, Offset(size.width / 2, size.height * 0.22f), Offset(size.width / 2, size.height * 0.6f), 3f, StrokeCap.Round)
                    drawCircle(Color.White, 2.2f, Offset(size.width / 2, size.height * 0.76f))
                }
            }
            Spacer(Modifier.width(6.dp))
        }
        Box(Modifier.height(40.dp).thinBox(Sys.panelLine).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            GlowText(title, body(18.sp, FontWeight.Bold), glow = accent)
        }
    }
}

@Composable
fun SysButton(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Color = LocalPalette.current.primary, small: Boolean = false,
              sfx: Sfx.S? = Sfx.S.TAP, onClick: () -> Unit) {
    val snd = resolveTap(sfx, LocalSoftSfx.current, accent == Sys.dim)
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val c = if (enabled) accent else Sys.dim.copy(alpha = 0.4f)
    Box(
        modifier
            .defaultMinSize(minHeight = if (small) 36.dp else 46.dp)
            .drawBehind {
                drawRect(c.copy(alpha = if (pressed) 0.35f else 0.12f))
                val p = Path().apply { moveTo(8.dp.toPx(), 0f); lineTo(size.width, 0f); lineTo(size.width, size.height - 8.dp.toPx()); lineTo(size.width - 8.dp.toPx(), size.height); lineTo(0f, size.height); lineTo(0f, 8.dp.toPx()); close() }
                glowPath(p, c, 1.3.dp.toPx(), if (enabled) (if (pressed) 1.3f else 0.8f) else 0.2f)
            }
            .clickable(src, null, enabled = enabled, role = Role.Button) { snd?.let { Sfx.play(it) }; onClick() }
            .padding(horizontal = if (small) 12.dp else 18.dp, vertical = if (small) 6.dp else 10.dp),
        contentAlignment = Alignment.Center
    ) { GlowText(text, body(if (small) 14.sp else 16.sp, FontWeight.Bold, if (enabled) Sys.text else Sys.dim), glow = if (enabled) c else Color.Transparent, align = TextAlign.Center) }
}

/** Square checkbox like the system goal list. */
@Composable
fun SysCheck(checked: Boolean, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = Sys.green, sfx: Sfx.S? = if (checked) Sfx.S.TOGGLE_OFF else Sfx.S.TOGGLE_ON, onToggle: (() -> Unit)?) {
    val pal = LocalPalette.current
    val snd = resolveTap(sfx, LocalSoftSfx.current, false)
    Canvas(
        modifier.size(26.dp)
            .then(if (onToggle != null) Modifier.clickable(enabled = enabled, role = Role.Checkbox) { snd?.let { Sfx.play(it) }; onToggle() } else Modifier)
    ) {
        val s = size.minDimension * 0.78f; val o = (size.minDimension - s) / 2
        drawRect(if (enabled) Sys.text else Sys.dim, Offset(o, o), Size(s, s), style = Stroke(1.8.dp.toPx()))
        if (checked) {
            val p = Path().apply { moveTo(o + s * 0.18f, o + s * 0.52f); lineTo(o + s * 0.42f, o + s * 0.76f); lineTo(o + s * 0.86f, o + s * 0.2f) }
            glowPath(p, color, 2.4.dp.toPx())
        } else if (!enabled) drawLine(pal.primary.copy(alpha = 0.3f), Offset(o, o + s), Offset(o + s, o), 1f)
    }
}

@Composable
fun StatBar(label: String, value: Float, max: Float, color: Color, modifier: Modifier = Modifier, valueText: String? = null) {
    Column(modifier) {
        if (label.isNotEmpty() || valueText != "") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(label, style = tech(14.sp, Sys.text))
                BasicText(valueText ?: "${value.toInt()}/${max.toInt()}", style = tech(14.sp, Sys.dim))
            }
            Spacer(Modifier.height(3.dp))
        }
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            drawRect(Color(0x33000000)); drawRect(Sys.panelLine, style = Stroke(1f))
            val f = if (max <= 0f) 0f else (value / max).coerceIn(0f, 1f)
            if (f > 0f) {
                drawRect(color.copy(alpha = 0.25f), Offset(0f, -3f), Size(size.width * f, size.height + 6f))
                drawRect(Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)), Offset(1f, 1f), Size(size.width * f - 2f, size.height - 2f))
                drawRect(Color.White.copy(alpha = 0.5f), Offset(1f, 1f), Size(size.width * f - 2f, 2f))
            }
        }
    }
}

@Composable
fun RankBadge(rank: String, modifier: Modifier = Modifier, color: Color = LocalPalette.current.secondary, size: Dp = 34.dp) {
    Box(modifier.size(size).drawBehind {
        val p = Path().apply { moveTo(this@drawBehind.size.width / 2, 0f); lineTo(this@drawBehind.size.width, this@drawBehind.size.height / 2); lineTo(this@drawBehind.size.width / 2, this@drawBehind.size.height); lineTo(0f, this@drawBehind.size.height / 2); close() }
        drawPath(p, color.copy(alpha = 0.15f)); glowPath(p, color, 1.4.dp.toPx(), 0.8f)
    }, contentAlignment = Alignment.Center) { GlowText(rank, tech(if (rank.length > 1) 9.sp else 15.sp), glow = color) }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    val pal = LocalPalette.current
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { glowPath(Path().apply { moveTo(0f, 0f); lineTo(size.width, size.height / 2); lineTo(0f, size.height); close() }, pal.secondary, 2f) }
        Spacer(Modifier.width(8.dp))
        GlowText(text, body(16.sp, FontWeight.Bold))
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.weight(1f).height(2.dp)) { drawLine(Brush.horizontalGradient(listOf(pal.primary, Color.Transparent)), Offset(0f, 1f), Offset(size.width, 1f), 2f) }
    }
}

/** Kasaka-fang style item card: icon box, name, category, rank, green bonus line, flavour text. */
@Composable
fun ItemCard(name: String, category: String, rank: String, bonus: String?, flavour: String, modifier: Modifier = Modifier, accent: Color = LocalPalette.current.secondary,
             locked: Boolean = false, footer: (@Composable ColumnScope.() -> Unit)? = null, icon: @Composable BoxScope.() -> Unit) {
    SysPanel(modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = name }, brackets = false, padding = 14.dp, flares = false) {
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(64.dp).thinBox(accent.copy(alpha = 0.7f), SystemTheme.c(0x66000814, 0x660A0014)), contentAlignment = Alignment.Center, content = icon)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GlowText(if (locked) "???" else name, body(17.sp, FontWeight.Bold), glow = accent, maxLines = 2)
                Row { Label(category, size = 12); Spacer(Modifier.width(10.dp)); Label(rank, size = 12, color = accent, w = FontWeight.Bold) }
                if (bonus != null && !locked) Label(bonus, color = Sys.green, size = 13, w = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.fillMaxWidth().height(1.dp)) { drawLine(Sys.panelLine, Offset.Zero, Offset(size.width, 0f)) }
        Spacer(Modifier.height(6.dp))
        Label(flavour, size = 13, color = Sys.text.copy(alpha = 0.8f))
        footer?.invoke(this)
    }
}

/** Angled shop tile (parallelogram). */
@Composable
fun ShopTile(title: String, price: String, modifier: Modifier = Modifier, enabled: Boolean = true, accent: Color = LocalPalette.current.primary, onClick: () -> Unit) {
    val snd = resolveTap(Sfx.S.TAP, LocalSoftSfx.current, false)
    Box(modifier.heightIn(min = 84.dp).drawBehind {
        val sk = 14.dp.toPx()
        val p = Path().apply { moveTo(sk, 0f); lineTo(size.width, 0f); lineTo(size.width - sk, size.height); lineTo(0f, size.height); close() }
        drawPath(p, Brush.verticalGradient(listOf(accent.copy(alpha = 0.18f), SystemTheme.c(0xAA050A14, 0xAA0C0514))))
        glowPath(p, if (enabled) accent else Sys.dim, 1.2.dp.toPx(), if (enabled) 0.7f else 0.2f)
    }.clickable(enabled = enabled, role = Role.Button) { snd?.let { Sfx.play(it) }; onClick() }.padding(horizontal = 22.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GlowText(title, body(14.sp, FontWeight.Bold), align = TextAlign.Center, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            BasicText(price, style = tech(14.sp, Sys.gold))
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, sfx: Sfx.S? = Sfx.S.SELECT, onClick: () -> Unit) {
    val pal = LocalPalette.current
    val snd = resolveTap(sfx, LocalSoftSfx.current, false)
    Box(modifier.padding(3.dp).thinBox(if (selected) pal.secondary else Sys.panelLine, if (selected) pal.primary.copy(alpha = 0.3f) else Color(0x44000000))
        .clickable(role = Role.Checkbox) { snd?.let { Sfx.play(it) }; onClick() }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        BasicText(text, style = body(14.sp, if (selected) FontWeight.Bold else FontWeight.Normal, if (selected) Sys.text else Sys.dim))
    }
}

@Composable
fun Stepper(label: String, value: String, modifier: Modifier = Modifier, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f), Sys.text, 14)
        SysButton("−", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, sfx = Sfx.S.MINUS, onClick = onMinus)
        Box(Modifier.widthIn(min = 72.dp).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) { BasicText(value, style = tech(17.sp), maxLines = 1, softWrap = false) }
        SysButton("+", Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp), small = true, sfx = Sfx.S.PLUS, onClick = onPlus)
    }
}

@Composable
fun Divider() { Canvas(Modifier.fillMaxWidth().height(9.dp)) { drawLine(Sys.panelLine.copy(alpha = 0.5f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), 1f) } }

/** System-styled slider (0..[max], [step]); fills from the reading side (right in Arabic). [onDone] fires on release. */
@Composable
fun SysSlider(value: Int, modifier: Modifier = Modifier, max: Int = 100, step: Int = 5, label: String = "", onChange: (Int) -> Unit = {}, onDone: (Int) -> Unit) {
    val pal = LocalPalette.current
    val rtl = androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    var local by remember(value) { mutableIntStateOf(value) }
    fun at(x: Float, w: Float): Int { val f = (x / w).coerceIn(0f, 1f).let { if (rtl) 1f - it else it }; return ((f * max / step).roundToIntCompat() * step).coerceIn(0, max) }
    Canvas(modifier.fillMaxWidth().height(40.dp)
        .semantics {
            contentDescription = label
            progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(local.toFloat(), 0f..max.toFloat(), max / step - 1)
            setProgress { v -> val n = (v / step).roundToIntCompat() * step; local = n.coerceIn(0, max); onDone(local); true }
        }
        .pointerInput(rtl) { detectTapGestures { o -> local = at(o.x, size.width.toFloat()); onChange(local); onDone(local) } }
        .pointerInput(rtl) {
            detectHorizontalDragGestures(onDragEnd = { onDone(local) }, onDragCancel = { onDone(local) }) { ch, _ -> local = at(ch.position.x, size.width.toFloat()); onChange(local) }
        }) {
        val y = size.height / 2; val f = local / max.toFloat()
        val x0 = if (rtl) size.width else 0f; val x1 = if (rtl) size.width * (1f - f) else size.width * f
        drawLine(Sys.panelLine.copy(alpha = 0.5f), Offset(0f, y), Offset(size.width, y), 4.dp.toPx())
        // ticks every 10 %
        for (i in 0..10) { val x = size.width * i / 10f; drawLine(Sys.panelLine.copy(alpha = 0.6f), Offset(x, y - 6.dp.toPx()), Offset(x, y - 3.dp.toPx()), 1f) }
        if (f > 0f) glowPath(Path().apply { moveTo(x0, y); lineTo(x1, y) }, pal.secondary, 3.dp.toPx(), 0.9f)
        val k = Path().apply { moveTo(x1, y - 9.dp.toPx()); lineTo(x1 + 6.dp.toPx(), y); lineTo(x1, y + 9.dp.toPx()); lineTo(x1 - 6.dp.toPx(), y); close() }
        drawPath(k, Sys.bg); glowPath(k, pal.secondary, 1.6.dp.toPx())
    }
}

private fun Float.roundToIntCompat() = kotlin.math.round(this).toInt()

/**
 * v1.2.2 holographic empty state for filtered / empty lists: pulsing hex-ring icon, a title and a short System-voice hint.
 * Theme-aware (blue HOLO / violet MONARCH through the palette).
 */
@Composable
fun EmptyState(title: String, hint: String, modifier: Modifier = Modifier, icon: Ico = Ico.HISTORY) {
    val pal = LocalPalette.current
    val reduced = LocalReducedMotion.current
    val pulse = if (reduced) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "empty")
        .animateFloat(0f, 1f, androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(1800), androidx.compose.animation.core.RepeatMode.Reverse), label = "p")
    Column(modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(72.dp).drawBehind {
            val p = pulse?.value ?: 0.5f
            val c = pal.secondary
            drawCircle(androidx.compose.ui.graphics.Brush.radialGradient(listOf(c.copy(alpha = 0.22f + 0.12f * p), Color.Transparent)), size.minDimension * 0.62f)
            // hexagon frame
            val r = size.minDimension * 0.42f; val cx = size.width / 2; val cy = size.height / 2
            val path = androidx.compose.ui.graphics.Path().apply {
                for (i in 0..5) { val a = Math.toRadians(60.0 * i - 90); val x = cx + r * kotlin.math.cos(a).toFloat(); val y = cy + r * kotlin.math.sin(a).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
                close()
            }
            drawPath(path, c.copy(alpha = 0.55f + 0.35f * p), style = androidx.compose.ui.graphics.drawscope.Stroke(1.6.dp.toPx()))
            drawCircle(c.copy(alpha = 0.25f), r * 1.18f, style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 8f))))
        }, contentAlignment = Alignment.Center) { SysIcon(icon, pal.secondary.copy(alpha = 0.9f), size = 30.dp) }
        Spacer(Modifier.height(10.dp))
        GlowText(title, body(15.sp, FontWeight.Bold, Sys.text), align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Label(hint, Modifier.fillMaxWidth(), color = Sys.dim, size = 12, align = TextAlign.Center)
    }
}
