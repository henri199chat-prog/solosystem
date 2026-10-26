package com.ayman.solosystem.domain.rewards

import com.ayman.solosystem.domain.game.Metric
import com.ayman.solosystem.domain.game.Metrics
import com.ayman.solosystem.domain.game.Raids
import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.skills.Effect
import com.ayman.solosystem.domain.skills.SkillCatalog
import com.ayman.solosystem.domain.skills.SkillDef
import kotlin.random.Random

/** Aggregated game-only bonuses from skills + equipped title. All capped. */
object Bonuses {
    private fun skillLv(s: AppState) = SkillCatalog.all.mapNotNull { d -> s.skillLevelsSeen[d.id]?.takeIf { it > 0 }?.let { d to it } }
    private fun title(s: AppState) = s.equippedTitle?.let { RewardCatalog.get(it) }?.takeIf { (s.inventory[it.id] ?: 0) > 0 }

    fun xpPct(s: AppState, cat: QuestCategory): Int =
        (skillLv(s).filter { it.first.effect == Effect.XP_CATEGORY && it.first.category == cat }.sumOf { it.first.pctPerLevel * it.second } +
            (title(s)?.takeIf { it.effect == Effect.XP_CATEGORY && it.category == cat }?.pct ?: 0)).coerceAtMost(25)
    fun goldPct(s: AppState): Int = (skillLv(s).filter { it.first.effect == Effect.GOLD_PCT }.sumOf { it.first.pctPerLevel * it.second } +
        (title(s)?.takeIf { it.effect == Effect.GOLD_PCT }?.pct ?: 0)).coerceAtMost(30)
    fun shield(s: AppState): Int = skillLv(s).count { it.first.effect == Effect.STREAK_SHIELD && it.second >= 3 }.coerceAtMost(2)
    fun rerollsPerDay(s: AppState): Int = skillLv(s).filter { it.first.effect == Effect.REROLL }.sumOf { if (it.second >= 4) 2 else 1 }.coerceAtMost(3)
    fun emergencyChancePct(s: AppState): Int = (25 + 3 * skillLv(s).filter { it.first.effect == Effect.EMERGENCY_CHANCE }.sumOf { it.second }).coerceAtMost(50)
    fun luck(s: AppState): Int = (skillLv(s).filter { it.first.effect == Effect.LOOT_LUCK }.sumOf { it.second } + (title(s)?.takeIf { it.effect == Effect.LOOT_LUCK }?.pct ?: 0)).coerceAtMost(10)
    fun fullDayPct(s: AppState): Int = (skillLv(s).filter { it.first.effect == Effect.BONUS_POOL }.sumOf { it.second * 2 } + (title(s)?.takeIf { it.effect == Effect.BONUS_POOL }?.pct ?: 0)).coerceAtMost(20)
}

/** Seeded, fair, auditable loot with a pity counter. */
object Loot {
    const val PITY_EPIC = 10      // the 10th box without an epic+ is guaranteed epic+
    const val PITY_LEGENDARY = 40 // the 40th box without a legendary is guaranteed legendary

    /** per-mille weights COMMON, RARE, EPIC, LEGENDARY */
    fun weights(box: Rarity, luck: Int): IntArray {
        val w = when (box) {
            Rarity.COMMON -> intArrayOf(700, 250, 45, 5); Rarity.RARE -> intArrayOf(400, 450, 130, 20)
            Rarity.EPIC -> intArrayOf(0, 500, 420, 80); Rarity.LEGENDARY -> intArrayOf(0, 0, 600, 400)
        }
        val l = luck.coerceIn(0, 10)
        val fromCommon = minOf(w[0], l * 10); w[0] -= fromCommon; w[1] += fromCommon * 7 / 10; w[2] += fromCommon * 2 / 10; w[3] += fromCommon - fromCommon * 7 / 10 - fromCommon * 2 / 10
        val fromRare = minOf(w[1], l * 5); w[1] -= fromRare; w[2] += fromRare
        return w
    }
    fun rng(st: LootState) = Random(st.seed xor (st.opened.toLong() * -7046029254386353131L) xor 0x2545F4914F6CDD1DL)

    /** Secret chest table (per-mille C/R/E/L) before luck: 0 / 350 / 450 / 200. */
    fun secretWeights(luck: Int): IntArray {
        val w = intArrayOf(0, 350, 450, 200)
        val l = luck.coerceIn(0, 10) * 5; w[1] -= l; w[3] += l
        return w
    }
    const val SECRET_EXCLUSIVE_PCT = 35
    /** Blessed box (safe): 0 / 450 / 420 / 130. Cursed box: 0 / 250 / 430 / 320 (higher legendary, may carry a curse). */
    fun blessedWeights(luck: Int): IntArray { val w = intArrayOf(0, 450, 420, 130); val l = luck.coerceIn(0, 10) * 5; w[1] -= l; w[3] += l; return w }
    fun cursedWeights(luck: Int): IntArray { val w = intArrayOf(0, 250, 430, 320); val l = luck.coerceIn(0, 10) * 5; w[1] -= l; w[3] += l; return w }
    /** Chance (%) that a LEGENDARY roll from these boxes is a Raid Key. */
    const val KEY_PCT_BLESSED = 40
    const val KEY_PCT_CURSED = 60
    const val CURSE_PCT = 30

    fun rollRarity(box: Rarity, st: LootState, luck: Int, r: Random, secret: Boolean = false, table: IntArray? = null): Rarity {
        if (st.sinceLegendary + 1 >= PITY_LEGENDARY) return Rarity.LEGENDARY
        val w = table ?: if (secret) secretWeights(luck) else weights(box, luck)
        if (st.sinceEpic + 1 >= PITY_EPIC) { val t = w[2] + w[3]; return if (r.nextInt(t) < w[2]) Rarity.EPIC else Rarity.LEGENDARY }
        var x = r.nextInt(w.sum())
        for (i in 0..3) { if (x < w[i]) return Rarity.entries[i]; x -= w[i] }
        return Rarity.COMMON
    }

    fun pickItem(rarity: Rarity, s: AppState, r: Random): RewardDef {
        val cosmetics = RewardCatalog.all.filter { it.cosmetic && it.lootable && it.rarity == rarity && (s.inventory[it.id] ?: 0) == 0 && it.id !in RewardCatalog.defaults }
        val currency = RewardCatalog.currency.filter { it.rarity == rarity && it.lootable }
        return if (cosmetics.isNotEmpty() && (currency.isEmpty() || r.nextInt(100) < 65)) cosmetics[r.nextInt(cosmetics.size)] else currency[r.nextInt(currency.size)]
    }
}

data class SyncResult(val state: AppState, val grants: List<RewardGrant>, val skillUps: List<Pair<SkillDef, Int>>, val rankUps: List<Leveling.PlayerRank>, val badges: List<RewardDef>)

data class WeeklyChallenge(val id: String, val name: Txt, val desc: Txt, val target: Int, val box: String, val event: Boolean = false, val progress: (AppState, Long, Long) -> Int)

object Rewards {
    fun owned(s: AppState, id: String) = id in RewardCatalog.defaults || (s.inventory[id] ?: 0) > 0

    /** Idempotent grant: the same key can never grant twice. Duplicate cosmetics convert to gold. */
    fun grant(s: AppState, key: String, rewardId: String, day: Long, source: Txt): Pair<AppState, RewardGrant?> {
        if (key in s.grantedKeys) return s to null
        val def = RewardCatalog.get(rewardId) ?: return s to null
        var st = s.copy(grantedKeys = s.grantedKeys + key)
        var goldInstead = 0
        when {
            def.kind == RewardKind.GOLD -> st = st.copy(ledger = st.ledger + XpEntry("gold:$key", 0, def.gold, day, def.name))
            def.cosmetic && owned(st, def.id) -> { goldInstead = def.rarity.dupGold; st = st.copy(ledger = st.ledger + XpEntry("gold:$key", 0, goldInstead, day, def.name)) }
            def.kind == RewardKind.BADGE && owned(st, def.id) -> return s.copy(grantedKeys = s.grantedKeys + key) to null
            else -> st = st.copy(inventory = st.inventory + (def.id to ((st.inventory[def.id] ?: 0) + 1)))
        }
        val g = RewardGrant(key, def.id, day, source, goldInstead)
        return st.copy(rewardLog = (st.rewardLog + g).takeLast(400)) to g
    }

    /** Open one box from the inventory. Returns null if none owned. Blessed/Cursed boxes may drop a Raid Key (legendary) and the Cursed one a curse. */
    fun openBox(s: AppState, boxId: String, day: Long): Pair<AppState, RewardGrant>? = openBoxEx(s, boxId, day)?.let { it.first to it.second }

    data class Opened(val first: AppState, val second: RewardGrant, val cursed: Boolean)

    fun openBoxEx(s: AppState, boxId: String, day: Long, tier: Int = 0): Opened? {
        val box = RewardCatalog.get(boxId)?.takeIf { it.kind == RewardKind.BOX } ?: return null
        if ((s.inventory[boxId] ?: 0) <= 0) return null
        val r = Loot.rng(s.loot)
        val secret = boxId == "box_secret" || boxId == "box_blessed" || boxId == "box_cursed"
        val luck = Bonuses.luck(s)
        val table = when (boxId) { "box_blessed" -> Loot.blessedWeights(luck); "box_cursed" -> Loot.cursedWeights(luck); else -> null }
        val rarity = Loot.rollRarity(box.rarity, s.loot, luck, r, secret, table)
        val st0 = s.copy(inventory = s.inventory + (boxId to (s.inventory.getValue(boxId) - 1)))
        val newLoot = s.loot.copy(opened = s.loot.opened + 1,
            sinceEpic = if (rarity >= Rarity.EPIC) 0 else s.loot.sinceEpic + 1,
            sinceLegendary = if (rarity == Rarity.LEGENDARY) 0 else s.loot.sinceLegendary + 1)
        val key = "loot:${s.loot.opened}"
        val keyPct = when (boxId) { "box_blessed" -> Loot.KEY_PCT_BLESSED; "box_cursed" -> Loot.KEY_PCT_CURSED; else -> 0 }
        // curse (cursed box only): a harder-challenge day tomorrow, never food-related, always inside safety caps
        val cursed = boxId == "box_cursed" && r.nextInt(100) < Loot.CURSE_PCT
        var st1 = st0.copy(loot = newLoot)
        if (cursed) st1 = st1.copy(curseDays = st1.curseDays + (day + 1), rewardLog = (st1.rewardLog + RewardGrant("$key:curse", "curse_iron", day, box.name)).takeLast(400))
        if (rarity == Rarity.LEGENDARY && keyPct > 0 && r.nextInt(100) < keyPct) {
            val k = RaidKey("key:${s.loot.opened}:$day", (tier + 1).coerceIn(1, 5), day, day + Raids.KEY_DAYS)
            val g = RewardGrant(key, "key_raid", day, box.name)
            return Opened(st1.copy(raidKeys = st1.raidKeys + k, grantedKeys = st1.grantedKeys + key, rewardLog = (st1.rewardLog + g).takeLast(400)), g, cursed)
        }
        val excl = if (secret) RewardCatalog.secretExclusives.filter { it.rarity == rarity && (st0.inventory[it.id] ?: 0) == 0 } else emptyList()
        val item = if (excl.isNotEmpty() && r.nextInt(100) < Loot.SECRET_EXCLUSIVE_PCT) excl[r.nextInt(excl.size)] else Loot.pickItem(rarity, st0, r)
        val (st, g) = grant(st1, key, item.id, day, box.name)
        return Opened(st, g ?: RewardGrant(key, item.id, day, box.name), cursed)
    }

    fun equip(s: AppState, id: String?): AppState {
        if (id == null) return s.copy(equippedTitle = null)
        val d = RewardCatalog.get(id) ?: return s
        if (!owned(s, id)) return s
        return when (d.kind) {
            RewardKind.TITLE -> s.copy(equippedTitle = id); RewardKind.THEME -> s.copy(equippedTheme = id)
            RewardKind.FRAME -> s.copy(equippedFrame = id); RewardKind.EMBLEM -> s.copy(equippedEmblem = id); else -> s
        }
    }

    fun consume(s: AppState, id: String): AppState? {
        val n = s.inventory[id] ?: 0
        return if (n <= 0) null else s.copy(inventory = s.inventory + (id to n - 1))
    }

    // ---------- milestone chests & rank ceremonies ----------
    fun chestFor(level: Int) = when { level % 100 == 0 -> "box_legendary"; level % 25 == 0 -> "box_epic"; level % 10 == 0 -> "box_rare"; else -> "box_common" }

    // ---------- daily surprise (only after all of today's required quests are done) ----------
    fun surpriseFor(s: AppState, day: Long): String {
        val r = Random(s.loot.seed xor (day * 0x9E3779B97F4A7C15uL.toLong()))
        val x = r.nextInt(100)
        return when { x < 40 -> "gold_s"; x < 70 -> "box_common"; x < 82 -> "token_reroll"; x < 90 -> "gold_m"; x < 96 -> "box_rare"; else -> "token_shield" }
    }
    fun allDailyDone(s: AppState, day: Long): Boolean {
        val qs = s.quests.filter { it.day == day && (it.type == QuestType.DAILY || it.type == QuestType.PENALTY) && !it.optional }
        return qs.isNotEmpty() && qs.all { it.status == QuestStatus.COMPLETED }
    }

    // ---------- weekly challenges (rotating) ----------
    private fun doneIn(s: AppState, a: Long, b: Long, pred: (Quest) -> Boolean) = s.quests.count { it.day in a..b && it.status == QuestStatus.COMPLETED && pred(it) }
    val challengePool = listOf(
        WeeklyChallenge("sessions", Txt("أسبوع الحديد", "Iron Week"), Txt("أكمل كل حصص الأسبوع المخططة.", "Complete every planned session this week."), 1, "box_rare") { s, a, _ ->
            val w = Stats.week(s, a); if (w.sessionsPlanned > 0 && w.sessionsDone >= w.sessionsPlanned) 1 else 0 },
        WeeklyChallenge("walks", Txt("خطوات الصياد", "Hunter's Steps"), Txt("أكمل 5 مهام مشي.", "Complete 5 walk quests."), 5, "box_common") { s, a, b -> doneIn(s, a, b) { it.templateId.startsWith("walk") } },
        WeeklyChallenge("checkins", Txt("عين على الجسد", "Eye on the Body"), Txt("سجّل الجاهزية 6 أيام.", "Log readiness on 6 days."), 6, "box_common") { s, a, b -> s.checkIns.count { it.day in a..b } },
        WeeklyChallenge("sleep", Txt("حارس الليل", "Night Watch"), Txt("أكمل روتين النوم 5 مرات.", "Complete the sleep routine 5 times."), 5, "box_common") { s, a, b -> doneIn(s, a, b) { it.templateId == "sleep" } },
        WeeklyChallenge("learn", Txt("أسبوع المعرفة", "Week of Knowledge"), Txt("أكمل 4 مهام تعلم.", "Complete 4 learning quests."), 4, "box_common") { s, a, b -> doneIn(s, a, b) { it.templateId == "learn" } },
        WeeklyChallenge("runner", Txt("عدّاء الظلال", "Shadow Runner"), Txt("أكمل هدف الجري 5 مرات.", "Complete the running goal 5 times."), 5, "box_common") { s, a, b -> doneIn(s, a, b) { it.templateId == "goal:run" } },
        WeeklyChallenge("perfect", Txt("ثلاثية الكمال", "Perfect Trio"), Txt("حقق 3 أيام مثالية.", "Achieve 3 perfect days."), 3, "box_rare") { s, a, b -> (a..b).count { allDailyDone(s, it) } },
        WeeklyChallenge("pr", Txt("كسر الحدود", "Break the Limit"), Txt("حقق رقمًا قياسيًا جديدًا بأمان.", "Set one new personal record safely."), 1, "box_rare") { s, a, b -> s.prs.count { it.day in a..b } },
        WeeklyChallenge("mobility", Txt("المرونة", "Supple"), Txt("أكمل الحركة والتمدد مرتين.", "Complete mobility twice."), 2, "box_common") { s, a, b -> doneIn(s, a, b) { it.templateId == "mobility" } },
        WeeklyChallenge("emergency", Txt("الاستجابة", "Response"), Txt("أكمل مهمتين طارئتين.", "Complete 2 emergency quests."), 2, "box_rare") { s, a, b -> doneIn(s, a, b) { it.type == QuestType.EMERGENCY } },
        WeeklyChallenge("discipline", Txt("القائد", "Commander"), Txt("أكمل 6 مهام انضباط.", "Complete 6 discipline quests."), 6, "box_common") { s, a, b -> doneIn(s, a, b) { it.category == QuestCategory.DISCIPLINE } },
        WeeklyChallenge("quests", Txt("صياد الأسبوع", "Hunter of the Week"), Txt("أكمل 30 مهمة.", "Complete 30 quests."), 30, "box_rare") { s, a, b -> doneIn(s, a, b) { true } }
    )
    val eventPool = listOf(
        WeeklyChallenge("ev_gate", Txt("حدث: البوابة الحمراء", "Event: The Red Gate"), Txt("حقق 5 أيام مثالية هذا الأسبوع.", "Achieve 5 perfect days this week."), 5, "box_epic", true) { s, a, b -> (a..b).count { allDailyDone(s, it) } },
        WeeklyChallenge("ev_dungeon", Txt("حدث: زنزانة التحمل", "Event: Endurance Dungeon"), Txt("أكمل كل الحصص و5 مهام مشي.", "Complete all sessions and 5 walks."), 2, "box_epic", true) { s, a, b ->
            val w = Stats.week(s, a); (if (w.sessionsPlanned > 0 && w.sessionsDone >= w.sessionsPlanned) 1 else 0) + (if (doneIn(s, a, b) { it.templateId.startsWith("walk") } >= 5) 1 else 0) },
        WeeklyChallenge("ev_scholar", Txt("حدث: مكتبة الظلال", "Event: Shadow Library"), Txt("أكمل 6 مهام تعلم ومراجعة أسبوعية.", "Complete 6 learning quests and a weekly review."), 7, "box_epic", true) { s, a, b ->
            minOf(6, doneIn(s, a, b) { it.templateId == "learn" }) + minOf(1, doneIn(s, a, b) { it.templateId == "review" }) }
    )
    fun challengesFor(weekStart: Long): List<WeeklyChallenge> {
        val idx = Math.floorDiv(weekStart, 7L)
        val n = challengePool.size
        val base = (0 until 3).map { challengePool[Math.floorMod(idx * 3 + it, n.toLong()).toInt()] }.distinctBy { it.id }
        return if (Math.floorMod(idx, 4L) == 0L) base + eventPool[Math.floorMod(idx / 4, eventPool.size.toLong()).toInt()] else base
    }

    /** Check every reward source and grant what was earned. Safe to call repeatedly (idempotent by key). */
    fun sync(s0: AppState, today: Long, prevLevel: Int): SyncResult {
        var s = s0
        val grants = mutableListOf<RewardGrant>(); val badges = mutableListOf<RewardDef>()
        fun g(key: String, id: String, src: Txt) { val (n, gr) = grant(s, key, id, today, src); s = n; if (gr != null) { grants += gr; RewardCatalog.get(id)?.takeIf { it.kind == RewardKind.BADGE }?.let { badges += it } } }
        val lvl = Ledger.level(s).level
        for (l in 5..lvl step 5) g("chest:$l", chestFor(l), Txt("صندوق المستوى $l", "Level $l chest"))
        val ranks = mutableListOf<Leveling.PlayerRank>()
        Leveling.PlayerRank.entries.filter { it != Leveling.PlayerRank.E && lvl >= it.minLevel }.forEach { r ->
            val key = "rankup:${r.name}"
            if (key !in s.grantedKeys) { if (r.minLevel > prevLevel) ranks += r; g(key, if (r >= Leveling.PlayerRank.S) "box_legendary" else "box_epic", Txt("ترقية الرتبة ${r.ar}", "Rank-up ${r.label}")) }
        }
        if (lvl >= Leveling.MAX_LEVEL) { g("max:title", "title_monarch", Txt("المستوى الأقصى", "Max level")); g("max:theme", "theme_monarch", Txt("المستوى الأقصى", "Max level")); g("max:frame", "frame_crown", Txt("المستوى الأقصى", "Max level")) }
        if (allDailyDone(s, today)) g("surprise:$today", surpriseFor(s, today), Txt("مفاجأة اليوم", "Daily surprise"))
        val ws = Stats.weekStart(today)
        challengesFor(ws).forEach { c -> if (c.progress(s, ws, ws + 6) >= c.target) g("wc:$ws:${c.id}", c.box, c.name) }
        val m = Metrics.compute(s, today)
        RewardCatalog.badges.forEach { b -> if ((m[b.metric] ?: 0) >= b.threshold) g("badge:${b.id}", b.id, b.name) }
        val ups = mutableListOf<Pair<SkillDef, Int>>()
        val seen = s.skillLevelsSeen.toMutableMap()
        SkillCatalog.all.forEach { d -> val lv = d.level(m[d.metric] ?: 0); if (lv > (seen[d.id] ?: 0)) { seen[d.id] = lv; ups += d to lv } }
        if (ups.isNotEmpty()) s = s.copy(skillLevelsSeen = seen)
        return SyncResult(s, grants, ups, ranks, badges)
    }

    fun metricValue(s: AppState, today: Long, m: Metric) = Metrics.compute(s, today)[m] ?: 0
}
