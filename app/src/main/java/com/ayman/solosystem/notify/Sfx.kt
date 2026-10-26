package com.ayman.solosystem.notify

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.SystemClock
import com.ayman.solosystem.R

/**
 * SOLO SYSTEM sound design (v1.2). Every interaction category has its own preloaded SoundPool sound (low latency).
 * - Plays on the MEDIA stream (USAGE_GAME) so the phone's media volume applies, scaled by the in-app volume slider.
 * - Respects the in-app toggle and the phone's silent/vibrate mode (plays only when the ringer is NORMAL).
 * - Anti-spam: per-sound minimum gap, a transition right after a tap is ducked, an error cuts the tap that caused it.
 * Level-up keeps Ayman's ORIGINAL anime clip (PING + BLIP) with an optional synthesized tail layered after it.
 */
object Sfx {
    enum class Cat { TAP, NAV, WINDOW, STINGER, ALERT }
    enum class S(val res: Int, val cat: Cat, val gain: Float = 1f, val gapMs: Int = 45) {
        // original anime clip (level-up) + quiet notice from the same clip (toasts)
        PING(R.raw.sys_ping, Cat.STINGER, 1f, 300), BLIP(R.raw.sys_blip, Cat.STINGER, 1f, 120), NOTICE(R.raw.sys_notice, Cat.WINDOW, 0.9f, 250),
        // buttons
        TAP(R.raw.ui_tap, Cat.TAP), TAP_SECONDARY(R.raw.ui_tap_secondary, Cat.TAP), TOGGLE_ON(R.raw.ui_toggle_on, Cat.TAP), TOGGLE_OFF(R.raw.ui_toggle_off, Cat.TAP),
        SELECT(R.raw.ui_select, Cat.TAP), YES(R.raw.ui_yes, Cat.TAP, 1f, 80), NO(R.raw.ui_no, Cat.TAP, 1f, 80), PLUS(R.raw.ui_plus, Cat.TAP, 1f, 35), MINUS(R.raw.ui_minus, Cat.TAP, 1f, 35),
        // windows
        WIN_OPEN(R.raw.win_open, Cat.WINDOW, 1f, 180), WIN_CLOSE(R.raw.win_close, Cat.WINDOW, 1f, 120),
        // navigation
        NAV_TAB(R.raw.nav_tab, Cat.NAV, 1f, 140), NAV_PUSH(R.raw.nav_push, Cat.NAV, 1f, 120), NAV_BACK(R.raw.nav_back, Cat.NAV, 1f, 120),
        // quests
        QUEST_TICK(R.raw.quest_tick, Cat.TAP, 1f, 40), QUEST_COMPLETE(R.raw.quest_complete, Cat.STINGER, 1f, 400), DAILY_COMPLETE(R.raw.daily_complete, Cat.STINGER, 1f, 1500),
        LEVELUP_TAIL(R.raw.levelup_tail, Cat.STINGER, 1f, 1000),
        // secret / rewards
        SECRET(R.raw.secret_detect, Cat.STINGER, 1f, 1000), CHEST_CHARGE(R.raw.chest_charge, Cat.STINGER, 1f, 500), CHEST_BREAK(R.raw.chest_break, Cat.STINGER, 1f, 500),
        ORB(R.raw.orb_form, Cat.STINGER, 1f, 500), KEY(R.raw.key_reveal, Cat.STINGER, 1f, 800),
        REWARD_COMMON(R.raw.reward_common, Cat.STINGER, 1f, 400), REWARD_RARE(R.raw.reward_rare, Cat.STINGER, 1f, 600), REWARD_LEGENDARY(R.raw.reward_legendary, Cat.STINGER, 1f, 1200),
        SYSTEM_AWAKEN(R.raw.system_awaken, Cat.STINGER, 1f, 3000),
        // alerts
        PENALTY(R.raw.penalty, Cat.ALERT, 1f, 1500), WARNING(R.raw.warning, Cat.ALERT, 1f, 500), TIMER_LOW(R.raw.timer_low, Cat.ALERT, 1f, 1500), ERROR(R.raw.error, Cat.ALERT, 1f, 200),
        // nutrition (ترياق الشفاء): soft, calm variants
        SOFT_TAP(R.raw.soft_tap, Cat.TAP), SOFT_TOGGLE(R.raw.soft_toggle, Cat.TAP), SOFT_CONFIRM(R.raw.soft_confirm, Cat.TAP, 1f, 150), SOFT_WATER(R.raw.soft_water, Cat.TAP, 1f, 80)
    }

    private var pool: SoundPool? = null
    private val ids = java.util.concurrent.ConcurrentHashMap<S, Int>()
    private val loaded = HashSet<Int>()
    private val lastPlay = HashMap<S, Long>()
    private var lastTapAt = 0L
    private var lastTapStream = 0
    private var am: AudioManager? = null
    @Volatile var enabled = true
    /** 0..1 in-app volume (slider); applied with a perceptual curve on top of the phone media volume. */
    @Volatile var volume = 0.8f

    fun init(ctx: Context) {
        if (pool != null) return
        am = ctx.getSystemService(AudioManager::class.java)
        val p = SoundPool.Builder().setMaxStreams(10).setAudioAttributes(AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build()
        p.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded += id } }
        pool = p
        res = ctx.applicationContext.resources
        // the three original anime clips load at start-up exactly as in 1.1 (a level-up window can open immediately)
        EARLY.forEach { ids[it] = p.load(ctx, it.res, 1) }
    }
    private val EARLY = listOf(S.PING, S.BLIP, S.NOTICE)

    @Volatile private var res: android.content.res.Resources? = null
    @Volatile private var loading = false

    /**
     * Decodes the clips — called by the UI once the first frames are on screen (not in Application.onCreate):
     * decoding 38 clips competes with the cold-start frame on slow devices (an input-dispatch ANR on a slow emulator).
     * Staggered on a low-priority thread; taps/navigation/windows first, stingers last. A sound requested before it
     * is decoded is simply skipped.
     */
    fun startLoading() {
        val p = pool ?: return; val r = res ?: return
        if (loading) return
        loading = true
        val order = S.entries.filter { it !in EARLY }.sortedBy { it.cat.ordinal }
        Thread({
            try { order.forEach { s -> ids[s] = r.openRawResourceFd(s.res).use { fd -> p.load(fd, 1) }; Thread.sleep(40) } } catch (_: Throwable) { }
        }, "SfxLoader").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    private fun gainCurve(v: Float) = (v.coerceIn(0f, 1f)).let { it * it * 0.85f + it * 0.15f }

    /** @param rate playback rate 0.5..2 (pitch); @param vol extra gain for this call. Returns the stream id (0 = not played). */
    @Synchronized
    fun play(s: S, vol: Float = 1f, rate: Float = 1f): Int {
        if (!enabled || volume <= 0.001f) return 0
        if (am?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return 0
        val now = SystemClock.uptimeMillis()
        if (now - (lastPlay[s] ?: 0L) < s.gapMs) return 0
        val id = ids[s] ?: return 0
        if (synchronized(loaded) { id !in loaded }) return 0
        val p = pool ?: return 0
        var g = s.gain * vol
        when (s.cat) {
            Cat.NAV, Cat.WINDOW -> if (now - lastTapAt < 110) g *= if (s == S.WIN_CLOSE) 0.6f else 0.55f   // don't stack transition + tap harshly
            Cat.ALERT -> if (s == S.ERROR && now - lastTapAt < 160 && lastTapStream != 0) p.stop(lastTapStream)    // the denied tap is cut by the error
            else -> {}
        }
        g = (g * gainCurve(volume)).coerceIn(0f, 1f)
        lastPlay[s] = now
        val stream = p.play(id, g, g, if (s.cat == Cat.STINGER || s.cat == Cat.ALERT) 2 else 1, 0, rate.coerceIn(0.5f, 2f))
        if (s.cat == Cat.TAP) { lastTapAt = now; lastTapStream = stream }
        return stream
    }

    /** Preview at a given slider value even while the in-app toggle is being changed. */
    fun preview(s: S, vol01: Float) { val e = enabled; val v = volume; enabled = true; volume = vol01; lastPlay.remove(s); play(s); enabled = e; volume = v }
}
