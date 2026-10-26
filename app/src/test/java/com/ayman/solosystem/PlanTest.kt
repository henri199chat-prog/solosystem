package com.ayman.solosystem

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.training.ExerciseLibrary
import com.ayman.solosystem.domain.training.PlanGenerator
import org.junit.Assert.*
import org.junit.Test

class PlanTest {
    private fun ids(p: PlayerProfile) = PlanGenerator.generate(p, Fx.MON, 6).week.flatMap { it.items }.map { ExerciseLibrary.get(it.exerciseId)!! }
    @Test fun libraryIsLarge() { assertTrue(ExerciseLibrary.all.size >= 40); assertEquals(ExerciseLibrary.all.size, ExerciseLibrary.all.map { it.id }.toSet().size) }
    @Test fun respectsEquipment() {
        val ex = ids(Fx.profile(eq = setOf(Equipment.NONE)))
        assertTrue(ex.isNotEmpty())
        assertTrue(ex.all { it.needs.isEmpty() })
    }
    @Test fun respectsInjuries() {
        val lim = setOf(Limitation.KNEE, Limitation.SHOULDER)
        val ex = ids(Fx.profile(eq = setOf(Equipment.FULL_GYM), lim = lim))
        assertTrue(ex.none { e -> e.avoid.any { it in lim } })
    }
    @Test fun daysPerWeek() {
        for (d in 2..6) {
            val plan = PlanGenerator.generate(Fx.profile(days = d, eq = setOf(Equipment.DUMBBELLS)), Fx.MON, 6)
            assertEquals(d, plan.week.count { it.isTraining })
            assertEquals(7, plan.week.size)
        }
        assertEquals(Split.FULL_BODY, PlanGenerator.chooseSplit(Fx.profile(days = 3)))
    }
    @Test fun timeBudget() {
        val p = Fx.profile(min = 30, eq = setOf(Equipment.FULL_GYM))
        PlanGenerator.generate(p, Fx.MON, 6).week.filter { it.isTraining }.forEach { assertTrue(PlanGenerator.estimateSeconds(it.items) <= 30 * 60) }
    }
}
