package com.ayman.solosystem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Crack(val pts: List<Offset>, val start: Float, val drift: Offset)

/** Normalised crack network (unit = min(width, height), origin = screen centre) with branches. */
private fun buildCracks(): List<Crack> {
    val r = Random(100)
    val out = mutableListOf<Crack>()
    fun grow(o: Offset, ang0: Float, len: Float, segs: Int, start: Float, depth: Int) {
        val pts = mutableListOf(o); var p = o; var a = ang0
        repeat(segs) {
            a += (r.nextFloat() - 0.5f) * 0.9f
            p += Offset(cos(a), sin(a)) * (len / segs) * (0.6f + r.nextFloat() * 0.8f)
            pts += p
        }
        out += Crack(pts, start, Offset((r.nextFloat() - 0.5f) * 0.25f, 0.15f + r.nextFloat() * 0.45f))
        if (depth < 2) for (i in 2 until pts.size step 2) if (r.nextFloat() < 0.55f)
            grow(pts[i], a + (if (r.nextBoolean()) 1 else -1) * (0.5f + r.nextFloat() * 0.8f), len * 0.45f, (segs * 0.6f).toInt().coerceAtLeast(3), start + 0.08f + i * 0.02f, depth + 1)
    }
    val origins = listOf(Offset(0f, 0f), Offset(-0.28f, -0.5f), Offset(0.3f, 0.45f), Offset(0.32f, -0.3f), Offset(-0.3f, 0.35f))
    origins.forEachIndexed { k, o -> repeat(if (k == 0) 7 else 3) { j -> grow(o, j * 6.283f / (if (k == 0) 7 else 3) + r.nextFloat() * 0.6f, if (k == 0) 0.9f else 0.45f, if (k == 0) 12 else 7, k * 0.06f, 0) } }
    return out
}

/**
 * One-time level-100 cinematic (v1.2): the blue System cracks (0–0.7 s), shatters in a flash (0.7 s), the fragments dissolve
 * while violet energy floods in and the whole palette morphs blue → violet (0.75–2.2 s), then the System announces its
 * evolution. Sound: SYSTEM_AWAKEN (cracks → shatter → sub → minor/tritone choir + bell → shimmer), timed to the visuals.
 * Reduced motion: no cinematic, the theme switches at once and only the window is shown.
 */
@Composable
fun SystemAwakeningWindow(dismiss: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val t = remember { Animatable(if (reduced) 3f else 0f) }
    var showWin by remember { mutableStateOf(reduced) }
    val cracks = remember { buildCracks() }
    val holoCyan = Color(0xFF00E5FF); val holoBlue = Color(0xFF2F8CFF)
    LaunchedEffect(Unit) {
        Sfx.play(Sfx.S.SYSTEM_AWAKEN)
        if (reduced) { SystemTheme.blend = 1f; return@LaunchedEffect }
        SystemTheme.blend = 0f
        launch { snapshotFlow { t.value }.collect { v -> SystemTheme.blend = ((v - 0.75f) / 1.45f).coerceIn(0f, 1f); if (v >= 2.3f) showWin = true } }
        t.animateTo(2.7f, tween(2700, easing = LinearEasing))
        SystemTheme.blend = 1f; showWin = true
    }
    DisposableEffect(Unit) { onDispose { SystemTheme.blend = 1f } }
    BackHandler(!showWin) { }
    Box(Modifier.fillMaxSize()) {
        if (showWin) NotificationWindow(tr("إشعار", "NOTIFICATION"), null, dismiss, sound = null) {
            GlitchText(tr("[تطوّر النظام]", "[SYSTEM EVOLUTION]"), 22)
            Spacer(Modifier.height(6.dp))
            GlowText(tr("لقد بلغت المستوى 100.", "You have reached level 100."), body(17.sp, FontWeight.Bold), Modifier.fillMaxWidth(), align = TextAlign.Center)
            GlowText(tr("تم إيقاظ سلطة: الملك الظلّ", "Authority awakened: SHADOW MONARCH"), body(16.sp, FontWeight.Bold, SystemTheme.MonarchSecondary), Modifier.fillMaxWidth(), glow = SystemTheme.MonarchMagenta, align = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Label(tr("النظام يتكيّف مع قوتك الجديدة. واجهة النظام البنفسجية مفعّلة.", "The System adapts to your new power. The violet System interface is now active."), color = Sys.text, size = 14, align = TextAlign.Center)
            Label(tr("لا طريق للعودة إلى الضعف.", "There is no way back to weakness."), color = Sys.red, size = 14, align = TextAlign.Center, w = FontWeight.Bold)
            Label(tr("يمكنك اختيار السمة الكلاسيكية الزرقاء من الإعدادات.", "You can pick the classic blue theme in Settings."), size = 11, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            SysButton(tr("حسنًا", "OK"), Modifier.fillMaxWidth(), sfx = Sfx.S.YES, onClick = dismiss)
        }
        if (!reduced && t.value < 2.7f) Canvas(Modifier.fillMaxSize().clickable(remember { MutableInteractionSource() }, null) {}) {
            val v = t.value
            val u = size.minDimension; val c = center
            fun P(o: Offset) = c + o * u
            // screen dim, then violet flood
            drawRect(Color.Black.copy(alpha = (0.65f * (v / 0.5f).coerceIn(0f, 1f)) * (1f - ((v - 2.1f) / 0.6f).coerceIn(0f, 1f))))
            val crackP = (v / 0.7f).coerceIn(0f, 1f)
            val dissolve = ((v - 0.75f) / 1.3f).coerceIn(0f, 1f)
            val col = lerp(holoCyan, SystemTheme.MonarchMagenta, dissolve)
            cracks.forEach { cr ->
                val local = ((crackP - cr.start) / (1f - cr.start).coerceAtLeast(0.2f)).coerceIn(0f, 1f)
                if (local <= 0f) return@forEach
                val n = ((cr.pts.size - 1) * local).toInt()
                val off = cr.drift * u * dissolve * dissolve
                val a = 1f - dissolve
                if (a <= 0.01f) return@forEach
                for (i in 0 until n) {
                    // fragments fall apart: alternate segments drift differently
                    val o2 = if (i % 2 == 0) off else off * 0.6f + Offset(off.y * 0.3f, 0f)
                    val p1 = P(cr.pts[i]) + o2; val p2 = P(cr.pts[i + 1]) + o2
                    drawLine(col.copy(alpha = 0.18f * a), p1, p2, 9.dp.toPx(), StrokeCap.Round, blendMode = BlendMode.Plus)
                    drawLine(lerp(holoBlue, SystemTheme.MonarchPrimary, dissolve).copy(alpha = 0.55f * a), p1, p2, 3.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.9f * a), p1, p2, 1.dp.toPx())
                }
            }
            // shatter flash
            val fl = if (v in 0.68f..1.1f) (1f - ((v - 0.7f) / 0.4f).coerceIn(0f, 1f)) else 0f
            if (fl > 0f) drawRect(Color.White.copy(alpha = 0.85f * fl))
            // violet energy smoke flooding outwards
            if (v > 0.75f) {
                val g = ((v - 0.75f) / 1.2f).coerceIn(0f, 1f); val out = 1f - ((v - 2.1f) / 0.6f).coerceIn(0f, 1f)
                val rad = size.maxDimension * (0.2f + 0.9f * g)
                drawCircle(Brush.radialGradient(listOf(SystemTheme.MonarchPrimary.copy(alpha = 0.45f * out), SystemTheme.MonarchMagenta.copy(alpha = 0.18f * out), Color.Transparent), c, rad), rad, c, blendMode = BlendMode.Plus)
                for (k in 0 until 10) {
                    val ang = k * 0.628f + v * 0.6f; val rr = u * (0.15f + 0.5f * g) * (0.7f + 0.3f * sin(k * 2.1f))
                    val p = c + Offset(cos(ang), sin(ang)) * rr
                    drawCircle(Brush.radialGradient(listOf(SystemTheme.MonarchMagenta.copy(alpha = 0.22f * out), Color.Transparent), p, u * 0.22f), u * 0.22f, p, blendMode = BlendMode.Plus)
                }
            }
        }
    }
}
