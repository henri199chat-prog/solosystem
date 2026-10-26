package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.Penalty
import com.ayman.solosystem.domain.training.PlanGenerator
import org.junit.Assert.*
import org.junit.Test

class PenaltyTest {
    private val mon = Fx.MON
    @Test fun stepEscalatesAndDecays() {
        var t = 0; var c = 0
        repeat(7) { val r = Penalty.step(t, c, true, false, PenaltyMode.NORMAL); t = r.first; c = r.second }
        assertEquals(5, t) // capped at tier 5
        repeat(3) { val r = Penalty.step(t, c, false, false, PenaltyMode.NORMAL); t = r.first; c = r.second }
        assertEquals(4, t)
        assertEquals(3, Penalty.step(2, 0, true, false, PenaltyMode.GENTLE).first.coerceAtMost(3))
        assertEquals(2, Penalty.step(0, 0, true, true, PenaltyMode.STRICT).first)
        assertEquals(0, Penalty.step(3, 0, true, true, PenaltyMode.OFF).first)
    }
    @Test fun factorScales() {
        assertEquals(0.2, Penalty.factor(1), 1e-9); assertEquals(0.6, Penalty.factor(5), 1e-9)
    }
    @Test fun capsApplied() {
        val cap = Penalty.Capacity(100, 20, true)
        assertEquals(60, Penalty.amount(5, cap, 0))       // ≤ 0.6 × best
        assertEquals(30, Penalty.amount(5, cap, 130))     // total ≤ 1.6 × best
        assertEquals(0, Penalty.amount(5, cap, 200))
        assertEquals(20, Penalty.amount(1, cap, 0))
    }
    @Test fun issuedOnNextTrainingDayNotRestAndWithinTime() {
        val s0 = Fx.state() // 3 days: Mon/Wed/Fri
        val tue = GameEngine.rollover(s0, mon + 1, Fx.NOW, 0)
        val p = tue.state.quests.first { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING }
        assertEquals("penalty", p.templateId)
        assertEquals(mon + 2, p.day) // Tuesday is rest → Wednesday
        assertTrue(PlanGenerator.dayPlan(tue.state.plan!!, p.day).isTraining)
        assertTrue(Penalty.totalMinutes(p.items) <= Penalty.MAX_MINUTES)
        assertTrue(p.items.none { it.sets > 6 })
    }
    @Test fun restDayMissesNeverTriggerPenalty() {
        val s0 = Fx.state() // Mon/Wed/Fri → Tuesday is rest
        var s = GameEngine.rollover(s0, mon + 1, Fx.NOW, 0).state
        // excuse Monday so only the rest day can matter
        s.quests.filter { it.day == mon && it.status == QuestStatus.MISSED }.forEach { s = GameEngine.setReason(s, it.id, MissReason.SICK, mon + 1, Fx.NOW).state }
        assertTrue(Penalty.isRestDay(s, mon + 1))
        s = GameEngine.rollover(s, mon + 2, Fx.NOW, 0).state // Tuesday's (rest-day) dailies expire unfinished
        assertTrue(s.quests.any { it.day == mon + 1 && it.status == QuestStatus.MISSED })
        assertEquals(0, Penalty.tier(s, mon + 2))
        assertTrue(s.quests.none { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
    }
    @Test fun deloadWeekDefers() {
        val s = Fx.state()
        val plan = s.plan!!
        val deloadMon = plan.startDay + 7L * (plan.deloadEvery - 1)
        assertTrue(PlanGenerator.isDeloadWeek(plan, deloadMon))
        val d = Penalty.scheduleDay(plan, deloadMon)
        assertFalse(PlanGenerator.isDeloadWeek(plan, d))
    }
    @Test fun excusedDoesNotEscalate() {
        val s0 = Fx.state()
        var s = GameEngine.rollover(s0, mon + 1, Fx.NOW, 0).state
        assertTrue(Penalty.tier(s, mon + 1) >= 1)
        s.quests.filter { it.day == mon && it.status == QuestStatus.MISSED }.forEach { s = GameEngine.setReason(s, it.id, MissReason.SICK, mon + 1, Fx.NOW).state }
        assertEquals(0, Penalty.tier(s, mon + 1))
        assertTrue(s.quests.none { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
    }
    @Test fun lowReadinessConvertsToRecovery() {
        var s = GameEngine.rollover(Fx.state(), mon + 1, Fx.NOW, 0).state
        s = GameEngine.rollover(s, mon + 2, Fx.NOW, 0).state
        s = GameEngine.checkIn(s, CheckIn(mon + 2, 4f, 5, 1, 2), mon + 2, Fx.NOW).state
        val open = s.quests.filter { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING }
        assertTrue(open.isNotEmpty() && open.all { it.templateId == "reflection" && it.optional })
    }
    @Test fun completingPenaltyRedeemsStreak() {
        var s = GameEngine.rollover(Fx.state(), mon + 1, Fx.NOW, 0).state
        s = GameEngine.rollover(s, mon + 2, Fx.NOW, 0).state
        val p = s.quests.first { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING }
        p.items.indices.forEach { s = GameEngine.togglePenaltyItem(s, p.id, it, mon + 2, Fx.NOW).state }
        assertEquals(QuestStatus.COMPLETED, s.quests.first { it.id == p.id }.status)
        assertTrue(mon in s.redeemedDays)
    }
}
