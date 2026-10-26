package com.ayman.solosystem

import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.quest.Penalty
import org.junit.Assert.*
import org.junit.Test

/** v1.2.1: rank-E floor 50/50/60, fast ramp to 100, migration up to the floor, safety kept. */
class MercilessGoalTest {
    private val mon = Fx.MON
    private fun goal(s: AppState, day: Long, row: String) = s.quests.first { it.day == day && it.templateId == "goal:$row" }

    @Test fun exactRampSchedule() {
        assertEquals(listOf(50, 65, 80, 95, 100), GoalPlan.schedule(GoalPlan.Row.PUSH))
        assertEquals(listOf(50, 65, 80, 95, 100), GoalPlan.schedule(GoalPlan.Row.SITUP))
        assertEquals(listOf(60, 75, 90, 100), GoalPlan.schedule(GoalPlan.Row.SQUAT))
    }

    @Test fun floorAppliesToEveryBeginnerRegardlessOfCapacity() {
        val weak = Fx.state(Fx.profile(fit = FitnessLevel.BEGINNER)).copy(assessments = listOf(Assessment(mon, pushReps = 2, squatReps = 5, plankSec = 10)))
        assertEquals(50, GoalPlan.initial(weak, GoalPlan.Row.PUSH)); assertEquals(50, GoalPlan.initial(weak, GoalPlan.Row.SITUP)); assertEquals(60, GoalPlan.initial(weak, GoalPlan.Row.SQUAT))
        // a stored lower track can never go below the floor
        val low = weak.copy(goalTracks = weak.goalTracks + ("push" to GoalTrack(5, 5, mon)))
        assertEquals(50, GoalPlan.track(low, GoalPlan.Row.PUSH, mon).current)
        assertEquals(50, GoalPlan.target(low, GoalPlan.Row.PUSH, mon))
        // running keeps its own (capacity / weight / intake) logic — no floor
        assertEquals(GoalPlan.capacityStart(weak, GoalPlan.Row.RUN), GoalPlan.initial(weak, GoalPlan.Row.RUN))
    }

    /** Simulates consistent weeks (completing each day's GOAL) and checks the weekly targets reach 100 in 3–4 weeks. */
    @Test fun consistentWeeksReachHundredFast() {
        var s = Fx.state()
        val seen = mutableMapOf("push" to mutableListOf<Int>(), "squat" to mutableListOf())
        for (week in 0 until 6) {
            val d0 = mon + week * 7
            listOf("push", "squat").forEach { seen[it]!!.add(GoalPlan.track(s, GoalPlan.Row.entries.first { r -> r.id == it }, d0).current) }
            // 7 completed days at the full current target
            val done = (0 until 7).flatMap { i -> listOf("push", "situp", "squat").map { row ->
                val r = GoalPlan.Row.entries.first { it.id == row }
                Quest("t:$row:${d0 + i}", "goal:$row", QuestType.DAILY, QuestCategory.TRAINING, Txt("", ""), Txt("", ""), Txt("", ""), Rank.D, 5, 5, d0 + i,
                    status = QuestStatus.COMPLETED, verify = Verify.COUNT, target = GoalPlan.track(s, r, d0).current, progress = GoalPlan.track(s, r, d0).current)
            } }
            s = GoalPlan.ramp(s.copy(quests = s.quests + done), d0 + 7)
        }
        assertEquals(listOf(50, 65, 80, 95, 100, 100), seen["push"])
        assertEquals(listOf(60, 75, 90, 100, 100, 100), seen["squat"])
        assertEquals(100, s.goalTracks["situp"]!!.current)
    }

    @Test fun safetyKept_restPainLoweringAndRaiseOnly() {
        val s = Fx.state()                                                   // Mon/Wed/Fri training, Tue rest
        assertEquals(30, GoalPlan.target(s, GoalPlan.Row.PUSH, mon + 1))   // rest day 60 %
        val pain = s.copy(checkIns = listOf(CheckIn(mon, 7f, 3, 3, 2)))
        assertEquals(25, GoalPlan.target(pain, GoalPlan.Row.PUSH, mon))    // pain 50 %
        val q = goal(s, mon, "push")
        assertEquals(25, GoalPlan.lowered(s, q, mon).target)               // illness/injury/pain button
        assertEquals(s, GameEngine.editGoal(s, q.id, "", 40, mon, Fx.NOW).state)   // can't lower
    }

    @Test fun variantsScaleToNewNumbers() {
        assertEquals(36, GoalPlan.toVariant(GoalPlan.variant(GoalPlan.Row.PUSH, "diamond"), 50))
        assertEquals(25, GoalPlan.toVariant(GoalPlan.variant(GoalPlan.Row.PUSH, "archer"), 50))
        assertEquals(100, GoalPlan.toVariant(GoalPlan.variant(GoalPlan.Row.SITUP, "plank"), 50))
        assertEquals(50, GoalPlan.toVariant(GoalPlan.variant(GoalPlan.Row.SQUAT, "lunge"), 60))
        assertTrue(GoalPlan.splitHint(50).en.startsWith("can be split into sets"))
        assertTrue(GoalPlan.setsHint(GoalPlan.Row.PUSH, 100).ar.contains("يمكن تقسيمها"))
    }

    @Test fun migrationRaisesToFloorNeverLowersAndKeepsToday() {
        var old = Fx.state()
        val push = goal(old, mon, "push")
        // a 1.2.0 save: tiny tracks, squat already above the floor, today's 5-rep push quest completed
        old = old.copy(schemaVersion = 2,
            goalTracks = mapOf("push" to GoalTrack(5, 5, mon - 3), "situp" to GoalTrack(5, 5, mon - 3), "squat" to GoalTrack(10, 75, mon - 3), "run" to GoalTrack(500, 500, mon - 3)),
            quests = old.quests.map { if (it.id == push.id) it.copy(target = 5, progress = 5, status = QuestStatus.COMPLETED) else it })
        val text = StateStore.json.encodeToString(AppState.serializer(), old)
        val s = StateStore.decode(text)
        assertEquals(StateStore.SCHEMA, s.schemaVersion)
        assertEquals(50, s.goalTracks["push"]!!.current); assertEquals(50, s.goalTracks["situp"]!!.current)
        assertEquals(75, s.goalTracks["squat"]!!.current)                   // higher → untouched
        assertEquals(500, s.goalTracks["run"]!!.current)                    // running untouched
        assertEquals(mon - 3, s.goalTracks["push"]!!.lastRampDay)           // ramp clock kept
        assertEquals(old.quests, s.quests)                                  // today's quest (completed) unchanged
        assertEquals(QuestStatus.COMPLETED, goal(s, mon, "push").status); assertEquals(5, goal(s, mon, "push").target)
        // next issued quest uses the new floor; old 5-rep days don't trigger an instant ramp
        assertEquals(50, GoalPlan.target(s, GoalPlan.Row.PUSH, mon + 2))
        assertEquals(50, GoalPlan.ramp(s, mon + 4).goalTracks["push"]!!.current)
        // a 2nd load is a no-op
        assertEquals(s, StateStore.decode(StateStore.encode(s)))
    }

    @Test fun penaltyScalesWithProvenGoalDaysButStaysCapped() {
        var s = Fx.state()
        val q = goal(s, mon, "push")
        s = GameEngine.logGoal(s, q.id, q.target, mon, Fx.NOW).state
        val base = Penalty.Capacity(16, 8, false)
        val c = Penalty.withGoal(s, "pushup", mon + 1, base)
        assertEquals(50, c.bestSessionVolume); assertEquals(12, c.bestSet)
        assertEquals(10, Penalty.amount(1, c, 0))                            // tier 1: +20 %
        assertEquals(30, Penalty.amount(5, c, 0))                            // tier 5 capped at 0.6 × best
        assertEquals(base, Penalty.withGoal(s, "knee_pushup", mon + 1, base)) // easier variation: unchanged
        val items = Penalty.buildItems(s, 5, mon + 2)
        assertTrue(Penalty.totalMinutes(items) <= Penalty.MAX_MINUTES + 0.01)
    }
}
