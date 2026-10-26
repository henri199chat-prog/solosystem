package com.ayman.solosystem

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ayman.solosystem.ui.SoloRoot

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val repo = (application as SoloApp).repo
        requestMaxRefreshRate()
        com.ayman.solosystem.ui.DeepLink.from(intent)
        setContent { SoloRoot(repo) }
    }
    /** Ask for the panel's highest refresh rate at the current resolution (e.g. 120 Hz on Galaxy S25 Ultra). */
    private fun requestMaxRefreshRate() {
        runCatching {
            @Suppress("DEPRECATION")
            val d = if (android.os.Build.VERSION.SDK_INT >= 30) display else windowManager.defaultDisplay
            val cur = d?.mode ?: return
            val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate } ?: return
            window.attributes = window.attributes.also { it.preferredDisplayModeId = best.modeId; it.preferredRefreshRate = best.refreshRate }
        }
    }
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent); setIntent(intent)
        com.ayman.solosystem.ui.DeepLink.from(intent)
        // a resumed activity gets new intents without onPause/onResume on recent Android
        if (BuildConfig.DEBUG && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) debugHooks((application as SoloApp).repo)
    }
    override fun onStart() { super.onStart(); com.ayman.solosystem.ui.WelcomeBus.onStart(System.currentTimeMillis()) }
    override fun onStop() {
        super.onStop()
        val now = System.currentTimeMillis()
        com.ayman.solosystem.ui.WelcomeBus.onStop(now)
        com.ayman.solosystem.ui.welcomeLeft(applicationContext, now)
    }
    override fun onResume() {
        super.onResume()
        val repo = (application as SoloApp).repo
        repo.refreshDayAsync()
        if (BuildConfig.DEBUG) debugHooks(repo)
    }
    /**
     * Hidden QA hooks — DEBUG builds only (BuildConfig.DEBUG is a compile-time false in release, so R8 removes this):
     *   adb shell am start -n com.ayman.solosystem.debug/com.ayman.solosystem.MainActivity --es debug_level 100
     *   --es debug_theme holo|monarch|reset   (reset = not awakened yet, to replay the cinematic)
     *   --es debug_welcome show|hold          (show the welcome now; hold = keep it up 60 s for screenshots)
     */
    private fun debugHooks(repo: com.ayman.solosystem.data.Repository) {
        val i = intent ?: return
        i.getStringExtra("debug_theme")?.let { t ->
            repo.set { s -> when (t) {
                "holo" -> s.copy(settings = s.settings.copy(classicTheme = true))
                "monarch" -> s.copy(monarchAwakened = true, settings = s.settings.copy(classicTheme = false))
                "reset" -> s.copy(monarchAwakened = false, settings = s.settings.copy(classicTheme = false))
                else -> s
            } }
            i.removeExtra("debug_theme")
        }
        i.getStringExtra("debug_welcome")?.let { w ->
            com.ayman.solosystem.ui.WelcomeBus.hold = w == "hold"
            com.ayman.solosystem.ui.WelcomeBus.force()
            i.removeExtra("debug_welcome")
        }
        // v1.3.0 QA seeds (DEBUG only): shadows = 30 completed days of push+squat goals; photoback = backdate the oldest transformation photo 8 weeks
        i.getStringExtra("debug_seed")?.let { k ->
            val today = repo.today()
            repo.set { s -> when (k) {
                "shadows" -> {
                    val base = s.quests.filter { it.day == today && it.templateId in setOf("goal:push", "goal:squat") }
                    s.copy(quests = s.quests + (1..30).flatMap { d -> base.map { q -> q.copy(id = "seed_${d}_${q.id}", day = today - d, status = com.ayman.solosystem.domain.model.QuestStatus.COMPLETED, progress = q.target) } })
                }
                "photoback" -> {
                    val first = s.transform.photos.minByOrNull { it.day }
                    if (first == null) s else s.copy(transform = s.transform.copy(photos = s.transform.photos.map { if (it.id == first.id) it.copy(day = today - 56, weightKg = (it.weightKg ?: 80.0) + 3.2) else it }.sortedBy { it.day }))
                }
                else -> s
            } }
            i.removeExtra("debug_seed")
        }
        i.getStringExtra("debug_level")?.toIntOrNull()?.let { lv ->
            repo.apply { s -> com.ayman.solosystem.domain.game.GameEngine.debugGrantToLevel(s, lv, repo.today(), repo.now()) }
            i.removeExtra("debug_level")
        }
    }
}
