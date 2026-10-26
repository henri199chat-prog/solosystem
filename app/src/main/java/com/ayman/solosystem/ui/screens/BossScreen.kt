package com.ayman.solosystem.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.boss.Boss
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.Crimson
import com.ayman.solosystem.ui.Nav
import com.ayman.solosystem.ui.SysWhite
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun BossScreen(repo: Repository, nav: Nav) {
    val st by repo.state.collectAsState()
    val ar = LocalArabic.current
    val today = repo.today()
    LaunchedEffect(Unit) { repo.refreshDayAsync() }
    val b = Boss.current(st, today)
    Screen(tr("زعيم الأسبوع", "WEEKLY BOSS"), nav) {
        if (b == null) { EmptyState(tr("لا زعيم الآن", "No boss right now"), tr("يظهر زعيم جديد كل يوم اثنين.", "A new boss appears every Monday.")); return@Screen }
        val d = Boss.def(b.bossId)
        val hpTarget = (b.maxHp - b.damage).coerceAtLeast(0)
        val hp = remember(b.week) { Animatable((b.maxHp - b.seenDamage).coerceAtLeast(0).toFloat()) }
        val shake = remember { Animatable(0f) }
        val flash = remember { Animatable(0f) }
        var hitText by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(b.week, b.damage) {
            val delta = b.damage - b.seenDamage
            if (delta > 0 && hp.value > hpTarget) {
                delay(450)
                // 3 strikes, each takes a third of the damage
                val per = (hp.value - hpTarget) / 3f
                repeat(3) { i ->
                    Sfx.play(if (i == 2) Sfx.S.CHEST_BREAK else Sfx.S.QUEST_TICK)
                    hitText = "-${(delta / 3 + if (i == 2) delta % 3 else 0)}"
                    flash.snapTo(1f)
                    kotlinx.coroutines.coroutineScope {
                        launch { shake.snapTo(1f); shake.animateTo(0f, tween(320)) }
                        launch { flash.animateTo(0f, tween(260)) }
                        hp.animateTo(if (i == 2) hpTarget.toFloat() else hp.value - per, tween(300, easing = FastOutSlowInEasing))
                    }
                    delay(140)
                }
                delay(500); hitText = null
                if (b.defeatedMs != null) Sfx.play(Sfx.S.REWARD_RARE)
            } else hp.snapTo(hpTarget.toFloat())
            repo.set { Boss.markSeen(it, b.week) }
        }
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
        val left = ((Boss.endMs(b.week) - now) / 1000).coerceAtLeast(0)
        val defeated = b.defeatedMs != null
        SysPanel(Modifier.fillMaxWidth(), accent = if (defeated) Sys.gold else Crimson, pulse = !defeated) {
            TitleBox(tr("زعيم الأسبوع", "WEEKLY BOSS"), accent = if (defeated) Sys.gold else Crimson)
            Spacer(Modifier.height(6.dp))
            GlowText(d.name.get(ar), body(24.sp, FontWeight.Bold), Modifier.fillMaxWidth(), glow = Crimson, align = TextAlign.Center)
            Label(d.epithet.get(ar) + " • " + tr("رتبة ", "Rank ") + listOf("E", "D", "C", "B", "A", "S", "N", "M")[b.rank.coerceIn(0, 7)], Modifier.fillMaxWidth(), size = 13, align = TextAlign.Center)
            Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                val t = rememberInfiniteTransition(label = "boss")
                val breathe by t.animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Reverse), label = "b")
                Canvas(Modifier.fillMaxSize()) {
                    val sx = sin(shake.value * 40f) * 16f * shake.value
                    translate(sx, 0f) { drawBoss(this, breathe, defeated) }
                    if (flash.value > 0f) drawRect(SysWhite.copy(alpha = 0.55f * flash.value))
                }
                hitText?.let { GlowText(it, tech(40.sp, SysWhite), Modifier.align(Alignment.TopEnd).padding(16.dp), glow = Crimson) }
                if (defeated) GlowText(tr("هُزم", "DEFEATED"), body(38.sp, FontWeight.Bold, Sys.gold), glow = Sys.gold)
            }
            // HP bar
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label("HP", color = Crimson, size = 14, w = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Canvas(Modifier.weight(1f).height(16.dp)) {
                    drawRect(Color(0x55300008))
                    val f = (hp.value / b.maxHp).coerceIn(0f, 1f)
                    drawRect(Brush.horizontalGradient(listOf(Color(0xFF7A0012), Crimson, Color(0xFFFF5A6E))), size = Size(size.width * f, size.height))
                    drawRect(Crimson, style = Stroke(2f))
                }
                Spacer(Modifier.width(8.dp))
                GlowText("${hp.value.toInt()}/${b.maxHp}", tech(15.sp))
            }
            Spacer(Modifier.height(6.dp))
            KeyValue(tr("الضرر هذا الأسبوع", "Damage this week"), "${b.damage}", Sys.gold)
            KeyValue(tr("الوقت المتبقي", "Time left"), "%dd %02d:%02d:%02d".format(java.util.Locale.US, left / 86400, left % 86400 / 3600, left % 3600 / 60, left % 60))
            Label(tr("كل نقطة XP تكسبها هذا الأسبوع (مهام، تدريب، غارات، بوابات) = ضرر. اهزمه قبل الأحد 23:59 لتحصل على صندوق نادر. إن نجا يهرب فقط — بلا عقاب.",
                "Every XP you earn this week (quests, training, raids, gates) = damage. Defeat it before Sunday 23:59 for a rare chest. If it survives it just leaves — no penalty."), size = 12)
            if (defeated) Label(tr("تمت إضافة صندوق نادر إلى مخزنك.", "A rare chest was added to your inventory."), Modifier.fillMaxWidth(), color = Sys.gold, size = 13, align = TextAlign.Center)
        }
        val past = st.bosses.filter { it.closed }.sortedByDescending { it.week }.take(8)
        val lastEscaped = past.firstOrNull()?.takeIf { it.defeatedMs == null && it.week == b.week - 7 }
        if (lastEscaped != null) SysPanel(Modifier.fillMaxWidth(), accent = Crimson, brackets = false) {
            Label(tr("الزعيم السابق ${Boss.def(lastEscaped.bossId).name.ar}: «${Boss.def(lastEscaped.bossId).taunt.ar}»", "Last boss ${Boss.def(lastEscaped.bossId).name.en}: “${Boss.def(lastEscaped.bossId).taunt.en}”"), color = Sys.text, size = 13)
        }
        if (past.isNotEmpty()) SysPanel(Modifier.fillMaxWidth(), brackets = false, flares = false) {
            SectionHeader(tr("سجل الزعماء", "Boss log"))
            past.forEach { p -> KeyValue(fmtDay(p.week, ar) + " • " + Boss.def(p.bossId).name.get(ar), if (p.defeatedMs != null) tr("هُزم", "Defeated") else tr("هرب", "Escaped"), if (p.defeatedMs != null) Sys.green else Sys.dim) }
        }
    }
}

/** Original boss art: horned armoured demon with a crown of spikes, cape and glowing eyes (pure Canvas paths). */
private fun drawBoss(ds: DrawScope, breathe: Float, defeated: Boolean): Unit = with(ds) {
    val w = size.width; val h = size.height; val cx = w / 2
    val glow = if (defeated) Color(0xFF555566) else Crimson
    drawCircle(Brush.radialGradient(listOf(glow.copy(alpha = 0.45f), Color.Transparent), Offset(cx, h * 0.45f), h * 0.6f), h * 0.6f, Offset(cx, h * 0.45f))
    val up = breathe * h * 0.015f
    // cape
    val cape = Path().apply { moveTo(cx - w * .08f, h * .3f); cubicTo(cx - w * .35f, h * .5f, cx - w * .3f, h * .9f, cx - w * .32f, h); lineTo(cx + w * .32f, h); cubicTo(cx + w * .3f, h * .9f, cx + w * .35f, h * .5f, cx + w * .08f, h * .3f); close() }
    drawPath(cape, Brush.verticalGradient(listOf(Color(0xFF2A0008), Color(0xFF080003))))
    translate(0f, -up) {
        // shoulders/armour
        val torso = Path().apply { moveTo(cx - w * .2f, h * .42f); lineTo(cx - w * .12f, h * .33f); lineTo(cx + w * .12f, h * .33f); lineTo(cx + w * .2f, h * .42f); lineTo(cx + w * .13f, h * .95f); lineTo(cx - w * .13f, h * .95f); close() }
        drawPath(torso, Brush.verticalGradient(listOf(Color(0xFF1B1F2E), Color(0xFF07080D))))
        drawPath(torso, glow.copy(alpha = 0.8f), style = Stroke(3f))
        listOf(-1f, 1f).forEach { s ->
            val pad = Path().apply { moveTo(cx + s * w * .12f, h * .33f); lineTo(cx + s * w * .27f, h * .36f); lineTo(cx + s * w * .22f, h * .48f); lineTo(cx + s * w * .17f, h * .42f); close() }
            drawPath(pad, Color(0xFF232838)); drawPath(pad, glow.copy(alpha = 0.7f), style = Stroke(2.5f))
            // horns
            val horn = Path().apply { moveTo(cx + s * w * .05f, h * .14f); quadraticTo(cx + s * w * .2f, h * .1f, cx + s * w * .22f, h * -.02f); quadraticTo(cx + s * w * .14f, h * .1f, cx + s * w * .07f, h * .2f); close() }
            drawPath(horn, Color(0xFFCFC6B8))
        }
        // head
        val head = Path().apply { moveTo(cx, h * .08f); lineTo(cx + w * .07f, h * .14f); lineTo(cx + w * .06f, h * .27f); lineTo(cx, h * .32f); lineTo(cx - w * .06f, h * .27f); lineTo(cx - w * .07f, h * .14f); close() }
        drawPath(head, Color(0xFF0D0F17)); drawPath(head, glow, style = Stroke(2.5f))
        // spikes crown
        for (i in -2..2) drawPath(Path().apply { moveTo(cx + i * w * .025f - w * .01f, h * .11f); lineTo(cx + i * w * .025f, h * (.04f - abs(i) * -.01f)); lineTo(cx + i * w * .025f + w * .01f, h * .11f); close() }, Color(0xFF8E8676))
        // eyes + core
        if (!defeated) listOf(-1f, 1f).forEach { s ->
            drawCircle(Color(0xFFFF3B4E).copy(alpha = 0.4f + 0.3f * breathe), w * .025f, Offset(cx + s * w * .028f, h * .19f))
            drawCircle(Color(0xFFFFD0D5), w * .008f, Offset(cx + s * w * .028f, h * .19f))
        }
        drawCircle(glow.copy(alpha = 0.6f + 0.4f * breathe), w * .02f, Offset(cx, h * .5f))
    }
}
