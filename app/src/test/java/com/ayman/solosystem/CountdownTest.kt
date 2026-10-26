package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.Countdown
import org.junit.Assert.*
import org.junit.Test

class CountdownTest {
    private val H = 3_600_000L
    @Test fun segmentsVsTime() {
        val start = 0L
        assertEquals(24, Countdown.ring(start, 0, 1440).lit)
        assertEquals(24, Countdown.ring(start, H / 2, 1440).lit)          // first hour: last segment fading
        assertEquals(0.5f, Countdown.ring(start, H / 2, 1440).partial, 1e-4f)
        assertEquals(23, Countdown.ring(start, H, 1440).lit)
        assertEquals(12, Countdown.ring(start, 12 * H, 1440).lit)
        assertEquals(1, Countdown.ring(start, 23 * H + 1, 1440).lit)
        assertTrue(Countdown.ring(start, 22 * H, 1440).warn)
        assertFalse(Countdown.ring(start, 20 * H, 1440).warn)
        val end = Countdown.ring(start, 24 * H, 1440); assertEquals(0, end.lit); assertTrue(end.expired)
        assertEquals(0, Countdown.ring(start, 30 * H, 1440).lit)
        // custom deadline 22:00 → 22 h window
        assertEquals(12, Countdown.ring(start, 11 * H, 22 * 60).lit)
        for (t in 0..24 * 60) { val r = Countdown.ring(start, t * 60_000L, 1440); assertTrue(r.lit in 0..24) }
    }
    @Test fun alertTimes() {
        val a = Countdown.alertTimes(0, 0, 1440).toMap()
        assertEquals(12 * H, a["half"]); assertEquals(24 * H - 24 * H / 10, a["ten"]); assertEquals(24 * H, a["end"])
        assertEquals(listOf("end"), Countdown.alertTimes(0, 23 * H, 1440).map { it.first })
    }
    @Test fun expiryTriggersPenaltyOnce() {
        val s0 = Fx.state() // Monday = training day
        val early = GameEngine.expireDeadline(s0, Fx.MON, 23 * H, 0)
        assertEquals(s0, early.state)
        val o1 = GameEngine.expireDeadline(s0, Fx.MON, 24 * H, 0)
        assertTrue(o1.state.quests.any { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
        assertTrue(o1.effects.any { it is com.ayman.solosystem.domain.game.UiEffect.PenaltyIssued })
        val o2 = GameEngine.expireDeadline(o1.state, Fx.MON, 25 * H, 0)
        assertEquals(o1.state, o2.state)
        assertTrue(o2.effects.isEmpty())
        val next = GameEngine.rollover(o1.state, Fx.MON + 1, Fx.NOW, 0).state
        assertEquals(1, next.quests.count { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
    }
    @Test fun notArmedWhenDoneOrRestDay() {
        val s = Fx.state(day = Fx.MON + 1) // Tuesday rest
        assertFalse(Countdown.armed(s, Fx.MON + 1))
        val o = GameEngine.expireDeadline(s, Fx.MON + 1, 24 * H, 0)
        assertTrue(o.state.quests.none { it.type == QuestType.PENALTY })
    }

    @Test fun ringStartsFullAtIssueAndDrainsToDeadline() {
        val dayStart = 0L; val end = Countdown.deadlineMs(dayStart, 1440)
        val issued = 15 * H + 20 * 60_000L // opened at 15:20
        val r0 = Countdown.ring(issued, end, issued)
        assertEquals(24, r0.lit); assertEquals(1.0, r0.fraction, 1e-9); assertFalse(r0.warn)
        val window = end - issued
        assertEquals(12, Countdown.ring(issued, end, issued + window / 2).lit)
        assertTrue(Countdown.ring(issued, end, end - window / 10).warn)
        assertTrue(Countdown.ring(issued, end, end).expired)
        val a = Countdown.alertTimes(issued, end, issued).toMap()
        assertEquals(end - window / 2, a["half"]); assertEquals(end - window / 10, a["ten"]); assertEquals(end, a["end"])
        // issued after the deadline → no time left, never NaN
        assertEquals(0, Countdown.ring(end + H, end, end + H).lit)
    }
    @Test fun issueTimeIsPersistedOnceAndSurvivesReload() {
        assertEquals(Fx.NOW, Fx.state().questIssuedAt[Fx.MON]) // first-ever day: issued at onboarding → starts full
        val s0 = Fx.state().copy(questIssuedAt = emptyMap())
        val t1 = 7 * H; val t2 = 18 * H
        val s1 = GameEngine.rollover(s0, Fx.MON, t1, 0, issue = true).state
        assertEquals(t1, s1.questIssuedAt[Fx.MON])
        // later opens do not move the start (no refill)
        val s2 = GameEngine.rollover(s1, Fx.MON, t2, 0, issue = true).state
        assertEquals(t1, s2.questIssuedAt[Fx.MON])
        assertEquals(t1, Countdown.windowStart(s2, Fx.MON, 0))
        // background work (midnight alarms) does not issue
        val s3 = GameEngine.rollover(s0, Fx.MON, t1, 0, issue = false).state
        assertNull(s3.questIssuedAt[Fx.MON])
        // stored, not recomputed: JSON round trip keeps it
        val back = com.ayman.solosystem.data.StateStore.decode(com.ayman.solosystem.data.StateStore.encode(s2))
        assertEquals(t1, back.questIssuedAt[Fx.MON])
        // full at issue moment
        assertEquals(24, Countdown.ring(Countdown.windowStart(back, Fx.MON, 0), Countdown.deadlineMs(0, back.settings.deadlineMinutes), t1).lit)
    }
    @Test fun penaltyOnEmptyUsesSameDeadline() {
        val s = GameEngine.rollover(Fx.state(), Fx.MON, 20 * H, 0).state
        assertTrue(GameEngine.expireDeadline(s, Fx.MON, 23 * H, 0).effects.none { it is com.ayman.solosystem.domain.game.UiEffect.PenaltyIssued })
        assertTrue(GameEngine.expireDeadline(s, Fx.MON, 24 * H, 0).effects.any { it is com.ayman.solosystem.domain.game.UiEffect.PenaltyIssued })
    }
}
