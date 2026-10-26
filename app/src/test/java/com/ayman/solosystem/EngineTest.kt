package com.ayman.solosystem

import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.training.Progression
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class EngineTest {
    private val day = Fx.MON
    @Test fun idempotentRewards() {
        val s0 = Fx.state()
        val q = s0.quests.first { it.day == day && it.templateId == "mobility" }
        val base = Ledger.totalXp(s0)
        val s1 = GameEngine.completeQuest(s0, q.id, day, Fx.NOW).state
        assertEquals(base + q.xp, Ledger.totalXp(s1))
        val s2 = GameEngine.completeQuest(s1, q.id, day, Fx.NOW).state // double tap
        assertEquals(Ledger.totalXp(s1), Ledger.totalXp(s2))
        val s3 = GameEngine.uncheckQuest(s2, q.id, day, Fx.NOW).state
        assertEquals(base, Ledger.totalXp(s3))
        val s4 = GameEngine.completeQuest(s3, q.id, day, Fx.NOW).state
        assertEquals(base + q.xp, Ledger.totalXp(s4))
        assertEquals(1, s4.ledger.count { it.key == "q:${q.id}" })
    }
    @Test fun workoutQuestCannotBeTicked() {
        val s0 = Fx.state()
        val t = s0.quests.first { it.verify == Verify.WORKOUT && it.day == day }
        assertEquals(QuestStatus.PENDING, GameEngine.completeQuest(s0, t.id, day, Fx.NOW).state.quests.first { it.id == t.id }.status)
    }
    @Test fun missedVsExcused() {
        val s0 = Fx.state()
        val next = GameEngine.rollover(s0, day + 1, Fx.NOW, 0).state
        val missed = next.quests.filter { it.day == day && it.status == QuestStatus.MISSED }
        assertTrue(missed.isNotEmpty())
        assertTrue(next.bonus.any { it.key.startsWith("loss:") })
        val xpBefore = Ledger.totalXp(next)
        val ex = GameEngine.setReason(next, missed.first().id, MissReason.SICK, day + 1, Fx.NOW).state
        assertEquals(QuestStatus.EXCUSED, ex.quests.first { it.id == missed.first().id }.status)
        assertFalse(ex.bonus.any { it.key == "loss:${missed.first().id}" })
        assertEquals(xpBefore, Ledger.totalXp(ex)) // earned XP never removed
        val lazy = GameEngine.setReason(next, missed[1].id, MissReason.entries.first { !it.excusable }, day + 1, Fx.NOW).state
        assertEquals(QuestStatus.MISSED, lazy.quests.first { it.id == missed[1].id }.status)
    }
    @Test fun workoutLogAndProgressionRule() {
        var s = Fx.state(Fx.profile(eq = setOf(Equipment.FULL_GYM), days = 3))
        var d = day
        val exId: String
        run {
            s = GameEngine.startSession(s, d, Fx.NOW).state
            val w = s.sessions.last { !it.finished }
            exId = w.entries.first().exerciseId
        }
        repeat(2) { i ->
            if (i > 0) { d += 7; s = GameEngine.rollover(s, d, Fx.NOW, 0).state; s = GameEngine.startSession(s, d, Fx.NOW + i).state }
            val w = s.sessions.last { !it.finished }
            w.entries.forEachIndexed { ei, e -> e.sets.indices.forEach { si -> s = GameEngine.updateSet(s, w.id, ei, si, SetLog(e.prescription.repMax, 40.0, 8, true)) } }
            s = GameEngine.finishSession(s, w.id, d, Fx.NOW + i).state
        }
        assertNotNull(s.progress[exId]?.suggestion)
        assertTrue(s.quests.any { it.verify == Verify.WORKOUT && it.status == QuestStatus.COMPLETED })
    }
    @Test fun rpeTooHighNoProgress() {
        val pr = Prescription(0, "goblet_squat", 3, 8, 12, 90)
        val e = SessionEntry(pr, pr.exerciseId, List(3) { SetLog(12, 20.0, 9, true) })
        assertFalse(Progression.isSuccess(e, null))
        assertTrue(Progression.isSuccess(e.copy(sets = List(3) { SetLog(12, 20.0, 8, true) }), null))
    }
    @Test fun persistenceRoundTrip() {
        var s = Fx.state()
        s = GameEngine.completeQuest(s, s.quests.first { it.templateId == "mobility" }.id, day, Fx.NOW).state
        val dir = Files.createTempDirectory("solo").toFile()
        val store = StateStore(dir); store.save(s)
        val back = store.load()
        assertEquals(s.copy(schemaVersion = StateStore.SCHEMA), back)
        assertEquals(Ledger.totalXp(s), Ledger.totalXp(back))
    }
    @Test fun noFakeMissesForUnopenedDays() {
        val s = GameEngine.rollover(Fx.state(), day + 5, Fx.NOW, 0).state
        assertTrue(s.quests.none { it.day in (day + 1)..(day + 4) })
    }
}
