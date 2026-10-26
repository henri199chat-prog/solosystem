package com.ayman.solosystem.ui.comp

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.domain.quest.Countdown
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

fun fmtRemaining(ms: Long): String { val s = (ms / 1000).coerceAtLeast(0); return "%02d:%02d:%02d".format(s / 3600, (s / 60) % 60, s % 60) }

/**
 * Segmented countdown ring: 24 radial dashes that disappear one by one until the daily deadline (real clock).
 * The current segment fades smoothly; the glow pulses; amber then red in the last 10%. Tap shows the remaining time.
 * [state]: 0 = armed (counting), 1 = done (frozen, green), 2 = not armed (rest/excused day, calm).
 */
@Composable
fun CountdownRing(startMs: Long, endMs: Long, state: Int, modifier: Modifier = Modifier, onExpired: () -> Unit) {
    val pal = LocalPalette.current
    val reduced = LocalReducedMotion.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showTime by remember { mutableStateOf(false) }
    LaunchedEffect(startMs, endMs, state) {
        while (true) {
            now = System.currentTimeMillis()
            val r = Countdown.ring(startMs, endMs, now)
            if (r.expired && state == 0) { onExpired(); break }
            delay(if (reduced) 5_000 else 1_000)
        }
    }
    val r = Countdown.ring(startMs, endMs, now)
    val red = r.fraction <= 0.04
    // timer-running-low alert: once when crossing into the last 10 % (amber) and again into the last 4 % (red), live only
    val stage = if (state != 0 || r.expired) 0 else if (red) 2 else if (r.warn) 1 else 0
    var lastStage by remember(startMs, endMs, state) { mutableIntStateOf(stage) }
    LaunchedEffect(stage) { if (stage > lastStage) com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.TIMER_LOW, rate = if (stage == 2) 1.12f else 1f); lastStage = stage }
    val base = when { state == 1 -> Sys.green; state == 0 && red -> Sys.red; state == 0 && r.warn -> Sys.amber; else -> pal.secondary }
    // pulse is read only inside the draw lambda → no recomposition per frame
    val pulseState: State<Float> = if (reduced || state == 1) remember { mutableFloatStateOf(1f) } else rememberInfiniteTransition(label = "ring").animateFloat(0.65f, 1f, infiniteRepeatable(tween(if (r.warn) 700 else 1600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "rp")
    val desc = if (LocalArabic.current) "الوقت المتبقي ${fmtRemaining(r.remainingMs)}" else "Time left ${fmtRemaining(r.remainingMs)}"
    Box(modifier.size(132.dp).semantics { contentDescription = desc }.clickable { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.SELECT); showTime = !showTime }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center; val ro = size.minDimension * 0.46f; val ri = size.minDimension * 0.33f
            val n = Countdown.SEGMENTS
            val pulse = pulseState.value
            for (i in 0 until n) {
                // segment 0 at top, clockwise; the highest lit index is the one fading.
                val a = -Math.PI / 2 + i * 2 * Math.PI / n
                val alpha = when {
                    state == 1 -> 1f
                    i < r.lit - 1 -> 1f
                    i == r.lit - 1 -> if (reduced) 1f else r.partial.coerceIn(0.15f, 1f)
                    else -> 0f
                }
                val p1 = c + Offset((cos(a) * ri).toFloat(), (sin(a) * ri).toFloat()); val p2 = c + Offset((cos(a) * ro).toFloat(), (sin(a) * ro).toFloat())
                drawLine(Sys.panelLine.copy(alpha = 0.12f), p1, p2, 5.dp.toPx(), StrokeCap.Round)
                if (alpha > 0f) {
                    drawLine(base.copy(alpha = 0.18f * alpha * pulse), p1, p2, 11.dp.toPx(), StrokeCap.Round)
                    drawLine(base.copy(alpha = 0.4f * alpha * pulse), p1, p2, 7.dp.toPx(), StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.95f * alpha), p1, p2, 3.dp.toPx(), StrokeCap.Round)
                }
            }
        }
        when {
            state == 1 -> SysIcon(Ico.CHECK, Sys.green, size = 40.dp)
            showTime || reduced -> BasicText(fmtRemaining(r.remainingMs), style = tech(15.sp, base))
            else -> GlowText("${r.lit}/${Countdown.SEGMENTS}", tech(13.sp, Sys.dim), glow = base)
        }
    }
}
