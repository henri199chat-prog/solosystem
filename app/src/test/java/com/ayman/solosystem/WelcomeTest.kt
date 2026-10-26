package com.ayman.solosystem

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.welcome.Welcome
import com.ayman.solosystem.domain.welcome.Welcome.Cat
import com.ayman.solosystem.domain.welcome.Welcome.QuestState
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class WelcomeTest {
    private fun ctx(firstEver: Boolean = false, hour: Int = 9, opens: Int = 1, awayMs: Long? = 3_600_000L, streak: Int = 2,
                    quest: QuestState = QuestState.NOT_STARTED, pct: Int = 0, left: Int = 600, penalty: Boolean = false, rest: Boolean = false,
                    sick: Boolean = false, lvlUp: Int? = null, rankUp: String? = null, level: Int = 5, monarch: Boolean = false, name: String? = null) =
        Welcome.Ctx(firstEver, hour, opens, awayMs, streak, quest, pct, left, penalty, rest, sick, lvlUp, rankUp, level, monarch, name)

    @Test fun poolIsLargeAndEveryCategoryHasLines() {
        assertTrue(Welcome.totalLines() >= 60)
        Cat.entries.forEach { assertTrue(it.name, Welcome.POOL[it].orEmpty().isNotEmpty() && Welcome.EN.containsKey(it)) }
    }

    @Test fun contextMapsToCategoryByPriority() {
        assertEquals(Cat.FIRST_EVER, Welcome.category(ctx(firstEver = true, sick = true)))
        assertEquals(Cat.SICK, Welcome.category(ctx(sick = true, penalty = true)))
        assertEquals(Cat.PENALTY, Welcome.category(ctx(penalty = true, awayMs = 5 * 86_400_000L)))
        assertEquals(Cat.ABSENCE_LONG, Welcome.category(ctx(awayMs = 3 * 86_400_000L + 5)))
        assertEquals(Cat.ABSENCE_DAY, Welcome.category(ctx(awayMs = 30 * 3_600_000L)))
        assertEquals(Cat.RANK_UP, Welcome.category(ctx(rankUp = "D", lvlUp = 25)))
        assertEquals(Cat.LEVEL_UP, Welcome.category(ctx(lvlUp = 6)))
        assertEquals(Cat.SECRET, Welcome.category(ctx(quest = QuestState.SECRET)))
        assertEquals(Cat.STREAK_MILESTONE, Welcome.category(ctx(quest = QuestState.COMPLETED, streak = 7)))
        assertEquals(Cat.COMPLETED, Welcome.category(ctx(quest = QuestState.COMPLETED, streak = 4)))
        assertEquals(Cat.REST, Welcome.category(ctx(rest = true)))
        assertEquals(Cat.RETURN_MANY, Welcome.category(ctx(opens = 4, quest = QuestState.IN_PROGRESS, pct = 40)))
        assertEquals(Cat.RETURN_AGAIN, Welcome.category(ctx(opens = 2)))
        assertEquals(Cat.MONARCH, Welcome.category(ctx(monarch = true, level = 120)))
        assertEquals(Cat.IN_PROGRESS_LATE, Welcome.category(ctx(quest = QuestState.IN_PROGRESS, pct = 60, left = 90)))
        assertEquals(Cat.IN_PROGRESS, Welcome.category(ctx(quest = QuestState.IN_PROGRESS, pct = 60)))
        assertEquals(Cat.NOT_STARTED_LATE, Welcome.category(ctx(left = 100)))
        assertEquals(Cat.NOT_STARTED_DAWN, Welcome.category(ctx(hour = 5)))
        assertEquals(Cat.NOT_STARTED_MORNING, Welcome.category(ctx(hour = 8)))
        assertEquals(Cat.NOT_STARTED_NOON, Welcome.category(ctx(hour = 13)))
        assertEquals(Cat.NOT_STARTED_EVENING, Welcome.category(ctx(hour = 17)))
        assertEquals(Cat.NOT_STARTED_NIGHT, Welcome.category(ctx(hour = 22)))
    }

    @Test fun neverRepeatsBackToBackAndAvoidsRecentHistory() {
        val rnd = Random(7)
        var m = Welcome.Memory()
        var last: String? = null
        repeat(300) { i ->
            val c = if (i % 2 == 0) ctx(hour = 8) else ctx(quest = QuestState.IN_PROGRESS, pct = 30)
            val w = Welcome.pick(c, m.recent, rnd)
            assertNotEquals(last, w.id)
            val sameCat = m.recent.filter { it.startsWith(w.cat.name + "#") }
            if (sameCat.size < Welcome.POOL.getValue(w.cat).size) assertFalse(w.id in m.recent)
            m = Welcome.remember(m, w, Fx.MON, Fx.NOW + i, 5, "E"); last = w.id
        }
        // a single-line pool can't repeat back-to-back either when 2+ lines exist: RANK_UP has 2
        var prev: String? = null
        var mm = Welcome.Memory()
        repeat(20) { val w = Welcome.pick(ctx(rankUp = "C"), mm.recent, rnd); assertNotEquals(prev, w.id); prev = w.id; mm = Welcome.remember(mm, w, Fx.MON, Fx.NOW, 5, "E") }
    }

    @Test fun placeholdersFilledAndGreetingByContext() {
        val w = Welcome.pick(ctx(awayMs = 3 * 86_400_000L), emptyList(), Random(1))
        assertTrue(w.lineAr.contains("3") && !w.lineAr.contains("{"))
        assertTrue(w.greetingAr.startsWith("مرحبًا بعودتك"))
        val k = Welcome.pick(ctx(monarch = true, level = 120), emptyList(), Random(1))
        assertTrue(k.greetingAr.contains("أيها الملك"))
        val n = Welcome.pick(ctx(name = "أيمن"), emptyList(), Random(1))
        assertTrue(n.greetingAr.contains("اللاعب أيمن"))
        val p = Welcome.pick(ctx(quest = QuestState.IN_PROGRESS, pct = 42, left = 125), emptyList(), Random(3))
        assertFalse(p.lineAr.contains("{")); assertFalse(p.lineEn.contains("{"))
    }

    @Test fun contextFromGameState() {
        var s = Fx.state()
        val today = Fx.MON
        val first = Welcome.context(s, Welcome.Memory(), today, Fx.NOW, 9 * 60)
        assertEquals(QuestState.NOT_STARTED, first.quest); assertEquals(1, first.opensToday); assertNull(first.awayMs)
        val push = s.quests.first { it.day == today && it.templateId == "goal:push" }
        s = com.ayman.solosystem.domain.game.GameEngine.logGoal(s, push.id, push.target, today, Fx.NOW).state
        val m = Welcome.Memory(lastSeenMs = Fx.NOW - 2 * 3_600_000L, day = today, opensToday = 2, lastLevel = 1, lastRank = "E")
        val c = Welcome.context(s, m, today, Fx.NOW, 20 * 60)
        assertEquals(QuestState.IN_PROGRESS, c.quest); assertEquals(25, c.questPct); assertEquals(3, c.opensToday)
        assertEquals(240, c.minutesLeft); assertEquals(2 * 3_600_000L, c.awayMs)
        assertEquals("Ayman", c.name)
        assertEquals(Cat.RETURN_MANY, Welcome.category(c))
    }
}
