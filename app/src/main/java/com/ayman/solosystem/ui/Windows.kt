package com.ayman.solosystem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.rewards.EmblemShape
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.training.Tiers
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun WarningText(modifier: Modifier = Modifier, daily: Boolean = true) {
    val ar = LocalArabic.current
    val t = buildAnnotatedString {
        if (ar) {
            append(if (daily) "تحذير: الفشل في إكمال المهمة اليومية سيؤدي إلى " else "تحذير: الفشل في إكمال مهمة العقاب سيؤدي إلى ")
            withStyle(SpanStyle(color = Sys.red, fontWeight = FontWeight.Bold)) { append("عقاب") }
            append(" مناسب.")
        } else {
            append(if (daily) "WARNING: Failure to complete the daily quest will result in an appropriate " else "WARNING: Failure to complete the penalty quest will result in an appropriate ")
            withStyle(SpanStyle(color = Sys.red, fontWeight = FontWeight.Bold)) { append("penalty") }
            append(".")
        }
    }
    BasicText(t, modifier.fillMaxWidth(), style = body(14.sp, FontWeight.Medium).copy(textAlign = TextAlign.Center, lineHeight = 22.sp))
}

/** Host-side control so every dismiss runs the CRT shut-off before the window is removed. */
class WindowCtl(val closing: State<Boolean>, val onClosed: () -> Unit)
val LocalWindowCtl = staticCompositionLocalOf<WindowCtl?> { null }

/** Modal NOTIFICATION window. Opens dot → line → panel (with a slight glitch); closes like a CRT shut-off (~180 ms). */
@Composable
fun NotificationWindow(title: String, accent: Color? = null, onDismiss: (() -> Unit)?, contentKey: Any? = null, header: (@Composable () -> Unit)? = null, sound: com.ayman.solosystem.notify.Sfx.S? = com.ayman.solosystem.notify.Sfx.S.WIN_OPEN, content: @Composable ColumnScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val ctl = LocalWindowCtl.current
    val p = remember { Animatable(if (reduced) 1f else 0f) }
    val flick = remember { Animatable(1f) }
    // open: thin bright centre line → expands vertically (~0.15 s) → fill → border/text glow flash + 80–100 % micro-flicker
    LaunchedEffect(Unit) { sound?.let { com.ayman.solosystem.notify.Sfx.play(it) }; if (!reduced) { p.animateTo(1f, tween(230, easing = LinearOutSlowInEasing)); flick.snapTo(0f); flick.animateTo(1f, tween(120, easing = LinearEasing)) } }
    val closing = ctl?.closing?.value == true
    LaunchedEffect(closing) {
        if (closing) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.WIN_CLOSE); if (!reduced) p.animateTo(0f, tween(180, easing = FastOutLinearInEasing)); ctl?.onClosed?.invoke() }
    }
    BackHandler(onDismiss != null && !closing) { onDismiss?.invoke() }
    Box(Modifier.fillMaxSize()
        .drawBehind { drawRect(WindowDim.copy(alpha = 0.72f * p.value.coerceAtLeast(0.15f))) }
        .clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Box(Modifier.widthIn(max = 460.dp).padding(18.dp)
            .drawBehind {
                // soft cyan (#0EA5E9) radial glow behind the window, ~20 % beyond its edges
                val g = p.value
                if (g > 0.05f) {
                    val c = center; val rx = size.width * 0.72f; val ry = size.height * 0.72f
                    scale(1f, ry / rx, c) { drawCircle(Brush.radialGradient(listOf(WindowGlow.copy(alpha = 0.28f * g), WindowGlow.copy(alpha = 0.12f * g), Color.Transparent), c, rx), rx, c) }
                }
            }) {
            Column(Modifier.graphicsLayer {
                val (sx, sy) = crtScale(p.value)
                scaleX = sx; scaleY = sy
                val f = flick.value
                alpha = when { sy < 0.05f -> 0.5f; f < 1f -> 0.8f + 0.2f * (((f * 1000f).toInt() / 23) % 3) / 2f; else -> 1f }
                translationX = if (p.value in 0.35f..0.9f && !closing) ((p.value * 97f).toInt() % 3 - 1) * 3.dp.toPx() else 0f
            }.drawWithContent {
                drawContent()
                val f = flick.value
                if (f < 1f) drawRect(WindowGlow.copy(alpha = 0.22f * (1f - f)), blendMode = BlendMode.Plus)
            }) {
                SysPanel(accent = accent, padding = 18.dp, contentKey = contentKey) {
                    if (header != null) header() else TitleBox(title, accent = accent ?: if (SystemTheme.monarch) SystemTheme.HeaderCyanWhite else LocalPalette.current.secondary)
                    Spacer(Modifier.height(14.dp))
                    Column(Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, content = content)
                }
            }
            CrtLine(p.value, Modifier.matchParentSize())
        }
    }
}

val SecretViolet = Color(0xFFB15CFF)
val SecretPink = Color(0xFFFF3DCB)

/** CRT / glitch overlay: scanlines, a rolling bright band and short RGB slice glitches. Time is read only in draw (no recomposition). */
@Composable
fun GlitchBlock(intense: Boolean = true, content: @Composable () -> Unit) {
    val reduced = LocalReducedMotion.current
    val t: State<Float> = if (reduced) remember { mutableFloatStateOf(0.5f) } else rememberInfiniteTransition(label = "crt").animateFloat(0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "t")
    Box(Modifier.fillMaxWidth().drawWithContent {
        drawContent()
        val time = t.value
        val step = 3.dp.toPx()
        var y = 0f
        while (y < size.height) { drawRect(Color.Black.copy(alpha = 0.16f), Offset(0f, y), androidx.compose.ui.geometry.Size(size.width, 1f)); y += step }
        val band = size.height * ((time * 1.3f) % 1.3f) - size.height * 0.15f
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, SecretViolet.copy(alpha = 0.16f), Color.Transparent), band, band + size.height * 0.15f),
            Offset(0f, band), androidx.compose.ui.geometry.Size(size.width, size.height * 0.15f), blendMode = BlendMode.Plus)
        // glitch slices in short bursts
        val burst = if (intense) time < 0.18f || time in 0.55f..0.62f else time in 0.55f..0.6f
        if (burst && !reduced) {
            val seed = (time * 60).toInt()
            for (i in 0 until 4) {
                val h = (((seed * 31 + i * 17) % 7) + 2) * 2.dp.toPx()
                val yy = (((seed * 53 + i * 97) % 100) / 100f) * size.height
                val dx = (((seed + i) % 5) - 2) * 6.dp.toPx()
                drawRect((if (i % 2 == 0) SecretPink else Color(0xFF3DF2FF)).copy(alpha = 0.22f), Offset(dx, yy), androidx.compose.ui.geometry.Size(size.width, h), blendMode = BlendMode.Plus)
            }
        }
    }) { content() }
}

/** Text with jittering red/cyan chromatic ghosts (offset applied in graphicsLayer → no recomposition). */
@Composable
fun GlitchText(text: String, size: Int, tech: Boolean = false) {
    val reduced = LocalReducedMotion.current
    val t: State<Float> = if (reduced) remember { mutableFloatStateOf(0f) } else rememberInfiniteTransition(label = "gt").animateFloat(0f, 1f, infiniteRepeatable(tween(1700, easing = LinearEasing)), label = "g")
    val style = if (tech) tech(size.sp) else body(size.sp, FontWeight.Bold)
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BasicText(text, style = style.copy(color = SecretPink.copy(alpha = 0.7f), textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().graphicsLayer {
            val v = t.value; translationX = (if (v < 0.12f || v in 0.5f..0.56f) 4f else 1.2f) * density * (if (((v * 40).toInt() % 2) == 0) 1 else -1) })
        BasicText(text, style = style.copy(color = Color(0xFF3DF2FF).copy(alpha = 0.7f), textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth().graphicsLayer {
            val v = t.value; translationX = -(if (v < 0.12f || v in 0.5f..0.56f) 4f else 1.2f) * density * (if (((v * 40).toInt() % 2) == 0) 1 else -1) })
        GlowText(text, style.copy(color = Color.White), Modifier.fillMaxWidth(), align = TextAlign.Center, glow = SecretViolet)
    }
}

private val WindowDim get() = SystemTheme.c(0xFF000510, 0xFF07000F)
private val WindowGlow get() = SystemTheme.c(0xFF0EA5E9, 0xFFA855F7)

@Composable private fun Center(text: String, size: Int = 16, color: Color = Sys.text, w: FontWeight = FontWeight.Medium) =
    GlowText(text, body(size.sp, w, color), Modifier.fillMaxWidth().padding(vertical = 3.dp), align = TextAlign.Center)

@Composable private fun Ok(text: String, onClick: () -> Unit) { Spacer(Modifier.height(12.dp)); SysButton(text, Modifier.fillMaxWidth(), onClick = onClick) }

@Composable
fun GreenCheck(size: Int = 72) {
    val reduced = LocalReducedMotion.current
    val a = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) { if (!reduced) a.animateTo(1f, tween(600)) }
    Canvas(Modifier.size(size.dp)) {
        drawCircle(Sys.green.copy(alpha = 0.15f), this.size.minDimension / 2)
        val p = Path().apply { moveTo(this@Canvas.size.width * 0.22f, this@Canvas.size.height * 0.52f); lineTo(this@Canvas.size.width * 0.42f, this@Canvas.size.height * 0.72f); lineTo(this@Canvas.size.width * (0.42f + 0.38f * a.value), this@Canvas.size.height * (0.72f - 0.44f * a.value)) }
        glowPath(p, Sys.green, 5.dp.toPx())
    }
}

/** Ray burst used by rank-up and MONARCH ceremonies. */
@Composable
fun RayBurst(color: Color, content: @Composable BoxScope.() -> Unit) {
    val reduced = LocalReducedMotion.current
    val rotState: State<Float> = if (reduced) remember { mutableFloatStateOf(0f) } else rememberInfiniteTransition(label = "rays").animateFloat(0f, 360f, infiniteRepeatable(tween(24000, easing = LinearEasing)), label = "r")
    Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val rot = rotState.value
            for (i in 0 until 16) {
                val a = Math.toRadians((rot + i * 22.5).toDouble())
                val r1 = size.minDimension * 0.25f; val r2 = size.minDimension * (if (i % 2 == 0) 0.5f else 0.4f)
                drawLine(color.copy(alpha = 0.35f), center + Offset((cos(a) * r1).toFloat(), (sin(a) * r1).toFloat()), center + Offset((cos(a) * r2).toFloat(), (sin(a) * r2).toFloat()), 3.dp.toPx())
            }
            drawCircle(color.copy(alpha = 0.12f), size.minDimension * 0.3f)
        }
        content()
    }
}

@Composable
fun EffectWindow(e: UiEffect, repo: Repository, nav: (String) -> Unit, dismiss: () -> Unit) {
    val ar = LocalArabic.current
    val st by repo.state.collectAsState()
    val notif = tr("إشعار", "NOTIFICATION")
    when (e) {
        is UiEffect.LevelUp -> if (e.isMax) {
            NotificationWindow(notif, Sys.gold, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.PING) {
                LaunchedEffect(Unit) { kotlinx.coroutines.delay(700); com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.REWARD_LEGENDARY) }
                RayBurst(Sys.gold) { EmblemIcon(EmblemShape.CROWN, Sys.gold, size = 84.dp) }
                Center("MONARCH", 34, Sys.gold, FontWeight.Bold)
                Center(tr("لقد بلغت المستوى الأقصى 300.", "You have reached the maximum level 300."), 17)
                Center(tr("اللقب: ${Leveling.titleFor(300).ar}", "Title: ${Leveling.titleFor(300).en}"), 15, Sys.gold)
                Label(tr("سنوات من الالتزام الحقيقي. المهام اليومية والسجل يستمران.", "Years of real consistency. Daily quests and logging continue."), align = TextAlign.Center)
                Ok(tr("حسنًا", "OK"), dismiss)
            }
        } else LevelUpWindow(e, dismiss)
        is UiEffect.QuestReward -> NotificationWindow(notif, null, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.QUEST_COMPLETE) {
            Center(tr("أكملت المهمة: ${e.quest.title.ar}", "Quest complete: ${e.quest.title.en}"), 16, w = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().thinBox().padding(12.dp)) {
                GlowText(tr("مكافآت المهمة", "Quest rewards"), body(15.sp, FontWeight.Bold))
                Spacer(Modifier.height(6.dp))
                Label(tr("• +${e.xp} نقطة خبرة", "• +${e.xp} XP"), color = Sys.text, size = 15)
                if (e.bonusXp > 0) Label(tr("• +${e.bonusXp} خبرة إضافية (مكافآت المهارات/اللقب)", "• +${e.bonusXp} bonus XP (skills/title)"), color = Sys.green, size = 14)
                if (e.gold > 0) Label(tr("• +${e.gold} ذهب", "• +${e.gold} gold"), color = Sys.gold, size = 15)
            }
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        is UiEffect.RewardReveal -> {
            var open by remember { mutableStateOf(false) }
            NotificationWindow(notif, null, dismiss, contentKey = open) {
                if (!open) {
                    Center(tr("لقد حصلت على مكافأة.", "You have received a reward."), 17, w = FontWeight.Bold)
                    Center(tr("هل تريد التحقق من مكافأتك؟", "Check your reward?"), 15)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SysButton(tr("نعم", "Yes"), Modifier.weight(1f), sfx = com.ayman.solosystem.notify.Sfx.S.YES) { open = true; playRarity(e.grants.mapNotNull { RewardCatalog.get(it.rewardId)?.rarity }.maxOrNull()) }
                        SysButton(tr("لا", "No"), Modifier.weight(1f), accent = Sys.dim, sfx = com.ayman.solosystem.notify.Sfx.S.NO) { dismiss() }
                    }
                    Label(tr("المكافآت محفوظة في المخزون في كل الأحوال.", "Rewards are saved in your inventory either way."), size = 12, align = TextAlign.Center)
                } else {
                    GlowText(tr("مكافآت المهمة", "Quest rewards"), body(16.sp, FontWeight.Bold))
                    e.grants.forEach { g ->
                        RewardCatalog.get(g.rewardId)?.let { d -> RewardCard(d, extra = if (g.goldInstead > 0) tr("مكرر ← +${g.goldInstead} ذهب", "Duplicate → +${g.goldInstead} gold") else null) }
                        Label(tr("المصدر: ", "Source: ") + g.source.get(ar), size = 12)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SysButton(tr("المخزون", "Inventory"), Modifier.weight(1f)) { dismiss(); nav("inventory") }
                        SysButton(tr("حسنًا", "OK"), Modifier.weight(1f)) { dismiss() }
                    }
                }
            }
        }
        is UiEffect.LootOpened -> if (e.box.id in setOf("box_blessed", "box_cursed", "box_secret") || e.grant.rewardId == "key_raid") SecretLootWindow(e, repo, nav, dismiss) else {
            val d = RewardCatalog.get(e.grant.rewardId)
            val reduced = LocalReducedMotion.current
            var revealed by remember { mutableStateOf(reduced) }
            LaunchedEffect(Unit) { if (!reduced) { kotlinx.coroutines.delay(900); revealed = true }; com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.CHEST_BREAK, 0.8f); playRarity(d?.rarity) }
            NotificationWindow(notif, d?.rarity?.c(), dismiss, contentKey = revealed, sound = if (reduced) null else com.ayman.solosystem.notify.Sfx.S.CHEST_CHARGE) {
                if (!revealed || d == null) {
                    RayBurst(e.box.rarity.c()) { SysIcon(Ico.BOX, e.box.rarity.c(), size = 72.dp) }
                    Center(tr("جارٍ فتح ${e.box.name.ar}...", "Opening ${e.box.name.en}..."))
                } else {
                    GlowText((if (ar) d.rarity.ar else d.rarity.en).uppercase(), tech(26.sp, d.rarity.c()), glow = d.rarity.c())
                    RewardCard(d, extra = if (e.grant.goldInstead > 0) tr("مكرر ← +${e.grant.goldInstead} ذهب", "Duplicate → +${e.grant.goldInstead} gold") else null)
                    Ok(tr("حسنًا", "OK"), dismiss)
                }
            }
        }
        is UiEffect.PenaltyIssued -> NotificationWindow(notif, Sys.red, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.PENALTY) {
            val q = e.quest
            val physical = q.templateId == "penalty"
            BasicText(buildAnnotatedString {
                append(if (ar) "[تم استلام مهمة " else "[A ")
                withStyle(SpanStyle(color = Sys.red, fontWeight = FontWeight.Bold)) { append(if (ar) "العقاب" else "PENALTY") }
                append(if (ar) "]" else " quest has been issued]")
            }, style = body(17.sp, FontWeight.Bold).copy(textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            if (physical) TierIndicator(q.penaltyTier)
            Center(q.title.get(ar), 15, w = FontWeight.Bold)
            q.items.forEach { Label("• " + penaltyLine(it, ar), color = Sys.text, size = 14) }
            Label(q.description.get(ar), size = 13)
            Label(tr("اليوم المحدد: ", "Scheduled: ") + java.time.LocalDate.ofEpochDay(q.day), size = 13, color = Sys.text)
            Spacer(Modifier.height(6.dp))
            WarningText(daily = false)
            Ok(tr("فهمت", "Understood")) { dismiss(); nav("quests") }
        }
        is UiEffect.SecretQuest -> SecretQuestInfoWindow(e, st, dismiss)
        is UiEffect.SecretReward -> SecretRewardWindow(e, repo, dismiss)
        is UiEffect.RaidGate -> RaidGateWindow(e, repo, nav, dismiss)
        is UiEffect.RaidResult -> RaidResultWindow(e, nav, dismiss)
        is UiEffect.AssessPropose -> AssessProposeWindow(e, repo, nav, dismiss)
        is UiEffect.PenaltyCleared -> NotificationWindow(notif, Sys.green, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.QUEST_COMPLETE) {
            GreenCheck()
            Center(tr("أكملت مهمة العقاب: نجاة", "Penalty quest complete: Survival"), 18, w = FontWeight.Bold)
            Label(tr("تمت استعادة درع السلسلة للأيام المرتبطة.", "Your streak shield has been restored for the linked days."), align = TextAlign.Center, color = Sys.green)
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        is UiEffect.MissedReview -> NotificationWindow(notif, Sys.amber, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.WARNING) {
            Center(tr("مهام لم تكتمل", "Unfinished quests"), 17, w = FontWeight.Bold)
            Label(tr("اختر سببًا لكل مهمة. الأعذار الحقيقية (مرض، إصابة، طارئ) لا تُحتسب ولا تصعّد العقاب. لا يُحذف أي XP مكتسب.", "Pick a reason for each. Real excuses (illness, injury, emergency) don't count and never escalate. Earned XP is never removed."), size = 13)
            e.quests.forEach { q0 ->
                val q = st.quests.firstOrNull { it.id == q0.id } ?: q0
                Spacer(Modifier.height(8.dp))
                Column(Modifier.fillMaxWidth().thinBox().padding(8.dp)) {
                    Label(q.title.get(ar), color = Sys.text, size = 14, w = FontWeight.Bold)
                    if (q.reason != null) Label(tr("السبب: ", "Reason: ") + (MissReason.entries.firstOrNull { it.name == q.reason }?.let { if (ar) it.ar else it.en } ?: q.reason!!), color = if (q.status == QuestStatus.EXCUSED) Sys.green else Sys.amber, size = 13)
                    else FlowRowCompat { MissReason.entries.forEach { r -> Chip(if (ar) r.ar else r.en, false) { repo.apply { s -> GameEngine.setReason(s, q.id, r, repo.today(), repo.now()) } } } }
                }
            }
            Ok(tr("تم", "Done"), dismiss)
        }
        is UiEffect.Emergency -> NotificationWindow(notif, Sys.amber, null, sound = com.ayman.solosystem.notify.Sfx.S.WARNING) {
            Center(tr("[مهمة طارئة]", "[EMERGENCY QUEST]"), 18, Sys.amber, FontWeight.Bold)
            Center(e.quest.title.get(ar), 16, w = FontWeight.Bold)
            Label(e.quest.objective.get(ar), color = Sys.text, size = 14, align = TextAlign.Center)
            Label(tr("اختيارية بالكامل — الرفض لا يسبب أي عقاب.", "Fully optional — declining causes no penalty."), size = 12, align = TextAlign.Center, color = Sys.green)
            Label("+${e.quest.xp} XP", color = LocalPalette.current.secondary, size = 15, align = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SysButton(tr("قبول", "Accept"), Modifier.weight(1f), small = true, sfx = com.ayman.solosystem.notify.Sfx.S.YES) { repo.apply { s -> GameEngine.emergencyRespond(s, e.quest.id, "accept", repo.today(), repo.now()) }; dismiss() }
                SysButton(tr("لاحقًا", "Later"), Modifier.weight(1f), small = true, accent = Sys.amber) { repo.apply { s -> GameEngine.emergencyRespond(s, e.quest.id, "postpone", repo.today(), repo.now()) }; dismiss() }
                SysButton(tr("رفض", "Decline"), Modifier.weight(1f), small = true, accent = Sys.dim, sfx = com.ayman.solosystem.notify.Sfx.S.NO) { repo.apply { s -> GameEngine.emergencyRespond(s, e.quest.id, "decline", repo.today(), repo.now()) }; dismiss() }
            }
        }
        is UiEffect.TierUnlocked -> NotificationWindow(notif, null, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.REWARD_RARE) {
            val t = Tiers.all[e.tier]
            RankBadge(if (ar) t.ar else t.label, size = 64.dp)
            Center(tr("تم فتح مستوى التدريب ${t.ar}", "Training tier ${t.label} unlocked"), 17, w = FontWeight.Bold)
            Label(tr("المستوى وحده لا يرفع الأحمال. اجتز اختبار القدرات أو حقق المعايير في تمارينك لتفعيله.", "Level alone never raises loads. Pass the assessment or hit the benchmarks in your sessions to activate it."), align = TextAlign.Center)
            Ok(tr("عرض الاختبار", "View assessment")) { dismiss(); nav("tiers") }
        }
        is UiEffect.TierActivated -> NotificationWindow(notif, Sys.green, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.REWARD_RARE) {
            val t = Tiers.all[e.tier]
            RayBurst(Sys.green) { RankBadge(if (ar) t.ar else t.label, size = 70.dp, color = Sys.green) }
            Center(tr("تم تفعيل مستوى التدريب ${t.ar}", "Training tier ${t.label} activated"), 17, w = FontWeight.Bold)
            Label(tr("خطتك أعيد توليدها بتمارين وحجم يناسب أداءك المسجّل.", "Your plan was regenerated with exercises and volume matching your logged performance."), align = TextAlign.Center)
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        is UiEffect.Safety -> NotificationWindow(tr("تنبيه سلامة", "SAFETY"), Sys.red, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.WARNING) {
            Label(e.text.get(ar), color = Sys.text, size = 15, align = TextAlign.Center)
            Ok(tr("فهمت", "Understood"), dismiss)
        }
        is UiEffect.SkillUp -> NotificationWindow(notif, null, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.REWARD_RARE) {
            Center(if (e.level == 1) tr("لقد اكتسبت مهارة جديدة!", "You have acquired a new skill!") else tr("ارتفع مستوى المهارة!", "Skill level up!"), 17, w = FontWeight.Bold)
            SkillCard(e.skill, e.level, 0)
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        is UiEffect.RankUp -> NotificationWindow(notif, Sys.gold, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.REWARD_LEGENDARY) {
            RayBurst(Sys.gold) { RankBadge(if (ar) e.rank.ar else e.rank.label, size = 80.dp, color = Sys.gold) }
            Center(tr("ترقية الرتبة!", "RANK UP!"), 24, Sys.gold, FontWeight.Bold)
            Center(tr("أصبحت الآن من الرتبة ${e.rank.ar}", "You are now rank ${e.rank.label}"), 16)
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        UiEffect.DailyComplete -> NotificationWindow(notif, Sys.green, dismiss, sound = com.ayman.solosystem.notify.Sfx.S.DAILY_COMPLETE) {
            GreenCheck()
            Center(tr("أكملت المهمة اليومية", "Daily quest complete"), 18, w = FontWeight.Bold)
            Label(tr("توقّف العدّاد. أحسنت — استرح جيدًا.", "The countdown stopped. Well done — recover well."), align = TextAlign.Center, color = Sys.green)
            Ok(tr("حسنًا", "OK"), dismiss)
        }
        UiEffect.SystemAwakening -> SystemAwakeningWindow(dismiss)
        is UiEffect.Toast -> {}
    }
}

@Composable
fun TierIndicator(tier: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Label(tr("مستوى العقاب", "Penalty tier"), color = Sys.text, size = 13)
        Spacer(Modifier.width(8.dp))
        for (i in 1..5) Box(Modifier.padding(2.dp).size(16.dp, 10.dp).background(if (i <= tier) Sys.red else Sys.panelLine.copy(alpha = 0.25f)))
        Spacer(Modifier.width(8.dp))
        BasicText("$tier/5", style = tech(14.sp, Sys.red))
    }
}

/** Minimal flow layout (wrapping row) without experimental APIs. */
@Composable
fun FlowRowCompat(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    androidx.compose.ui.layout.Layout(content, modifier) { ms, c ->
        val ps = ms.map { it.measure(c.copy(minWidth = 0)) }
        var x = 0; var y = 0; var rowH = 0
        val pos = ps.map { p -> if (x + p.width > c.maxWidth && x > 0) { x = 0; y += rowH; rowH = 0 }; val r = x to y; x += p.width; rowH = maxOf(rowH, p.height); r }
        layout(c.maxWidth, y + rowH) { ps.forEachIndexed { i, p -> p.placeRelative(pos[i].first, pos[i].second) } }
    }
}
