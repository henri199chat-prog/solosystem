package com.ayman.solosystem

import com.ayman.solosystem.data.StateStore
import com.ayman.solosystem.domain.boss.Boss
import com.ayman.solosystem.domain.dungeon.Dungeon
import com.ayman.solosystem.domain.dungeon.Fix
import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.guild.Guild
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.shadows.Shadows
import com.ayman.solosystem.domain.transform.Transform
import org.junit.Assert.*
import org.junit.Test

class V13Test {
    private fun day(s: AppState, d: Long) = GameEngine.rollover(s, d, Fx.NOW + (d - Fx.MON) * 86_400_000L, 0L).state
    /** [n] consecutive days of finished GOAL rows starting at MON. */
    private fun streakDays(n: Int, skip: Set<Int> = emptySet()): Pair<AppState, Long> {
        var s = Fx.state()
        var d = Fx.MON
        for (i in 0 until n) {
            if (i > 0) { d = Fx.MON + i; s = day(s, d) }
            if (i !in skip) s = Fx.completeGoals(s, d)
        }
        return s to d
    }

    // ---------------------------------------------------------------- migration
    @Test fun real_1_2_2_save_decodes_with_v13_defaults_and_roundtrips() {
        val text = javaClass.classLoader!!.getResource("save_1_2_2.json")!!.readText()
        val s = StateStore.decode(text)
        assertEquals(StateStore.SCHEMA, s.schemaVersion)
        assertNotNull(s.profile)
        assertTrue(s.quests.isNotEmpty() && s.ledger.isNotEmpty())
        assertEquals(GuildState(), s.guild); assertEquals(ShadowState(), s.shadows)
        assertTrue(s.dungeonRuns.isEmpty() && s.bosses.isEmpty()); assertEquals(TransformState(), s.transform)
        // nothing earned is lost: XP ledger + nutrition meals identical to the raw decode
        val raw = StateStore.json.decodeFromString(AppState.serializer(), text)
        assertEquals(raw.ledger, s.ledger); assertEquals(raw.nutrition, s.nutrition); assertEquals(raw.inventory, s.inventory)
        assertEquals(s, StateStore.decode(StateStore.encode(s)))
    }

    // ---------------------------------------------------------------- guild
    @Test fun guild_scoring_and_ranks() {
        assertEquals(100, Guild.pushPts(60)); assertEquals(100, Guild.plankPts(180)); assertEquals(100, Guild.squatPts(80)); assertEquals(100, Guild.runPts(210)); assertEquals(0, Guild.runPts(600))
        assertEquals(0, Guild.rankFor(Guild.score(5, 20, 10, null)))
        assertEquals(5, Guild.rankFor(Guild.score(50, 180, 80, 220)))
        assertEquals("C", Guild.rankLabel(Guild.rankFor(Guild.score(20, 90, 40, null))))
        assertEquals(Guild.score(20, 90, 40, null), Guild.score(20, 90, 40, 0)) // 0 = no run test
    }
    @Test fun guild_monthly_cooldown_xp_once_and_pain_stop_has_no_penalty() {
        var s = Fx.state()
        s = Guild.record(s, 20, 60, 30, null, Fx.MON, Fx.NOW).state
        assertEquals(1, s.guild.tests.size); assertTrue(s.ledger.any { it.key == "guild:${Fx.MON}" })
        val again = Guild.record(s, 30, 60, 30, null, Fx.MON + 3, Fx.NOW)
        assertEquals(1, again.state.guild.tests.size)
        assertFalse(Guild.canTest(s, Fx.MON + 27)); assertTrue(Guild.canTest(s, Fx.MON + 28))
        val p = Guild.painStop(s, Fx.MON, Fx.NOW).state
        assertEquals(1, p.guild.painStops); assertEquals(s.ledger, p.ledger); assertEquals(s.quests, p.quests)
        assertTrue(Guild.licenseNo(s).matches(Regex("HL-\\d{6}")))
    }

    // ---------------------------------------------------------------- shadows
    @Test fun shadow_needs_30_day_streak_then_arise_and_xp_perk() {
        val (s29, d29) = streakDays(29)
        assertEquals(29, Shadows.streak(s29, "push", d29))
        assertTrue(Shadows.pending(s29, d29).isEmpty())
        assertTrue(Shadows.arise(s29, "push", d29, Fx.NOW).state.shadows.army.isEmpty())
        var s = day(s29, d29 + 1); val d = d29 + 1
        s = Fx.completeGoals(s, d)
        assertEquals(30, Shadows.streak(s, "push", d))
        assertTrue(Shadows.pending(s, d).any { it.source == "push" })
        assertTrue(Shadows.extractionsPossible(s, d) in 1..3)
        assertTrue("extraction offered once", s.shadows.offered.contains("push"))
        s = Shadows.arise(s, "push", d, Fx.NOW).state
        assertEquals(1, s.shadows.army.count { it.source == "push" })
        val q = s.quests.first { it.templateId == "goal:push" }
        assertEquals(Shadows.XP_PCT, Shadows.xpPct(s, q))
        assertEquals(0, Shadows.xpPct(s, s.quests.first { it.templateId == "goal:squat" }.let { it.copy(templateId = "goal:none") }))
    }
    @Test fun shadow_sleeps_on_break_and_revives_after_7_days() {
        var (s, d) = streakDays(30)
        s = Shadows.arise(s, "push", d, Fx.NOW).state
        d += 1; s = day(s, d)            // day 31 skipped (no goals)
        d += 1; s = day(s, d)            // rollover marks day 31 missed → shadow sleeps
        assertTrue(s.shadows.army.first().asleep)
        assertEquals(0, Shadows.xpPct(s, s.quests.first { it.templateId == "goal:push" && it.day == d }))
        repeat(7) { i -> if (i > 0) { d += 1; s = day(s, d) }; s = Fx.completeGoals(s, d) }
        assertFalse(s.shadows.army.first().asleep); assertEquals(1, s.shadows.army.first().revived)
    }
    @Test fun shadow_guard_and_freeze_once_per_month() {
        var (s, d) = streakDays(30)
        s = Shadows.arise(s, "push", d, Fx.NOW).state
        assertTrue(Shadows.freezeReady(s, d + 1))
        s = day(s, d + 1)
        val f = Shadows.freeze(s, d + 1, Fx.NOW).state
        assertTrue((d + 1) in f.shieldedDays)
        assertTrue(f.quests.filter { it.day == d + 1 && it.type == QuestType.DAILY }.none { it.status == QuestStatus.PENDING })
        assertFalse(Shadows.freezeReady(f, d + 2))
        // guard with nothing failed → blocked, unchanged
        assertEquals(f, Shadows.guard(f, d + 1, Fx.NOW).state)
    }

    // ---------------------------------------------------------------- boss
    @Test fun boss_spawns_takes_xp_damage_drops_one_rare_chest_and_never_penalises() {
        var s = Fx.state()
        val b0 = Boss.current(s, Fx.MON)!!
        assertEquals(Boss.maxHp(0), b0.maxHp)
        assertTrue(b0.damage > 0 || s.ledger.none { it.day == Fx.MON && it.xp > 0 })
        s = Fx.completeGoals(s, Fx.MON)
        assertTrue(Boss.current(s, Fx.MON)!!.damage > b0.damage)
        // grind XP until defeated
        s = s.copy(ledger = s.ledger + XpEntry("test:grind", 2000, 0, Fx.MON, Txt("x", "x")))
        s = GameEngine.post(s, s, Fx.MON, Fx.NOW, emptyList()).state
        val b = Boss.current(s, Fx.MON)!!
        assertNotNull(b.defeatedMs)
        assertTrue("boss:${Fx.MON}" in s.grantedKeys)
        val inv = s.inventory
        s = GameEngine.post(s, s, Fx.MON, Fx.NOW, emptyList()).state
        assertEquals("chest granted once", inv, s.inventory)
        // next week: new boss; an undefeated boss just leaves, no penalty quest
        var t = Fx.state(); val pBefore = t.quests.count { it.type == QuestType.PENALTY }
        t = GameEngine.rollover(t, Fx.MON + 7, Fx.NOW, 0L).state
        assertTrue(t.bosses.first { it.week == Fx.MON }.closed)
        assertNotNull(Boss.current(t, Fx.MON + 7))
        assertTrue(t.history.any { it.kind == HistoryKind.BOSS && it.text.en.contains("escaped") })
        assertTrue(t.quests.count { it.type == QuestType.PENALTY } >= pBefore) // penalties only from the normal missed-quest rules
        assertFalse(t.bonus.any { it.key.startsWith("boss") })
    }

    // ---------------------------------------------------------------- dungeon
    @Test fun tracker_filters_accuracy_jumps_and_jitter() {
        val tr = Dungeon.Tracker()
        val lat0 = 36.8; val lon0 = 10.18
        var t = 0L
        fun f(dLatM: Double, acc: Float = 5f) = Fix(lat0 + dLatM / 111_195.0, lon0, acc, t)
        tr.add(f(0.0))
        for (i in 1..100) { t += 2000; tr.add(f(i * 6.0)) }             // 600 m at 3 m/s
        assertEquals(600.0, tr.meters, 3.0)
        t += 2000; tr.add(f(600.5))                                      // jitter
        t += 2000; tr.add(f(5000.0))                                     // jump (2.2 km in 2 s)
        t += 2000; tr.add(f(5100.0, acc = 80f))                          // bad accuracy
        assertEquals(600.0, tr.meters, 3.0)
        assertEquals(333, Dungeon.paceSecPerKm(600, 200_000))
    }
    @Test fun dungeon_result_feeds_run_goal_and_clear_once() {
        var s = Fx.state()
        val gate = Dungeon.gates(s).first()
        val runQ = s.quests.first { it.day == Fx.MON && it.templateId == "goal:run" }
        val o = Dungeon.finish(s, gate, Fx.NOW, Fx.NOW + 5 * 60_000, gate.meters, Fx.MON, Fx.NOW + 5 * 60_000).state
        assertEquals(runQ.progress + gate.meters, o.quests.first { it.id == runQ.id }.progress)
        assertTrue(o.dungeonRuns.single().cleared); assertTrue(o.ledger.any { it.key == "dg:${Fx.NOW}" })
        assertEquals(o, Dungeon.finish(o, gate, Fx.NOW, Fx.NOW + 5 * 60_000, gate.meters, Fx.MON, Fx.NOW).state)
        val pain = Dungeon.finish(s, gate, Fx.NOW + 1, Fx.NOW + 60_000, 300, Fx.MON, Fx.NOW, pain = true).state
        assertFalse(pain.dungeonRuns.single().cleared)
        assertTrue(Dungeon.gates(s).all { it.meters >= 500 && it.limitMin >= 5 })
    }

    // ---------------------------------------------------------------- transform
    @Test fun transform_photo_meta_and_reminder() {
        var s = Fx.state()
        assertTrue(Transform.reminderDue(s, Fx.MON))
        s = Transform.add(s, "p1", "transform/p1.jpg", Fx.MON, Fx.NOW)
        assertFalse(Transform.reminderDue(s, Fx.MON + 6)); assertTrue(Transform.reminderDue(s, Fx.MON + 7))
        s = Transform.add(s, "p2", "transform/p2.jpg", Fx.MON + 30, Fx.NOW)
        assertEquals("p1", Transform.first(s)!!.id); assertEquals("p2", Transform.latest(s)!!.id)
        assertEquals(1, Transform.remove(s, "p1").transform.photos.size)
        assertTrue(Transform.setLock(s, true).transform.lock)
    }
}
