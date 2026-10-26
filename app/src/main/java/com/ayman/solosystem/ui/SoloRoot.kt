package com.ayman.solosystem.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import android.os.Build
import com.ayman.solosystem.domain.model.BgFx
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.solosystem.data.Repository
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.ui.comp.*
import com.ayman.solosystem.ui.screens.*
import com.ayman.solosystem.ui.theme.*
import kotlinx.coroutines.delay

class Nav {
    val stack = mutableStateListOf("status")
    val current get() = stack.last()
    /** Navigation whooshes: holographic swipe for tabs, a forward push into sub-screens, a reverse pull for back. */
    fun go(r: String, sound: Boolean = true) {
        if (r in tabs) { if (sound && !(stack.size == 1 && current == r)) com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.NAV_TAB); stack.clear(); stack.add(r) }
        else if (current != r) { if (sound) com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.NAV_PUSH); stack.add(r) }
    }
    fun back(): Boolean = if (stack.size > 1) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.NAV_BACK); stack.removeAt(stack.lastIndex); true } else false
    companion object { val tabs = listOf("status", "quests", "training", "nutrition", "progress", "more") }
}

@Composable
fun SoloRoot(repo: Repository) {
    // v1.2.1: the save loads off the main thread; until then only a plain System-dark screen (never onboarding / defaults)
    val ready by repo.loaded.collectAsState()
    if (!ready) { Box(Modifier.fillMaxSize().background(Color(0xFF02060C))); return }
    SoloRootLoaded(repo)
}

@Composable
private fun SoloRootLoaded(repo: Repository) {
    val s by repo.state.collectAsState()
    val reduced = rememberReducedMotion()
    // v1.2 theme switch: HOLO blue ↔ MONARCH violet (level 100). Snap on start; animate when changed in Settings;
    // the very first awakening is driven by the cinematic (SystemAwakeningWindow), not here.
    val themeTarget = SystemTheme.target(s.monarchAwakened, s.settings.classicTheme)
    val awakenedAtStart = remember { SystemTheme.blend = themeTarget; mutableStateOf(s.monarchAwakened) }
    LaunchedEffect(themeTarget) {
        if (s.monarchAwakened && !awakenedAtStart.value) { awakenedAtStart.value = true; return@LaunchedEffect }
        if (SystemTheme.blend == themeTarget) return@LaunchedEffect
        if (reduced) { SystemTheme.blend = themeTarget; return@LaunchedEffect }
        androidx.compose.animation.core.animate(SystemTheme.blend, themeTarget, animationSpec = tween(700)) { v, _ -> SystemTheme.blend = v }
    }
    val pal = SystemTheme.palette(paletteFor(s.equippedTheme, s.equippedFrame))
    LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; androidx.compose.runtime.withFrameNanos { }; kotlinx.coroutines.delay(700); com.ayman.solosystem.notify.Sfx.startLoading() }
    SideEffect { com.ayman.solosystem.notify.Sfx.enabled = s.settings.uiSoundsOn; com.ayman.solosystem.notify.Sfx.volume = s.settings.sfxVolume / 100f }
    CompositionLocalProvider(
        LocalArabic provides s.settings.arabic,
        LocalLayoutDirection provides if (s.settings.arabic) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalPalette provides pal,
        LocalReducedMotion provides reduced
    ) {
        ProvideFxClock(maxFps = if (s.settings.bgFx == BgFx.LOW) 60 else 120) {
            SystemBackground(fx = s.settings.bgFx) {
                val nav = remember { Nav() }
                val windowOpen = remember { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().systemBarsPadding()) {
                    // heavy blur of the screen behind an open system window (API 31+), applied at layer level only
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        renderEffect = if (windowOpen.value && Build.VERSION.SDK_INT >= 31) BlurEffect(22.dp.toPx(), 22.dp.toPx(), TileMode.Decal) else null
                    }) {
                        if (s.profile == null) OnboardingFlow(repo) else Main(repo, nav)
                    }
                    EffectsHost(repo, nav, windowOpen)
                    WelcomeHost(repo, windowOpen)
                }
            }
        }
    }
}

@Composable
private fun Main(repo: Repository, nav: Nav) {
    BackHandler(nav.stack.size > 1 || nav.current != "status") { if (!nav.back()) { com.ayman.solosystem.notify.Sfx.play(com.ayman.solosystem.notify.Sfx.S.NAV_BACK); nav.go("status", sound = false) } }
    // v1.3.0 deep links (notification / widget / foreground-service taps carry extra "open"=<route>)
    val link by DeepLink.open.collectAsState()
    LaunchedEffect(link) { link?.let { r -> DeepLink.open.value = null; if (r in DeepLink.ROUTES) nav.go(r) } }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth().refreshBeam(nav.current)) {
            val r = nav.current
            when {
                r == "status" -> StatusScreen(repo, nav)
                r == "quests" -> QuestsScreen(repo, nav)
                r == "training" -> TrainingScreen(repo, nav)
                r == "progress" -> ProgressScreen(repo, nav)
                r == "nutrition" -> CompositionLocalProvider(LocalSoftSfx provides true) { NutritionScreen(repo, nav) }
                r == "more" -> MoreScreen(nav)
                r == "skills" -> SkillsScreen(repo, nav)
                r == "inventory" -> InventoryScreen(repo, nav)
                r == "history" -> HistoryScreen(repo, nav)
                r == "calendar" -> CalendarScreen(repo, nav)
                r == "settings" -> SettingsScreen(repo, nav)
                r == "tiers" -> TiersScreen(repo, nav)
                r == "raid" -> RaidScreen(repo, nav)
                r == "profile" -> ProfileEditScreen(repo, nav)
                r == "guild" -> GuildScreen(repo, nav)
                r == "shadows" -> ShadowsScreen(repo, nav)
                r == "boss" -> BossScreen(repo, nav)
                r == "dungeon" -> DungeonScreen(repo, nav)
                r == "transform" -> TransformScreen(repo, nav)
                r.startsWith("exercise/") -> ExerciseScreen(repo, nav, r.substringAfter("exercise/"))
                else -> StatusScreen(repo, nav)
            }
        }
        BottomNav(nav)
    }
}

@Composable
private fun BottomNav(nav: Nav) {
    val pal = LocalPalette.current
    val items = listOf(
        Triple("status", Ico.STATUS, tr("الحالة", "Status")), Triple("quests", Ico.QUESTS, tr("المهام", "Quests")),
        Triple("training", Ico.TRAINING, tr("التدريب", "Training")), Triple("nutrition", Ico.POTION, tr("الترياق", "Elixir")),
        Triple("progress", Ico.PROGRESS, tr("التقدم", "Progress")),
        Triple("more", Ico.MORE, tr("المزيد", "More"))
    )
    val tab = nav.stack.firstOrNull { it in Nav.tabs } ?: "status"
    val root = if (nav.current in Nav.tabs) nav.current else if (nav.stack.size == 1) "more" else tab
    val navBg = SystemTheme.c(0xF0040810, 0xF008040F)
    Row(Modifier.fillMaxWidth().background(navBg).thinBox(Sys.panelLine.copy(alpha = 0.4f), navBg).padding(vertical = 4.dp)) {
        items.forEach { (id, ic, label) ->
            val sel = root == id
            Column(Modifier.weight(1f).clickable(role = Role.Tab) { nav.go(id) }.padding(vertical = 6.dp).semantics { contentDescription = label },
                horizontalAlignment = Alignment.CenterHorizontally) {
                SysIcon(ic, if (sel) (if (id == "nutrition") Elixir else pal.secondary) else Sys.dim, size = 22.dp)
                Spacer(Modifier.height(2.dp))
                BasicText(label, style = body(11.sp, if (sel) FontWeight.Bold else FontWeight.Normal, if (sel) Sys.text else Sys.dim), maxLines = 1)
            }
        }
    }
}

@Composable
private fun EffectsHost(repo: Repository, nav: Nav, windowOpen: MutableState<Boolean>) {
    val queue = remember { mutableStateListOf<UiEffect>() }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val toasts = remember { mutableStateListOf<Pair<Long, String>>() }
    val ar = LocalArabic.current
    LaunchedEffect(repo) {
        repo.effects.collect { e ->
            if (e is UiEffect.Toast) { toasts.add(System.nanoTime() to e.text.get(repo.state.value.settings.arabic)); com.ayman.solosystem.notify.Sfx.play(if (e.blocked) com.ayman.solosystem.notify.Sfx.S.ERROR else com.ayman.solosystem.notify.Sfx.S.NOTICE) }
            else if (queue.none { it == e }) queue.add(e)
            val a = repo.state.value.settings.arabic
            when (e) {   // system notifications for the hidden condition (shown even if the app goes to background)
                is UiEffect.SecretQuest -> com.ayman.solosystem.notify.Reminders.show(ctx, 11, "[SYSTEM] SECRET QUEST",
                    if (a) "تم اكتشاف شرط خفي... تحوّلت المهمة اليومية إلى مهمة سرّية!" else "Hidden condition detected... The daily quest became a SECRET QUEST!", repo.state.value, alert = true)
                is UiEffect.SecretReward -> if (!e.direct) com.ayman.solosystem.notify.Reminders.show(ctx, 12, if (a) "إشعار" else "NOTIFICATION",
                    if (a) "تلقيت مكافآت. تفقد مكافآتك؟" else "You have received rewards. Check your rewards?", repo.state.value, alert = true)
                else -> {}
            }
        }
    }
    val top = queue.firstOrNull()
    SideEffect { val st = repo.state.value.settings; com.ayman.solosystem.notify.Sfx.enabled = st.uiSoundsOn; com.ayman.solosystem.notify.Sfx.volume = st.sfxVolume / 100f }
    SideEffect { windowOpen.value = top != null }
    if (top != null) {
        val closing = remember(top) { mutableStateOf(false) }
        val ctl = remember(top) { WindowCtl(closing) { if (queue.isNotEmpty()) queue.removeAt(0) } }
        key(top) {
            CompositionLocalProvider(LocalWindowCtl provides ctl) {
                EffectWindow(top, repo, { r -> nav.go(r) }) { closing.value = true }
            }
        }
    }
    toasts.firstOrNull()?.let { t ->
        LaunchedEffect(t.first) { delay(2600); toasts.remove(t) }
        Box(Modifier.fillMaxSize().padding(top = 24.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(true, enter = fadeIn(tween(150))) {
                SysPanel(brackets = false, flares = false, padding = 12.dp, modifier = Modifier.clickable { toasts.remove(t) }) {
                    Label(t.second, color = Sys.text, size = 14)
                }
            }
        }
    }
}

object DeepLink {
    val ROUTES = setOf("guild", "shadows", "boss", "dungeon", "transform", "quests", "status")
    val open = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    fun from(i: android.content.Intent?) { i?.getStringExtra("open")?.let { open.value = it; i.removeExtra("open") } }
}
