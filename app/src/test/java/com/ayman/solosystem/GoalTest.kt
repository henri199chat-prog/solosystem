package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.quest.Countdown
import com.ayman.solosystem.domain.quest.GoalPlan
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.roundToInt
import com.ayman.solosystem.domain.game.UiEffect

class GoalTest {
    private val mon = Fx.MON
    private fun goals(s: AppState, day: Long) = s.quests.filter { it.day == day && GoalPlan.isGoal(it) }

    @Test fun dailyQuestIsTheFourAnimeRowsAndSideTasksAreOptional() {
        val s = Fx.state()
        val g = goals(s, mon)
        assertEquals(listOf("goal:push", "goal:situp", "goal:squat", "goal:run"), g.map { it.templateId })
        assertTrue(g.all { !it.optional && it.type == QuestType.DAILY && it.progress == 0 })
        val side = s.quests.filter { it.day == mon && it.type == QuestType.DAILY && !GoalPlan.isGoal(it) }
        assertTrue(side.isNotEmpty() && side.all { it.optional })
        assertTrue("nutrition habit moved to Elixir", side.none { it.templateId == "nutrition" })
    }

    @Test fun targetsStartAtRankEFloorCapacityCanOnlyRaise() {
        val s = Fx.state()                                   // NOVICE, no assessment
        val push = goals(s, mon).first { it.templateId == "goal:push" }
        assertEquals(50, push.target)                         // 1.2.1 merciless floor (capacity alone would give 15)
        assertEquals(15, GoalPlan.capacityStart(s, GoalPlan.Row.PUSH))
        assertEquals(50, goals(s, mon).first { it.templateId == "goal:situp" }.target)
        assertEquals(60, goals(s, mon).first { it.templateId == "goal:squat" }.target)
        val run = goals(s, mon).first { it.templateId == "goal:run" }
        assertEquals(1500, run.target)                        // 1.5 km walk/jog
        val tested = Fx.state().copy(assessments = listOf(Assessment(mon, pushReps = 20, squatReps = 30, plankSec = 60)))
        assertEquals(50, GoalPlan.initial(tested, GoalPlan.Row.PUSH))     // 40 from capacity < floor 50
        assertEquals(60, GoalPlan.initial(tested, GoalPlan.Row.SQUAT))
        assertEquals(50, GoalPlan.initial(tested, GoalPlan.Row.SITUP))
        val strong = Fx.state().copy(assessments = listOf(Assessment(mon, pushReps = 35, squatReps = 45, plankSec = 160)))
        assertEquals(70, GoalPlan.initial(strong, GoalPlan.Row.PUSH))     // capacity above the floor still counts
        assertEquals(90, GoalPlan.initial(strong, GoalPlan.Row.SQUAT))
        assertEquals(80, GoalPlan.initial(strong, GoalPlan.Row.SITUP))
        assertTrue(GoalPlan.Row.entries.all { GoalPlan.initial(tested, it) <= it.cap })
    }

    @Test fun restDayIsLighter() {
        val s = Fx.state()                                    // Mon/Wed/Fri training → Tue rest
        assertTrue(GoalPlan.target(s, GoalPlan.Row.PUSH, mon + 1) < GoalPlan.target(s, GoalPlan.Row.PUSH, mon))
    }

    @Test fun weeklyRampOnlyAfterConsistentPainFreeWeekAndCapped() {
        var s = Fx.state()
        val start = s.goalTracks["push"]!!.current
        // simulate 5 completed push rows last week
        val done = (1..5).map { d -> goals(s, mon).first { it.templateId == "goal:push" }.copy(id = "x$d", day = mon + d, status = QuestStatus.COMPLETED) }
        s = s.copy(quests = s.quests + done)
        val grown = GoalPlan.ramp(s, mon + 7)
        assertEquals(start + 15, grown.goalTracks["push"]!!.current)
        // pain recently → hold
        val pain = GoalPlan.ramp(s.copy(checkIns = listOf(CheckIn(mon + 6, 7f, 3, 3, 2))), mon + 7)
        assertEquals(start, pain.goalTracks["push"]!!.current)
        // not enough completions → hold
        assertEquals(start, GoalPlan.ramp(Fx.state(), mon + 7).goalTracks["push"]!!.current)
        // never above 100 / 10 km
        val near = s.copy(goalTracks = s.goalTracks + ("push" to GoalTrack(15, 98, mon)))
        assertEquals(100, GoalPlan.ramp(near, mon + 7).goalTracks["push"]!!.current)
    }

    @Test fun loggingCompletesAndOverachievementGivesReversibleBonus() {
        var s = Fx.state()
        val q = goals(s, mon).first { it.templateId == "goal:push" }
        s = GameEngine.logGoal(s, q.id, 5, mon, Fx.NOW).state
        assertEquals(QuestStatus.PENDING, s.quests.first { it.id == q.id }.status)
        s = GameEngine.logGoal(s, q.id, q.target * 2 - 5, mon, Fx.NOW).state     // → [2T/T]
        val after = s.quests.first { it.id == q.id }
        assertEquals(QuestStatus.COMPLETED, after.status)
        assertEquals(q.target * 2, after.progress)                              // overachievement kept, e.g. [30/15]
        assertTrue(s.ledger.any { it.key == "ov:${q.id}" })
        s = GameEngine.logGoal(s, q.id, -q.target, mon, Fx.NOW).state          // back to [T/T]
        assertFalse(s.ledger.any { it.key == "ov:${q.id}" })
        assertEquals(QuestStatus.COMPLETED, s.quests.first { it.id == q.id }.status)
        s = GameEngine.logGoal(s, q.id, -1, mon, Fx.NOW).state
        assertEquals(QuestStatus.PENDING, s.quests.first { it.id == q.id }.status)
        assertFalse(s.ledger.any { it.key == "q:${q.id}" })
    }

    @Test fun ringAndPenaltyAreTiedToGoalRowsOnly() {
        val s0 = Fx.state()
        val goalsDone = Fx.completeGoals(s0, mon)
        assertTrue("side tasks don't hold the ring", Countdown.allDone(goalsDone, mon))
        val next = GameEngine.rollover(goalsDone, mon + 1, Fx.NOW, 0).state
        assertTrue(next.quests.none { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
        val missed = GameEngine.rollover(Fx.completeAllManual(s0, mon), mon + 1, Fx.NOW, 0).state
        assertTrue(missed.quests.any { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING })
    }
}

class GoalUpgradeTest {
    @Test fun midDayUpgradeFromOldFormatAddsGoalRows() {
        val s0 = Fx.state()
        val legacy = s0.copy(quests = s0.quests.filterNot { GoalPlan.isGoal(it) }.map { it.copy(optional = false) }, goalTracks = emptyMap())
        val up = GameEngine.rollover(legacy, Fx.MON, Fx.NOW, 0).state
        assertEquals(4, up.quests.count { it.day == Fx.MON && GoalPlan.isGoal(it) })
        assertTrue(up.quests.filter { it.day == Fx.MON && it.type == QuestType.DAILY && !GoalPlan.isGoal(it) }.all { it.optional })
    }
}

class SideTaskMigrationTest {
    @Test fun sideTasksAreTheKeptFourAndRetiredItemsMigrateCleanly() {
        val s0 = Fx.state()
        val side = s0.quests.filter { it.day == Fx.MON && it.type == QuestType.DAILY && !GoalPlan.isGoal(it) }.map { it.templateId.substringBefore(":") }.toSet()
        assertTrue(side.containsAll(setOf("checkin", "mobility", "sleep")))
        assertTrue(side.none { it in GameEngine.RETIRED })
        // weekly review on Sunday
        val sun = GameEngine.rollover(s0, Fx.MON + 6, Fx.NOW, 0).state
        assertTrue(sun.quests.any { it.day == Fx.MON + 6 && it.templateId == "review" && it.optional })
        // old 1.0.x save: pending learn today, missed walk yesterday with a loss
        fun old(t: String, day: Long, st: QuestStatus) = s0.quests.first { it.templateId == "sleep" }.copy(id = "d:$t:$day", templateId = t, day = day, status = st, optional = false)
        val legacy = s0.copy(quests = s0.quests + old("learn", Fx.MON, QuestStatus.PENDING) + old("walk", Fx.MON - 1, QuestStatus.MISSED) + old("learn", Fx.MON - 3, QuestStatus.COMPLETED),
            bonus = s0.bonus + BonusEntry("loss:d:walk:${Fx.MON - 1}", Fx.MON - 1, -5))
        val m = GameEngine.migrateDailies(legacy, Fx.MON)
        assertTrue(m.quests.none { it.id == "d:learn:${Fx.MON}" })
        assertTrue(m.quests.first { it.id == "d:walk:${Fx.MON - 1}" }.reasonReviewed)
        assertTrue(m.bonus.none { it.key == "loss:d:walk:${Fx.MON - 1}" })
        assertTrue("history kept", m.quests.any { it.id == "d:learn:${Fx.MON - 3}" && it.status == QuestStatus.COMPLETED })
        assertEquals(m, GameEngine.migrateDailies(m, Fx.MON))   // idempotent
    }
}

class GoalVarietyEditTest {
    private val mon = Fx.MON
    private fun goal(s: AppState, row: String, day: Long = mon) = s.quests.first { it.day == day && it.templateId == "goal:$row" }
    private fun toasts(o: com.ayman.solosystem.domain.game.Outcome) = o.effects.filterIsInstance<UiEffect.Toast>().map { it.text.ar }

    @Test fun variantsRotateAfterFirstWeekWithEquivalentDifficultyAndRespectLimitations() {
        val s = Fx.state(Fx.profile(fit = FitnessLevel.INTERMEDIATE))
        assertTrue("first week = base movements", s.quests.filter { GoalPlan.isGoal(it) }.all { it.variant.isEmpty() })
        val days = (mon + 7) until (mon + 35)
        val used = mutableMapOf<GoalPlan.Row, MutableSet<String>>()
        days.forEach { d ->
            GoalPlan.Row.entries.forEach { r ->
                val v = GoalPlan.rotatedVariant(s, r, d)
                used.getOrPut(r) { mutableSetOf() } += v.id
                if (v.id.isNotEmpty()) {
                    val base = GoalPlan.target(s, r, d)
                    val t = GoalPlan.toVariant(v, base)
                    // never easier than the base amount, and within one rounding step of it
                    assertTrue("$r ${v.id}", GoalPlan.intensity(v, t) >= base - 1e-6)
                    assertTrue("$r ${v.id}", GoalPlan.intensity(v, t) <= base + v.factor * (if (v.unit == GoalPlan.GUnit.M) 100 else if (v.unit == GoalPlan.GUnit.SEC) 5 else 1) + 1e-6)
                    assertTrue(v.tier <= GoalPlan.tier(s))
                }
            }
        }
        assertTrue("variety on every row", GoalPlan.Row.entries.all { (used[it]?.size ?: 0) >= 2 })
        assertTrue("still mostly base", days.count { GoalPlan.rotatedVariant(s, GoalPlan.Row.PUSH, it).id.isEmpty() } > days.count() / 2)
        // knee limitation → no jump squats / lunges / sprints / intervals / stairs
        val knee = Fx.state(Fx.profile(fit = FitnessLevel.ADVANCED, lim = setOf(Limitation.KNEE)))
        days.forEach { d ->
            assertTrue(GoalPlan.rotatedVariant(knee, GoalPlan.Row.SQUAT, d).id !in setOf("jump", "lunge", "split"))
            assertTrue(GoalPlan.rotatedVariant(knee, GoalPlan.Row.RUN, d).id.isEmpty())
        }
        // beginner tier → only tier-0 variants
        val beg = Fx.state(Fx.profile(fit = FitnessLevel.BEGINNER))
        days.forEach { d -> GoalPlan.Row.entries.forEach { r -> assertEquals(0, GoalPlan.rotatedVariant(beg, r, d).tier) } }
        // rest day → base movement only
        val rested = days.filter { !com.ayman.solosystem.domain.training.PlanGenerator.dayPlan(s.plan!!, it).isTraining }
        assertTrue(rested.isNotEmpty() && rested.all { d -> GoalPlan.Row.entries.all { GoalPlan.rotatedVariant(s, it, d).id.isEmpty() } })
    }

    @Test fun playerCanOnlyRaiseIntensity() {
        var s = Fx.state()
        val q = goal(s, "push")                                   // 50 push-ups (rank-E floor)
        // lowering the amount → refused with the SYSTEM message, state unchanged
        var o = GameEngine.editGoal(s, q.id, "", q.target - 5, mon, Fx.NOW)
        assertEquals(s, o.state); assertTrue(toasts(o).any { "لا يمكن تخفيض شدة المهمة" in it })
        // easier variant → refused
        o = GameEngine.editGoal(s, q.id, "wide", 99, mon, Fx.NOW).let { GameEngine.editGoal(it.state, q.id, "", 200, mon, Fx.NOW) }
        assertTrue(toasts(o).any { "لا يمكن تخفيض شدة المهمة" in it })
        // a harder variant at less-than-equivalent volume → refused (50 push-ups ≈ 35.7 diamond)
        o = GameEngine.editGoal(s, q.id, "diamond", 8, mon, Fx.NOW)
        assertEquals(s, o.state); assertTrue(toasts(o).any { "لا يمكن تخفيض شدة المهمة" in it })
        // raising the amount → accepted, the completion bar stays the original 50
        o = GameEngine.editGoal(s, q.id, "", 60, mon, Fx.NOW); s = o.state
        val r = goal(s, "push")
        assertEquals(50, r.target); assertEquals(60, r.stretch)
        // then lowering the raised amount → refused too
        assertEquals(s, GameEngine.editGoal(s, q.id, "", 55, mon, Fx.NOW).state)
        // swap to diamond: requirement converted (50/1.4 → 36), raised amount kept at equivalent-or-more
        s = GameEngine.editGoal(s, q.id, "diamond", 45, mon, Fx.NOW).state
        val d = goal(s, "push")
        assertEquals("diamond", d.variant); assertEquals(36, d.target); assertEquals(45, d.stretch)
    }

    @Test fun completionJudgedOnOriginalTargetAndRaiseBonusIsProportionalCappedIdempotent() {
        var s = Fx.state()
        val q = goal(s, "squat")
        s = GameEngine.editGoal(s, q.id, "", q.target * 3, mon, Fx.NOW).state       // raise to 3×
        // reaching only the original target completes it (no risk), no raise bonus yet
        s = GameEngine.logGoal(s, q.id, q.target, mon, Fx.NOW).state
        assertEquals(QuestStatus.COMPLETED, goal(s, "squat").status)
        assertTrue(s.ledger.none { it.key == "st:${q.id}" })
        // the full day is judged on the original targets → ring/penalty unaffected by raising
        // reaching the raised amount → bonus, capped at +100 % of the quest XP
        s = GameEngine.logGoal(s, q.id, q.target * 2, mon, Fx.NOW).state
        val st = s.ledger.filter { it.key == "st:${q.id}" }
        assertEquals(1, st.size); assertEquals(q.xp, st.first().xp)
        // idempotent: more logging / re-applying does not duplicate
        s = GameEngine.logGoal(s, q.id, 1, mon, Fx.NOW).state
        s = GameEngine.logGoal(s, q.id, 0, mon, Fx.NOW, force = true).state
        assertEquals(1, s.ledger.count { it.key == "st:${q.id}" })
        // reversible: undo below the raised amount removes it
        s = GameEngine.logGoal(s, q.id, -(q.target + 1), mon, Fx.NOW).state
        assertTrue(s.ledger.none { it.key == "st:${q.id}" })
        assertEquals(QuestStatus.COMPLETED, goal(s, "squat").status)
        // proportional: +20 % → ~20 % of XP
        val p = goal(Fx.state(), "push").let { it.copy(stretch = it.target + it.target / 5) }
        assertEquals((p.xp * ((p.stretch - p.target).toDouble() / p.target)).roundToInt().coerceAtLeast(2), GoalPlan.stretchXp(p))
    }

    @Test fun onlyTheSystemLowersAfterPainOrIllness() {
        var s = Fx.state()
        val push = goal(s, "push"); val run = goal(s, "run")
        s = GameEngine.editGoal(s, push.id, "", push.target + 10, mon, Fx.NOW).state
        // check-in with notable pain → GOAL rows lowered to the base movement at ~50 %, raise cleared
        s = GameEngine.checkIn(s, CheckIn(mon, 7f, 3, 3, 2), mon, Fx.NOW).state
        val p = goal(s, "push")
        assertTrue(p.target < push.target); assertEquals(0, p.stretch); assertEquals("", p.variant)
        assertTrue(goal(s, "run").target < run.target)
        // idempotent: a second report does not halve again
        val again = GameEngine.checkIn(s, CheckIn(mon, 7f, 3, 3, 3), mon, Fx.NOW).state
        assertEquals(p.target, goal(again, "push").target)
        // illness excuse on one row lowers the others
        var t = Fx.state()
        val sq = goal(t, "squat")
        t = GameEngine.setReason(t, goal(t, "situp").id, MissReason.SICK, mon, Fx.NOW).state
        assertEquals(QuestStatus.EXCUSED, goal(t, "situp").status)
        assertTrue(goal(t, "squat").target < sq.target)
    }
}
