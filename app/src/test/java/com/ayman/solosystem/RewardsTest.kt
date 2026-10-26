package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.rewards.*
import com.ayman.solosystem.domain.skills.SkillCatalog
import org.junit.Assert.*
import org.junit.Test

class RewardsTest {
    @Test fun catalogSizes() {
        assertTrue(SkillCatalog.all.size >= 60)
        assertEquals(6, SkillCatalog.all.map { it.branch }.toSet().size)
        assertTrue(RewardCatalog.all.size >= 100)
        assertEquals(SkillCatalog.all.size, SkillCatalog.all.map { it.id }.toSet().size)
        assertEquals(RewardCatalog.all.size, RewardCatalog.all.map { it.id }.toSet().size)
        assertTrue(SkillCatalog.all.all { it.thresholds.size == 5 && it.thresholds.zipWithNext().all { (a, b) -> a < b } })
    }
    @Test fun noDuplicateGrants() {
        val s0 = Fx.state()
        val (s1, g1) = Rewards.grant(s0, "k1", "title_wolf_slayer", Fx.MON, Txt("a", "a"))
        val (s2, g2) = Rewards.grant(s1, "k1", "title_wolf_slayer", Fx.MON, Txt("a", "a"))
        assertNotNull(g1); assertNull(g2); assertEquals(s1, s2)
        assertEquals(1, s2.inventory["title_wolf_slayer"])
        // same cosmetic from another source → converted to gold, never a duplicate item
        val gold = Ledger.goldBalance(s2)
        val (s3, g3) = Rewards.grant(s2, "k2", "title_wolf_slayer", Fx.MON, Txt("b", "b"))
        assertEquals(1, s3.inventory["title_wolf_slayer"]); assertTrue(g3!!.goldInstead > 0); assertEquals(gold + g3.goldInstead, Ledger.goldBalance(s3))
    }
    @Test fun milestoneChestsOnce() {
        var s = Fx.state()
        s = s.copy(ledger = s.ledger + XpEntry("t", Leveling.totalXpFor(10), 0, Fx.MON, Txt("", "")))
        val a = GameEngine.post(s, s, Fx.MON, Fx.NOW, emptyList()).state
        val b = GameEngine.post(a, a, Fx.MON, Fx.NOW, emptyList()).state
        assertTrue("chest:5" in a.grantedKeys && "chest:10" in a.grantedKeys)
        assertEquals(a.inventory, b.inventory)
    }
    @Test fun lootPityEpic() {
        var st = LootState(seed = 7)
        var r = kotlin.random.Random(1)
        repeat(500) {
            val rar = Loot.rollRarity(Rarity.COMMON, st, 0, Loot.rng(st))
            assertTrue("pity broken", st.sinceEpic < Loot.PITY_EPIC)
            st = st.copy(opened = st.opened + 1, sinceEpic = if (rar >= Rarity.EPIC) 0 else st.sinceEpic + 1, sinceLegendary = if (rar == Rarity.LEGENDARY) 0 else st.sinceLegendary + 1)
            assertTrue(st.sinceEpic < Loot.PITY_EPIC); assertTrue(st.sinceLegendary < Loot.PITY_LEGENDARY)
        }
        assertEquals(Rarity.LEGENDARY, Loot.rollRarity(Rarity.COMMON, LootState(sinceLegendary = Loot.PITY_LEGENDARY - 1), 0, r))
        assertTrue(Loot.rollRarity(Rarity.COMMON, LootState(sinceEpic = Loot.PITY_EPIC - 1), 0, r) >= Rarity.EPIC)
    }
    @Test fun lootDeterministicAndBoxConsumed() {
        var s = Fx.state()
        assertEquals(1, s.inventory["box_common"])
        val a = Rewards.openBox(s, "box_common", Fx.MON)!!; val b = Rewards.openBox(s, "box_common", Fx.MON)!!
        assertEquals(a.second.rewardId, b.second.rewardId) // seeded, reproducible
        assertEquals(0, a.first.inventory["box_common"]); assertNull(Rewards.openBox(a.first, "box_common", Fx.MON))
    }
    @Test fun weightsValid() {
        for (r in Rarity.entries) for (l in 0..10) { val w = Loot.weights(r, l); assertTrue(w.all { it >= 0 }); assertEquals(1000, w.sum()) }
    }
    @Test fun surpriseOnlyAfterAllDaily() {
        val s0 = Fx.state()
        assertFalse(s0.grantedKeys.any { it.startsWith("surprise:") })
        var s = Fx.completeAllManual(s0, Fx.MON)
        assertFalse("side tasks alone don't finish the daily quest", s.grantedKeys.any { it.startsWith("surprise:") })
        s = Fx.completeGoals(s, Fx.MON)
        assertTrue(s.grantedKeys.contains("surprise:${Fx.MON}"))
    }
    @Test fun skillsUnlockFromBehaviour() {
        var s = Fx.state()
        s = GameEngine.checkIn(s, CheckIn(Fx.MON, 8f, 2, 4, 0), Fx.MON, Fx.NOW).state
        assertTrue(s.skillLevelsSeen.isEmpty() || s.skillLevelsSeen.values.all { it >= 1 })
    }
}
