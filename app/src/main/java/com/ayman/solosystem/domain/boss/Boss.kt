package com.ayman.solosystem.domain.boss

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.game.UiEffect
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.rewards.Rewards
import kotlin.math.roundToInt

/**
 * Weekly Boss: spawns each Monday (first sync of the ISO week). Every XP point earned that week (quests, training, raids…) = 1 damage.
 * Defeat before Sunday 23:59 → a rare chest (once). If the boss survives it just leaves with a taunt — never a penalty.
 */
object Boss {
    data class Def(val name: Txt, val epithet: Txt, val taunt: Txt)
    val defs = listOf(
        Def(Txt("كاساكا", "Kasaka"), Txt("الأفعى الزرقاء", "Blue Venom Fang"), Txt("هه… أهذا كل ما لديك أيها الصياد؟", "Heh… is that all you have, Hunter?")),
        Def(Txt("سيرك", "Cerberus"), Txt("حارس البوابة", "Gatekeeper"), Txt("البوابة لا تُفتح للضعفاء.", "The gate does not open for the weak.")),
        Def(Txt("فولكان", "Vulcan"), Txt("ملك الشياطين", "Demon King"), Txt("عُد حين تحترق إرادتك.", "Return when your will burns.")),
        Def(Txt("إيغريس الأحمر", "Crimson Knight"), Txt("فارس الدم", "Blood-Red Commander"), Txt("سيفك لم يلمسني بعد.", "Your blade has not touched me yet.")),
        Def(Txt("باران", "Baran"), Txt("ملك الشياطين الأعلى", "High Demon Lord"), Txt("الضعف خطيئة يا صياد.", "Weakness is a sin, Hunter.")),
        Def(Txt("كامِش", "Kamish"), Txt("التنين الأسود", "Black Dragon"), Txt("سأنتظرك الأسبوع القادم… إن تجرأت.", "I will wait for you next week… if you dare.")),
    )
    fun def(id: Int) = defs[((id % defs.size) + defs.size) % defs.size]

    /** HP ≈ 5 full-adherence days at rank E, +12 % per player rank above E (so it always stays reachable with ~5–6 good days). */
    fun maxHp(rank: Int) = (Leveling.FULL_ADHERENCE_DAILY_XP * 5 * (1 + 0.12 * rank)).roundToInt()

    fun rankIndex(s: AppState): Int = Leveling.rankFor(com.ayman.solosystem.domain.progression.Ledger.level(s).level).ordinal

    fun damage(s: AppState, week: Long): Int =
        s.ledger.filter { it.day in week..(week + 6) && it.xp > 0 && !it.key.startsWith("boss:") }.sumOf { it.xp }

    fun current(s: AppState, today: Long) = s.bosses.firstOrNull { it.week == Stats.weekStart(today) }

    fun sync(s0: AppState, today: Long, now: Long): Pair<AppState, List<UiEffect>> {
        if (s0.profile == null) return s0 to emptyList()
        var s = s0
        val eff = mutableListOf<UiEffect>()
        val week = Stats.weekStart(today)
        // close past weeks
        s.bosses.filter { !it.closed && it.week < week }.forEach { b ->
            val dmg = damage(s, b.week)
            s = s.copy(bosses = s.bosses.map { if (it.week == b.week) it.copy(closed = true, damage = dmg) else it })
            if (b.defeatedMs == null) s = GameEngine.hist(s, now, today, HistoryKind.BOSS, Txt("هرب الزعيم ${def(b.bossId).name.ar}: «${def(b.bossId).taunt.ar}» (لا عقاب)", "Boss ${def(b.bossId).name.en} escaped: “${def(b.bossId).taunt.en}” (no penalty)"))
        }
        if (s.bosses.none { it.week == week }) {
            val rank = runCatching { rankIndex(s) }.getOrDefault(0)
            val id = (week / 7).toInt()
            s = s.copy(bosses = (s.bosses + BossFight(week, id, rank, maxHp(rank))).takeLast(60))
            s = GameEngine.hist(s, now, today, HistoryKind.BOSS, Txt("ظهر زعيم الأسبوع: ${def(id).name.ar}", "Weekly Boss appeared: ${def(id).name.en}"))
        }
        val b = s.bosses.first { it.week == week }
        val dmg = damage(s, week)
        if (dmg != b.damage || (b.defeatedMs == null && dmg >= b.maxHp)) {
            val defeated = b.defeatedMs == null && dmg >= b.maxHp
            s = s.copy(bosses = s.bosses.map { if (it.week == week) it.copy(damage = dmg, defeatedMs = if (defeated) now else it.defeatedMs) else it })
            if (defeated) {
                val (s2, g) = Rewards.grant(s, "boss:$week", "box_rare", today, Txt("هزيمة زعيم الأسبوع", "Weekly Boss defeated"))
                s = GameEngine.hist(s2, now, today, HistoryKind.BOSS, Txt("هزمت الزعيم ${def(b.bossId).name.ar}! صندوق نادر", "Defeated ${def(b.bossId).name.en}! Rare chest"))
                eff += UiEffect.Toast(Txt("[SYSTEM] تمت هزيمة زعيم الأسبوع ${def(b.bossId).name.ar}!", "[SYSTEM] Weekly Boss ${def(b.bossId).name.en} defeated!"))
                if (g != null) eff += UiEffect.RewardReveal(listOf(g))
            }
        }
        return s to eff
    }

    /** The boss screen played the hit animation up to the current damage. */
    fun markSeen(s: AppState, week: Long) = s.copy(bosses = s.bosses.map { if (it.week == week) it.copy(seenDamage = it.damage) else it })

    /** Seconds until Sunday 23:59:59 local of this boss's week (for the HUD timer). */
    fun endMs(week: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()) =
        java.time.LocalDate.ofEpochDay(week + 7).atStartOfDay(zone).toInstant().toEpochMilli() - 1000
}
