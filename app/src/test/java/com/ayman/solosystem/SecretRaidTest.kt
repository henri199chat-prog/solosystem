package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Raids
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.rewards.Loot
import com.ayman.solosystem.domain.rewards.Rarity
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.rewards.Rewards
import org.junit.Assert.*
import org.junit.Test

class SecretRewardFlowTest {
    private val mon = Fx.MON
    private val ds = Fx.NOW - 3 * 3_600_000L
    private fun secret(s0: AppState = Fx.state()): Pair<AppState, List<UiEffect>> {
        var s = s0; val e = mutableListOf<UiEffect>()
        s0.quests.filter { it.day == mon && GoalPlan.isGoal(it) }.forEach { q -> val o = GameEngine.logGoal(s, q.id, q.target * 2, mon, Fx.NOW, dayStartMs = ds); s = o.state; e += o.effects }
        return s to e
    }

    @Test fun sequenceInfoThenNoticeWithGoalRows() {
        val (s, e) = secret()
        val iq = e.indexOfFirst { it is UiEffect.SecretQuest }; val ir = e.indexOfFirst { it is UiEffect.SecretReward }
        assertTrue(iq >= 0 && ir > iq)                                            // QUEST INFO first, then the notice
        val info = e[iq] as UiEffect.SecretQuest
        assertEquals(4, info.rows.size)
        info.rows.forEach { assertEquals(it.target * 2, it.progress) }          // [200/100]
        assertFalse((e[ir] as UiEffect.SecretReward).direct)
        assertTrue(s.secretClaims[mon]!!.points in 3..10)
    }

    @Test fun claimGrantsHealPointsAndChosenBoxOnce() {
        val (s0, _) = secret()
        val pts = s0.secretClaims[mon]!!.points
        val before = Ledger.pointsAvailable(s0)
        val o = GameEngine.claimSecret(s0, mon, "blessed", mon, Fx.NOW)
        val s = o.state
        assertTrue(s.secretClaims[mon]!!.claimed); assertEquals("blessed", s.secretClaims[mon]!!.choice)
        assertEquals(1, s.inventory["token_fullheal"]); assertEquals(0, s.inventory["box_blessed"])      // box opened at once
        assertEquals(before + pts, Ledger.pointsAvailable(s))
        val lo = o.effects.filterIsInstance<UiEffect.LootOpened>().single(); assertEquals("box_blessed", lo.box.id)
        // idempotent
        val o2 = GameEngine.claimSecret(s, mon, "cursed", mon, Fx.NOW)
        assertEquals(s.copy(), o2.state); assertNull(o2.state.inventory["box_cursed"])
        assertEquals(pts, o2.state.bonusPoints)
        // the cursed choice opens the cursed box
        val c = GameEngine.claimSecret(s0, mon, "cursed", mon, Fx.NOW)
        assertEquals("box_cursed", c.effects.filterIsInstance<UiEffect.LootOpened>().single().box.id)
    }

    @Test fun blessedIsSafeCursedHasHigherLegendaryAndCurses() {
        fun run(box: String, n: Int): Triple<Int, Int, AppState> {
            var s = Fx.state().copy(inventory = mapOf(box to n)); var leg = 0
            repeat(n) { val o = Rewards.openBoxEx(s, box, mon, 0)!!; s = o.first
                val r = if (o.second.rewardId == "key_raid") Rarity.LEGENDARY else RewardCatalog.get(o.second.rewardId)!!.rarity; if (r == Rarity.LEGENDARY) leg++ }
            return Triple(leg, s.rewardLog.count { it.rewardId == "curse_iron" }, s)
        }
        val (lb, cb, sb) = run("box_blessed", 400); val (lc, cc, sc) = run("box_cursed", 400)
        assertEquals(0, cb); assertTrue(sb.curseDays.isEmpty())
        assertTrue("cursed legendary $lc > blessed $lb", lc > lb)
        assertTrue(cc in 60..180)
        // keys only come from legendary rolls, rank = tier + 1, expire after 7 days; never food-related curse ids
        (sb.raidKeys + sc.raidKeys).forEach { assertEquals(1, it.rank); assertEquals(it.day + 7, it.expiresDay) }
        assertTrue(sc.raidKeys.isNotEmpty())
        assertFalse(RewardCatalog.get("curse_iron")!!.flavour.en.contains("eat", true) && !RewardCatalog.get("curse_iron")!!.flavour.en.contains("Never food"))
        // pity respected
        val p = Fx.state().copy(inventory = mapOf("box_blessed" to 1), loot = LootState(sinceLegendary = Loot.PITY_LEGENDARY - 1))
        val pg = Rewards.openBoxEx(p, "box_blessed", mon)!!
        assertTrue(pg.second.rewardId == "key_raid" || RewardCatalog.get(pg.second.rewardId)!!.rarity == Rarity.LEGENDARY)
        assertEquals(0, pg.first.loot.sinceLegendary)
        // normal boxes never drop keys, curses or the heal token
        var n = Fx.state().copy(inventory = mapOf("box_legendary" to 200))
        repeat(200) { n = Rewards.openBoxEx(n, "box_legendary", mon)!!.first }
        assertTrue(n.raidKeys.isEmpty()); assertTrue(n.rewardLog.none { it.rewardId in setOf("key_raid", "curse_iron", "token_fullheal") })
    }

    @Test fun curseRaisesTargetsWithinCapsAndNeverOnLightDays() {
        val s = Fx.state()
        val d = (mon..mon + 6).first { GoalPlan.lightDay(s, it) == 1.0 }
        GoalPlan.Row.entries.forEach { r ->
            val base = GoalPlan.target(s, r, d); val cur = GoalPlan.target(s.copy(curseDays = setOf(d)), r, d)
            assertTrue("$r $cur >= $base", cur >= base); assertTrue(cur <= maxOf(r.cap, base))
            if (r == GoalPlan.Row.RUN) GoalPlan.runEnergyCapM(s, d)?.let { assertTrue(cur <= it) }
        }
        val capped = s.copy(goalTracks = mapOf("push" to GoalTrack(100, 100, d)), curseDays = setOf(d))
        assertEquals(100, GoalPlan.target(capped, GoalPlan.Row.PUSH, d))
        val light = (mon..mon + 6).firstOrNull { GoalPlan.lightDay(s, it) < 1.0 }
        if (light != null) assertEquals(GoalPlan.target(s, GoalPlan.Row.PUSH, light), GoalPlan.target(s.copy(curseDays = setOf(light)), GoalPlan.Row.PUSH, light))
    }

    @Test fun fullRecoveryTokenMakesAFreeRestDay() {
        var s = Fx.state(); s = Rewards.grant(s, "t", "token_fullheal", mon, Txt("", "")).first
        val o = GameEngine.useToken(s, "token_fullheal", mon, Fx.NOW)
        assertEquals(0, o.state.inventory["token_fullheal"]); assertTrue(mon in o.state.shieldedDays)
        assertTrue(o.state.quests.filter { it.day == mon && GoalPlan.isGoal(it) }.all { it.status == QuestStatus.EXCUSED })
    }
}

class RaidTest {
    private val mon = Fx.MON
    private fun withKey(s0: AppState = Fx.state(), day: Long): Pair<AppState, RaidKey> {
        val k = RaidKey("k1", Raids.rankFor(s0), day, day + Raids.KEY_DAYS)
        return s0.copy(raidKeys = listOf(k)) to k
    }
    private fun trainDay(s: AppState) = (mon..mon + 6).first { GoalPlan.lightDay(s, it) == 1.0 }

    @Test fun raidIsOneTierAboveAndInsideSafetyCaps() {
        val s = Fx.state(); val d = trainDay(s)
        assertEquals(GoalPlan.tier(s) + 1, Raids.rankFor(s))
        val steps = Raids.steps(s, Raids.rankFor(s), d)
        assertEquals(4, steps.size)
        steps.forEach { st ->
            val r = GoalPlan.Row.entries.first { it.id == st.row }; val v = GoalPlan.variant(r, st.variant)
            assertTrue(v.tier <= Raids.rankFor(s))
            assertTrue("${st.row} ${GoalPlan.intensity(v, st.amount)}", GoalPlan.intensity(v, st.amount) <= r.cap + 1e-6)
            if (r == GoalPlan.Row.RUN) GoalPlan.runEnergyCapM(s, d)?.let { assertTrue(GoalPlan.intensity(v, st.amount) <= it + 1e-6) }
            assertTrue("harder than the daily target", GoalPlan.intensity(v, st.amount) >= minOf(GoalPlan.track(s, r, d).current.toDouble(), r.cap.toDouble()) - 1e-6)
        }
        // limitations respected: knee → no lunges/split/jump/stairs
        val knee = Fx.state(Fx.profile(lim = setOf(Limitation.KNEE)))
        assertTrue(Raids.steps(knee, 2, d).none { it.variant in setOf("lunge", "split", "jump", "stairs", "intervals", "sprints") })
    }

    @Test fun clearGivesBigXpRareLootAndRecord() {
        val (s0, k) = withKey(day = trainDay(Fx.state())); val d = k.day
        var s = GameEngine.enterRaid(s0, k.id, d, Fx.NOW).state
        assertTrue(s.raidKeys.single().used); assertNotNull(Raids.active(s))
        s.raids.single().steps.indices.forEach { s = GameEngine.raidStep(s, it) }
        val o = GameEngine.finishRaid(s, d, Fx.NOW + 20 * 60_000L)
        val r = o.state.raids.single()
        assertTrue(r.cleared); assertEquals(Raids.xpFor(k.rank), r.xp)
        assertTrue(o.state.ledger.any { it.key == r.id && it.xp == Raids.xpFor(k.rank) })
        assertEquals(1, o.state.inventory["box_epic"])
        assertTrue(o.state.history.any { it.kind == HistoryKind.RAID && it.xpDelta > 0 })
        assertTrue(o.effects.any { it is UiEffect.RaidResult && it.raid.cleared })
        assertNull(Raids.active(o.state))
        // key cannot be reused
        assertTrue(GameEngine.enterRaid(o.state, k.id, d, Fx.NOW).state.raids.size == 1)
    }

    @Test fun failingOrLeavingHasNoPenalty() {
        val (s0, k) = withKey(day = trainDay(Fx.state())); val d = k.day
        var s = GameEngine.enterRaid(s0, k.id, d, Fx.NOW).state
        s = GameEngine.raidStep(s, 0)
        fun noPenalty(a: AppState) {
            assertEquals(s0.ledger.sumOf { it.xp }, a.ledger.sumOf { it.xp }); assertEquals(s0.ledger.sumOf { it.gold }, a.ledger.sumOf { it.gold })
            assertEquals(s0.bonus, a.bonus); assertEquals(s0.penaltyTier, a.penaltyTier)
            assertEquals(s0.quests.count { it.type == QuestType.PENALTY }, a.quests.count { it.type == QuestType.PENALTY })
        }
        val fail = GameEngine.finishRaid(s, d, Fx.NOW + 10 * 60_000L); assertFalse(fail.state.raids.single().cleared); noPenalty(fail.state)
        // all done but after the timer → not cleared, still no penalty
        var late = s; late.raids.single().steps.indices.drop(1).forEach { late = GameEngine.raidStep(late, it) }
        val lo = GameEngine.finishRaid(late, d, Fx.NOW + 200 * 60_000L); assertFalse(lo.state.raids.single().cleared); noPenalty(lo.state)
        // pain stops it, no penalty, safety window
        val p = GameEngine.finishRaid(late, d, Fx.NOW + 5 * 60_000L, pain = true)
        assertFalse(p.state.raids.single().cleared); noPenalty(p.state); assertTrue(p.effects.any { it is UiEffect.Safety })
    }

    @Test fun gateRespectsRestPainExpiryAndOncePerDay() {
        val base = Fx.state()
        val td = trainDay(base)
        val (s, k) = withKey(base, td)
        assertTrue(GameEngine.raidGate(s, k.id, td).effects.single() is UiEffect.RaidGate)
        val pain = s.copy(history = s.history + HistoryEvent(Fx.NOW, td, HistoryKind.PAIN, Txt("", "")))
        assertTrue(GameEngine.raidGate(pain, k.id, td).effects.single() is UiEffect.Toast)
        assertTrue(GameEngine.enterRaid(pain, k.id, td, Fx.NOW).state.raids.isEmpty())
        assertFalse(GameEngine.enterRaid(pain, k.id, td, Fx.NOW).state.raidKeys.single().used)    // key kept
        (mon..mon + 6).firstOrNull { GoalPlan.lightDay(base, it) < 1.0 }?.let { rest ->
            val (rs, rk) = withKey(base, rest); assertNotNull(Raids.blockReason(rs, rest)); assertTrue(GameEngine.enterRaid(rs, rk.id, rest, Fx.NOW).state.raids.isEmpty())
        }
        assertTrue(Raids.activeKeys(s, td + 7).isNotEmpty()); assertTrue(Raids.activeKeys(s, td + 8).isEmpty())   // 7-day expiry
        val two = s.copy(raidKeys = s.raidKeys + k.copy(id = "k2"))
        var x = GameEngine.enterRaid(two, "k1", td, Fx.NOW).state; x = GameEngine.finishRaid(x, td, Fx.NOW + 1000).state
        assertTrue(GameEngine.enterRaid(x, "k2", td, Fx.NOW).state.raids.size == 1)                // one raid per day
    }

    @Test fun systemProposesTierAssessmentAfterClears() {
        val s = Fx.state().copy(secretDays = setOf(mon - 3, mon - 1))
        assertTrue(GameEngine.proposeAssess(s, mon).single() is UiEffect.AssessPropose)
        assertTrue(GameEngine.proposeAssess(GameEngine.markAssessProposed(s, mon), mon).isEmpty())
        assertTrue(GameEngine.proposeAssess(Fx.state().copy(secretDays = setOf(mon)), mon).isEmpty())
    }
}

class LevelUpSourceTest {
    @Test fun goalQuestXpIsLabelledDailyQuest() {
        val s0 = Fx.state(); var s = s0
        s0.quests.filter { it.day == Fx.MON && GoalPlan.isGoal(it) }.forEach { q -> s = GameEngine.logGoal(s, q.id, q.target, Fx.MON, Fx.NOW, dayStartMs = Fx.NOW - 3 * 3_600_000L).state }
        val src = GameEngine.levelSource(s0, s)
        assertEquals("المهمة اليومية", src?.ar)
    }
    @Test fun noNewXpMeansNoSource() {
        val s = Fx.state()
        assertNull(GameEngine.levelSource(s, s))
    }
}
