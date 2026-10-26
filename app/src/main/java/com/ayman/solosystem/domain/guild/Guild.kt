package com.ayman.solosystem.domain.guild

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.Outcome
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import kotlin.math.roundToInt

/**
 * Hunter Guild: a safe monthly self-assessment (stop 1–2 reps before technical failure; pain = stop) → Hunter rank E…S.
 * Purely informational/cosmetic: it never changes the plan, tiers or penalties. Its result is printed on the Hunter License card.
 */
object Guild {
    val RANKS = listOf("E", "D", "C", "B", "A", "S")
    /** Score thresholds for D, C, B, A, S (E below 20). */
    val THRESHOLDS = intArrayOf(20, 35, 50, 65, 80)
    const val COOLDOWN_DAYS = 28
    const val XP = 60

    fun pushPts(reps: Int) = (reps * 2).coerceIn(0, 100)            // 50 push-ups = 100
    fun plankPts(sec: Int) = (sec / 1.8).roundToInt().coerceIn(0, 100) // 3 min = 100
    fun squatPts(reps: Int) = (reps * 1.25).roundToInt().coerceIn(0, 100) // 80 squats = 100
    /** 1 km: 3:30 = 100 … 8:00 = 0. */
    fun runPts(sec: Int) = ((480 - sec) / 2.7).roundToInt().coerceIn(0, 100)

    fun score(push: Int, plank: Int, squat: Int, run1kmSec: Int?): Int {
        val p = listOfNotNull(pushPts(push), plankPts(plank), squatPts(squat), run1kmSec?.takeIf { it > 0 }?.let { runPts(it) })
        return (p.sum().toDouble() / p.size).roundToInt()
    }
    fun rankFor(score: Int) = THRESHOLDS.count { score >= it }
    fun rankLabel(i: Int) = RANKS[i.coerceIn(0, 5)]

    fun last(s: AppState) = s.guild.tests.maxByOrNull { it.ms }
    fun nextDay(s: AppState): Long? = last(s)?.let { it.day + COOLDOWN_DAYS }
    fun canTest(s: AppState, today: Long) = (nextDay(s) ?: Long.MIN_VALUE) <= today

    /** Best 1 km pace from GPS dungeon runs ≥ 1 km (seconds per km), a suggestion for the optional run test. */
    fun gpsKmSec(s: AppState): Int? = s.dungeonRuns.filter { it.meters >= 1000 && !it.pain }.minOfOrNull { ((it.endMs - it.startMs) / 1000.0 / (it.meters / 1000.0)).roundToInt() }

    fun record(s0: AppState, push: Int, plank: Int, squat: Int, run1kmSec: Int?, today: Long, now: Long): Outcome {
        if (!canTest(s0, today)) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] التقييم التالي متاح بعد ${nextDay(s0)!! - today} يوم.", "[SYSTEM] Next assessment in ${nextDay(s0)!! - today} days."), blocked = true)))
        val sc = score(push.coerceAtLeast(0), plank.coerceAtLeast(0), squat.coerceAtLeast(0), run1kmSec)
        val rank = rankFor(sc)
        val prev = last(s0)?.rank
        val t = GuildTest(today, now, push, plank, squat, run1kmSec?.takeIf { it > 0 }, sc, rank)
        var s = s0.copy(guild = s0.guild.copy(tests = s0.guild.tests + t))
        val key = "guild:$today"
        if (s.ledger.none { it.key == key }) s = s.copy(ledger = s.ledger + XpEntry(key, XP, 10, today, Txt("تقييم نقابة الصيادين", "Hunter Guild assessment"), QuestCategory.TRAINING))
        s = GameEngine.hist(s, now, today, HistoryKind.GUILD, Txt("تقييم النقابة: الرتبة ${rankLabel(rank)} ($sc)", "Guild assessment: rank ${rankLabel(rank)} ($sc)"), XP)
        val msg = when {
            prev == null -> Txt("[SYSTEM] تم تسجيلك كصياد من الرتبة ${rankLabel(rank)}.", "[SYSTEM] You are registered as a rank ${rankLabel(rank)} Hunter.")
            rank > prev -> Txt("[SYSTEM] ترقية! الرتبة ${rankLabel(prev)} ← ${rankLabel(rank)}.", "[SYSTEM] Promotion! Rank ${rankLabel(prev)} → ${rankLabel(rank)}.")
            else -> Txt("[SYSTEM] الرتبة ${rankLabel(rank)} مؤكدة.", "[SYSTEM] Rank ${rankLabel(rank)} confirmed.")
        }
        val o = GameEngine.post(s0, s, today, now, listOf(UiEffect.Toast(msg)))
        return o
    }

    /** Pain = stop immediately. Nothing is recorded except the safety stop; no penalty, the test can be retaken any time. */
    fun painStop(s0: AppState, today: Long, now: Long): Outcome {
        val s = GameEngine.hist(s0.copy(guild = s0.guild.copy(painStops = s0.guild.painStops + 1)), now, today, HistoryKind.PAIN,
            Txt("ألم أثناء تقييم النقابة — توقّف (لا عقاب)", "Pain during the Guild assessment — stopped (no penalty)"))
        return Outcome(s, listOf(UiEffect.Safety(GameEngine.safetyText(SymptomType.SHARP_PAIN), true)))
    }

    fun setPhoto(s: AppState, file: String?) = s.copy(guild = s.guild.copy(licensePhoto = file))

    /** Stable, non-identifying licence number derived from the creation day. */
    fun licenseNo(s: AppState): String {
        val c = s.profile?.createdDay ?: 0L
        val h = ((c * 2654435761L) xor 0x5011L).let { if (it < 0) -it else it } % 1_000_000L
        return "HL-" + h.toString().padStart(6, '0')
    }
}
