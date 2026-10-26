package com.ayman.solosystem.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Raids
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.*
import kotlin.random.Random

val SysWhite = Color(0xFFF4F8FF)
val Crimson = Color(0xFFD0102B)
private val GoldHot = Color(0xFFFFD36B)
private val Orb = Color(0xFFC9A8FF)
private val AuraPink = Color(0xFFFF4FA3)
private val AuraOrange = Color(0xFFFF8A3D)

/** Octagon "[!]" header icon used by the QUEST INFO window (anime). */
@Composable
fun OctagonBang(size: Int = 40, color: Color = SysWhite) {
    Canvas(Modifier.size(size.dp)) {
        val r = this.size.minDimension / 2 - 2f
        val p = Path().apply { for (i in 0 until 8) { val a = Math.toRadians(22.5 + i * 45.0); val pt = center + Offset((cos(a) * r).toFloat(), (sin(a) * r).toFloat()); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }; close() }
        glowPath(p, color, 2.4f, 0.8f)
        drawLine(color, Offset(center.x, this.size.height * 0.24f), Offset(center.x, this.size.height * 0.6f), 4f, StrokeCap.Round)
        drawCircle(color, 2.6f, Offset(center.x, this.size.height * 0.75f))
    }
}

@Composable
private fun InfoHeader(title: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) { OctagonBang(30) }
        Spacer(Modifier.width(8.dp))
        Box(Modifier.height(44.dp).thinBox(Sys.panelLine).padding(horizontal = 18.dp), contentAlignment = Alignment.Center) {
            GlowText(title, body(19.sp, FontWeight.Bold), glow = SysWhite)
        }
    }
}

/** Anime selection button: the selected one gets a thick glowing white border. */
@Composable
fun SystemChoice(text: String, selected: Boolean, modifier: Modifier = Modifier, sfx: com.ayman.solosystem.notify.Sfx.S? = com.ayman.solosystem.notify.Sfx.S.TAP, onClick: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "sel").animateFloat(0.65f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "p")
    Box(modifier.height(50.dp).clickable(role = Role.Button) { sfx?.let { com.ayman.solosystem.notify.Sfx.play(it) }; onClick() }.drawBehind {
        val cr = CornerRadius(4.dp.toPx())
        drawRoundRect(Color(0x66000814), cornerRadius = cr)
        if (selected) {
            val a = pulse.value
            listOf(14f to 0.08f, 9f to 0.16f, 5f to 0.32f).forEach { (w, al) -> drawRoundRect(SysWhite.copy(alpha = al * a), style = Stroke(w.dp.toPx() / 2), cornerRadius = cr) }
            drawRoundRect(SysWhite, style = Stroke(3.2.dp.toPx()), cornerRadius = cr)
        } else drawRoundRect(Sys.panelLine, style = Stroke(1.dp.toPx()), cornerRadius = cr)
    }, contentAlignment = Alignment.Center) {
        GlowText(text, body(18.sp, FontWeight.Bold), glow = if (selected) SysWhite else Sys.panelLine)
    }
}

// ------------------------------------------------------------------ 1. QUEST INFO
@Composable
fun SecretQuestInfoWindow(e: UiEffect.SecretQuest, st: AppState, dismiss: () -> Unit) {
    val ar = LocalArabic.current
    NotificationWindow("", SysWhite, dismiss, header = { InfoHeader(tr("معلومات المهمة", "QUEST INFO")) }, sound = com.ayman.solosystem.notify.Sfx.S.SECRET) {
        GlitchBlock(intense = false) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                GlowText(tr("[مهمة سرّية: تدريب القوّة وصلت.]", "[Secret Quest: Strength Training has arrived.]"), body(17.sp, FontWeight.Bold), Modifier.fillMaxWidth(), glow = SysWhite, align = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                GlowText(tr("الهدف", "GOAL"), body(22.sp, FontWeight.Bold), glow = SysWhite)
                Spacer(Modifier.height(6.dp))
                e.rows.forEach { q ->
                    val v = GoalPlan.variantOf(q)
                    val unit = v?.let { if (ar) it.unit.ar else it.unit.en } ?: ""
                    val cur = v?.let { GoalPlan.fmtV(it, q.progress) } ?: "${q.progress}"
                    val tgt = v?.let { GoalPlan.fmtV(it, q.target) } ?: "${q.target}"
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label("- " + q.title.get(ar), Modifier.weight(1f), Sys.text, 16, w = FontWeight.Medium)
                        BasicText(buildAnnotatedString {
                            append("[")
                            withStyle(SpanStyle(color = Sys.gold, fontWeight = FontWeight.ExtraBold)) { append(cur) }
                            append("/$tgt$unit]")
                        }, style = tech(17.sp, Sys.text))
                    }
                }
                Spacer(Modifier.height(10.dp))
                val warn = buildAnnotatedString {
                    append(tr("تحذير: الضعف ", "WARNING: Doubling is "))
                    withStyle(SpanStyle(color = Sys.red, fontWeight = FontWeight.Bold)) { append(tr("ليس مطلوبًا أبدًا", "never required")) }
                    append(tr(". عند أي ألم توقّف فورًا. المكافآت تُمنح مرة واحدة لهذا اليوم.", ". Stop at once on any pain. Rewards are granted once for this day."))
                }
                BasicText(warn, Modifier.fillMaxWidth(), style = body(13.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center, lineHeight = 20.sp))
            }
        }
        if (e.overCap) Label(tr("⚠ تجاوزت سقف القدرة/الطاقة الآمن في صف واحد على الأقل. الإنجاز محسوب، لكن استمع لجسدك.", "⚠ At least one row went past your safe capacity/energy cap. It counts, but listen to your body."), color = Sys.amber, size = 12, align = TextAlign.Center)
        val add = com.ayman.solosystem.domain.nutrition.Nutrition.runAddBack(st, e.day)
        if (add > 0) Label(tr("أضيف ~$add سعرة لهدف الترياق اليوم مقابل الجري.", "~$add kcal added to today's Elixir target for the run."), color = com.ayman.solosystem.ui.screens.Elixir, size = 12, align = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        SystemChoice(tr("حسنًا", "OK"), true, Modifier.fillMaxWidth(), onClick = dismiss)
    }
}

// ------------------------------------------------------------------ 2 + 3. NOTICE → REWARDS LIST
@Composable
fun SecretRewardWindow(e: UiEffect.SecretReward, repo: Repository, dismiss: () -> Unit) {
    val st by repo.state.collectAsState()
    val claim = st.secretClaims[e.day]
    var list by remember { mutableStateOf(e.direct) }
    var yes by remember { mutableStateOf(true) }
    var pick by remember { mutableStateOf("blessed") }
    if (!list) {
        NotificationWindow(tr("إشعار", "NOTIFICATION"), SysWhite, dismiss, contentKey = false) {
            Center2(tr("تلقيت مكافآت.", "You have received rewards."), 18)
            Center2(tr("تفقد مكافآتك؟", "Check your rewards?"), 17)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                SystemChoice(tr("لا", "No"), !yes, Modifier.weight(1f), sfx = if (!yes) com.ayman.solosystem.notify.Sfx.S.NO else com.ayman.solosystem.notify.Sfx.S.SELECT) { if (!yes) dismiss() else yes = false }
                SystemChoice(tr("نعم", "Yes"), yes, Modifier.weight(1f), sfx = if (yes) com.ayman.solosystem.notify.Sfx.S.YES else com.ayman.solosystem.notify.Sfx.S.SELECT) { if (yes) list = true else yes = true }
            }
            Label(tr("اضغط مرة للاختيار ومرة للتأكيد. المكافآت تبقى في المخزون إن اخترت لا.", "Tap once to select, again to confirm. Rewards wait in your inventory if you pick No."), size = 11, align = TextAlign.Center)
        }
        return
    }
    NotificationWindow(tr("مكافآت المهمة", "QUEST REWARDS"), SysWhite, dismiss, contentKey = true) {
        Center2(tr("تلقيت مكافآت", "You have received rewards"), 16)
        Spacer(Modifier.height(8.dp))
        RewardLine("1.", tr("الشفاء الكامل", "Full Recovery"), false) {}
        Label(tr("داخل اللعبة فقط: رمز يحوّل يومًا إلى يوم راحة كامل مجاني بلا عقاب.", "In-game only: a token that turns a day into a free full rest day, no penalty."), size = 11, align = TextAlign.Center)
        RewardLine("2.", tr("نقاط القدرة +${claim?.points ?: 0}", "Ability points +${claim?.points ?: 0}"), false) {}
        Label(tr("وزّعها على سماتك من شاشة الحالة.", "Allocate them to your attributes on the Status screen."), size = 11, align = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp, 110.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) { GlowText("3.", body(18.sp, FontWeight.Bold), glow = SysWhite) }
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                RewardLine(null, tr("صندوق غنائم عشوائية مبارك", "Blessed random loot box"), pick == "blessed") { pick = "blessed" }
                RewardLine(null, tr("صندوق غنائم عشوائية ملعون", "Cursed random loot box"), pick == "cursed") { pick = "cursed" }
            }
        }
        Spacer(Modifier.height(6.dp))
        Label(if (pick == "blessed") tr("سأختار الصندوق المبارك حاليًا — فرص جيدة وآمنة، بلا لعنات.", "I'll take the Blessed box for now — good, safe odds, never cursed.")
            else tr("الملعون: فرص أسطورية أعلى، لكن قد يحمل لعنة تحدٍّ أصعب ليوم واحد (+10 % ضمن حدود السلامة، لا علاقة لها بالأكل).", "Cursed: higher legendary odds, but may carry a harder one-day challenge curse (+10 % within safety caps, never food-related)."),
            color = if (pick == "blessed") Sys.text else AuraPink, size = 13, align = TextAlign.Center, w = FontWeight.Medium)
        Spacer(Modifier.height(10.dp))
        if (claim == null || claim.claimed) {
            Label(tr("تم استلام المكافآت.", "Rewards already claimed."), color = Sys.green, size = 13, align = TextAlign.Center)
            SystemChoice(tr("حسنًا", "OK"), true, Modifier.fillMaxWidth(), onClick = dismiss)
        } else SystemChoice(tr("استلام", "Claim"), true, Modifier.fillMaxWidth(), sfx = com.ayman.solosystem.notify.Sfx.S.YES) {
            repo.apply { s -> GameEngine.claimSecret(s, e.day, pick, repo.today(), repo.now()) }; dismiss()
        }
    }
}

@Composable private fun Center2(text: String, size: Int) =
    GlowText(text, body(size.sp, FontWeight.Bold), Modifier.fillMaxWidth().padding(vertical = 3.dp), glow = SysWhite, align = TextAlign.Center)

/** One reward row; the selected one becomes a SOLID white box with dark text (anime highlight). */
@Composable
private fun RewardLine(num: String?, text: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (num != null) { Box(Modifier.size(40.dp).thinBox(Sys.panelLine), contentAlignment = Alignment.Center) { GlowText(num, body(17.sp, FontWeight.Bold), glow = SysWhite) }; Spacer(Modifier.width(8.dp)) }
        Box(Modifier.weight(1f).height(46.dp).clickable(role = Role.Button, onClick = onClick)
            .then(if (selected) Modifier.background(SysWhite, RoundedCornerShape(2.dp)) else Modifier.thinBox(Sys.panelLine)), contentAlignment = Alignment.Center) {
            if (selected) BasicText(text, style = body(16.sp, FontWeight.Bold, Color(0xFF0A1322)).copy(textAlign = TextAlign.Center))
            else GlowText(text, body(16.sp, FontWeight.Bold), glow = SysWhite, align = TextAlign.Center)
        }
    }
}

// ------------------------------------------------------------------ 4. OPENING ANIMATION
private const val ORB_SHADER = """
uniform float2 res;
uniform float t;
uniform float grow;
uniform float stretch;
uniform float aura;
float hash(float2 p) { return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453); }
float noise(float2 p) { float2 i = floor(p); float2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
  return mix(mix(hash(i), hash(i + float2(1, 0)), f.x), mix(hash(i + float2(0, 1)), hash(i + float2(1, 1)), f.x), f.y); }
half4 main(float2 p) {
  float2 uv = (p - res * 0.5) / res.y;
  uv.y /= (1.0 + stretch * 1.6);
  uv.x *= (1.0 + stretch * 0.35);
  float d = length(uv);
  float r = 0.11 * grow;
  float core = smoothstep(r, r * 0.25, d);
  float glow = exp(-d * d / (r * r * 5.0 + 1e-4)) * grow;
  float a = atan(uv.y, uv.x);
  float n = noise(float2(a * 3.0 + t * 5.0, t * 3.0)) * 0.6 + noise(float2(a * 9.0 - t * 11.0, d * 20.0)) * 0.4;
  float ring = r * (1.5 + 0.5 * n);
  float e = exp(-abs(d - ring) * 70.0) * aura;
  float arcs = pow(noise(float2(a * 14.0 + t * 25.0, d * 40.0 - t * 30.0)), 6.0) * 6.0 * aura * smoothstep(r * 3.5, r, d);
  half3 col = half3(1.0) * core + half3(0.78, 0.6, 1.0) * glow * 0.95 + half3(1.0, 0.42, 0.3) * e + half3(1.0, 0.3, 0.75) * arcs;
  float al = clamp(max(max(core, glow * 0.95), max(e, arcs)), 0.0, 1.0);
  return half4(col * al, al);
}
"""

@RequiresApi(33)
private class OrbFx { val sh = RuntimeShader(ORB_SHADER); val brush = ShaderBrush(sh) }

private class Voxel(val x: Float, val y: Float, val vx: Float, val vy: Float, val rot: Float, val size: Float, val delay: Float)

/**
 * Box glows gold from the seams → shatters into voxel squares floating up → condenses into a white/purple orb with sparkles →
 * stretches with a pink-orange electric aura → flash → item. ~3.6 s; AGSL orb on API 33+, Canvas fallback below.
 */
@Composable
fun BoxOpening(cursed: Boolean, modifier: Modifier = Modifier, onDone: () -> Unit) {
    val reduced = LocalReducedMotion.current
    val prog = remember { Animatable(if (reduced) 1f else 0f) }
    val clock = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        if (reduced) { onDone(); return@LaunchedEffect }
        val start = withFrameNanos { it }
        // sound timeline locked to the animation: charge (seam glow) → voxel shatter → orb forms
        val cues = mutableListOf(0f to com.ayman.solosystem.notify.Sfx.S.CHEST_CHARGE, 0.97f to com.ayman.solosystem.notify.Sfx.S.CHEST_BREAK, 1.3f to com.ayman.solosystem.notify.Sfx.S.ORB)
        launchClock@ while (prog.value < 1f) {
            val n = withFrameNanos { it }
            val t = (n - start) / 1e9f
            while (cues.isNotEmpty() && t >= cues[0].first) { com.ayman.solosystem.notify.Sfx.play(cues.removeAt(0).second) }
            clock.floatValue = t
            prog.snapTo((t / 3.6f).coerceAtMost(1f))
        }
        onDone()
    }
    val fx = remember { if (Build.VERSION.SDK_INT >= 33) OrbFx() else null }
    val vox = remember { val r = Random(7); List(90) { i -> val gx = i % 10; val gy = i / 10
        Voxel(gx / 9f - 0.5f, gy / 8f - 0.5f, (r.nextFloat() - 0.5f) * 1.4f, -(0.6f + r.nextFloat() * 1.3f), r.nextFloat() * 360f, 0.6f + r.nextFloat() * 0.8f, r.nextFloat() * 0.06f) } }
    val sparks = remember { val r = Random(11); List(26) { Triple(r.nextFloat() * 6.283f, 0.6f + r.nextFloat() * 1.2f, r.nextFloat()) } }
    val boxCol = if (cursed) Color(0xFF7A1630) else Color(0xFFE87A84)
    Canvas(modifier.fillMaxWidth().height(300.dp)) {
        val T = prog.value; val time = clock.floatValue
        val c = center; val u = size.minDimension
        fun ph(a: Float, b: Float) = ((T - a) / (b - a)).coerceIn(0f, 1f)
        // phase A: the closed box, gold light leaking from the seams, rising shake
        val seam = ph(0.02f, 0.26f); val shatter = ph(0.27f, 0.56f)
        if (T < 0.29f) {
            val shake = seam * seam * 6.dp.toPx() * sin(time * 90f)
            val bw = u * 0.46f; val bh = u * 0.36f
            val tl = Offset(c.x - bw / 2 + shake, c.y - bh / 2 + u * 0.05f)
            // rays from the seams
            for (i in 0 until 14) {
                val a = Math.toRadians(-90.0 + (i - 6.5) * 13.0 + sin(time * 3 + i) * 3)
                val len = u * (0.25f + 0.35f * seam) * (0.7f + 0.3f * ((i * 37) % 5) / 4f)
                drawLine(GoldHot.copy(alpha = 0.32f * seam), Offset(c.x + shake, c.y - bh * 0.2f), Offset(c.x + shake + (cos(a) * len).toFloat(), c.y - bh * 0.2f + (sin(a) * len).toFloat()), (4 + i % 3 * 3).dp.toPx(), blendMode = BlendMode.Plus)
            }
            drawRect(Brush.verticalGradient(listOf(boxCol, boxCol.copy(red = boxCol.red * 0.7f, green = boxCol.green * 0.6f)), tl.y, tl.y + bh), tl, Size(bw, bh))
            val lid = Offset(tl.x - bw * 0.05f, tl.y - bh * 0.22f)
            drawRect(boxCol.copy(alpha = 1f), lid, Size(bw * 1.1f, bh * 0.24f))
            // ribbon
            drawRect(Color(0xFFFFE6A8), Offset(c.x - bw * 0.07f + shake, lid.y), Size(bw * 0.14f, bh * 1.22f))
            drawRect(Color(0xFFFFE6A8), Offset(lid.x, lid.y + bh * 0.06f), Size(bw * 1.1f, bh * 0.1f))
            // glowing seams
            val g = 0.25f + 0.75f * seam
            listOf(9f to 0.12f, 5f to 0.25f, 2.2f to 0.9f).forEach { (w, a) ->
                val col = (if (seam > 0.7f) Color.White else GoldHot).copy(alpha = a * g)
                drawLine(col, Offset(lid.x, tl.y + 1f), Offset(lid.x + bw * 1.1f, tl.y + 1f), w.dp.toPx(), blendMode = BlendMode.Plus)
                drawLine(col, Offset(c.x - bw * 0.07f + shake, lid.y), Offset(c.x - bw * 0.07f + shake, tl.y + bh), w.dp.toPx() * 0.7f, blendMode = BlendMode.Plus)
                drawLine(col, Offset(c.x + bw * 0.07f + shake, lid.y), Offset(c.x + bw * 0.07f + shake, tl.y + bh), w.dp.toPx() * 0.7f, blendMode = BlendMode.Plus)
            }
            drawCircle(Brush.radialGradient(listOf(GoldHot.copy(alpha = 0.55f * seam), Color.Transparent), Offset(c.x, tl.y), u * 0.5f), u * 0.5f, Offset(c.x, tl.y), blendMode = BlendMode.Plus)
        }
        // burst flash at the break
        val flash1 = (1f - abs(T - 0.28f) / 0.04f).coerceIn(0f, 1f)
        if (flash1 > 0f) drawRect(Color.White.copy(alpha = 0.75f * flash1), blendMode = BlendMode.Plus)
        // phase B: voxel squares floating up, gold → white, fading
        if (shatter in 0.001f..0.999f) vox.forEach { v ->
            val k = ((shatter - v.delay) / (1f - v.delay)).coerceIn(0f, 1f)
            if (k <= 0f) return@forEach
            val px = c.x + v.x * u * 0.46f + v.vx * u * 0.35f * k
            val py = c.y + v.y * u * 0.36f + v.vy * u * 0.55f * k - u * 0.1f * k * k
            val s = v.size * u * 0.045f * (1f - 0.5f * k)
            val col = lerp(GoldHot, Color.White, k)
            val a = (1f - k).coerceIn(0f, 1f)
            rotate(v.rot + k * 220f, Offset(px, py)) {
                drawRect(col.copy(alpha = 0.22f * a), Offset(px - s, py - s), Size(s * 2, s * 2), blendMode = BlendMode.Plus)
                drawRect(col.copy(alpha = a), Offset(px - s / 2, py - s / 2), Size(s, s))
                drawRect(Color.White.copy(alpha = 0.8f * a), Offset(px - s / 2, py - s / 2), Size(s / 2, s / 2))
            }
        }
        // phase C/D: orb condenses, sparkles orbit; then stretches with an electric pink-orange aura
        val grow = ph(0.36f, 0.6f); val stretch = ph(0.62f, 0.86f); val aura = ph(0.58f, 0.8f) * (1f - ph(0.9f, 0.97f))
        val orbVis = grow > 0f && T < 0.97f
        if (orbVis) {
            if (fx != null && Build.VERSION.SDK_INT >= 33) {
                fx.sh.setFloatUniform("res", size.width, size.height); fx.sh.setFloatUniform("t", time)
                fx.sh.setFloatUniform("grow", grow); fx.sh.setFloatUniform("stretch", stretch); fx.sh.setFloatUniform("aura", aura)
                drawRect(fx.brush, blendMode = BlendMode.Plus)
            } else {
                val r = size.height * 0.11f * grow
                withTransform({ scale(1f / (1f + stretch * 0.35f), 1f + stretch * 1.6f, c) }) {
                    drawCircle(Brush.radialGradient(listOf(Orb.copy(alpha = 0.9f * grow), Orb.copy(alpha = 0.25f * grow), Color.Transparent), c, r * 2.6f), r * 2.6f, c, blendMode = BlendMode.Plus)
                    drawCircle(Color.White, r, c)
                }
            }
            // electric arcs (both paths) — jagged polylines regenerated every frame
            if (aura > 0f) {
                val rr = Random((time * 30).toInt())
                repeat(7) { i ->
                    val p = Path(); val a0 = rr.nextFloat() * 6.283f
                    val rad = size.height * 0.11f * (1.4f + rr.nextFloat() * 0.6f)
                    for (j in 0..7) {
                        val a = a0 + j * 0.22f
                        val rj = rad * (1f + (rr.nextFloat() - 0.5f) * 0.35f)
                        val pt = Offset(c.x + cos(a) * rj / (1f + stretch * 0.35f), c.y + sin(a) * rj * (1f + stretch * 1.6f))
                        if (j == 0) p.moveTo(pt.x, pt.y) else p.lineTo(pt.x, pt.y)
                    }
                    val col = if (i % 2 == 0) AuraPink else AuraOrange
                    drawPath(p, col.copy(alpha = 0.35f * aura), style = Stroke(6.dp.toPx()), blendMode = BlendMode.Plus)
                    drawPath(p, Color.White.copy(alpha = 0.85f * aura), style = Stroke(1.4.dp.toPx()), blendMode = BlendMode.Plus)
                }
            }
            // sparkles
            sparks.forEach { (a0, sp, ph0) ->
                val a = a0 + time * sp
                val rad = size.height * (0.16f + 0.1f * ph0) * (1f + stretch * 0.6f)
                val pt = Offset(c.x + cos(a) * rad, c.y + sin(a) * rad * 0.8f)
                val tw = (0.5f + 0.5f * sin(time * 9f + ph0 * 20f)) * grow
                sparkle(pt, (3 + 4 * ph0).dp.toPx() * tw, Color.White.copy(alpha = tw))
            }
        }
        // final white flash → item
        val flash2 = ph(0.86f, 0.93f) * (1f - ph(0.95f, 1f))
        if (flash2 > 0f) drawRect(Color.White.copy(alpha = 0.9f * flash2), blendMode = BlendMode.Plus)
    }
}

private fun DrawScope.sparkle(p: Offset, r: Float, c: Color) {
    if (r <= 0.5f) return
    val path = Path().apply { moveTo(p.x, p.y - r); quadraticTo(p.x, p.y, p.x + r, p.y); quadraticTo(p.x, p.y, p.x, p.y + r); quadraticTo(p.x, p.y, p.x - r, p.y); quadraticTo(p.x, p.y, p.x, p.y - r); close() }
    drawPath(path, c, blendMode = BlendMode.Plus)
    drawCircle(c.copy(alpha = c.alpha * 0.3f), r * 0.9f, p, blendMode = BlendMode.Plus)
}

// ------------------------------------------------------------------ 5. KEY art + item card
/** Ornate crimson key (bow with spikes + gem, ridged shaft, double bit). [silhouette] = glowing white silhouette. */
@Composable
fun KeyArt(modifier: Modifier = Modifier, silhouette: Boolean = false) {
    val t = rememberInfiniteTransition(label = "key").animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "k")
    Canvas(modifier) {
        val w = size.width; val h = size.height
        val cy = h / 2
        val main = if (silhouette) Color.White else Crimson
        val dark = if (silhouette) Color.White else Color(0xFF6E0618)
        val hi = if (silhouette) Color.White else Color(0xFFFF6A7A)
        val pulse = 0.6f + 0.4f * sin(t.value * 6.283f)
        withTransform({ rotate(-18f, center) }) {
            // glow underlay
            drawRoundRect((if (silhouette) Color.White else Crimson).copy(alpha = 0.18f * pulse), Offset(w * 0.08f, cy - h * 0.2f), Size(w * 0.84f, h * 0.4f), CornerRadius(h * 0.2f), blendMode = BlendMode.Plus)
            // bow: spiked ring
            val bc = Offset(w * 0.22f, cy); val br = h * 0.2f
            val spikes = Path().apply { for (i in 0 until 16) { val a = i * PI / 8; val rr = if (i % 2 == 0) br * 1.45f else br * 0.95f; val pt = bc + Offset((cos(a) * rr).toFloat(), (sin(a) * rr).toFloat()); if (i == 0) moveTo(pt.x, pt.y) else lineTo(pt.x, pt.y) }; close() }
            drawPath(spikes, dark)
            drawPath(spikes, main, style = Stroke(h * 0.025f))
            drawCircle(main, br, bc)
            drawCircle(dark, br * 0.55f, bc)
            drawCircle(if (silhouette) Color.White else Color(0xFFFF2D55), br * 0.32f, bc)
            if (!silhouette) drawCircle(Color.White.copy(alpha = 0.7f), br * 0.1f, bc + Offset(-br * 0.1f, -br * 0.1f))
            // collar + shaft
            drawRect(main, Offset(bc.x + br * 1.2f, cy - h * 0.06f), Size(w * 0.07f, h * 0.12f))
            drawRect(main, Offset(bc.x + br * 1.2f, cy - h * 0.035f), Size(w * 0.56f, h * 0.07f))
            for (i in 0 until 5) drawRect(dark, Offset(bc.x + br * 1.2f + w * (0.12f + i * 0.08f), cy - h * 0.035f), Size(w * 0.015f, h * 0.07f))
            drawLine(hi.copy(alpha = 0.8f), Offset(bc.x + br * 1.2f, cy - h * 0.02f), Offset(bc.x + br * 1.2f + w * 0.56f, cy - h * 0.02f), h * 0.008f)
            // bit (double, ornate)
            val bx = bc.x + br * 1.2f + w * 0.42f
            val bit = Path().apply { moveTo(bx, cy + h * 0.03f); lineTo(bx, cy + h * 0.2f); lineTo(bx + w * 0.05f, cy + h * 0.2f); lineTo(bx + w * 0.05f, cy + h * 0.12f); lineTo(bx + w * 0.09f, cy + h * 0.12f); lineTo(bx + w * 0.09f, cy + h * 0.24f); lineTo(bx + w * 0.14f, cy + h * 0.24f); lineTo(bx + w * 0.14f, cy + h * 0.03f); close() }
            drawPath(bit, main); drawPath(bit, dark, style = Stroke(h * 0.012f))
            val top = Path().apply { moveTo(bx + w * 0.02f, cy - h * 0.03f); lineTo(bx + w * 0.02f, cy - h * 0.14f); lineTo(bx + w * 0.07f, cy - h * 0.09f); lineTo(bx + w * 0.12f, cy - h * 0.14f); lineTo(bx + w * 0.12f, cy - h * 0.03f); close() }
            drawPath(top, main)
            // tip finial
            drawCircle(main, h * 0.045f, Offset(bc.x + br * 1.2f + w * 0.58f, cy))
            if (silhouette) drawRoundRect(Color.White.copy(alpha = 0.25f * pulse), Offset(w * 0.04f, cy - h * 0.32f), Size(w * 0.92f, h * 0.64f), CornerRadius(h * 0.3f), style = Stroke(h * 0.05f), blendMode = BlendMode.Plus)
        }
    }
}

@Composable
fun KeyItemCard(k: RaidKey?, today: Long) {
    val rank = Raids.rankLabel(k?.rank ?: 1)
    Row(Modifier.fillMaxWidth().thinBox(Sys.panelLine).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(96.dp).background(Color(0xAA000814)).thinBox(SysWhite.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) { KeyArt(Modifier.size(84.dp), silhouette = true) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            GlowText(tr("غرض: مفتاح الغارة [الرتبة $rank]", "Item: Raid Key [Rank $rank]"), body(15.sp, FontWeight.Bold), glow = SysWhite)
            Label(tr("صعوبة الاكتساب: الرتبة S", "Acquisition difficulty: Rank S"), color = Sys.text, size = 13)
            Label(tr("الفئة: مفتاح", "Category: Key"), color = Sys.text, size = 13)
        }
    }
    Spacer(Modifier.height(6.dp))
    Label(tr("مفتاح يفتح بوابة غارة واحدة: حصة صعبة أعلى من رتبتك الحالية بدرجة (رتبة $rank)، مولّدة من قدرتك وضمن حدود السلامة. الفشل لا يسبب أي عقاب.",
        "Opens one raid gate: a hard session one tier above your current tier (rank $rank), generated from your capacity within safety caps. Failing gives no penalty."), color = Sys.text, size = 13, align = TextAlign.Center)
    if (k != null) Label(tr("يبقى في المخزون حتى تستخدمه • ينتهي بعد ${(k.expiresDay - today).coerceAtLeast(0)} أيام", "Stays in your inventory until used • expires in ${(k.expiresDay - today).coerceAtLeast(0)} days"), color = Sys.amber, size = 12, align = TextAlign.Center)
}

/** LootOpened for Blessed / Cursed / Secret boxes: full opening sequence, then the item (or the KEY card). */
@Composable
fun SecretLootWindow(e: UiEffect.LootOpened, repo: Repository, nav: (String) -> Unit, dismiss: () -> Unit) {
    val ar = LocalArabic.current
    val d = RewardCatalog.get(e.grant.rewardId)
    var done by remember { mutableStateOf(false) }
    NotificationWindow(if (done) tr("تم الحصول على غرض", "ITEM OBTAINED") else e.box.name.get(ar), if (done) (d?.rarity?.c() ?: SysWhite) else SysWhite, dismiss, contentKey = done, sound = null) {
        if (!done) { BoxOpening(e.cursed || e.box.id == "box_cursed") { done = true; if (e.grant.rewardId == "key_raid") com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.KEY) else playRarity(d?.rarity) }; return@NotificationWindow }
        if (e.grant.rewardId == "key_raid") {
            KeyArt(Modifier.fillMaxWidth().height(130.dp))
            KeyItemCard(e.key, repo.today())
        } else if (d != null) {
            GlowText((if (ar) d.rarity.ar else d.rarity.en).uppercase(), tech(26.sp, d.rarity.c()), glow = d.rarity.c())
            RewardCard(d, extra = if (e.grant.goldInstead > 0) tr("مكرر ← +${e.grant.goldInstead} ذهب", "Duplicate → +${e.grant.goldInstead} gold") else null)
        }
        if (e.cursed) {
            Spacer(Modifier.height(6.dp))
            Column(Modifier.fillMaxWidth().thinBox(AuraPink).padding(8.dp)) {
                GlowText(tr("لعنة: الثقل الحديدي", "Curse: Iron Weight"), body(14.sp, FontWeight.Bold, AuraPink), glow = AuraPink)
                Label(tr("غدًا صفوف GOAL أصعب بـ 10 % — لا تتجاوز سقوف السلامة، وتُلغى تلقائيًا في يوم راحة أو ألم. لا علاقة لها بالأكل.", "Tomorrow the GOAL rows are 10 % harder — never above safety caps, auto-cancelled on rest or pain days. Never food-related."), size = 12)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemChoice(tr("المخزون", "Inventory"), false, Modifier.weight(1f)) { dismiss(); nav("inventory") }
            SystemChoice(tr("حسنًا", "OK"), true, Modifier.weight(1f), onClick = dismiss)
        }
    }
}

// ------------------------------------------------------------------ RAID windows
@Composable
fun RaidGateWindow(e: UiEffect.RaidGate, repo: Repository, nav: (String) -> Unit, dismiss: () -> Unit) {
    var yes by remember { mutableStateOf(true) }
    val st by repo.state.collectAsState()
    val steps = remember { Raids.steps(st, e.key.rank, repo.today()) }
    val ar = LocalArabic.current
    NotificationWindow("", Crimson, dismiss, header = { InfoHeader(tr("بوابة الغارة", "RAID GATE")) }, sound = com.ayman.solosystem.notify.Sfx.S.SECRET) {
        KeyArt(Modifier.fillMaxWidth().height(90.dp))
        GlowText(tr("غارة الرتبة ${Raids.rankLabel(e.key.rank)}", "Rank ${Raids.rankLabel(e.key.rank)} raid"), body(20.sp, FontWeight.Bold, Crimson), glow = Crimson)
        steps.forEach { s -> Label("• ${Raids.stepName(s).get(ar)} — ${Raids.stepAmount(s, ar)}" + (if (s.sets > 1) tr(" (${s.sets} مجموعات)", " (${s.sets} sets)") else ""), color = Sys.text, size = 14) }
        Label(tr("المدة: ${Raids.limitMin(steps)} دقيقة • إحماء 5 دقائق أولًا • عند أي ألم توقّف. الفشل أو الانسحاب بلا عقاب.", "Time: ${Raids.limitMin(steps)} min • 5-min warm-up first • stop on any pain. Failing or leaving has no penalty."), size = 12, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Center2(tr("هل تريد دخول الغارة؟", "Do you want to enter the raid?"), 18)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SystemChoice(tr("لا", "No"), !yes, Modifier.weight(1f), sfx = if (!yes) com.ayman.solosystem.notify.Sfx.S.NO else com.ayman.solosystem.notify.Sfx.S.SELECT) { if (!yes) dismiss() else yes = false }
            SystemChoice(tr("نعم", "Yes"), yes, Modifier.weight(1f), sfx = if (yes) com.ayman.solosystem.notify.Sfx.S.YES else com.ayman.solosystem.notify.Sfx.S.SELECT) {
                if (!yes) yes = true else { repo.apply { s -> GameEngine.enterRaid(s, e.key.id, repo.today(), repo.now()) }; dismiss(); nav("raid") }
            }
        }
    }
}

@Composable
fun RaidResultWindow(e: UiEffect.RaidResult, nav: (String) -> Unit, dismiss: () -> Unit) {
    val r = e.raid
    NotificationWindow(tr("إشعار", "NOTIFICATION"), if (r.cleared) Sys.gold else SysWhite, dismiss, sound = if (r.cleared) com.ayman.solosystem.notify.Sfx.S.REWARD_LEGENDARY else com.ayman.solosystem.notify.Sfx.S.WIN_OPEN) {
        if (r.cleared) {
            RayBurst(Sys.gold) { GlowText(Raids.rankLabel(r.rank), tech(46.sp, Sys.gold), glow = Sys.gold) }
            Center2(tr("تمت تصفية الغارة!", "RAID CLEARED!"), 22)
            Label(tr("+${r.xp} XP • +${Raids.goldFor(r.rank)} ذهب", "+${r.xp} XP • +${Raids.goldFor(r.rank)} gold"), color = Sys.gold, size = 16, align = TextAlign.Center, w = FontWeight.Bold)
            e.box?.let { g -> RewardCatalog.get(g.rewardId)?.let { RewardCard(it, extra = tr("غنيمة نادرة", "Rare loot")) } }
            Label(tr("سُجّلت في سجل الغارات.", "Recorded in your raid log."), size = 12, align = TextAlign.Center)
        } else {
            Center2(tr("انتهت الغارة", "The raid has ended"), 20)
            Label(tr("لا عقاب — الغارات اختيارية. المحاولة نفسها تدريب.", "No penalty — raids are optional. The attempt itself is training."), color = Sys.green, size = 14, align = TextAlign.Center)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (e.box != null) SystemChoice(tr("المخزون", "Inventory"), false, Modifier.weight(1f)) { dismiss(); nav("inventory") }
            SystemChoice(tr("حسنًا", "OK"), true, Modifier.weight(1f), onClick = dismiss)
        }
    }
}

@Composable
fun AssessProposeWindow(e: UiEffect.AssessPropose, repo: Repository, nav: (String) -> Unit, dismiss: () -> Unit) {
    val ar = LocalArabic.current
    LaunchedEffect(Unit) { repo.set { GameEngine.markAssessProposed(it, repo.today()) } }
    NotificationWindow("", SysWhite, dismiss, header = { InfoHeader(tr("اقتراح النظام", "SYSTEM PROPOSAL")) }) {
        Center2(tr("تقييم الرتبة متاح", "Tier assessment available"), 18)
        Label(e.why.get(ar), color = Sys.text, size = 14, align = TextAlign.Center)
        Label(tr("الاختبار اختياري: توقّف قبل الفشل الفني بتكرار أو اثنين.", "Optional test: stop 1–2 reps before technical failure."), size = 12, align = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SystemChoice(tr("لاحقًا", "Later"), false, Modifier.weight(1f), sfx = com.ayman.solosystem.notify.Sfx.S.TAP_SECONDARY, onClick = dismiss)
            SystemChoice(tr("عرض الاختبار", "View test"), true, Modifier.weight(1f)) { dismiss(); nav("tiers") }
        }
    }
}

// ------------------------------------------------------------------ RAID screen (timer)
@Composable
fun RaidScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    val r = Raids.active(st)
    com.ayman.solosystem.ui.screens.Screen(tr("الغارة", "RAID"), nav) {
        if (r == null) {
            val keys = Raids.activeKeys(st, today)
            SysPanel(Modifier.fillMaxWidth(), accent = Crimson) {
                TitleBox(tr("مفاتيح الغارة", "RAID KEYS"), accent = Crimson)
                if (keys.isEmpty()) Label(tr("لا مفاتيح الآن. قد يسقط مفتاح أسطوري من صناديق المهمة السرّية.", "No keys now. A legendary key may drop from Secret Quest boxes."), Modifier.fillMaxWidth(), align = TextAlign.Center)
                keys.forEach { k ->
                    Spacer(Modifier.height(8.dp)); KeyItemCard(k, today)
                    SysButton(tr("استخدم المفتاح", "Use key"), Modifier.fillMaxWidth(), accent = Crimson) { repo.apply { s -> GameEngine.raidGate(s, k.id, today) } }
                }
            }
        } else {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(r.id) { while (true) { now = System.currentTimeMillis(); delay(250) } }
            val el = ((now - r.startMs) / 1000).coerceAtLeast(0); val lim = r.limitMin * 60L
            val left = lim - el
            SysPanel(Modifier.fillMaxWidth(), accent = Crimson, pulse = true) {
                TitleBox(tr("غارة الرتبة ${Raids.rankLabel(r.rank)}", "RANK ${Raids.rankLabel(r.rank)} RAID"), accent = Crimson)
                Spacer(Modifier.height(8.dp))
                GlowText("%02d:%02d".format(java.util.Locale.US, abs(left) / 60, abs(left) % 60), tech(44.sp, if (left >= 0) SysWhite else Sys.amber), Modifier.fillMaxWidth(), glow = if (left >= 0) Crimson else Sys.amber, align = TextAlign.Center)
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRect(Sys.panelLine.copy(alpha = 0.3f))
                    drawRect(Crimson, size = Size(size.width * (el.toFloat() / lim).coerceIn(0f, 1f), size.height))
                }
                Label(if (left >= 0) tr("الوقت المتبقي", "Time left") else tr("انتهى الوقت — يمكنك الإنهاء، لا عقاب", "Time is up — you can finish, no penalty"), Modifier.fillMaxWidth(), align = TextAlign.Center, size = 12)
                Spacer(Modifier.height(8.dp))
                r.steps.forEachIndexed { i, s ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { repo.set { GameEngine.raidStep(it, i) } }.thinBox(if (s.done) Sys.green else Sys.panelLine).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Label(if (s.done) "☑" else "☐", color = if (s.done) Sys.green else Sys.text, size = 20)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Label(Raids.stepName(s).get(ar), color = Sys.text, size = 15, w = FontWeight.Bold)
                            Label(Raids.stepAmount(s, ar) + (if (s.sets > 1) tr(" • ${s.sets} مجموعات", " • ${s.sets} sets") else ""), size = 13)
                        }
                    }
                }
                Label(tr("إحماء 5 دقائق أولًا. توقّف قبل الفشل الفني. الراحة بين المجموعات حرّة.", "5-min warm-up first. Stop before technical failure. Rest between sets freely."), size = 12, align = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                SysButton(tr("إنهاء الغارة", "Finish raid"), Modifier.fillMaxWidth(), accent = Crimson) { repo.apply { s -> GameEngine.finishRaid(s, today, repo.now()) } }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SysButton(tr("انسحاب (بلا عقاب)", "Leave (no penalty)"), Modifier.weight(1f), small = true, accent = Sys.dim) { repo.apply { s -> GameEngine.finishRaid(s, today, repo.now()) } }
                    SysButton(tr("ألم — توقّف الآن", "Pain — stop now"), Modifier.weight(1f), small = true, accent = Sys.red) { repo.apply { s -> GameEngine.finishRaid(s, today, repo.now(), pain = true) } }
                }
            }
        }
        val past = st.raids.filter { it.endMs != null }.takeLast(10).reversed()
        if (past.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("سجل الغارات", "Raid log"))
            Label(tr("غارات مُصفّاة: ", "Raids cleared: ") + Raids.clears(st), color = Sys.gold, size = 13)
            past.forEach { x -> Label("${java.time.LocalDate.ofEpochDay(x.day)} • ${Raids.rankLabel(x.rank)} • " + (if (x.cleared) tr("تمت التصفية +${x.xp} XP", "Cleared +${x.xp} XP") else tr("لم تُصفَّ (بلا عقاب)", "Not cleared (no penalty)")), color = if (x.cleared) Sys.green else Sys.dim, size = 12) }
        }
    }
}
