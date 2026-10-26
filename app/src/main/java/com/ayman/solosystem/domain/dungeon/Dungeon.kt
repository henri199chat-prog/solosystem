package com.ayman.solosystem.domain.dungeon

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Outcome
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.rewards.Rewards
import kotlin.math.*

/** A GPS fix (pure data so the tracker logic is unit-testable without Android). */
data class Fix(val lat: Double, val lon: Double, val accM: Float, val timeMs: Long)

/**
 * GPS Dungeon (Gate run): on-device GPS only (no network, no map tiles). Distance gates every [WAYPOINT_M] act as waypoints.
 * Result: metres are logged on today's RUN GOAL row (→ XP + the Elixir's run kcal add-back), a clear gives bonus XP + a chest. No penalty.
 */
object Dungeon {
    const val WAYPOINT_M = 250
    const val MAX_ACC_M = 30f
    /** Faster than this (m/s) between fixes = GPS jump, ignored. */
    const val MAX_SPEED = 9.0
    /** Smaller moves are treated as jitter while standing still. */
    const val MIN_STEP_M = 2.0

    data class Gate(val rank: Int, val meters: Int, val limitMin: Int)
    /** Gate options: distance scales with the GOAL run target (never more than ~1.5× of it), time limit = 9 min/km (walk-run friendly). */
    fun gates(s: AppState): List<Gate> {
        val base = (s.goalTracks["run"]?.current ?: 2000).coerceIn(1000, 10_000)
        return listOf(0.5, 0.75, 1.0, 1.25, 1.5).mapIndexed { i, f ->
            val m = ((base * f) / 250.0).roundToInt().coerceAtLeast(2) * 250
            Gate(i, m, ceil(m / 1000.0 * 9).toInt().coerceAtLeast(5))
        }
    }

    fun haversine(a: Fix, b: Fix): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat); val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return 2 * r * asin(min(1.0, sqrt(h)))
    }

    /** Pure accumulator: feed fixes, read [meters]. Filters bad accuracy, GPS jumps and stand-still jitter. */
    class Tracker {
        var meters = 0.0; private set
        var last: Fix? = null; private set
        var accepted = 0; private set
        fun add(f: Fix): Double {
            if (f.accM > MAX_ACC_M) return 0.0
            val p = last ?: run { last = f; accepted++; return 0.0 }
            val dt = (f.timeMs - p.timeMs) / 1000.0
            if (dt <= 0) return 0.0
            val d = haversine(p, f)
            if (d < MIN_STEP_M) return 0.0
            if (d / dt > MAX_SPEED) { last = f; return 0.0 }   // jump: re-anchor, don't count
            meters += d; last = f; accepted++
            return d
        }
    }

    fun paceSecPerKm(meters: Int, ms: Long): Int? = if (meters < 50) null else (ms / 1000.0 / (meters / 1000.0)).roundToInt()
    fun xpFor(rank: Int) = 25 + 15 * rank

    fun finish(s0: AppState, gate: Gate, startMs: Long, endMs: Long, meters: Int, today: Long, now: Long, pain: Boolean = false): Outcome {
        val id = "dg:$startMs"
        if (s0.dungeonRuns.any { it.id == id }) return Outcome(s0)
        val inTime = endMs - startMs <= gate.limitMin * 60_000L
        val cleared = !pain && meters >= gate.meters && inTime
        val xp = if (cleared) xpFor(gate.rank) else 0
        var s = s0
        val eff = mutableListOf<UiEffect>()
        // 1. metres → today's RUN GOAL row (handles XP, completion, run kcal add-back)
        val runQ = s.quests.firstOrNull { it.day == today && it.templateId == "goal:run" && it.verify == Verify.COUNT && it.status != QuestStatus.EXCUSED }
        var logged = 0
        if (runQ != null && meters > 0) {
            val o = GameEngine.logGoal(s, runQ.id, meters, today, now)
            s = o.state; eff += o.effects; logged = meters
        }
        val run = DungeonRun(id, today, startMs, endMs, meters, gate.meters, gate.rank, cleared, xp, logged, pain)
        s = s.copy(dungeonRuns = (s.dungeonRuns + run).takeLast(300))
        val before = s
        if (cleared) {
            s = s.copy(ledger = s.ledger + XpEntry(id, xp, 5 + 3 * gate.rank, today, Txt("تصفية بوابة GPS", "GPS Gate cleared"), QuestCategory.TRAINING))
            val (s2, _) = Rewards.grant(s, "dgloot:$id", if (gate.rank >= 3) "box_rare" else "box_common", today, Txt("تصفية بوابة GPS", "GPS Gate cleared")); s = s2
            s = GameEngine.hist(s, now, today, HistoryKind.DUNGEON, Txt("تصفية بوابة GPS ${meters} م (+$xp XP)", "GPS Gate cleared ${meters} m (+$xp XP)"), xp)
        } else s = GameEngine.hist(s, now, today, HistoryKind.DUNGEON,
            Txt(if (pain) "خروج من البوابة بسبب الألم — لا عقاب" else "انتهت البوابة دون تصفية ($meters م) — لا عقاب", if (pain) "Left the gate due to pain — no penalty" else "Gate ended without a clear ($meters m) — no penalty"))
        if (pain) s = GameEngine.hist(s, now, today, HistoryKind.PAIN, Txt("ألم أثناء الجري — توقّف", "Pain during the run — stopped"))
        val o = GameEngine.post(before, s, today, now, eff)
        return o
    }

}
