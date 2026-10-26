package com.ayman.solosystem.domain.game

import com.ayman.solosystem.domain.model.*
import com.ayman.solosystem.domain.progression.Ledger
import com.ayman.solosystem.domain.progression.Leveling
import com.ayman.solosystem.domain.progression.Stats
import com.ayman.solosystem.domain.quest.GoalPlan
import com.ayman.solosystem.domain.quest.Countdown
import com.ayman.solosystem.domain.quest.Penalty
import com.ayman.solosystem.domain.quest.QuestGenerator
import com.ayman.solosystem.domain.rewards.Bonuses
import com.ayman.solosystem.domain.rewards.RewardCatalog
import com.ayman.solosystem.domain.rewards.RewardDef
import com.ayman.solosystem.domain.rewards.Rewards
import com.ayman.solosystem.domain.rewards.TokenType
import com.ayman.solosystem.domain.skills.SkillDef
import com.ayman.solosystem.domain.training.*
import kotlin.math.roundToInt

sealed interface UiEffect {
    data class Toast(val text: Txt, val blocked: Boolean = false) : UiEffect
    data class LevelUp(val from: Int, val to: Int, val isMax: Boolean, val newTitle: Txt?, val points: Int, val source: Txt? = null) : UiEffect
    data class QuestReward(val quest: Quest, val xp: Int, val bonusXp: Int, val gold: Int) : UiEffect
    data class RewardReveal(val grants: List<RewardGrant>) : UiEffect
    data class LootOpened(val grant: RewardGrant, val box: RewardDef, val cursed: Boolean = false, val key: RaidKey? = null) : UiEffect
    data class PenaltyIssued(val quest: Quest) : UiEffect
    data class PenaltyCleared(val quest: Quest) : UiEffect
    data class MissedReview(val quests: List<Quest>) : UiEffect
    data class Emergency(val quest: Quest) : UiEffect
    data class TierUnlocked(val tier: Int) : UiEffect
    data class TierActivated(val tier: Int) : UiEffect
    data class Safety(val text: Txt, val stopSession: Boolean, val swapEntry: Int? = null) : UiEffect
    data class SkillUp(val skill: SkillDef, val level: Int) : UiEffect
    data class RankUp(val rank: Leveling.PlayerRank) : UiEffect
    data object DailyComplete : UiEffect
    /** v1.2: one-time level-100 evolution of the System (blue → violet Shadow-Monarch theme). */
    data object SystemAwakening : UiEffect
    /** Hidden condition met: the day became a SECRET QUEST. [overCap] = some 200 % exceeded the safe capacity cap → "listen to your body". */
    data class SecretQuest(val day: Long, val overCap: Boolean, val rows: List<Quest> = emptyList()) : UiEffect
    /** "إشعار / تلقيت مكافآت. / تفقد مكافآتك؟" → rewards list. [direct] = skip the yes/no notice (opened from the inventory). */
    data class SecretReward(val day: Long, val direct: Boolean = false) : UiEffect
    data class RaidGate(val key: RaidKey) : UiEffect
    data class RaidResult(val raid: Raid, val box: RewardGrant?) : UiEffect
    data class AssessPropose(val why: Txt) : UiEffect
}

data class Outcome(val state: AppState, val effects: List<UiEffect> = emptyList())

/** Pure game rules. Every function takes the state + clock values and returns a new state + UI effects. */
object GameEngine {
    const val FULL_DAY_BONUS = 15
    const val MISS_LOSS = 3
    /** Level at which the System evolves into the violet Shadow-Monarch theme. */
    const val MONARCH_LEVEL = 100

    internal fun hist(s: AppState, now: Long, day: Long, k: HistoryKind, t: Txt, xp: Int = 0) =
        s.copy(history = (s.history + HistoryEvent(now, day, k, t, xp)).takeLast(1500))

    // ------------------------------------------------------------------ onboarding
    fun onboard(profile: PlayerProfile, settings: Settings, today: Long, now: Long, seed: Long): Outcome {
        val p = profile.copy(createdDay = today)
        val plan = PlanGenerator.generate(p, today, settings.deloadEvery)
        var s = AppState(profile = p, plan = plan, settings = settings.copy(graceDaysPerWeek = settings.difficulty.defaultGrace), loot = LootState(seed = seed),
            highestLevelSeen = 1)
        s = hist(s, now, today, HistoryKind.PLAN, Txt("تم إنشاء خطة ${plan.split.ar}", "Created plan: ${plan.split.en}"))
        val (s2, _) = Rewards.grant(s, "starter:box", "box_common", today, Txt("هدية البداية", "Starter gift"))
        return rollover(s2, today, now, dayStartMs = 0L).let { it.copy(effects = listOf(UiEffect.RewardReveal(s2.rewardLog.takeLast(1))) + it.effects) }
    }

    // ------------------------------------------------------------------ day rollover
    fun rollover(s0: AppState, today: Long, now: Long, dayStartMs: Long, issue: Boolean = true): Outcome {
        if (s0.profile == null || s0.plan == null) return Outcome(s0)
        var s = s0
        // the ring window starts when the player first gets today's quest (app open or morning notification)
        if (issue) s = Countdown.markIssued(s, today, now)
        val eff = mutableListOf<UiEffect>()
        val newlyMissed = mutableListOf<Quest>()
        // 1. close past days
        s = s.copy(quests = s.quests.map { q ->
            val past = (q.deadlineDay ?: Long.MAX_VALUE) < today
            when {
                q.status == QuestStatus.PENDING && past && q.type != QuestType.MAIN -> q.copy(status = QuestStatus.MISSED).also { newlyMissed += it }
                q.status == QuestStatus.OFFERED && q.day < today -> q.copy(status = QuestStatus.DECLINED, reason = "expired", reasonReviewed = true)
                else -> q
            }
        })
        newlyMissed.filter { !it.optional }.forEach { q ->
            s = s.copy(bonus = s.bonus.filterNot { it.key == "loss:${q.id}" } + BonusEntry("loss:${q.id}", q.day, -MISS_LOSS))
            s = hist(s, now, q.day, HistoryKind.QUEST_MISSED, Txt("فاتتك: ${q.title.ar}", "Missed: ${q.title.en}"))
        }
        // 2. bank the temporary bonus pool of finished weeks (earned XP is never removed)
        val ws = Stats.weekStart(today)
        val oldWeeks = s.bonus.filter { Stats.weekStart(it.day) < ws }.groupBy { Stats.weekStart(it.day) }
        oldWeeks.forEach { (w, list) ->
            val sum = list.sumOf { it.xp }
            if (sum > 0 && s.ledger.none { it.key == "bank:$w" }) {
                s = s.copy(ledger = s.ledger + XpEntry("bank:$w", sum, Leveling.goldFor(sum), today, Txt("مكافأة الأسبوع المؤقتة", "Weekly bonus pool")))
                s = hist(s, now, today, HistoryKind.BONUS_BANKED, Txt("تم حفظ $sum XP من مكافأة الأسبوع", "Banked $sum bonus XP"), sum)
            }
        }
        if (oldWeeks.isNotEmpty()) s = s.copy(bonus = s.bonus.filter { Stats.weekStart(it.day) >= ws })
        // 3. today's quests (after migrating 1.0.x data)
        s = migrateDailies(s, today)
        if (s.quests.none { it.day == today && it.type == QuestType.DAILY && it.id.startsWith("d:") }) {
            s = GoalPlan.ramp(s, today)
            s = s.copy(quests = s.quests + QuestGenerator.daily(s, today))
        } else if (s.quests.none { it.day == today && GoalPlan.isGoal(it) }) {
            // upgrade from 1.0.x mid-day: today's old habit list becomes side tasks, the anime GOAL rows become the daily quest
            s = GoalPlan.ramp(s, today)
            s = s.copy(quests = s.quests.map { if (it.day == today && it.type == QuestType.DAILY && it.id.startsWith("d:") && it.status == QuestStatus.PENDING) it.copy(optional = true) else it } +
                GoalPlan.quests(s, today))
        }
        s = addMains(s, today)
        val unsafe = Penalty.unsafeToday(s, today)
        QuestGenerator.emergency(s, today, unsafe, Bonuses.emergencyChancePct(s))?.let { e ->
            if (s.quests.none { it.id == e.id }) { s = s.copy(quests = s.quests + e); eff += UiEffect.Emergency(e) }
        }
        // 4. penalties
        val (s3, issued) = Penalty.sync(s, today)
        s = s3
        if (issued != null) {
            s = hist(s, now, today, HistoryKind.PENALTY_ISSUED, issued.title)
            eff += UiEffect.PenaltyIssued(issued)
        }
        s = s.copy(lastProcessedDay = today)
        val review = s.quests.filter { it.status == QuestStatus.MISSED && !it.reasonReviewed && !it.optional && it.day >= today - 7 }
        if (review.isNotEmpty()) eff += UiEffect.MissedReview(review)
        return post(s0, s, today, now, eff)
    }

    /** Called by the countdown ring / alarms when the deadline passes. Idempotent. */
    fun expireDeadline(s0: AppState, today: Long, now: Long, dayStartMs: Long): Outcome {
        val (s, issued) = Countdown.expire(s0, today, dayStartMs, now)
        if (s === s0) return Outcome(s0)
        val eff = mutableListOf<UiEffect>()
        var st = s
        val missed = st.quests.filter { it.day == today && it.status == QuestStatus.MISSED && !it.optional && s0.quests.firstOrNull { o -> o.id == it.id }?.status == QuestStatus.PENDING }
        missed.forEach { q -> st = st.copy(bonus = st.bonus.filterNot { it.key == "loss:${q.id}" } + BonusEntry("loss:${q.id}", q.day, -MISS_LOSS)) }
        if (issued != null) { st = hist(st, now, today, HistoryKind.PENALTY_ISSUED, issued.title); eff += UiEffect.PenaltyIssued(issued) }
        if (missed.isNotEmpty()) eff += UiEffect.MissedReview(missed)
        return post(s0, st, today, now, eff)
    }

    private fun addMains(s0: AppState, today: Long): AppState {
        var s = s0
        val doneIds = s.quests.filter { it.status == QuestStatus.COMPLETED }.map { it.id }.toSet()
        QuestGenerator.mains.forEach { d ->
            if (s.quests.none { it.id == d.id } && (d.prereq == null || d.prereq in doneIds)) s = s.copy(quests = s.quests + QuestGenerator.mainQuest(d, today))
        }
        return s
    }

    // ------------------------------------------------------------------ post-processing
    /** Level-ups, main quests, tiers, rewards, skills. Called after every mutation. */
    fun post(before: AppState, s0: AppState, today: Long, now: Long, effects: List<UiEffect>): Outcome {
        var s = s0
        val eff = effects.toMutableList()
        // main quests progress (milestones from real data)
        repeat(3) {
            s = addMains(s, today)
            val upd = s.quests.filter { it.type == QuestType.MAIN && it.status == QuestStatus.PENDING && it.templateId != "assess" }
            upd.forEach { q ->
                val d = QuestGenerator.mains.firstOrNull { it.id == q.id } ?: return@forEach
                val v = d.metric(s, today)
                if (v != q.progress) s = s.copy(quests = s.quests.map { if (it.id == q.id) it.copy(progress = minOf(v, q.target)) else it })
                if (v >= q.target) { val o = completeInternal(s, q.id, today, now); s = o.state; eff += o.effects }
            }
        }
        // training tiers: unlock by level → assessment quest; activate only on real benchmarks
        val lvl = Ledger.level(s).level
        val unlocked = Tiers.unlockedTier(lvl)
        val next = s.activeTier + 1
        if (next <= unlocked && next < Tiers.all.size && s.quests.none { it.id == "m:assess:$next" }) {
            s = s.copy(quests = s.quests + QuestGenerator.assessmentQuest(next, today)); eff += UiEffect.TierUnlocked(next)
        }
        Tiers.eligibleNext(s, lvl, today)?.let { t ->
            val p = s.profile!!
            val plan = PlanGenerator.generate(p, s.plan!!.startDay, s.settings.deloadEvery, t)
            s = s.copy(activeTier = t, plan = plan)
            s.quests.firstOrNull { it.id == "m:assess:$t" && it.status == QuestStatus.PENDING }?.let { q -> val o = completeInternal(s, q.id, today, now); s = o.state; eff += o.effects }
            s = hist(s, now, today, HistoryKind.PLAN, Txt("تفعيل رتبة التدريب ${Tiers.all[t].ar}", "Training tier ${Tiers.all[t].label} activated"))
            eff += UiEffect.TierActivated(t)
        }
        // level up
        val after = Ledger.level(s)
        if (after.level > s.highestLevelSeen) {
            val from = s.highestLevelSeen
            val newTitle = Leveling.titleMilestones().filter { it.first in (from + 1)..after.level }.lastOrNull()?.second
            eff += UiEffect.LevelUp(from, after.level, after.isMax, newTitle, Leveling.pointsEarned(after.level) - Leveling.pointsEarned(from), levelSource(before, s))
            s = hist(s, now, today, HistoryKind.LEVEL_UP, Txt("ارتفع المستوى إلى ${after.level}", "Level up to ${after.level}"))
        }
        // rewards & skills
        val r = Rewards.sync(s, today, s.highestLevelSeen)
        s = r.state.copy(highestLevelSeen = maxOf(s.highestLevelSeen, after.level))
        r.rankUps.forEach { eff += UiEffect.RankUp(it) }
        r.skillUps.forEach { (d, l) -> eff += UiEffect.SkillUp(d, l); s = hist(s, now, today, HistoryKind.ACHIEVEMENT, Txt("مهارة: ${d.name.ar} Lv.$l", "Skill: ${d.name.en} Lv.$l")) }
        r.badges.forEach { b -> s = s.copy(achievements = s.achievements + (b.id to now)); s = hist(s, now, today, HistoryKind.ACHIEVEMENT, Txt("شارة: ${b.name.ar}", "Badge: ${b.name.en}")) }
        if (r.grants.isNotEmpty()) eff += UiEffect.RewardReveal(r.grants)
        // v1.3.0: Shadow Army (sleep/revive/extraction notice) + Weekly Boss (damage = XP this week)
        com.ayman.solosystem.domain.shadows.Shadows.sync(s, today, now).let { (st, e) -> s = st; eff += e }
        com.ayman.solosystem.domain.boss.Boss.sync(s, today, now).let { (st, e) -> s = st; eff += e }
        if (!Countdown.allDone(before, today) && Countdown.allDone(s, today)) eff += UiEffect.DailyComplete
        if (!s.monarchAwakened && maxOf(s.highestLevelSeen, after.level) >= MONARCH_LEVEL) {
            s = hist(s.copy(monarchAwakened = true), now, today, HistoryKind.SYSTEM, Txt("تطوّر النظام: سمة الملك الظلّ", "The System evolved: Shadow Monarch theme"))
            eff += UiEffect.SystemAwakening
        }
        return Outcome(s, eff)
    }

    /** What pushed the level over: the newest XP entry → "[المهمة اليومية]" for daily/GOAL quests, the raid name, or its label. */
    fun levelSource(before: AppState, s: AppState): Txt? {
        val old = before.ledger.map { it.key }.toHashSet()
        val e = s.ledger.lastOrNull { it.xp > 0 && it.key !in old } ?: return null
        if (e.key.startsWith("raid:")) return e.label
        val qid = e.key.substringAfter(":")
        val q = s.quests.firstOrNull { it.id == qid }
        if (q != null && (q.type == QuestType.DAILY || GoalPlan.isGoal(q))) return Txt("المهمة اليومية", "Daily Quest")
        return q?.title ?: e.label
    }

    // ------------------------------------------------------------------ quests
    private fun bonusXp(s: AppState, q: Quest) = (q.xp * (Bonuses.xpPct(s, q.category) + com.ayman.solosystem.domain.shadows.Shadows.xpPct(s, q)) / 100.0).roundToInt()

    private fun completeInternal(s0: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id } ?: return Outcome(s0)
        if (q.status == QuestStatus.COMPLETED) return Outcome(s0)
        if (s0.ledger.any { it.key == "q:$id" }) return Outcome(s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(status = QuestStatus.COMPLETED) else it }))
        var s = s0
        val bonus = bonusXp(s, q)
        val gold = Leveling.goldFor(q.xp + bonus); val goldBonus = (gold * Bonuses.goldPct(s) / 100.0).roundToInt()
        s = s.copy(quests = s.quests.map { if (it.id == id) it.copy(status = QuestStatus.COMPLETED, completedMs = now, progress = maxOf(it.progress, it.target)) else it },
            ledger = s.ledger + XpEntry("q:$id", q.xp, Leveling.goldFor(q.xp), today, q.title, q.category) +
                (if (bonus > 0 || goldBonus > 0) listOf(XpEntry("sb:$id", bonus, gold - Leveling.goldFor(q.xp) + goldBonus, today, Txt("مكافأة المهارات: ${q.title.ar}", "Skill bonus: ${q.title.en}"), q.category)) else emptyList()))
        s = hist(s, now, today, HistoryKind.XP, Txt("أكملت: ${q.title.ar}", "Completed: ${q.title.en}"), q.xp + bonus)
        val eff = mutableListOf<UiEffect>(UiEffect.QuestReward(q, q.xp, bonus, gold + goldBonus))
        if (q.type == QuestType.PENALTY) {
            s = s.copy(redeemedDays = s.redeemedDays + q.redeemsDays)
            s = hist(s, now, today, HistoryKind.PENALTY_DONE, Txt("أكملت مهمة العقاب: نجاة", "Penalty Quest cleared: Survival"))
            eff += UiEffect.PenaltyCleared(q)
        }
        // full-day completion bonus (temporary pool, reversible)
        if (Rewards.allDailyDone(s, q.day) && s.bonus.none { it.key == "full:${q.day}" }) {
            val b = (FULL_DAY_BONUS * (1 + Bonuses.fullDayPct(s) / 100.0)).roundToInt()
            s = s.copy(bonus = s.bonus + BonusEntry("full:${q.day}", q.day, b))
            s = hist(s, now, today, HistoryKind.BONUS, Txt("مكافأة اليوم الكامل +$b XP (مؤقتة حتى نهاية الأسبوع)", "Full-day bonus +$b XP (pending until week end)"), b)
        }
        return Outcome(s, eff)
    }

    fun completeQuest(s: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s.quests.firstOrNull { it.id == id } ?: return Outcome(s)
        if (q.status != QuestStatus.PENDING) return Outcome(s)
        if (q.verify == Verify.COUNT) return logGoal(s, id, (q.target - q.progress).coerceAtLeast(0), today, now)
        if (q.verify != Verify.MANUAL) return Outcome(s, listOf(UiEffect.Toast(when (q.verify) {
            Verify.WORKOUT -> Txt("[SYSTEM] تكتمل هذه المهمة عند إنهاء حصة التدريب.", "[SYSTEM] This completes when you finish the training session.")
            Verify.CHECKIN -> Txt("[SYSTEM] سجّل الجاهزية لإكمال هذه المهمة.", "[SYSTEM] Log your readiness to complete this.")
            else -> Txt("[SYSTEM] تكتمل تلقائيًا من بياناتك.", "[SYSTEM] Completes automatically from your data.") })))
        if (q.day > today) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] هذه المهمة لليوم القادم.", "[SYSTEM] This quest is for an upcoming day."), blocked = true)))
        if (q.prerequisiteId != null && s.quests.none { it.id == q.prerequisiteId && it.status == QuestStatus.COMPLETED })
            return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] أكمل المهمة السابقة أولًا.", "[SYSTEM] Complete the prerequisite first."), blocked = true)))
        if (q.items.isNotEmpty() && q.items.any { !it.done }) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] أكمل كل أهداف المهمة أولًا.", "[SYSTEM] Finish every goal first."), blocked = true)))
        val o = completeInternal(s, id, today, now)
        return post(s, o.state, today, now, o.effects)
    }

    /** 1.0.x habit items retired in 1.1.0 (nutrition moved to the Healing Elixir section). */
    val RETIRED = setOf("learn", "walk", "walk_long", "priorities", "nutrition")

    /**
     * Idempotent data migration: drop today's/future pending retired items, close any unreviewed missed ones
     * (and refund their small temporary loss), and make today's remaining old habit items optional side tasks.
     * Completed history and earned XP are untouched.
     */
    fun migrateDailies(s: AppState, today: Long): AppState {
        val retired = { q: Quest -> q.type == QuestType.DAILY && q.id.startsWith("d:") && q.templateId in RETIRED }
        val drop = s.quests.filter { retired(it) && it.day >= today && it.status == QuestStatus.PENDING }.map { it.id }.toSet()
        val close = s.quests.filter { retired(it) && it.status == QuestStatus.MISSED && !it.reasonReviewed }.map { it.id }.toSet()
        val soften = s.quests.any { it.day >= today && it.type == QuestType.DAILY && it.id.startsWith("d:") && !GoalPlan.isGoal(it) && !it.optional && it.status == QuestStatus.PENDING }
        if (drop.isEmpty() && close.isEmpty() && !soften) return s
        return s.copy(
            quests = s.quests.filterNot { it.id in drop }.map { q ->
                when {
                    q.id in close -> q.copy(reason = "retired", reasonReviewed = true, optional = true)
                    q.day >= today && q.type == QuestType.DAILY && q.id.startsWith("d:") && !GoalPlan.isGoal(q) && q.status == QuestStatus.PENDING -> q.copy(optional = true)
                    else -> q
                }
            },
            bonus = s.bonus.filterNot { b -> close.any { b.key == "loss:$it" } })
    }

    /**
     * Log reps/metres on an anime GOAL row (negative = undo). Completes at target; may go beyond it.
     * Reaching ${'$'}{GoalPlan.OVER_FACTOR}× the target grants an overachievement bonus (reversible if undone).
     */
    fun logGoal(s0: AppState, id: String, amount: Int, today: Long, now: Long, force: Boolean = false, dayStartMs: Long? = null): Outcome {
        val o = logGoalInner(s0, id, amount, today, now, force)
        val (s, e) = secretCheck(o.state, today, now, dayStartMs ?: java.time.LocalDate.ofEpochDay(today).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        return Outcome(s, o.effects + e)
    }

    /**
     * SECRET QUEST (never announced or suggested): all 4 GOAL rows at ≥ 200 % of their ORIGINAL target (the requirement, not the
     * player's raised amount) before the ring deadline → the day converts permanently and a Secret chest is granted once (key secret:<day>).
     * System-lowered (pain/illness) days are not eligible.
     */
    fun secretCheck(s0: AppState, today: Long, now: Long, dayStartMs: Long): Pair<AppState, List<UiEffect>> {
        if (today in s0.secretDays) return s0 to emptyList()
        if (s0.history.any { it.day == today && it.kind == HistoryKind.PAIN }) return s0 to emptyList()   // pain stops everything
        val g = s0.quests.filter { it.day == today && GoalPlan.isGoal(it) }
        if (g.size != GoalPlan.Row.entries.size) return s0 to emptyList()
        if (g.any { it.lowered || it.status != QuestStatus.COMPLETED || it.progress < it.target * GoalPlan.OVER_FACTOR }) return s0 to emptyList()
        if (now > Countdown.deadlineMs(dayStartMs, s0.settings.deadlineMinutes) || Countdown.expiryKey(today) in s0.grantedKeys) return s0 to emptyList()
        val overCap = g.any { q -> val r = GoalPlan.row(q.templateId)!!; val i = GoalPlan.intensity(GoalPlan.variant(r, q.variant), q.target * GoalPlan.OVER_FACTOR)
            i > r.cap || (r == GoalPlan.Row.RUN && i > (GoalPlan.runEnergyCapM(s0, today) ?: Int.MAX_VALUE)) }
        var s = s0.copy(secretDays = s0.secretDays + today)
        s = hist(s, now, today, HistoryKind.SECRET, Txt("تم اكتشاف شرط خفي — تحوّلت المهمة اليومية إلى مهمة سرّية", "Hidden condition found — the daily quest became a SECRET QUEST"))
        val eff = mutableListOf<UiEffect>(UiEffect.SecretQuest(today, overCap, g.sortedBy { q -> GoalPlan.Row.entries.indexOf(GoalPlan.row(q.templateId)) }))
        // reward selection: exactly once per day (key secret:<day>); granted when the player picks it
        if ("secret:$today" !in s.grantedKeys && today !in s.secretClaims) {
            s = s.copy(grantedKeys = s.grantedKeys + "secret:$today", secretClaims = s.secretClaims + (today to SecretClaim(today, Raids.secretPoints(s))))
            eff += UiEffect.SecretReward(today)
        }
        return s to eff
    }

    private fun logGoalInner(s0: AppState, id: String, amount: Int, today: Long, now: Long, force: Boolean): Outcome {
        val q = s0.quests.firstOrNull { it.id == id } ?: return Outcome(s0)
        if (!GoalPlan.isGoal(q) || q.day != today || q.status !in setOf(QuestStatus.PENDING, QuestStatus.COMPLETED)) return Outcome(s0)
        val maxP = q.target * 5
        val np = (q.progress + amount).coerceIn(0, maxP)
        if (np == q.progress && !force) return Outcome(s0)
        var s = s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(progress = np) else it })
        val eff = mutableListOf<UiEffect>()
        if (q.status == QuestStatus.PENDING && np >= q.target) {
            val o = completeInternal(s, id, today, now); s = o.state; eff += o.effects
        } else if (q.status == QuestStatus.COMPLETED && np < q.target) {
            val keys = setOf("q:$id", "sb:$id", "ov:$id", "st:$id")
            val removed = s.ledger.filter { it.key in keys }.sumOf { it.xp }
            s = s.copy(quests = s.quests.map { if (it.id == id) it.copy(status = QuestStatus.PENDING, completedMs = null) else it },
                ledger = s.ledger.filterNot { it.key in keys }, bonus = s.bonus.filterNot { it.key == "full:${q.day}" })
            s = hist(s, now, today, HistoryKind.XP_REVERSED, Txt("ألغيت: ${q.title.ar}", "Unchecked: ${q.title.en}"), -removed)
            return Outcome(s)
        }
        // player-raised intensity: bonus once the raised amount is reached (keyed → idempotent, reversible)
        val sx = GoalPlan.stretchXp(q)
        val hasSt = s.ledger.any { it.key == "st:$id" }
        if (sx > 0 && np >= q.stretch && !hasSt) {
            s = s.copy(ledger = s.ledger + XpEntry("st:$id", sx, Leveling.goldFor(sx), today, Txt("شدة مرفوعة: ${q.title.ar}", "Raised intensity: ${q.title.en}"), q.category))
            s = hist(s, now, today, HistoryKind.BONUS, Txt("أنجزت الشدة المرفوعة [${np}/${q.stretch}] +$sx XP", "Raised intensity done [${np}/${q.stretch}] +$sx XP"), sx)
            eff += UiEffect.Toast(Txt("[SYSTEM] أنجزت الشدة التي اخترتها! +$sx XP.", "[SYSTEM] You hit the intensity you chose! +$sx XP."))
        } else if (hasSt && (sx == 0 || np < q.stretch)) s = s.copy(ledger = s.ledger.filterNot { it.key == "st:$id" })
        val over = np >= q.target * GoalPlan.OVER_FACTOR
        val hasOver = s.ledger.any { it.key == "ov:$id" }
        if (over && !hasOver) {
            val xp = (q.xp / 2).coerceAtLeast(5)
            s = s.copy(ledger = s.ledger + XpEntry("ov:$id", xp, Leveling.goldFor(xp) + 5, today, Txt("تجاوز الهدف: ${q.title.ar}", "Overachieved: ${q.title.en}"), q.category))
            s = hist(s, now, today, HistoryKind.BONUS, Txt("تجاوز الهدف [${np}/${q.target}] +$xp XP", "Overachievement [${np}/${q.target}] +$xp XP"), xp)
            eff += UiEffect.Toast(Txt("[SYSTEM] تجاوزت الهدف! مكافأة إضافية +$xp XP.", "[SYSTEM] Goal exceeded! Bonus reward +$xp XP."))
        } else if (!over && hasOver) s = s.copy(ledger = s.ledger.filterNot { it.key == "ov:$id" })
        return if (q.status == QuestStatus.PENDING && np >= q.target) post(s0, s, today, now, eff) else Outcome(s, eff)
    }

    /**
     * Player edit of an issued GOAL row: only raising intensity is allowed (more reps/distance or a harder variant).
     * Lowering is refused with a SYSTEM message — only the System lowers (pain / illness, see [systemLowerGoals]).
     * Completion stays judged against the original requirement, so raising is never risky; reaching the raised amount pays bonus XP.
     */
    fun editGoal(s0: AppState, id: String, variant: String, amount: Int, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id } ?: return Outcome(s0)
        if (!GoalPlan.isGoal(q) || q.day != today || q.status !in setOf(QuestStatus.PENDING, QuestStatus.COMPLETED)) return Outcome(s0)
        return when (val e = GoalPlan.edit(q, variant, amount)) {
            GoalPlan.Edit.Lower -> Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يمكن تخفيض شدة المهمة.", "[SYSTEM] Quest intensity cannot be lowered."), blocked = true)))
            GoalPlan.Edit.Invalid -> Outcome(s0)
            is GoalPlan.Edit.Ok -> {
                var s = s0.copy(quests = s0.quests.map { if (it.id == id) e.q else it })
                s = hist(s, now, today, HistoryKind.BONUS, Txt("رفعت الشدة: ${e.q.title.ar} ${GoalPlan.fmtV(GoalPlan.variantOf(e.q)!!, maxOf(e.q.stretch, e.q.target))}", "Raised intensity: ${e.q.title.en} ${GoalPlan.fmtV(GoalPlan.variantOf(e.q)!!, maxOf(e.q.stretch, e.q.target))}"))
                // re-evaluate completion / bonuses against the (unchanged-in-effort) requirement
                val o = logGoal(s, id, 0, today, now, force = true)
                Outcome(o.state, listOf(UiEffect.Toast(Txt("[SYSTEM] تم قبول رفع الشدة. الإنجاز يُحسب على الهدف الأصلي، والزيادة تمنحك مكافأة.", "[SYSTEM] Intensity raise accepted. Completion counts against the original target; the extra earns a bonus."))) + o.effects)
            }
        }
    }

    /** Pain button on a GOAL row: stop now. Pending → excused (no penalty); other rows lowered; no secret/over bonus chase today. */
    fun goalPain(s0: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id && GoalPlan.isGoal(it) && it.day == today } ?: return Outcome(s0)
        var s = s0
        if (q.status == QuestStatus.PENDING) s = setReason(s, id, MissReason.INJURY, today, now).state
        s = systemLowerGoals(s, today, now, Txt("ألم", "pain"))
        s = hist(s, now, today, HistoryKind.PAIN, Txt("ألم أثناء: ${q.title.ar} — توقّف", "Pain during: ${q.title.en} — stopped"))
        return Outcome(s, listOf(UiEffect.Safety(safetyText(SymptomType.SHARP_PAIN), false)))
    }

    /** System-only lowering of today's pending GOAL rows after a pain report / illness / injury. */
    fun systemLowerGoals(s0: AppState, today: Long, now: Long, why: Txt): AppState {
        val ids = s0.quests.filter { GoalPlan.isGoal(it) && it.day == today && it.status == QuestStatus.PENDING && !it.lowered }.map { it.id }
        if (ids.isEmpty()) return s0
        var s = s0.copy(quests = s0.quests.map { if (it.id in ids) GoalPlan.lowered(s0, it, today) else it })
        s = hist(s, now, today, HistoryKind.PAIN, Txt("خفّض النظام مهام GOAL (${why.ar})", "System lowered GOAL rows (${why.en})"))
        ids.forEach { id -> val o = logGoal(s, id, 0, today, now, force = true); s = o.state }
        return s
    }

    /** Unchecking reverses exactly what was granted (keyed by quest id). Only for today's manual quests. */
    fun uncheckQuest(s0: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id } ?: return Outcome(s0)
        if (q.status != QuestStatus.COMPLETED || q.verify != Verify.MANUAL || q.day != today) return Outcome(s0)
        val removed = s0.ledger.filter { it.key == "q:$id" || it.key == "sb:$id" }.sumOf { it.xp }
        var s = s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(status = QuestStatus.PENDING, completedMs = null, progress = 0, items = it.items.map { i -> i.copy(done = false) }) else it },
            ledger = s0.ledger.filterNot { it.key == "q:$id" || it.key == "sb:$id" },
            bonus = s0.bonus.filterNot { it.key == "full:${q.day}" })
        if (q.type == QuestType.PENALTY) s = s.copy(redeemedDays = s.redeemedDays - q.redeemsDays.toSet())
        s = hist(s, now, today, HistoryKind.XP_REVERSED, Txt("ألغيت: ${q.title.ar}", "Unchecked: ${q.title.en}"), -removed)
        return Outcome(s)
    }

    fun togglePenaltyItem(s: AppState, id: String, idx: Int, today: Long, now: Long): Outcome {
        val q = s.quests.firstOrNull { it.id == id } ?: return Outcome(s)
        if (q.status != QuestStatus.PENDING || q.day > today || idx !in q.items.indices) return Outcome(s)
        val items = q.items.mapIndexed { i, it -> if (i == idx) it.copy(done = !it.done) else it }
        val s2 = s.copy(quests = s.quests.map { if (it.id == id) it.copy(items = items, progress = items.count { i -> i.done }, target = items.size) else it })
        if (items.all { it.done }) { val o = completeInternal(s2, id, today, now); return post(s, o.state, today, now, o.effects) }
        return Outcome(s2)
    }

    /** Stopping a penalty because of pain is never a failure: it becomes an optional recovery quest. */
    fun stopPenaltyForPain(s0: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id && it.type == QuestType.PENALTY } ?: return Outcome(s0)
        if (q.status != QuestStatus.PENDING) return Outcome(s0)
        var s = s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(status = QuestStatus.EXCUSED, reason = "pain", reasonReviewed = true) else it })
        val rq = Penalty.reflectionQuest(today, q.redeemsDays, converted = true)
        if (s.quests.none { it.id == rq.id }) s = s.copy(quests = s.quests + rq)
        s = hist(s, now, today, HistoryKind.PENALTY_STOPPED, Txt("أوقفت مهمة العقاب بسبب الألم — ليس فشلًا", "Penalty stopped for pain — not a failure"))
        return post(s0, s, today, now, listOf(UiEffect.Safety(safetyText(SymptomType.SHARP_PAIN), false)))
    }

    fun postpone(s: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s.quests.firstOrNull { it.id == id } ?: return Outcome(s)
        if (q.status != QuestStatus.PENDING || q.type == QuestType.MAIN || q.type == QuestType.PENALTY || q.day != today) return Outcome(s)
        if (q.postponeCount >= 2) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يمكن تأجيل هذه المهمة أكثر.", "[SYSTEM] This quest can't be postponed again."), blocked = true)))
        val copy = q.copy(id = "${q.id.substringBefore("~")}~p${q.postponeCount + 1}", day = today + 1, deadlineDay = today + 1, postponeCount = q.postponeCount + 1)
        var s2 = s.copy(quests = s.quests.map { if (it.id == id) it.copy(status = QuestStatus.POSTPONED, reasonReviewed = true) else it } + copy)
        s2 = hist(s2, now, today, HistoryKind.QUEST_POSTPONED, Txt("أجّلت: ${q.title.ar} إلى الغد", "Postponed: ${q.title.en} to tomorrow"))
        return Outcome(s2, listOf(UiEffect.Toast(Txt("[SYSTEM] تم التأجيل إلى الغد.", "[SYSTEM] Postponed to tomorrow."))))
    }

    /** Excuse (illness, injury, emergency) or record a reason for a missed quest. Excused never escalates; earned XP untouched. */
    fun setReason(s0: AppState, id: String, reason: MissReason, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id } ?: return Outcome(s0)
        if (q.status != QuestStatus.MISSED && q.status != QuestStatus.PENDING) return Outcome(s0)
        var s = s0
        val eff = mutableListOf<UiEffect>()
        if (reason.excusable) {
            s = s.copy(quests = s.quests.map { if (it.id == id) it.copy(status = QuestStatus.EXCUSED, reason = reason.name, reasonReviewed = true) else it },
                bonus = s.bonus.filterNot { it.key == "loss:$id" })
            s = hist(s, now, today, HistoryKind.QUEST_EXCUSED, Txt("معذور (${reason.ar}): ${q.title.ar}", "Excused (${reason.en}): ${q.title.en}"))
            if ((reason == MissReason.SICK || reason == MissReason.INJURY) && q.day == today) s = systemLowerGoals(s, today, now, Txt(reason.ar, reason.en))
            eff += UiEffect.Toast(Txt("[SYSTEM] تم تسجيل العذر. لا عقاب. اعتنِ بنفسك.", "[SYSTEM] Excuse recorded. No penalty. Take care of yourself."))
        } else {
            if (q.status == QuestStatus.PENDING) return Outcome(s0)
            s = s.copy(quests = s.quests.map { if (it.id == id) it.copy(reason = reason.name, reasonReviewed = true) else it })
            if (q.day >= today - 2 && q.type == QuestType.DAILY && q.verify == Verify.MANUAL) {
                val mk = q.copy(id = "mk:${q.id}", day = today, deadlineDay = today, status = QuestStatus.PENDING, optional = true, recoveryFor = q.id,
                    xp = maxOf(1, q.xp / 2), title = Txt("تعويض: ${q.title.ar}", "Make-up: ${q.title.en}"), reason = null, reasonReviewed = false)
                if (s.quests.none { it.id == mk.id }) s = s.copy(quests = s.quests + mk)
            }
            eff += UiEffect.Toast(Txt("[SYSTEM] تم تسجيل السبب. أُضيفت مهمة تعويض اختيارية.", "[SYSTEM] Reason saved. An optional make-up quest was added."))
        }
        val (s2, issued) = Penalty.sync(s, today)
        s = s2
        if (issued != null) eff += UiEffect.PenaltyIssued(issued)
        return post(s0, s, today, now, eff)
    }

    fun emergencyRespond(s0: AppState, id: String, action: String, today: Long, now: Long): Outcome {
        val q = s0.quests.firstOrNull { it.id == id && it.type == QuestType.EMERGENCY } ?: return Outcome(s0)
        if (q.status != QuestStatus.OFFERED) return Outcome(s0)
        val s = when (action) {
            "accept" -> s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(status = QuestStatus.PENDING) else it })
            "postpone" -> if (q.postponeCount >= 1) s0 else s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(day = today + 1, deadlineDay = today + 1, postponeCount = 1) else it })
            else -> hist(s0.copy(quests = s0.quests.map { if (it.id == id) it.copy(status = QuestStatus.DECLINED, reasonReviewed = true) else it }), now, today, HistoryKind.QUEST_DECLINED, Txt("رفضت: ${q.title.ar}", "Declined: ${q.title.en}"))
        }
        return Outcome(s)
    }

    private val rerollPools: Map<String, List<Pair<Txt, Txt>>> = mapOf(
        "nutrition" to listOf(Txt("مصدر بروتين في كل وجبة رئيسية", "A protein source at each main meal") to Txt("مثل البيض، البقول، الدجاج أو الألبان.", "E.g. eggs, legumes, chicken or dairy."),
            Txt("حصتان من الخضار أو الفواكه", "Two servings of vegetables or fruit") to Txt("أضفها إلى وجباتك.", "Add them to your meals."),
            Txt("حضّر وجبة الغد مسبقًا", "Prep tomorrow's meal") to Txt("التخطيط يقلل القرارات المتسرعة.", "Planning reduces rushed choices."),
            Txt("وجبة بطيئة بلا شاشة", "One slow meal without screens") to Txt("كل ببطء وانتبه للشبع.", "Eat slowly and notice fullness."),
            Txt("استبدل مشروبًا محلّى", "Swap one sugary drink") to Txt("اختر ماءً أو شايًا بدون سكر.", "Choose water or unsweetened tea.")),
        "learn" to listOf(Txt("تعلّم 15 دقيقة", "Learn for 15 minutes") to Txt("اقرأ أو تعلم مهارة تطورك.", "Read or practise a skill that grows you."),
            Txt("ادرس تقنية تمرين", "Study an exercise technique") to Txt("افتح تفاصيل أحد تمارينك وراجع نقاط التقنية.", "Open one exercise's details and review the cues."),
            Txt("10 صفحات", "10 pages") to Txt("اقرأ 10 صفحات من كتاب مفيد.", "Read 10 pages of a useful book."),
            Txt("لغة جديدة", "New language") to Txt("15 دقيقة لتعلم كلمات جديدة.", "15 minutes learning new words.")),
        "priorities" to listOf(Txt("أولويات اليوم", "Today's priorities") to Txt("اكتب أهم 3 مهام وابدأ بالأولى.", "Write your top 3 tasks and start the first."),
            Txt("ترتيب المكان", "Tidy your space") to Txt("رتّب غرفتك أو مكتبك 10 دقائق.", "Tidy your room or desk for 10 minutes."),
            Txt("بلا هاتف ساعة", "One phone-free hour") to Txt("ساعة تركيز بدون إشعارات.", "One focused hour without notifications.")),
        "walk" to listOf(Txt("مشي خفيف", "Easy walk") to Txt("امشِ 10 دقائق بوتيرة مريحة.", "Walk 10 minutes at an easy pace."),
            Txt("الدرج بدل المصعد", "Stairs over elevator") to Txt("استعمل الدرج اليوم كلما أمكن، بوتيرة مريحة.", "Take the stairs today when you can, at an easy pace."),
            Txt("مشي بعد الأكل", "Post-meal stroll") to Txt("10 دقائق مشي بعد وجبة.", "10 minutes walking after a meal."))
    )

    fun rerollsLeft(s: AppState, today: Long) = Bonuses.rerollsPerDay(s) - (s.rerollsUsed[today] ?: 0)

    fun reroll(s: AppState, id: String, today: Long, now: Long): Outcome {
        val q = s.quests.firstOrNull { it.id == id } ?: return Outcome(s)
        val pool = rerollPools[q.templateId] ?: return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يمكن إعادة سحب هذه المهمة.", "[SYSTEM] This quest can't be rerolled."), blocked = true)))
        if (q.status != QuestStatus.PENDING || q.day != today) return Outcome(s)
        val useToken = rerollsLeft(s, today) <= 0
        if (useToken && (s.inventory["token_reroll"] ?: 0) <= 0) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] لا توجد إعادات سحب متاحة اليوم.", "[SYSTEM] No rerolls left today."), blocked = true)))
        val cur = pool.indexOfFirst { it.first.en == q.objective.en || it.first.en == q.title.en }
        val pick = pool[(maxOf(cur, 0) + 1 + (now % (pool.size - 1).coerceAtLeast(1)).toInt()) % pool.size].let { if (it.first.en == q.title.en) pool[(pool.indexOf(it) + 1) % pool.size] else it }
        var s2 = s.copy(quests = s.quests.map { if (it.id == id) it.copy(title = pick.first, objective = pick.second) else it })
        s2 = if (useToken) Rewards.consume(s2, "token_reroll")!! else s2.copy(rerollsUsed = (s2.rerollsUsed.filterKeys { it >= today - 7 }) + (today to (s2.rerollsUsed[today] ?: 0) + 1))
        return Outcome(s2, listOf(UiEffect.Toast(Txt("[SYSTEM] تمت إعادة سحب المهمة.", "[SYSTEM] Quest rerolled."))))
    }

    // ------------------------------------------------------------------ check-in
    fun checkIn(s0: AppState, c: CheckIn, today: Long, now: Long): Outcome {
        var s = s0.copy(checkIns = s0.checkIns.filterNot { it.day == c.day } + c)
        val eff = mutableListOf<UiEffect>()
        s.quests.firstOrNull { it.day == today && it.templateId == "checkin" && it.status == QuestStatus.PENDING }?.let { val o = completeInternal(s, it.id, today, now); s = o.state; eff += o.effects }
        when (Readiness.advice(c)) {
            ReadinessAdvice.RECOVERY_ONLY -> eff += UiEffect.Safety(Txt("[SYSTEM] أبلغت عن ألم ملحوظ. اليوم للتعافي فقط: مشي خفيف وحركة لطيفة. إذا استمر الألم أو اشتد فاستشر مختصًا.", "[SYSTEM] You reported notable pain. Today is recovery only: easy walking and gentle mobility. If pain persists or worsens, consult a professional."), false)
            ReadinessAdvice.REDUCED -> eff += UiEffect.Toast(Txt("[SYSTEM] جاهزية منخفضة: سيتم تخفيف حصة اليوم تلقائيًا.", "[SYSTEM] Low readiness: today's session will be reduced automatically."))
            else -> {}
        }
        if (c.pain >= 2 && c.day == today) s = systemLowerGoals(s, today, now, Txt("ألم", "pain"))
        val (s2, issued) = Penalty.sync(s, today); s = s2
        if (issued != null) eff += UiEffect.PenaltyIssued(issued)
        return post(s0, s, today, now, eff)
    }

    // ------------------------------------------------------------------ workouts
    fun trainingQuestToday(s: AppState, today: Long) = s.quests.firstOrNull { it.day == today && it.verify == Verify.WORKOUT && it.status == QuestStatus.PENDING }

    fun sessionTypeFor(s: AppState, today: Long): DayType? {
        val plan = s.plan ?: return null
        trainingQuestToday(s, today)?.let { q -> q.templateId.substringAfter("train:", "").takeIf { it.isNotEmpty() }?.let { n -> return DayType.valueOf(n) } }
        return PlanGenerator.dayPlan(plan, today).type.takeIf { it != DayType.REST }
    }

    fun startSession(s: AppState, today: Long, now: Long, extraType: DayType? = null): Outcome {
        val plan = s.plan ?: return Outcome(s)
        s.sessions.firstOrNull { !it.finished }?.let { return Outcome(s) }
        val type = extraType ?: sessionTypeFor(s, today) ?: return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] اليوم يوم تعافٍ. لا حصة مخططة.", "[SYSTEM] Rest day. No session planned."))))
        val c = s.checkIns.lastOrNull { it.day == today }
        val adv = Readiness.advice(c)
        if (adv == ReadinessAdvice.RECOVERY_ONLY) return Outcome(s, listOf(UiEffect.Safety(Txt("[SYSTEM] بسبب الألم المسجل اليوم، لا يُنصح بالتدريب الشاق. يمكنك اعتبار المهمة معذورة (إصابة) والقيام بالمشي الخفيف.", "[SYSTEM] Because of today's logged pain, hard training isn't advised. You can excuse the quest (injury) and do easy walking instead."), false)))
        val day = plan.week.firstOrNull { it.type == type } ?: return Outcome(s)
        val deload = PlanGenerator.isDeloadWeek(plan, today) || Readiness.deloadRecommended(s.checkIns, today)
        val reduced = !deload && adv == ReadinessAdvice.REDUCED
        val entries = day.items.map { pr0 ->
            val pr = when { deload -> PlanGenerator.deload(pr0); reduced -> PlanGenerator.reduced(pr0); else -> pr0 }
            val prog = s.progress[pr.exerciseId]
            val (lo, _) = Progression.repRange(pr, prog)
            SessionEntry(pr, pr.exerciseId, List(pr.sets) { SetLog(reps = lo, loadKg = prog?.targetLoadKg) })
        }
        val ses = WorkoutSession("w:$today:$now", today, type, deload, reduced, entries, now)
        return Outcome(s.copy(sessions = s.sessions + ses), if (deload) listOf(UiEffect.Toast(Txt("[SYSTEM] أسبوع تخفيف: حجم أقل وجهد RPE 6.", "[SYSTEM] Deload: less volume, RPE 6."))) else emptyList())
    }

    private fun updSession(s: AppState, id: String, f: (WorkoutSession) -> WorkoutSession) = s.copy(sessions = s.sessions.map { if (it.id == id && !it.finished) f(it) else it })

    fun updateSet(s: AppState, sid: String, entry: Int, set: Int, log: SetLog) = updSession(s, sid) { w ->
        w.copy(entries = w.entries.mapIndexed { i, e -> if (i != entry) e else e.copy(sets = e.sets.mapIndexed { j, x -> if (j == set) log.copy(rpe = log.rpe?.let { Progression.clampRpe(it) }, reps = log.reps.coerceIn(0, 999)) else x }) })
    }
    fun addSet(s: AppState, sid: String, entry: Int) = updSession(s, sid) { w -> w.copy(entries = w.entries.mapIndexed { i, e -> if (i != entry || e.sets.size >= 10) e else e.copy(sets = e.sets + (e.sets.lastOrNull()?.copy(done = false) ?: SetLog())) }) }

    fun swapOptions(s: AppState, exerciseId: String): List<Exercise> {
        val p = s.profile ?: return emptyList(); val ex = ExerciseLibrary.get(exerciseId) ?: return emptyList()
        return ExerciseLibrary.all.filter { it.pattern == ex.pattern && it.id != ex.id && ExerciseLibrary.available(it, p.equipment) && ExerciseLibrary.safeFor(it, p.limitations) && it.id !in p.avoidExerciseIds }
            .sortedBy { kotlin.math.abs(it.level - ex.level) }
    }

    fun swap(s: AppState, sid: String, entry: Int, newId: String) = updSession(s, sid) { w ->
        w.copy(entries = w.entries.mapIndexed { i, e -> if (i != entry) e else {
            val ex = ExerciseLibrary.get(newId)
            val pr = e.prescription.copy(exerciseId = newId, timed = ex?.timed ?: e.prescription.timed)
            SessionEntry(pr, newId, List(pr.sets) { SetLog(reps = pr.repMin, loadKg = s.progress[newId]?.targetLoadKg) }, note = "swapped:${e.exerciseId}")
        } })
    }

    fun safetyText(t: SymptomType): Txt = when (t) {
        SymptomType.CHEST_PAIN, SymptomType.DIZZINESS, SymptomType.BREATHLESS -> Txt(
            "[SYSTEM] توقف عن التمرين الآن واجلس واسترح. إذا كان لديك ألم أو ضيق في الصدر، أو دوخة شديدة، أو ضيق تنفس لا يزول بالراحة — اطلب مساعدة طبية فورًا. تم إيقاف الحصة واعتبار مهمة اليوم معذورة.",
            "[SYSTEM] Stop exercising now, sit and rest. If you have chest pain or tightness, severe dizziness, or breathlessness that doesn't settle with rest — seek medical help immediately. Session stopped and today's quest excused.")
        SymptomType.SHARP_PAIN, SymptomType.JOINT_PAIN -> Txt(
            "[SYSTEM] أوقف هذا التمرين. لا تتدرب على ألم حاد أو ألم مفصلي. يمكنك اختيار بديل لا يسبب ألمًا أو إنهاء الحصة. إذا استمر الألم، استشر مختصًا. التوقف ليس فشلًا.",
            "[SYSTEM] Stop this exercise. Don't train through sharp or joint pain. Pick a pain-free alternative or end the session. If pain persists, see a professional. Stopping is not failure.")
        SymptomType.MILD_DISCOMFORT -> Txt(
            "[SYSTEM] خفّف الحمل أو قلّل مدى الحركة أو اختر بديلًا. إذا تحوّل الانزعاج إلى ألم، توقف.",
            "[SYSTEM] Reduce the load or range of motion, or pick an alternative. If discomfort turns into pain, stop.")
    }

    fun reportPain(s0: AppState, sid: String, entry: Int?, type: SymptomType, area: Limitation?, addLimitation: Boolean, today: Long, now: Long): Outcome {
        val w = s0.sessions.firstOrNull { it.id == sid && !it.finished } ?: return Outcome(s0)
        val exId = entry?.let { w.entries.getOrNull(it)?.exerciseId }
        val severe = type == SymptomType.CHEST_PAIN || type == SymptomType.DIZZINESS || type == SymptomType.BREATHLESS
        val stopEx = type == SymptomType.SHARP_PAIN || type == SymptomType.JOINT_PAIN
        var s = updSession(s0, sid) { x ->
            x.copy(painReports = x.painReports + PainReport(now, exId, area, type, if (severe) "stop_session" else if (stopEx) "stop_exercise" else "modify"),
                entries = x.entries.mapIndexed { i, e -> if (i == entry) e.copy(pain = true, status = if (stopEx || severe) EntryStatus.STOPPED else e.status) else e },
                stoppedForSafety = x.stoppedForSafety || severe, finishedMs = if (severe) now else x.finishedMs)
        }
        s = hist(s, now, today, HistoryKind.PAIN, Txt("بلاغ: ${type.ar}", "Report: ${type.en}"))
        if (severe) trainingQuestToday(s, today)?.let { q -> s = s.copy(quests = s.quests.map { if (it.id == q.id) it.copy(status = QuestStatus.EXCUSED, reason = "pain", reasonReviewed = true) else it }) }
        if (stopEx || severe) s = systemLowerGoals(s, today, now, Txt(type.ar, type.en))
        if (addLimitation && area != null) s = updateProfile(s, s.profile!!.copy(limitations = s.profile!!.limitations + area), today, now).state
        if (exId != null && (stopEx || severe)) s = s.copy(progress = s.progress + (exId to (s.progress[exId] ?: ExerciseProgress(exId)).copy(successStreak = 0)))
        return post(s0, s, today, now, listOf(UiEffect.Safety(safetyText(type), severe, if (stopEx) entry else null)))
    }

    fun discardSession(s: AppState, sid: String) = s.copy(sessions = s.sessions.filterNot { it.id == sid && !it.finished })

    fun finishSession(s0: AppState, sid: String, today: Long, now: Long): Outcome {
        val w = s0.sessions.firstOrNull { it.id == sid && !it.finished } ?: return Outcome(s0)
        val doneSets = w.entries.sumOf { e -> e.sets.count { it.done } }
        if (doneSets == 0) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] لم تسجل أي مجموعة بعد.", "[SYSTEM] No sets logged yet."), blocked = true)))
        val fin = w.copy(finishedMs = now, entries = w.entries.map { if (it.status == EntryStatus.ACTIVE && it.sets.any { x -> x.done }) it.copy(status = EntryStatus.DONE) else it })
        var s = s0.copy(sessions = s0.sessions.map { if (it.id == sid) fin else it })
        val p = s.profile!!
        val eff = mutableListOf<UiEffect>()
        if (!fin.deload) fin.entries.forEach { e -> if (e.sets.any { it.done } || e.pain) s = s.copy(progress = s.progress + (e.exerciseId to Progression.evaluate(e, s.progress[e.exerciseId], p, today))) }
        val prs = Progression.detectPrs(fin, s.prs)
        if (prs.isNotEmpty()) {
            s = s.copy(prs = s.prs + prs)
            prs.forEach { pr -> s = hist(s, now, today, HistoryKind.PR, Txt("رقم قياسي: ${ExerciseLibrary.get(pr.exerciseId)?.ar ?: pr.exerciseId}", "PR: ${ExerciseLibrary.get(pr.exerciseId)?.en ?: pr.exerciseId}")) }
            eff += UiEffect.Toast(Txt("[SYSTEM] رقم قياسي جديد ×${prs.size}!", "[SYSTEM] New personal record ×${prs.size}!"))
        }
        s = hist(s, now, today, HistoryKind.WORKOUT, Txt("أنهيت حصة ${fin.dayType.ar}", "Finished ${fin.dayType.en} session"))
        trainingQuestToday(s, today)?.let { q -> val o = completeInternal(s, q.id, today, now); s = o.state; eff += o.effects }
        return post(s0, s, today, now, eff)
    }

    fun acceptSuggestion(s: AppState, exId: String): AppState { val (pl, pr) = Progression.accept(s.plan!!, s.progress, exId); return s.copy(plan = pl, progress = pr) }
    fun dismissSuggestion(s: AppState, exId: String) = s.copy(progress = Progression.dismiss(s.progress, exId))

    fun recordAssessment(s0: AppState, a: Assessment, today: Long, now: Long): Outcome {
        val s = hist(s0.copy(assessments = s0.assessments + a), now, today, HistoryKind.SYSTEM, Txt("تم تسجيل اختبار الرتبة", "Assessment recorded"))
        val o = post(s0, s, today, now, emptyList())
        return if (o.effects.none { it is UiEffect.TierActivated }) o.copy(effects = o.effects + UiEffect.Toast(Txt("[SYSTEM] تم الحفظ. المعايير لم تكتمل بعد — واصل التدريب.", "[SYSTEM] Saved. Benchmarks not met yet — keep training."))) else o
    }

    // ------------------------------------------------------------------ profile, attributes, shop, settings
    fun updateProfile(s: AppState, p: PlayerProfile, today: Long, now: Long): Outcome {
        val plan = PlanGenerator.generate(p, s.plan?.startDay ?: today, s.settings.deloadEvery, s.activeTier)
        var st = s.copy(profile = p, plan = plan)
        // refresh today's untouched dailies so they match the new plan
        val keep = st.quests.filterNot { it.day == today && it.type == QuestType.DAILY && it.status == QuestStatus.PENDING && it.id.startsWith("d:") }
        val fresh = QuestGenerator.daily(st, today).filter { f -> keep.none { it.id == f.id } }
        st = st.copy(quests = keep + fresh)
        st = hist(st, now, today, HistoryKind.PLAN, Txt("تم تحديث الخطة", "Plan updated"))
        return Outcome(st, listOf(UiEffect.Toast(Txt("[SYSTEM] تم تحديث الملف والخطة.", "[SYSTEM] Profile and plan updated."))))
    }

    fun allocate(s: AppState, a: Attr, today: Long, now: Long): AppState {
        if (Ledger.pointsAvailable(s) <= 0) return s
        val x = s.allocated
        val n = when (a) { Attr.STR -> x.copy(str = x.str + 1); Attr.AGI -> x.copy(agi = x.agi + 1); Attr.VIT -> x.copy(vit = x.vit + 1); Attr.END -> x.copy(end = x.end + 1); Attr.DISC -> x.copy(disc = x.disc + 1) }
        return hist(s.copy(allocated = n), now, today, HistoryKind.ATTRIBUTE, Txt("نقطة إلى ${a.name}", "Point to ${a.name}"))
    }

    fun shopLocked(s: AppState, today: Long): Boolean =
        s.quests.any { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING && !it.optional && it.day <= today } ||
            s.quests.any { it.status == QuestStatus.MISSED && !it.reasonReviewed && !it.optional && it.day >= today - 7 }

    fun addShopReward(s: AppState, name: String, cost: Int, now: Long): AppState {
        val n = name.trim().take(40); if (n.isEmpty() || cost !in 1..100000) return s
        return s.copy(rewards = s.rewards + ShopReward("u:$now", n, cost))
    }
    fun removeShopReward(s: AppState, id: String) = s.copy(rewards = s.rewards.filterNot { it.id == id })
    fun buy(s: AppState, id: String, today: Long, now: Long): Outcome {
        val r = s.rewards.firstOrNull { it.id == id } ?: return Outcome(s)
        if (shopLocked(s, today)) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يمكنك الشراء قبل إنهاء مهمة العقاب أو مراجعة المهام الفائتة.", "[SYSTEM] You can't buy until the penalty quest is cleared or missed quests are reviewed."), blocked = true)))
        if (Ledger.goldBalance(s) < r.cost) return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] الذهب غير كافٍ.", "[SYSTEM] Not enough gold."), blocked = true)))
        val st = hist(s.copy(purchases = s.purchases + Purchase(r.id, r.name, r.cost, now)), now, today, HistoryKind.PURCHASE, Txt("اشتريت: ${r.name}", "Bought: ${r.name}"))
        return Outcome(st, listOf(UiEffect.Toast(Txt("[SYSTEM] تم الشراء: ${r.name}. استمتع بمكافأتك!", "[SYSTEM] Purchased: ${r.name}. Enjoy your reward!"))))
    }

    /** DEBUG BUILDS ONLY (called behind BuildConfig.DEBUG; stripped from release): grant the XP needed to reach [level]
     *  through the normal ledger + post() path, so level-up and the level-100 awakening fire exactly like in real play. */
    fun debugGrantToLevel(s0: AppState, level: Int, today: Long, now: Long): Outcome {
        val need = Leveling.totalXpFor(level) - Ledger.totalXp(s0)
        if (need <= 0) return post(s0, s0, today, now, emptyList())
        val s = s0.copy(ledger = s0.ledger + XpEntry("debug:$now", need, 0, today, Txt("تجربة (نسخة المطوّر)", "Debug grant")))
        return post(s0, s, today, now, emptyList())
    }

    fun updateSettings(s0: AppState, n: Settings, today: Long, now: Long): Outcome {
        var s = s0.copy(settings = n.copy(graceDaysPerWeek = n.graceDaysPerWeek.coerceIn(0, 3), deloadEvery = n.deloadEvery.coerceIn(4, 8), deadlineMinutes = n.deadlineMinutes.coerceIn(17 * 60, 1440)))
        if (n.deloadEvery != s0.settings.deloadEvery && s.profile != null) s = s.copy(plan = PlanGenerator.generate(s.profile!!, s.plan!!.startDay, s.settings.deloadEvery, s.activeTier))
        val eff = mutableListOf<UiEffect>()
        if (n.penaltyMode != s0.settings.penaltyMode) {
            // re-issue any untouched penalty under the new mode
            s = s.copy(quests = s.quests.filterNot { it.type == QuestType.PENALTY && it.status == QuestStatus.PENDING && it.items.none { i -> i.done } && it.day >= today })
            val (s2, issued) = Penalty.sync(s, today); s = s2; if (issued != null) eff += UiEffect.PenaltyIssued(issued)
        }
        return Outcome(s, eff)
    }

    fun addWeight(s: AppState, kg: Double, today: Long) = if (kg in 25.0..350.0) com.ayman.solosystem.domain.nutrition.Nutrition.review(s.copy(bodyWeights = s.bodyWeights.filterNot { it.day == today } + WeightEntry(today, kg)), today) else s

    // ------------------------------------------------------------------ rewards
    fun openBox(s0: AppState, boxId: String, today: Long, now: Long): Outcome {
        val op = Rewards.openBoxEx(s0, boxId, today, GoalPlan.tier(s0)) ?: return Outcome(s0)
        val s = op.first; val g = op.second
        var st = hist(s, now, today, HistoryKind.ACHIEVEMENT, Txt("فتحت ${RewardCatalog.get(boxId)!!.name.ar}: ${RewardCatalog.get(g.rewardId)!!.name.ar}", "Opened ${RewardCatalog.get(boxId)!!.name.en}: ${RewardCatalog.get(g.rewardId)!!.name.en}"))
        if (op.cursed) st = hist(st, now, today, HistoryKind.SYSTEM, Txt("لعنة: غدًا صفوف GOAL +10 % (ضمن حدود السلامة)", "Curse: tomorrow GOAL rows +10 % (within safety caps)"))
        val key = if (g.rewardId == "key_raid") st.raidKeys.lastOrNull() else null
        val o = post(s0, st, today, now, listOf(UiEffect.LootOpened(g, RewardCatalog.get(boxId)!!, op.cursed, key)))
        return o
    }

    fun equip(s: AppState, id: String?) = Rewards.equip(s, id)


    // ------------------------------------------------------------------ secret quest rewards
    /**
     * Claim a Secret Quest reward set once: (1) Full Recovery token (in-game rest day), (2) +N attribute points,
     * (3) the chosen box ("blessed" safe odds / "cursed" higher legendary + possible curse) which opens immediately.
     */
    fun claimSecret(s0: AppState, day: Long, choice: String, today: Long, now: Long): Outcome {
        val c = s0.secretClaims[day] ?: return Outcome(s0)
        if (c.claimed) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] تم استلام هذه المكافآت بالفعل.", "[SYSTEM] These rewards were already claimed."), blocked = true)))
        val box = if (choice == "cursed") "box_cursed" else "box_blessed"
        var s = s0.copy(secretClaims = s0.secretClaims + (day to c.copy(claimed = true, choice = choice)), bonusPoints = s0.bonusPoints + c.points)
        s = Rewards.grant(s, "secret:$day:heal", "token_fullheal", today, Txt("مهمة سرّية", "Secret Quest")).first
        s = Rewards.grant(s, "secret:$day:box", box, today, Txt("مهمة سرّية", "Secret Quest")).first
        s = hist(s, now, today, HistoryKind.SECRET, Txt("مكافآت المهمة السرّية: الشفاء الكامل، +${c.points} نقاط قدرة، ${RewardCatalog.get(box)!!.name.ar}",
            "Secret Quest rewards: Full Recovery, +${c.points} ability points, ${RewardCatalog.get(box)!!.name.en}"))
        val o = openBox(s, box, today, now)
        return Outcome(o.state, o.effects + proposeAssess(o.state, today))
    }

    /** After secret quests / raid clears the system may propose a tier assessment (max once per 14 days, never on unsafe days). */
    fun proposeAssess(s: AppState, today: Long): List<UiEffect> {
        val last = maxOf(s.assessProposedDay ?: Long.MIN_VALUE / 2, s.assessments.maxOfOrNull { it.day } ?: Long.MIN_VALUE / 2)
        val wins = s.secretDays.count { it > last } + s.raids.count { it.cleared && it.day > last }
        if (today - last < 14 || wins < 2 || Penalty.unsafeToday(s, today)) return emptyList()
        return listOf(UiEffect.AssessPropose(Txt("أنهيت $wins مهمة سرّية/غارة منذ آخر تقييم. قد تكون مستعدًا لرتبة أعلى.", "You cleared $wins secret quests/raids since your last assessment. You may be ready for a higher tier.")))
    }
    fun markAssessProposed(s: AppState, today: Long) = s.copy(assessProposedDay = today)

    // ------------------------------------------------------------------ raids
    fun raidGate(s: AppState, keyId: String, today: Long): Outcome {
        val k = Raids.activeKeys(s, today).firstOrNull { it.id == keyId } ?: return Outcome(s, listOf(UiEffect.Toast(Txt("[SYSTEM] انتهت صلاحية المفتاح.", "[SYSTEM] The key has expired."), blocked = true)))
        Raids.blockReason(s, today)?.let { return Outcome(s, listOf(UiEffect.Toast(it, blocked = true))) }
        return Outcome(s, listOf(UiEffect.RaidGate(k)))
    }

    fun enterRaid(s0: AppState, keyId: String, today: Long, now: Long): Outcome {
        val k = Raids.activeKeys(s0, today).firstOrNull { it.id == keyId } ?: return Outcome(s0)
        Raids.blockReason(s0, today)?.let { return Outcome(s0, listOf(UiEffect.Toast(it, blocked = true))) }
        val steps = Raids.steps(s0, k.rank, today)
        val raid = Raid("raid:${k.id}", k.id, k.rank, today, now, Raids.limitMin(steps), steps)
        var s = s0.copy(raidKeys = s0.raidKeys.map { if (it.id == keyId) it.copy(used = true) else it }, raids = s0.raids + raid)
        s = hist(s, now, today, HistoryKind.RAID, Txt("دخول غارة من الرتبة ${Raids.rankLabel(k.rank)}", "Entered a rank ${Raids.rankLabel(k.rank)} raid"))
        return Outcome(s)
    }

    fun raidStep(s: AppState, idx: Int): AppState {
        val r = Raids.active(s) ?: return s
        return s.copy(raids = s.raids.map { if (it.id == r.id) it.copy(steps = it.steps.mapIndexed { i, st -> if (i == idx) st.copy(done = !st.done) else st }) else it })
    }

    /** Finish: all steps done inside the timer = CLEAR (big XP, rare loot, clear record). Otherwise it just ends — no penalty, ever. */
    fun finishRaid(s0: AppState, today: Long, now: Long, pain: Boolean = false): Outcome {
        val r = Raids.active(s0) ?: return Outcome(s0)
        val inTime = now - r.startMs <= r.limitMin * 60_000L
        val cleared = !pain && inTime && r.steps.all { it.done }
        val xp = if (cleared) Raids.xpFor(r.rank) else 0
        val done = r.copy(endMs = now, cleared = cleared, xp = xp)
        var s = s0.copy(raids = s0.raids.map { if (it.id == r.id) done else it })
        var box: RewardGrant? = null
        val eff = mutableListOf<UiEffect>()
        if (cleared) {
            s = s.copy(ledger = s.ledger + XpEntry(r.id, xp, Raids.goldFor(r.rank), today, Txt("غارة ${Raids.rankLabel(r.rank)}", "Raid ${Raids.rankLabel(r.rank)}"), QuestCategory.TRAINING))
            val (s2, g) = Rewards.grant(s, "raidloot:${r.id}", "box_epic", today, Txt("تصفية غارة", "Raid clear")); s = s2; box = g
            s = hist(s, now, today, HistoryKind.RAID, Txt("تمت تصفية غارة الرتبة ${Raids.rankLabel(r.rank)} (+$xp XP)", "Cleared a rank ${Raids.rankLabel(r.rank)} raid (+$xp XP)"), xp)
        } else s = hist(s, now, today, HistoryKind.RAID, Txt(if (pain) "غادرت الغارة بسبب الألم — لا عقاب" else "انتهت الغارة دون تصفية — لا عقاب", if (pain) "Left the raid due to pain — no penalty" else "Raid ended without a clear — no penalty"))
        if (pain) { s = hist(s, now, today, HistoryKind.PAIN, Txt("ألم أثناء الغارة — توقّف", "Pain during raid — stopped")); eff += UiEffect.Safety(safetyText(SymptomType.SHARP_PAIN), true) }
        eff += UiEffect.RaidResult(done, box)
        val o = post(s0, s, today, now, eff)
        return if (cleared) Outcome(o.state, o.effects + proposeAssess(o.state, today)) else o
    }

    fun useToken(s0: AppState, id: String, today: Long, now: Long): Outcome {
        val def = RewardCatalog.get(id) ?: return Outcome(s0)
        return when (def.token) {
            TokenType.SHIELD -> {
                val day = (today - 7 until today).lastOrNull { Stats.outcome(s0, it, today) == com.ayman.solosystem.domain.progression.DayOutcome.FAIL && it !in s0.shieldedDays }
                    ?: return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] لا يوجد يوم فاشل يحتاج إلى حماية.", "[SYSTEM] No failed day needs a shield."), blocked = true)))
                val s = Rewards.consume(s0, id) ?: return Outcome(s0)
                Outcome(hist(s.copy(shieldedDays = s.shieldedDays + day), now, today, HistoryKind.SYSTEM, Txt("درع السلسلة يحمي يومًا فاشلًا", "Streak shield protected a failed day")),
                    listOf(UiEffect.Toast(Txt("[SYSTEM] تم تفعيل درع السلسلة.", "[SYSTEM] Streak shield activated."))))
            }
            TokenType.EMERGENCY -> {
                if (Penalty.unsafeToday(s0, today)) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] جاهزيتك منخفضة اليوم. ارتح.", "[SYSTEM] Low readiness today. Rest."))))
                val n = s0.quests.count { it.day == today && it.type == QuestType.EMERGENCY }
                val q = QuestGenerator.emergency(s0.copy(settings = s0.settings.copy(emergencyQuests = true)), today, false, 100, n) ?: return Outcome(s0)
                val s = Rewards.consume(s0, id) ?: return Outcome(s0)
                Outcome(s.copy(quests = s.quests + q), listOf(UiEffect.Emergency(q)))
            }
            TokenType.FULL_REST -> {
                val ids = s0.quests.filter { it.day == today && it.status == QuestStatus.PENDING && (it.type == QuestType.DAILY || GoalPlan.isGoal(it)) }.map { it.id }
                if (ids.isEmpty() && today in s0.shieldedDays) return Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] اليوم محمي بالفعل.", "[SYSTEM] Today is already protected."), blocked = true)))
                val s = Rewards.consume(s0, id) ?: return Outcome(s0)
                val st = s.copy(quests = s.quests.map { if (it.id in ids) it.copy(status = QuestStatus.EXCUSED, reason = "FULL_REST", reasonReviewed = true) else it },
                    bonus = s.bonus.filterNot { b -> ids.any { b.key == "loss:$it" } }, shieldedDays = s.shieldedDays + today)
                Outcome(hist(st, now, today, HistoryKind.SYSTEM, Txt("الشفاء الكامل: يوم راحة كامل مجاني", "Full Recovery: a free full rest day")),
                    listOf(UiEffect.Toast(Txt("[SYSTEM] الشفاء الكامل — اليوم يوم راحة. لا عقاب، والسلسلة محمية.", "[SYSTEM] Full Recovery — today is a rest day. No penalty, streak protected."))))
            }
            TokenType.REROLL -> Outcome(s0, listOf(UiEffect.Toast(Txt("[SYSTEM] استعمل زر إعادة السحب على مهمة يومية.", "[SYSTEM] Use the reroll button on a daily quest."))))
            null -> Outcome(s0)
        }
    }
}
