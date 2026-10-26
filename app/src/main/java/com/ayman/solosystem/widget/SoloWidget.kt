package com.ayman.solosystem.widget

import android.content.Context
import android.content.Intent
import android.graphics.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.glance.*
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.*
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.ayman.solosystem.MainActivity
import com.ayman.solosystem.R
import com.ayman.solosystem.SoloApp
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.AppState
import com.ayman.solosystem.domain.model.Quest
import com.ayman.solosystem.domain.model.QuestStatus
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.quest.GoalPlan
import kotlinx.coroutines.*

/**
 * Home-screen widget (Glance): level ring + today's GOAL rows with a one-tap "+N" log (same engine path as the app,
 * so XP / completion / secret checks are identical). Tapping elsewhere opens the app.
 */
class SoloWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = (context.applicationContext as SoloApp).repo
        withContext(Dispatchers.IO) { repo.awaitLoaded() }
        provideContent {
            val s by repo.state.collectAsState()
            Content(context, s, repo.today())
        }
    }

    @Composable
    private fun Content(ctx: Context, s: AppState, today: Long) {
        val ar = s.settings.arabic
        val monarch = s.monarchAwakened && !s.settings.classicTheme
        val accent = if (monarch) 0xFFE07BFF.toInt() else 0xFF4FD8FF.toInt()
        val bg = if (monarch) 0xF0120822.toInt() else 0xF00A1322.toInt()
        val goals = s.quests.filter { it.day == today && GoalPlan.isGoal(it) && it.status in setOf(QuestStatus.PENDING, QuestStatus.COMPLETED) }
            .sortedBy { GoalPlan.row(it.templateId)?.ordinal ?: 9 }
        val lv = Ledger.level(s)
        val done = goals.count { it.progress >= it.target }
        val ring = ringBitmap(ctx, lv.level, lv.fraction, if (goals.isEmpty()) 0f else goals.sumOf { minOf(1.0, it.progress.toDouble() / it.target) }.toFloat() / goals.size, accent)
        val open = actionStartActivity(Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        Row(GlanceModifier.fillMaxSize().background(ColorProvider(Color(bg))).cornerRadius(18.dp).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.width(96.dp).clickable(open), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(ImageProvider(ring), if (ar) "المستوى ${lv.level}" else "Level ${lv.level}", GlanceModifier.size(84.dp))
                Text(if (goals.isEmpty()) "—" else "$done/${goals.size}", style = TextStyle(color = ColorProvider(Color(accent)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                Text("[SYSTEM] " + (if (ar) "المهمة اليومية" else "DAILY QUEST"), GlanceModifier.clickable(open),
                    style = TextStyle(color = ColorProvider(Color(0xFFEFF6FF)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                Spacer(GlanceModifier.height(4.dp))
                if (goals.isEmpty()) Text(if (ar) "افتح التطبيق لاستلام مهمة اليوم." else "Open the app to receive today's quest.", GlanceModifier.clickable(open),
                    style = TextStyle(color = ColorProvider(Color(0xFF8FA3BF)), fontSize = 12.sp))
                goals.forEach { q -> GoalRow(q, ar, accent) }
            }
        }
    }

    @Composable
    private fun GoalRow(q: Quest, ar: Boolean, accent: Int) {
        val v = GoalPlan.variantOf(q)
        val ok = q.progress >= q.target
        val name = v?.let { if (ar) it.ar else it.en } ?: (if (ar) q.title.ar else q.title.en)
        val unit = v?.unit?.let { if (ar) it.ar else it.en } ?: ""
        val prog = v?.let { "${GoalPlan.fmtV(it, q.progress)} / ${GoalPlan.fmtV(it, q.target)}" } ?: "${q.progress} / ${q.target}"
        val step = stepFor(q)
        Row(GlanceModifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text((if (ok) "✔ " else "") + name, GlanceModifier.defaultWeight(), maxLines = 1,
                style = TextStyle(color = ColorProvider(Color(if (ok) 0xFF39F28A else 0xFFEFF6FF)), fontSize = 12.sp))
            Text("[$prog${if (unit.isEmpty()) "" else " $unit"}]", style = TextStyle(color = ColorProvider(Color(0xFF8FA3BF)), fontSize = 11.sp))
            Spacer(GlanceModifier.width(6.dp))
            Box(GlanceModifier.width(52.dp).height(26.dp).background(ColorProvider(Color(accent).copy(alpha = 0.22f))).cornerRadius(6.dp)
                .clickable(actionRunCallback<LogGoalAction>(actionParametersOf(LogGoalAction.ID to q.id, LogGoalAction.AMOUNT to step))),
                contentAlignment = Alignment.Center) {
                Text("+${stepLabel(q, step)}", style = TextStyle(color = ColorProvider(Color(accent)), fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            }
        }
    }

    companion object {
        fun stepFor(q: Quest): Int = when (GoalPlan.variantOf(q)?.unit) { GoalPlan.GUnit.M -> 500; GoalPlan.GUnit.SEC -> 30; GoalPlan.GUnit.FLOORS -> 1; else -> 10 }
        fun stepLabel(q: Quest, step: Int) = if (GoalPlan.variantOf(q)?.unit == GoalPlan.GUnit.M) GoalPlan.fmtKm(step) else "$step"

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        private var pending: Job? = null
        /** Debounced refresh from the repository's onChanged hook (no-op when no widget is placed). */
        fun requestUpdate(ctx: Context) {
            pending?.cancel()
            pending = scope.launch { delay(1200); runCatching { SoloWidget().updateAll(ctx.applicationContext) } }
        }

        fun ringBitmap(ctx: Context, level: Int, xpFrac: Float, dayFrac: Float, accent: Int): Bitmap {
            val n = 240; val b = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888); val c = Canvas(b)
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
            val outer = RectF(14f, 14f, n - 14f, n - 14f); val inner = RectF(40f, 40f, n - 40f, n - 40f)
            p.strokeWidth = 14f; p.color = 0x33FFFFFF; c.drawArc(outer, 0f, 360f, false, p)
            p.color = accent; p.setShadowLayer(10f, 0f, 0f, accent); c.drawArc(outer, -90f, 360f * dayFrac.coerceIn(0f, 1f), false, p)
            p.clearShadowLayer(); p.strokeWidth = 7f; p.color = 0x22FFFFFF; c.drawArc(inner, 0f, 360f, false, p)
            p.color = 0xFF39F28A.toInt(); c.drawArc(inner, -90f, 360f * xpFrac.coerceIn(0f, 1f), false, p)
            val tf = runCatching { ResourcesCompat.getFont(ctx, R.font.rajdhani_bold) }.getOrNull() ?: Typeface.DEFAULT_BOLD
            val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFEFF6FF.toInt(); typeface = tf; textAlign = Paint.Align.CENTER }
            t.textSize = 30f; t.color = accent; c.drawText("LV", n / 2f, n / 2f - 22f, t)
            t.textSize = 64f; t.color = 0xFFEFF6FF.toInt(); t.setShadowLayer(12f, 0f, 0f, accent); c.drawText("$level", n / 2f, n / 2f + 34f, t)
            return b
        }
    }
}

class LogGoalAction : ActionCallback {
    companion object { val ID = ActionParameters.Key<String>("id"); val AMOUNT = ActionParameters.Key<Int>("amount") }
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[ID] ?: return; val amount = parameters[AMOUNT] ?: return
        val repo = (context.applicationContext as SoloApp).repo
        withContext(Dispatchers.IO) {
            repo.awaitLoaded()
            repo.apply { s -> GameEngine.logGoal(s, id, amount, repo.today(), repo.now(), dayStartMs = repo.dayStartMs()) }
        }
        runCatching { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.QUEST_TICK) }
        SoloWidget().update(context, glanceId)
    }
}

class SoloWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SoloWidget() }
