package com.ayman.solosystem.ui.comp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ayman.solosystem.domain.rewards.EmblemShape
import com.ayman.solosystem.domain.training.Pose
import kotlin.math.cos
import kotlin.math.sin

enum class Ico { STATUS, QUESTS, TRAINING, PROGRESS, MORE, SKILLS, INVENTORY, HISTORY, CALENDAR, SETTINGS, BOX, GOLD, TOKEN, CHECK, LOCK, TIMER, PAIN, CHART, SHOP, BADGE, TITLE, THEME, FRAME, EMBLEM, POTION }

private fun DrawScope.poly(vararg pts: Float): Path = Path().apply {
    moveTo(pts[0] * size.width, pts[1] * size.height)
    var i = 2; while (i < pts.size) { lineTo(pts[i] * size.width, pts[i + 1] * size.height); i += 2 }
}

@Composable
fun SysIcon(kind: Ico, color: Color, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    Canvas(modifier.size(size)) {
        val w = size.toPx() * 0.075f
        val st = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun g(p: Path) = glowPath(p, color, w, 0.6f)
        when (kind) {
            Ico.STATUS -> { g(Path().apply { addOval(Rect(Offset(this@Canvas.size.width / 2, this@Canvas.size.height * 0.3f), this@Canvas.size.width * 0.17f)) }); g(poly(0.2f, 0.9f, 0.25f, 0.65f, 0.5f, 0.55f, 0.75f, 0.65f, 0.8f, 0.9f)) }
            Ico.QUESTS -> { g(poly(0.2f, 0.1f, 0.8f, 0.1f, 0.8f, 0.9f, 0.2f, 0.9f, 0.2f, 0.1f)); g(poly(0.32f, 0.35f, 0.42f, 0.45f, 0.6f, 0.25f)); drawLine(color, Offset(this@Canvas.size.width * .32f, this@Canvas.size.height * .65f), Offset(this@Canvas.size.width * .68f, this@Canvas.size.height * .65f), w) }
            Ico.TRAINING -> { g(poly(0.1f, 0.5f, 0.9f, 0.5f)); g(poly(0.2f, 0.3f, 0.2f, 0.7f)); g(poly(0.8f, 0.3f, 0.8f, 0.7f)); g(poly(0.1f, 0.38f, 0.1f, 0.62f)); g(poly(0.9f, 0.38f, 0.9f, 0.62f)) }
            Ico.PROGRESS, Ico.CHART -> { g(poly(0.1f, 0.9f, 0.9f, 0.9f)); g(poly(0.15f, 0.7f, 0.4f, 0.45f, 0.6f, 0.58f, 0.88f, 0.2f)) }
            Ico.POTION -> { g(poly(0.4f, 0.08f, 0.6f, 0.08f)); g(poly(0.42f, 0.08f, 0.42f, 0.36f, 0.18f, 0.72f, 0.24f, 0.92f, 0.76f, 0.92f, 0.82f, 0.72f, 0.58f, 0.36f, 0.58f, 0.08f)); g(poly(0.27f, 0.66f, 0.73f, 0.66f)); drawCircle(color, w * 0.9f, Offset(this@Canvas.size.width * .45f, this@Canvas.size.height * .8f)) }
            Ico.MORE -> { for (i in 0..2) drawCircle(color, w * 1.1f, Offset(this@Canvas.size.width * (0.25f + 0.25f * i), this@Canvas.size.height / 2)) }
            Ico.SKILLS -> g(poly(0.5f, 0.05f, 0.62f, 0.38f, 0.95f, 0.4f, 0.68f, 0.6f, 0.78f, 0.95f, 0.5f, 0.74f, 0.22f, 0.95f, 0.32f, 0.6f, 0.05f, 0.4f, 0.38f, 0.38f, 0.5f, 0.05f))
            Ico.INVENTORY, Ico.BOX -> { g(poly(0.12f, 0.35f, 0.88f, 0.35f, 0.88f, 0.88f, 0.12f, 0.88f, 0.12f, 0.35f)); g(poly(0.06f, 0.2f, 0.94f, 0.2f, 0.94f, 0.35f, 0.06f, 0.35f, 0.06f, 0.2f)); g(poly(0.5f, 0.2f, 0.5f, 0.88f)) }
            Ico.HISTORY, Ico.TIMER -> { drawCircle(color, this@Canvas.size.minDimension * 0.4f, style = st); g(poly(0.5f, 0.25f, 0.5f, 0.5f, 0.68f, 0.62f)) }
            Ico.CALENDAR -> { g(poly(0.12f, 0.2f, 0.88f, 0.2f, 0.88f, 0.9f, 0.12f, 0.9f, 0.12f, 0.2f)); g(poly(0.12f, 0.4f, 0.88f, 0.4f)); g(poly(0.3f, 0.08f, 0.3f, 0.28f)); g(poly(0.7f, 0.08f, 0.7f, 0.28f)) }
            Ico.SETTINGS -> { drawCircle(color, this@Canvas.size.minDimension * 0.16f, style = st); for (i in 0 until 8) { val a = i * Math.PI / 4; val c = Offset(this@Canvas.size.width / 2, this@Canvas.size.height / 2); val r1 = this@Canvas.size.minDimension * 0.28f; val r2 = this@Canvas.size.minDimension * 0.44f; drawLine(color, c + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), c + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()), w * 1.6f, StrokeCap.Round) } }
            Ico.GOLD -> { drawCircle(color.copy(alpha = 0.25f), this@Canvas.size.minDimension * 0.42f); drawCircle(color, this@Canvas.size.minDimension * 0.42f, style = st); g(poly(0.5f, 0.28f, 0.5f, 0.72f)) }
            Ico.TOKEN -> g(poly(0.5f, 0.05f, 0.9f, 0.3f, 0.9f, 0.7f, 0.5f, 0.95f, 0.1f, 0.7f, 0.1f, 0.3f, 0.5f, 0.05f))
            Ico.CHECK -> g(poly(0.15f, 0.55f, 0.4f, 0.8f, 0.88f, 0.2f))
            Ico.LOCK -> { g(poly(0.2f, 0.45f, 0.8f, 0.45f, 0.8f, 0.9f, 0.2f, 0.9f, 0.2f, 0.45f)); g(poly(0.32f, 0.45f, 0.32f, 0.25f, 0.5f, 0.12f, 0.68f, 0.25f, 0.68f, 0.45f)) }
            Ico.PAIN -> { g(poly(0.5f, 0.08f, 0.95f, 0.88f, 0.05f, 0.88f, 0.5f, 0.08f)); g(poly(0.5f, 0.38f, 0.5f, 0.62f)); drawCircle(color, w, Offset(this@Canvas.size.width / 2, this@Canvas.size.height * 0.75f)) }
            Ico.SHOP -> { g(poly(0.1f, 0.15f, 0.22f, 0.15f, 0.32f, 0.68f, 0.82f, 0.68f, 0.9f, 0.3f, 0.26f, 0.3f)); drawCircle(color, w * 1.2f, Offset(this@Canvas.size.width * 0.38f, this@Canvas.size.height * 0.84f)); drawCircle(color, w * 1.2f, Offset(this@Canvas.size.width * 0.76f, this@Canvas.size.height * 0.84f)) }
            Ico.BADGE -> { drawCircle(color, this@Canvas.size.minDimension * 0.3f, Offset(this@Canvas.size.width / 2, this@Canvas.size.height * 0.38f), style = st); g(poly(0.35f, 0.62f, 0.28f, 0.95f, 0.5f, 0.82f, 0.72f, 0.95f, 0.65f, 0.62f)) }
            Ico.TITLE -> g(poly(0.1f, 0.8f, 0.1f, 0.3f, 0.3f, 0.5f, 0.5f, 0.2f, 0.7f, 0.5f, 0.9f, 0.3f, 0.9f, 0.8f, 0.1f, 0.8f))
            Ico.THEME -> { drawCircle(color, this@Canvas.size.minDimension * 0.4f, style = st); drawArc(color, 90f, 180f, true, Offset(this@Canvas.size.width * 0.1f, this@Canvas.size.height * 0.1f), Size(this@Canvas.size.width * 0.8f, this@Canvas.size.height * 0.8f)) }
            Ico.FRAME -> { g(poly(0.1f, 0.1f, 0.9f, 0.1f, 0.9f, 0.9f, 0.1f, 0.9f, 0.1f, 0.1f)); g(poly(0.25f, 0.25f, 0.75f, 0.25f, 0.75f, 0.75f, 0.25f, 0.75f, 0.25f, 0.25f)) }
            Ico.EMBLEM -> drawEmblem(EmblemShape.STAR, color)
        }
    }
}


@Composable
fun EmblemIcon(shape: EmblemShape, color: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Canvas(modifier.size(size)) { drawEmblem(shape, color) }
}

fun DrawScope.drawEmblem(shape: EmblemShape, color: Color) {
    val w = size.minDimension * 0.06f
    fun g(p: Path) = glowPath(p, color, w, 0.7f)
    fun circle(r: Float, c: Offset = center) = Path().apply { addOval(Rect(c, size.minDimension * r)) }
    fun star(n: Int, r1: Float, r2: Float, rot: Double = -Math.PI / 2) = Path().apply {
        for (i in 0 until n * 2) { val r = (if (i % 2 == 0) r1 else r2) * size.minDimension; val a = rot + i * Math.PI / n
            val x = center.x + (cos(a) * r).toFloat(); val y = center.y + (sin(a) * r).toFloat(); if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
    when (shape) {
        EmblemShape.BANG -> { g(circle(0.4f)); g(poly(0.5f, 0.22f, 0.5f, 0.6f)); drawCircle(color, w, Offset(center.x, size.height * 0.75f)) }
        EmblemShape.SWORD -> { g(poly(0.5f, 0.05f, 0.58f, 0.15f, 0.58f, 0.68f, 0.42f, 0.68f, 0.42f, 0.15f, 0.5f, 0.05f)); g(poly(0.25f, 0.68f, 0.75f, 0.68f)); g(poly(0.5f, 0.68f, 0.5f, 0.92f)) }
        EmblemShape.SHIELD -> g(poly(0.5f, 0.06f, 0.88f, 0.2f, 0.82f, 0.6f, 0.5f, 0.94f, 0.18f, 0.6f, 0.12f, 0.2f, 0.5f, 0.06f))
        EmblemShape.CROWN -> g(poly(0.1f, 0.78f, 0.1f, 0.3f, 0.3f, 0.52f, 0.5f, 0.18f, 0.7f, 0.52f, 0.9f, 0.3f, 0.9f, 0.78f, 0.1f, 0.78f))
        EmblemShape.EYE -> { g(poly(0.05f, 0.5f, 0.3f, 0.25f, 0.7f, 0.25f, 0.95f, 0.5f, 0.7f, 0.75f, 0.3f, 0.75f, 0.05f, 0.5f)); g(circle(0.13f)) }
        EmblemShape.FLAME -> g(poly(0.5f, 0.05f, 0.7f, 0.35f, 0.8f, 0.65f, 0.65f, 0.92f, 0.35f, 0.92f, 0.2f, 0.65f, 0.32f, 0.4f, 0.42f, 0.55f, 0.5f, 0.05f))
        EmblemShape.BOLT -> g(poly(0.6f, 0.05f, 0.22f, 0.55f, 0.48f, 0.55f, 0.38f, 0.95f, 0.78f, 0.42f, 0.52f, 0.42f, 0.6f, 0.05f))
        EmblemShape.STAR -> g(star(5, 0.45f, 0.19f))
        EmblemShape.MOON -> { g(circle(0.4f)); drawCircle(Color(0xFF060B14), size.minDimension * 0.33f, center + Offset(size.width * 0.16f, -size.height * 0.08f)) }
        EmblemShape.DIAMOND -> g(poly(0.5f, 0.05f, 0.85f, 0.4f, 0.5f, 0.95f, 0.15f, 0.4f, 0.5f, 0.05f))
        EmblemShape.HEX -> g(star(3, 0.42f, 0.42f, -Math.PI / 2))
        EmblemShape.WING -> g(poly(0.1f, 0.3f, 0.5f, 0.55f, 0.9f, 0.3f, 0.8f, 0.6f, 0.5f, 0.85f, 0.2f, 0.6f, 0.1f, 0.3f))
        EmblemShape.FANG -> g(poly(0.3f, 0.1f, 0.7f, 0.1f, 0.62f, 0.5f, 0.5f, 0.95f, 0.38f, 0.5f, 0.3f, 0.1f))
        EmblemShape.SUN -> { g(circle(0.2f)); g(star(12, 0.46f, 0.3f)) }
        EmblemShape.KEY -> { g(circle(0.15f, Offset(size.width * 0.3f, size.height * 0.5f))); g(poly(0.45f, 0.5f, 0.92f, 0.5f, 0.92f, 0.65f)); g(poly(0.75f, 0.5f, 0.75f, 0.62f)) }
        EmblemShape.GATE -> { g(poly(0.15f, 0.92f, 0.15f, 0.4f, 0.5f, 0.08f, 0.85f, 0.4f, 0.85f, 0.92f)); g(poly(0.35f, 0.92f, 0.35f, 0.5f, 0.65f, 0.5f, 0.65f, 0.92f)) }
        EmblemShape.SKULL -> { g(poly(0.2f, 0.65f, 0.2f, 0.35f, 0.5f, 0.1f, 0.8f, 0.35f, 0.8f, 0.65f, 0.65f, 0.7f, 0.65f, 0.9f, 0.35f, 0.9f, 0.35f, 0.7f, 0.2f, 0.65f)); drawCircle(color, size.minDimension * 0.07f, Offset(size.width * 0.38f, size.height * 0.45f)); drawCircle(color, size.minDimension * 0.07f, Offset(size.width * 0.62f, size.height * 0.45f)) }
        EmblemShape.LOTUS -> { g(poly(0.5f, 0.15f, 0.62f, 0.5f, 0.5f, 0.8f, 0.38f, 0.5f, 0.5f, 0.15f)); g(poly(0.1f, 0.45f, 0.4f, 0.8f, 0.6f, 0.8f, 0.9f, 0.45f)) }
        EmblemShape.TRIDENT -> { g(poly(0.5f, 0.08f, 0.5f, 0.92f)); g(poly(0.2f, 0.15f, 0.2f, 0.42f, 0.8f, 0.42f, 0.8f, 0.15f)) }
        EmblemShape.ORB -> { g(circle(0.4f)); g(circle(0.2f)); drawCircle(color.copy(alpha = 0.3f), size.minDimension * 0.4f) }
    }
}

/** Simple stick-figure diagram of an exercise pose (clearly a diagram, not a fake video). */
fun DrawScope.drawPose(pose: Pose, c: Color, phase: Float) {
    val w = size.width; val h = size.height; val sw = size.minDimension * 0.035f
    fun L(a: Offset, b: Offset) = drawLine(c, a, b, sw, StrokeCap.Round)
    fun P(x: Float, y: Float) = Offset(x * w, y * h)
    fun head(o: Offset) = drawCircle(c, size.minDimension * 0.06f, o, style = Stroke(sw))
    val t = phase
    drawLine(c.copy(alpha = 0.25f), P(0.05f, 0.92f), P(0.95f, 0.92f), sw * 0.6f)
    when (pose) {
        Pose.SQUAT, Pose.JUMP -> { val hip = 0.5f + 0.18f * t; val k = P(0.6f, 0.72f + 0.02f * t); head(P(0.45f, hip - 0.33f)); L(P(0.45f, hip - 0.26f), P(0.42f, hip)); L(P(0.42f, hip), k); L(k, P(0.52f, 0.92f)); L(P(0.45f, hip - 0.2f), P(0.7f, hip - 0.18f)) }
        Pose.HINGE -> { val a = 0.2f + 0.25f * t; head(P(0.32f + a * 0.6f, 0.25f + a * 0.5f)); L(P(0.36f + a * 0.6f, 0.3f + a * 0.5f), P(0.32f, 0.55f)); L(P(0.32f, 0.55f), P(0.34f, 0.92f)); L(P(0.5f + a * 0.4f, 0.4f + a * 0.4f), P(0.5f + a * 0.4f, 0.65f + a * 0.2f)) }
        Pose.LUNGE -> { val d = 0.1f * t; head(P(0.48f, 0.18f + d)); L(P(0.48f, 0.24f + d), P(0.48f, 0.55f + d)); L(P(0.48f, 0.55f + d), P(0.68f, 0.72f + d * 0.5f)); L(P(0.68f, 0.72f + d * 0.5f), P(0.7f, 0.92f)); L(P(0.48f, 0.55f + d), P(0.3f, 0.78f)); L(P(0.3f, 0.78f), P(0.18f, 0.92f)) }
        Pose.PUSHUP, Pose.PLANK, Pose.DIP -> { val y = if (pose == Pose.PLANK) 0.62f else 0.55f + 0.12f * t; head(P(0.18f, y - 0.06f)); L(P(0.24f, y), P(0.85f, 0.88f)); L(P(0.28f, y + 0.02f), P(0.28f, 0.9f)) }
        Pose.PRESS, Pose.RAISE -> { val up = 0.15f + 0.2f * t; head(P(0.5f, 0.28f)); L(P(0.5f, 0.34f), P(0.5f, 0.62f)); L(P(0.5f, 0.62f), P(0.4f, 0.92f)); L(P(0.5f, 0.62f), P(0.6f, 0.92f)); L(P(0.5f, 0.38f), P(0.3f, 0.5f - up)); L(P(0.5f, 0.38f), P(0.7f, 0.5f - up)) }
        Pose.ROW -> { head(P(0.3f, 0.35f)); L(P(0.35f, 0.4f), P(0.6f, 0.6f)); L(P(0.6f, 0.6f), P(0.62f, 0.92f)); L(P(0.42f, 0.46f), P(0.42f + 0.05f * t, 0.72f - 0.18f * t)) }
        Pose.PULLUP, Pose.HANG -> { val y = 0.25f - 0.12f * t; L(P(0.15f, 0.08f), P(0.85f, 0.08f)); head(P(0.5f, y + 0.18f)); L(P(0.5f, y + 0.24f), P(0.5f, y + 0.58f)); L(P(0.5f, y + 0.26f), P(0.35f, 0.08f)); L(P(0.5f, y + 0.26f), P(0.65f, 0.08f)); L(P(0.5f, y + 0.58f), P(0.45f, y + 0.8f)) }
        Pose.BRIDGE, Pose.CORE_FLOOR -> { val y = 0.75f - 0.15f * t; head(P(0.15f, 0.85f)); L(P(0.2f, 0.85f), P(0.5f, y)); L(P(0.5f, y), P(0.7f, 0.7f)); L(P(0.7f, 0.7f), P(0.75f, 0.92f)) }
        Pose.CURL -> { head(P(0.5f, 0.22f)); L(P(0.5f, 0.28f), P(0.5f, 0.6f)); L(P(0.5f, 0.6f), P(0.42f, 0.92f)); L(P(0.5f, 0.6f), P(0.58f, 0.92f)); L(P(0.5f, 0.34f), P(0.55f, 0.5f)); L(P(0.55f, 0.5f), P(0.6f + 0.05f * t, 0.65f - 0.25f * t)) }
        Pose.CARRY, Pose.WALK -> { val s = 0.1f * (t - 0.5f); head(P(0.5f, 0.2f)); L(P(0.5f, 0.26f), P(0.5f, 0.58f)); L(P(0.5f, 0.58f), P(0.42f + s, 0.92f)); L(P(0.5f, 0.58f), P(0.58f - s, 0.92f)); L(P(0.5f, 0.32f), P(0.4f - s, 0.56f)); L(P(0.5f, 0.32f), P(0.6f + s, 0.56f)) }
    }
}
