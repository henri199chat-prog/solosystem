package com.ayman.solosystem

import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.*
import org.junit.Test

/** v1.2.0: Shadow-Monarch awakening at level 100 + sound settings migration from 1.1.0. */
class ThemeSoundTest {
    private fun awakenings(o: com.ayman.solosystem.domain.game.Outcome) = o.effects.count { it is UiEffect.SystemAwakening }

    @Test fun awakeningFiresOnceAtLevel100() {
        val s0 = Fx.state()
        assertFalse(s0.monarchAwakened)
        val at99 = GameEngine.debugGrantToLevel(s0, 99, Fx.MON, Fx.NOW)
        assertEquals(0, awakenings(at99)); assertFalse(at99.state.monarchAwakened)
        val at100 = GameEngine.debugGrantToLevel(at99.state, 100, Fx.MON, Fx.NOW + 1)
        assertEquals(1, awakenings(at100)); assertTrue(at100.state.monarchAwakened)
        assertTrue(at100.state.history.any { it.kind == HistoryKind.SYSTEM && it.text.en.contains("Shadow Monarch") })
        val at120 = GameEngine.debugGrantToLevel(at100.state, 120, Fx.MON, Fx.NOW + 2)
        assertEquals("cinematic is one-time", 0, awakenings(at120)); assertTrue(at120.state.monarchAwakened)
    }

    @Test fun jumpPast100StillAwakens() {
        val o = GameEngine.debugGrantToLevel(Fx.state(), 150, Fx.MON, Fx.NOW)
        assertEquals(1, awakenings(o)); assertTrue(o.state.monarchAwakened)
    }

    @Test fun blockedToastFlagged() {
        val t = UiEffect.Toast(Txt("x", "x"))
        assertFalse(t.blocked)
        assertTrue(UiEffect.Toast(Txt("x", "x"), blocked = true).blocked)
    }

    @Test fun migrationFrom110KeepsSoundChoiceAndBlueTheme() {
        // Simulate a 1.1.0 save: strip every field added in 1.2.0.
        fun as110(s: AppState): String {
            val root = StateStore.json.parseToJsonElement(StateStore.encode(s)).jsonObject
            val set = JsonObject(root["settings"]!!.jsonObject - setOf("uiSounds", "sfxVolume", "classicTheme"))
            return JsonObject(root - "monarchAwakened" + ("settings" to set)).toString()
        }
        val muted = Fx.state(settings = Settings(systemSounds = false))
        val m = StateStore.decode(as110(muted))
        assertNull(m.settings.uiSounds); assertFalse("silenced 1.1.0 users stay silent", m.settings.uiSoundsOn)
        assertEquals(80, m.settings.sfxVolume); assertFalse(m.settings.classicTheme); assertFalse(m.monarchAwakened)
        val loud = StateStore.decode(as110(Fx.state()))
        assertTrue(loud.settings.uiSoundsOn)
        // explicit choice overrides the inherited value and round-trips
        val own = StateStore.decode(StateStore.encode(muted.copy(settings = muted.settings.copy(uiSounds = true, sfxVolume = 35))))
        assertTrue(own.settings.uiSoundsOn); assertEquals(35, own.settings.sfxVolume)
        assertEquals(muted.quests, m.quests); assertEquals(muted.ledger, m.ledger)
    }
}
