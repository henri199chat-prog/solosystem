package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.training.Tiers
import org.junit.Assert.*
import org.junit.Test

class TierTest {
    private fun withXp(s: AppState, level: Int) = s.copy(ledger = s.ledger + XpEntry("test", Leveling.totalXpFor(level), 0, Fx.MON, Txt("t", "t")))
    @Test fun unlockByLevel() {
        assertEquals(0, Tiers.unlockedTier(24)); assertEquals(1, Tiers.unlockedTier(25)); assertEquals(7, Tiers.unlockedTier(300))
    }
    @Test fun levelAloneDoesNotActivate() {
        val s0 = withXp(Fx.state(), 30)
        val s = GameEngine.post(s0, s0, Fx.MON, Fx.NOW, emptyList()).state
        assertEquals(0, s.activeTier)
        assertTrue(s.quests.any { it.id == "m:assess:1" && it.status == QuestStatus.PENDING })
    }
    @Test fun benchmarksActivate() {
        val s0 = withXp(Fx.state(), 30)
        val s = GameEngine.recordAssessment(s0, Assessment(Fx.MON, pushReps = 12, squatReps = 30, plankSec = 50, pullReps = 2), Fx.MON, Fx.NOW).state
        assertEquals(1, s.activeTier)
        val s2 = GameEngine.recordAssessment(s, Assessment(Fx.MON, 100, 100, 300, 30), Fx.MON, Fx.NOW).state
        assertEquals("one at a time, level-gated", 1, s2.activeTier)
    }
    @Test fun lowLevelHighPerformanceNotActive() {
        val s = GameEngine.recordAssessment(Fx.state(), Assessment(Fx.MON, 50, 80, 200, 20), Fx.MON, Fx.NOW).state
        assertEquals(0, s.activeTier)
    }
}
