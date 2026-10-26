package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Metric
import com.ayman.solosystem.domain.game.Metrics
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.nutrition.Nutrition
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.rewards.Loot
import com.ayman.solosystem.domain.rewards.Rarity
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.rewards.Rewards
import org.junit.Assert.*
import org.junit.Test

class SecretQuestTest {
    private val mon = Fx.MON
    private val dayStart = Fx.NOW - 3 * 3_600_000L          // 3 h into the ring window (deadline = +24 h)
    private fun goals(s: AppState) = s.quests.filter { it.day == mon && GoalPlan.isGoal(it) }

    /** Logs [factor]× the ORIGINAL target on every row (last row gets [lastDelta] extra). */
    private fun log(s0: AppState, factor: Int, lastDelta: Int = 0, ds: Long = dayStart): Pair<AppState, List<UiEffect>> {
        var s = s0; val eff = mutableListOf<UiEffect>()
        goals(s0).forEachIndexed { i, q ->
            val o = GameEngine.logGoal(s, q.id, q.target * factor - q.progress + (if (i == 3) lastDelta else 0), mon, Fx.NOW, dayStartMs = ds)
            s = o.state; eff += o.effects
        }
        return s to eff
    }

    @Test fun exactly200PercentConvertsAndGrantsSecretChest() {
        val (below, e0) = log(Fx.state(), 2, lastDelta = -100)            // run 100 m short of 200 %
        assertTrue(mon !in below.secretDays); assertTrue(e0.none { it is UiEffect.SecretQuest })
        val (s, e) = log(Fx.state(), 2)                                      // exactly 200 %
        assertTrue(mon in s.secretDays)
        assertEquals(1, e.count { it is UiEffect.SecretQuest }); assertEquals(1, e.count { it is UiEffect.SecretReward })
        assertEquals(1, s.secretClaims.size); assertFalse(s.secretClaims[mon]!!.claimed)
        assertTrue(s.history.any { it.kind == HistoryKind.SECRET })
        assertEquals(1, Metrics.compute(s, mon)[Metric.SECRET_QUESTS])
        // raising the amount does NOT move the bar: 200 % of the original target still counts
        var r = Fx.state(); val push = goals(r).first()
        r = GameEngine.editGoal(r, push.id, "", push.target * 3, mon, Fx.NOW).state
        assertTrue(mon in log(r, 2).first.secretDays)
    }

    @Test fun afterDeadlineNoConversion() {
        val (s, e) = log(Fx.state(), 2, ds = Fx.NOW - 25 * 3_600_000L)
        assertTrue(mon !in s.secretDays); assertTrue(e.none { it is UiEffect.SecretQuest || it is UiEffect.SecretReward })
        val short = Fx.state(settings = Settings(deadlineMinutes = 120))
        assertTrue(mon !in log(short, 2).first.secretDays)                  // 3 h in, 2 h window → expired
    }

    @Test fun idempotentOncePerDayAndPermanent() {
        var (s, _) = log(Fx.state(), 2)
        val q = goals(s).first()
        val o1 = GameEngine.logGoal(s, q.id, 10, mon, Fx.NOW, dayStartMs = dayStart); s = o1.state
        assertTrue(o1.effects.none { it is UiEffect.SecretQuest || it is UiEffect.SecretReward })
        s = GameEngine.logGoal(s, q.id, -q.progress + q.target, mon, Fx.NOW, dayStartMs = dayStart).state   // drop under 200 %
        assertTrue("permanent", mon in s.secretDays)
        val o2 = GameEngine.logGoal(s, q.id, q.target, mon, Fx.NOW, dayStartMs = dayStart); s = o2.state      // back to 200 %
        assertTrue(o2.effects.none { it is UiEffect.SecretReward })
        assertEquals(1, s.secretClaims.size); assertEquals(1, s.grantedKeys.count { it == "secret:$mon" })
    }

    @Test fun painStopsAndLoweredDaysAreNotEligible() {
        var s = Fx.state()
        s = GameEngine.goalPain(s, goals(s).first().id, mon, Fx.NOW).state
        assertTrue(mon !in log(s, 2).first.secretDays)
        var t = Fx.state()
        t = GameEngine.checkIn(t, CheckIn(mon, 7f, 3, 3, 2), mon, Fx.NOW).state
        assertTrue(goals(t).all { it.lowered })
        assertTrue(mon !in log(t, 2).first.secretDays)
    }

    @Test fun overCapShowsListenToYourBodyAndAddsRunKcal() {
        // a big run track: 200 % of the run target is past the energy cap → still converts, overCap note, kcal added back
        var s = Fx.state()
        val base = Nutrition.targetsFor(s, mon)!!.kcal
        val (c, e) = log(s, 2)
        assertTrue(mon in c.secretDays)
        val run = goals(c).first { it.templateId == "goal:run" }
        val cap = GoalPlan.runEnergyCapM(c, mon)!!
        val over = run.target * 2 > cap || GoalPlan.Row.entries.any { r -> goals(c).first { it.templateId == "goal:${r.id}" }.target * 2 > r.cap }
        assertEquals(over, (e.first { it is UiEffect.SecretQuest } as UiEffect.SecretQuest).overCap)
        assertTrue(Nutrition.targetsFor(c, mon)!!.kcal > base)              // doubled km → more kcal today
        // force a case past the energy cap
        s = s.copy(goalTracks = s.goalTracks + ("run" to GoalTrack(1500, 4000, mon)))
        s = s.copy(quests = s.quests.filterNot { it.day == mon && GoalPlan.isGoal(it) } + GoalPlan.quests(s, mon))
        val (c2, e2) = log(s, 2)
        assertTrue(mon in c2.secretDays)
        assertTrue((e2.first { it is UiEffect.SecretQuest } as UiEffect.SecretQuest).overCap)
    }

    @Test fun secretChestHasItsOwnTableExclusivesAndRespectsPity() {
        var s = Fx.state().copy(inventory = mapOf("box_secret" to 400))
        val got = mutableListOf<String>(); var commons = 0
        repeat(400) {
            val (n, g) = Rewards.openBox(s, "box_secret", mon)!!; s = n
            got += g.rewardId
            if (RewardCatalog.get(g.rewardId)!!.rarity == Rarity.COMMON) commons++
        }
        assertEquals(0, commons)                                              // never common
        assertTrue(got.any { it.startsWith("title_secret_") })                 // exclusives obtainable here
        // exclusives never appear in normal boxes
        var n0 = Fx.state().copy(inventory = mapOf("box_legendary" to 300))
        repeat(300) { val (n, g) = Rewards.openBox(n0, "box_legendary", mon)!!; n0 = n; assertFalse(g.rewardId.startsWith("title_secret_")) }
        // pity: 40th box without legendary is legendary, secret chest included
        val p = Fx.state().copy(inventory = mapOf("box_secret" to 1), loot = LootState(sinceLegendary = Loot.PITY_LEGENDARY - 1))
        val (pn, pg) = Rewards.openBox(p, "box_secret", mon)!!
        assertEquals(Rarity.LEGENDARY, RewardCatalog.get(pg.rewardId)!!.rarity); assertEquals(0, pn.loot.sinceLegendary)
        // odds: epic+ share much higher than a common box
        val w = Loot.secretWeights(0); val c = Loot.weights(Rarity.COMMON, 0)
        assertTrue(w[2] + w[3] >= 5 * (c[2] + c[3]))
    }
}

class SmartRunTest {
    private val mon = Fx.MON
    private fun meals(s: AppState, kcalPerDay: Int, days: LongRange) = s.copy(nutrition = s.nutrition.copy(meals = days.flatMap { d ->
        (1..3).map { i -> MealLog("m$d$i", d, 0, Txt("x", "x"), kcalPerDay / 3, 40.0) } }))

    @Test fun runEnergyIsAddedBackToTodaysElixirTarget() {
        val s = Fx.state()
        val base = Nutrition.baseTargetsFor(s, mon)!!
        val t = Nutrition.targetsFor(s, mon)!!
        val run = s.quests.first { it.day == mon && it.templateId == "goal:run" }
        assertEquals(Nutrition.runKcal(75.0, run.target), t.runKcal)          // ~1 kcal/kg/km: 75 kg × 1.5 km ≈ 113
        assertEquals(113, t.runKcal)
        assertTrue(t.kcal >= base.kcal + 100 && t.carbs > base.carbs)
        assertTrue("never below maintenance", t.kcal > base.tdee)
    }

    @Test fun energyCapFollowsIntake() {
        val s = Fx.state()
        val unknown = GoalPlan.runEnergyCapM(s, mon)!!                       // no logs: burn ≤ 1× surplus (350/75 → 4.6 km)
        assertEquals(4600, unknown)
        val tgt = Nutrition.baseTargetsFor(s, mon)!!.kcal
        val eating = GoalPlan.runEnergyCapM(meals(s, tgt + 200, (mon - 7)..(mon - 1)), mon)!!
        val under = GoalPlan.runEnergyCapM(meals(s, (tgt * 0.7).toInt(), (mon - 7)..(mon - 1)), mon)!!
        assertTrue(eating > unknown && under < unknown)
        assertEquals(9300, eating); assertEquals(2300, under)
        assertNotNull(GoalPlan.runCapReason(meals(s, (tgt * 0.7).toInt(), (mon - 7)..(mon - 1)), mon))
        // the target is clamped by the cap
        val big = s.copy(goalTracks = s.goalTracks + ("run" to GoalTrack(1500, 8000, mon)))
        assertEquals(2300, GoalPlan.target(meals(big, (tgt * 0.7).toInt(), (mon - 7)..(mon - 1)), GoalPlan.Row.RUN, mon))
    }

    @Test fun runRampFollowsWeightTrendWithReasonAndOtherRowsUnchanged() {
        var s = Fx.state()
        val done = GoalPlan.Row.entries.flatMap { r -> (1..5).map { d -> s.quests.first { it.day == mon && it.templateId == "goal:${r.id}" }.copy(id = "x${r.id}$d", day = mon + d, status = QuestStatus.COMPLETED) } }
        s = s.copy(quests = s.quests + done)
        val start = s.goalTracks["run"]!!.current; val push = s.goalTracks["push"]!!.current
        val day = mon + 7
        fun w(vararg kg: Double) = kg.mapIndexed { i, k -> WeightEntry(day - 13 + i * 6L, k) }
        // on target (+0.4 %/wk) → +0.5 km
        val up = GoalPlan.ramp(s.copy(bodyWeights = w(75.0, 75.3, 75.6)), day).goalTracks
        assertEquals(start + 500, up["run"]!!.current); assertTrue(up["run"]!!.note!!.en.startsWith("+"))
        // stalled → −0.5 km with reason (never below 500 m)
        val down = GoalPlan.ramp(s.copy(bodyWeights = w(75.0, 75.0, 74.95)), day).goalTracks
        assertEquals(start - 500, down["run"]!!.current); assertTrue(down["run"]!!.note!!.en.contains("stalled"))
        // slow gain → hold; no trend → hold
        assertEquals(start, GoalPlan.ramp(s.copy(bodyWeights = w(75.0, 75.1, 75.2)), day).goalTracks["run"]!!.current)
        assertEquals(start, GoalPlan.ramp(s, day).goalTracks["run"]!!.current)
        // push/sit-ups/squats ignore the weight trend
        assertEquals(push + 15, down["push"]!!.current); assertEquals(push + 15, GoalPlan.ramp(s, day).goalTracks["push"]!!.current)
    }
}
