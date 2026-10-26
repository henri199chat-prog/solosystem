package com.ayman.solosystem.data

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Outcome
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.AppState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Holds the single AppState, persists every change, emits UI effects.
 * v1.2.1: the save file is read + decoded on a background thread (it took 0.6 s release / 4 s debug on the main thread at
 * start-up on the emulator). [loaded] gates the UI; every mutation first waits for the load, so the placeholder state is
 * never saved, shown as onboarding or used for reminders.
 */
class Repository(private val store: StateStore, private val onChanged: (AppState) -> Unit = {}) {
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded
    private val latch = java.util.concurrent.CountDownLatch(1)
    init {
        Thread({
            val t0 = System.nanoTime()
            val s = try { store.load() } catch (_: Throwable) { AppState() }
            _state.value = s
            _loaded.value = true
            latch.countDown()
            runCatching { android.util.Log.i("SoloStartup", "bgLoad=${(System.nanoTime() - t0) / 1_000_000}ms") }
            onChanged(s)
        }, "SoloLoad").apply { priority = Thread.MAX_PRIORITY }.start()
    }
    /** Blocks until the save file is loaded (instant after start-up). */
    fun awaitLoaded() = latch.await()
    private val _effects = Channel<UiEffect>(Channel.BUFFERED)
    val effects = _effects.receiveAsFlow()

    fun today(): Long = LocalDate.now().toEpochDay()
    fun now(): Long = System.currentTimeMillis()
    fun dayStartMs(day: Long = today()): Long = LocalDate.ofEpochDay(day).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Synchronized fun apply(f: (AppState) -> Outcome) {
        awaitLoaded()
        val o = f(_state.value)
        commit(o.state)
        o.effects.forEach { _effects.trySend(it) }
    }
    @Synchronized fun set(f: (AppState) -> AppState) { awaitLoaded(); commit(f(_state.value)) }

    private fun commit(s: AppState) {
        if (s == _state.value) return
        _state.value = s
        io.launch { synchronized(store) { store.save(_state.value) } }
        onChanged(s)
    }

    /** [issue] = the player is seeing the quest now (app open / morning notification): starts today's ring window if not started. */
    fun refreshDay(issue: Boolean = true) = apply { s ->
        val t = today()
        var o = GameEngine.rollover(s, t, now(), dayStartMs(t), issue)
        val e = GameEngine.expireDeadline(o.state, t, now(), dayStartMs(t))
        Outcome(com.ayman.solosystem.domain.nutrition.Nutrition.review(e.state, t), o.effects + e.effects)
    }

    /** v1.2.1: day rollover computed off the main thread on resume (apply() is synchronized; StateFlow is thread-safe). */
    fun refreshDayAsync() { io.launch { val t0 = System.nanoTime(); refreshDay(); android.util.Log.i("SoloStartup", "refreshDay=${(System.nanoTime() - t0) / 1_000_000}ms") } }

    fun replaceAll(s: AppState) { awaitLoaded(); _state.value = s; store.save(s); onChanged(s) }
    fun reset() { awaitLoaded(); store.clear(); _state.value = AppState(); onChanged(_state.value) }
    fun exportJson(): String = awaitLoaded().let { StateStore }.encode(_state.value)
    fun importJson(text: String): Boolean = try { replaceAll(StateStore.decode(text)); refreshDay(); true } catch (_: Exception) { false }
}
