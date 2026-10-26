package com.ayman.solosystem.ui

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.welcome.Welcome
import com.ayman.solosystem.notify.Sfx
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** App-open signal from MainActivity: cold start, or a resume after ≥ [Welcome.RESUME_AFTER_MS] away. */
object WelcomeBus {
    val requests = MutableStateFlow(0)
    private var started = false
    private var lastStopMs = 0L
    /** Debug only: keep the window up for screenshots. */
    @Volatile var hold = false
    fun onStart(nowMs: Long) {
        if (!started || nowMs - lastStopMs >= Welcome.RESUME_AFTER_MS) requests.value = requests.value + 1
        started = true
    }
    fun onStop(nowMs: Long) { lastStopMs = nowMs }
    fun force() { requests.value = requests.value + 1 }
}

/** Welcome memory in its own small prefs file (never part of the game save). */
private object WelcomePrefs {
    fun load(ctx: Context): Welcome.Memory {
        val p = ctx.getSharedPreferences("solo_welcome", Context.MODE_PRIVATE)
        return Welcome.Memory(
            lastSeenMs = p.getLong("lastSeen", -1L).takeIf { it > 0 }, day = p.getLong("day", -1L), opensToday = p.getInt("opens", 0),
            recent = p.getString("recent", "").orEmpty().split(',').filter { it.isNotBlank() },
            lastLevel = p.getInt("lastLevel", -1).takeIf { it > 0 }, lastRank = p.getString("lastRank", null))
    }
    fun save(ctx: Context, m: Welcome.Memory) {
        ctx.getSharedPreferences("solo_welcome", Context.MODE_PRIVATE).edit()
            .putLong("lastSeen", m.lastSeenMs ?: -1L).putLong("day", m.day).putInt("opens", m.opensToday)
            .putString("recent", m.recent.joinToString(",")).putInt("lastLevel", m.lastLevel ?: -1).putString("lastRank", m.lastRank).apply()
    }
    /** Absence is measured from the last time the app was left. */
    fun touch(ctx: Context, nowMs: Long) { ctx.getSharedPreferences("solo_welcome", Context.MODE_PRIVATE).edit().putLong("lastSeen", nowMs).apply() }
}

fun welcomeLeft(ctx: Context, nowMs: Long) = runCatching { WelcomePrefs.touch(ctx, nowMs) }

/**
 * Non-blocking top-of-screen SYSTEM notice. Computed off the main thread after the first frames; auto-dismisses after ~3.6 s,
 * tap to close; touches outside the panel pass through to the app.
 */
@Composable
fun WelcomeHost(repo: Repository, windowOpen: State<Boolean>) {
    val ctx = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val req by WelcomeBus.requests.collectAsState()
    var shown by remember { mutableStateOf<Welcome.Shown?>(null) }
    LaunchedEffect(req) {
        if (req == 0) return@LaunchedEffect
        withFrameNanos { }; withFrameNanos { }
        delay(900)
        // wait for any modal System window (level-up, secret quest…) to close first, at most 6 s
        withTimeoutOrNull(6000) { snapshotFlow { windowOpen.value }.first { !it } }
        val st = repo.state.value
        if (!st.settings.welcome) { withContext(Dispatchers.IO) { WelcomePrefs.touch(ctx, repo.now()) }; return@LaunchedEffect }
        shown = withContext(Dispatchers.Default) {
            runCatching {
                val mem = withContext(Dispatchers.IO) { WelcomePrefs.load(ctx) }
                val now = repo.now(); val today = repo.today()
                val t = java.time.LocalTime.now()
                val c = Welcome.context(st, mem, today, now, t.hour * 60 + t.minute)
                val w = Welcome.pick(c, mem.recent)
                val lvl = Ledger.level(st).level
                withContext(Dispatchers.IO) { WelcomePrefs.save(ctx, Welcome.remember(mem, w, today, now, lvl, Leveling.rankFor(lvl).name)) }
                w
            }.getOrNull()
        }
    }
    val w = shown ?: return
    val ar = LocalArabic.current
    val reduced = LocalReducedMotion.current
    val p = remember(w) { Animatable(if (reduced) 1f else 0f) }
    var closing by remember(w) { mutableStateOf(false) }
    LaunchedEffect(w) {
        Sfx.play(Sfx.S.WIN_OPEN)
        if (!reduced) p.animateTo(1f, tween(230, easing = LinearOutSlowInEasing))
        delay(if (WelcomeBus.hold) 60_000 else 3600)
        closing = true
    }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        Sfx.play(Sfx.S.WIN_CLOSE)
        if (!reduced) p.animateTo(0f, tween(180, easing = FastOutLinearInEasing))
        shown = null
    }
    Box(Modifier.fillMaxWidth().padding(top = 28.dp, start = 14.dp, end = 14.dp), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.widthIn(max = 460.dp)) {
            Column(Modifier.graphicsLayer { val (sx, sy) = crtScale(p.value); scaleX = sx; scaleY = sy; alpha = if (sy < 0.05f) 0.5f else 1f }
                .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { closing = true }) {
                Box {
                // opaque glass behind the panel so the screen underneath never bleeds through the text
                Box(Modifier.matchParentSize().padding(horizontal = 6.dp, vertical = 10.dp).background(SystemTheme.c(0xF5030912, 0xF5090312)))
                SysPanel(padding = 14.dp, pulse = false) {
                    TitleBox(if (ar) "إشعار" else "NOTIFICATION", accent = if (SystemTheme.monarch) SystemTheme.HeaderCyanWhite else LocalPalette.current.secondary)
                    Spacer(Modifier.height(10.dp))
                    BasicText(if (ar) w.greetingAr else w.greetingEn, Modifier.fillMaxWidth(), style = body(16.sp, FontWeight.Bold, Sys.text).copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(6.dp))
                    BasicText(if (ar) w.lineAr else w.lineEn, Modifier.fillMaxWidth(), style = body(14.sp, FontWeight.Medium, Sys.text).copy(textAlign = TextAlign.Center, lineHeight = 21.sp))
                    Spacer(Modifier.height(8.dp))
                    // v1.2.2: subtle close affordance (tap anywhere still closes; auto-dismiss unchanged)
                    BasicText(if (ar) "اضغط للإغلاق" else "Tap to close", Modifier.fillMaxWidth(), style = body(11.sp, FontWeight.Normal, Sys.dim.copy(alpha = 0.75f)).copy(textAlign = TextAlign.Center))
                }
                // small ✕ in the reading-start corner, 48dp target
                Box(Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 10.dp).size(40.dp)
                    .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { closing = true }
                    .semantics { contentDescription = if (ar) "إغلاق" else "Close" }, contentAlignment = Alignment.Center) {
                    BasicText("✕", style = tech(14.sp, Sys.dim))
                }
                }
            }
            CrtLine(p.value, Modifier.matchParentSize())
        }
    }
}
