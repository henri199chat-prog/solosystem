package com.ayman.solosystem

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import org.junit.Assert.*
import org.junit.Test

class LevelingTest {
    @Test fun questXpFormula() {
        assertEquals(10, Leveling.questXp(Rank.E, 10, QuestType.DAILY))       // 10 × 1.0 × 1
        assertEquals(8, Leveling.questXp(Rank.E, 2, QuestType.DAILY))         // 10 × 0.75 → 7.5 → 8
        assertEquals(44, Leveling.questXp(Rank.C, 45, QuestType.DAILY))       // 35 × 1.25 = 43.75
        assertEquals(30, Leveling.questXp(Rank.D, 10, QuestType.EMERGENCY))   // 20 × 1.5
        assertEquals(360, Leveling.questXp(Rank.S, 10, QuestType.MAIN))
    }
    @Test fun thresholds() {
        assertEquals(95, Leveling.xpToNext(1))
        assertEquals(1, Leveling.levelFor(94).level)
        assertEquals(2, Leveling.levelFor(95).level)
        assertEquals(Leveling.totalXpFor(10), Leveling.totalXpFor(9) + Leveling.xpToNext(9))
        for (l in 2..299) assertTrue(Leveling.xpToNext(l) >= Leveling.xpToNext(l - 1))
        val years = Leveling.yearsToMax()
        assertTrue("years=$years", years in 4.0..5.0)
    }
    @Test fun capAt300() {
        val max = Leveling.totalXpFor(300)
        val a = Leveling.levelFor(max); val b = Leveling.levelFor(max * 3)
        assertEquals(300, a.level); assertEquals(300, b.level)
        assertTrue(a.isMax && b.isMax); assertEquals(1.0, b.fraction.toDouble(), 1e-6); assertEquals(0, Leveling.xpToNext(300))
        assertEquals(299, Leveling.levelFor(max - 1).level)
    }
    @Test fun titles() {
        assertEquals("لاعب جديد", Leveling.titleFor(1).ar); assertEquals("الخطوات الأولى", Leveling.titleFor(2).ar)
        assertEquals("لاعب منضبط", Leveling.titleFor(5).ar); assertEquals("لاعب نخبة", Leveling.titleFor(12).ar)
        assertEquals("لاعب متقدم", Leveling.titleFor(20).ar)
    }
}
