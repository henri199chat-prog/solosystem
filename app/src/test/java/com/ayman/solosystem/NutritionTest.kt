package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.nutrition.Nutrition
import com.ayman.solosystem.data.StateStore
import org.junit.Assert.*
import org.junit.Test

class NutritionTest {
    private val base = Nutrition.Inputs(70.0, 178.0, 24, Sex.MALE, ActivityLevel.LIGHT, 3, 45, 1)

    @Test fun mifflinStJeorBmr() {
        assertEquals(10 * 70 + 6.25 * 178 - 5 * 24 + 5, Nutrition.bmr(70.0, 178.0, 24, Sex.MALE), 1e-9)   // 1697.5
        assertEquals(10 * 60 + 6.25 * 165 - 5 * 30 - 161, Nutrition.bmr(60.0, 165.0, 30, Sex.FEMALE), 1e-9)
    }

    @Test fun leanBulkTargetsAreSafeAndConsistent() {
        val t = Nutrition.targets(base)
        assertTrue(t.surplus in 300..500)
        assertTrue("never below maintenance", t.kcal > t.tdee)
        assertTrue(t.kcal - t.tdee <= Nutrition.MAX_SURPLUS + 10)
        assertEquals(126, t.protein)                               // 1.8 g/kg at 3 days
        assertTrue(t.protein / 70.0 in 1.6..2.2)
        assertTrue(t.fat / 70.0 >= 0.8 - 1e-9 && t.fat * 9 >= t.kcal * 0.2 - 9)
        assertEquals(t.kcal.toDouble(), (t.protein * 4 + t.fat * 9 + t.carbs * 4).toDouble(), 6.0)
        assertEquals(2450, t.waterMl)
        assertEquals(2950, Nutrition.targets(base, trainingToday = true).waterMl)
    }

    @Test fun trainingLoadAndLevelRaiseMaintenance() {
        val light = Nutrition.targets(base)
        val heavy = Nutrition.targets(base.copy(trainingDays = 5, sessionMin = 60))
        val higherLevel = Nutrition.targets(base.copy(planLevel = 5))
        assertTrue(heavy.tdee > light.tdee + 100)
        assertTrue(higherLevel.tdee > light.tdee)
        assertEquals(140, heavy.protein)                            // 2.0 g/kg at 5 days
    }

    @Test fun surplusIsClampedEvenWithLargeAdjustments() {
        assertEquals(Nutrition.MAX_SURPLUS, Nutrition.targets(base, kcalAdjust = 5000).surplus)
        assertEquals(Nutrition.MIN_SURPLUS, Nutrition.targets(base, kcalAdjust = -5000).surplus)
        assertTrue(Nutrition.targets(base, kcalAdjust = -5000).kcal > Nutrition.targets(base).tdee)
    }

    @Test fun adjustmentSteps() {
        assertEquals(200, Nutrition.adjustmentFor(-0.2))
        assertEquals(150, Nutrition.adjustmentFor(0.1))
        assertEquals(0, Nutrition.adjustmentFor(0.35))
        assertEquals(-150, Nutrition.adjustmentFor(0.6))
        assertEquals(-200, Nutrition.adjustmentFor(1.0))
    }

    private fun withWeights(s: AppState, today: Long, perWeekKg: Double): AppState =
        s.copy(bodyWeights = (0..2).map { w -> WeightEntry(today - 14 + w * 7, 70.0 + perWeekKg * w) })

    @Test fun twoWeekReviewRaisesWhenTooSlowAndExplains() {
        val today = Fx.MON + 30
        val s = withWeights(Fx.state(), today, 0.0)   // flat for 2 weeks
        assertEquals(0.0, Nutrition.weeklyRatePct(s.bodyWeights, today)!!, 1e-9)
        val r = Nutrition.review(s, today)
        assertEquals(150, r.nutrition.kcalAdjust)
        assertEquals(today, r.nutrition.lastReviewDay)
        assertTrue(r.nutrition.adjustments.last().reason.en.contains("+150"))
        // no second change within 14 days
        assertEquals(r, Nutrition.review(r, today + 5))
        // XP/quests untouched
        assertEquals(s.ledger, r.ledger); assertEquals(s.quests, r.quests)
    }

    @Test fun reviewLowersWhenTooFastAndKeepsOnTarget() {
        val today = Fx.MON + 30
        val fast = Nutrition.review(withWeights(Fx.state(), today, 0.7), today)   // ~1%/wk
        assertEquals(-200, fast.nutrition.kcalAdjust)
        val ok = Nutrition.review(withWeights(Fx.state(), today, 0.25), today)    // ~0.36%/wk
        assertEquals(0, ok.nutrition.kcalAdjust)
        val notEnough = Fx.state().copy(bodyWeights = listOf(WeightEntry(today, 70.0)))
        assertEquals(notEnough, Nutrition.review(notEnough, today))
    }

    @Test fun mealTimingFollowsTrainingRules() {
        val t = Nutrition.targets(base)
        val o = Nutrition.schedule(t, wake = 7 * 60, bed = 23 * 60, trainStart = 18 * 60, sessionMin = 60)
        val pre = o.first { it.kind == Nutrition.Kind.PRE }
        assertEquals(18 * 60 - 150, pre.minute)
        val elixir = o.first { it.kind == Nutrition.Kind.ELIXIR }
        assertTrue(elixir.minute - (19 * 60) in 30..120)
        assertTrue(o.size in 4..6)
        assertTrue("no heavy meal in the 90 min before training", o.none { it.minute in (18 * 60 - 90) until 18 * 60 && it.kind == Nutrition.Kind.MEAL })
        assertEquals(t.protein.toDouble(), o.sumOf { it.protein }.toDouble(), 3.0)
        assertEquals(t.kcal.toDouble(), o.sumOf { it.kcal }.toDouble(), 5.0)
        // early session → light snack instead of a full pre meal
        val early = Nutrition.schedule(t, 7 * 60, 23 * 60, 9 * 60, 45)
        assertTrue(early.any { it.kind == Nutrition.Kind.PRE_SNACK } && early.none { it.kind == Nutrition.Kind.PRE })
        // rest day: 4–5 meals
        assertTrue(Nutrition.schedule(t, 7 * 60, 23 * 60, null, 45).size in 4..5)
    }

    @Test fun trackingStreakComplianceAndNoXp() {
        val s0 = Fx.state()
        val today = Fx.MON
        val t = Nutrition.targetsFor(s0, today)!!
        var s = s0
        listOf(today - 1, today).forEach { d -> s = Nutrition.addMeal(s, MealLog("m$d", d, Fx.NOW, Txt("x", "x"), t.kcal, t.protein.toDouble())) }
        assertEquals(2, Nutrition.streak(s, today))
        assertEquals(2, s.nutrition.bestStreak)
        assertTrue(Nutrition.compliance(Nutrition.totals(s, today), t, 5) >= 85)
        assertEquals(0, Nutrition.compliance(Nutrition.totals(s, today - 5), t, 5))
        assertEquals(s0.ledger, s.ledger); assertEquals(s0.bonus, s.bonus); assertEquals(s0.quests, s.quests)
        // implausible entries rejected; state round-trips
        assertEquals(s, Nutrition.addMeal(s, MealLog("bad", today, Fx.NOW, Txt("x", "x"), 99999, 1.0)))
        assertEquals(s.nutrition, StateStore.decode(StateStore.encode(s)).nutrition)
    }

    @Test fun warnsOnImplausibleInputs() {
        val p = Fx.profile()
        assertTrue(Nutrition.warnings(base, p).isEmpty())
        assertTrue(Nutrition.warnings(base.copy(kg = 45.0), p).isNotEmpty())   // BMI ~14
        assertTrue(Nutrition.warnings(null, p.copy(heightCm = null)).isNotEmpty())
        assertNotNull(GameEngine)
    }
}
