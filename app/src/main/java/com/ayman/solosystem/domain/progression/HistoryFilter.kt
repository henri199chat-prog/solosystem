package com.ayman.solosystem.domain.progression

import com.ayman.solosystem.domain.model.HistoryEvent
import com.ayman.solosystem.domain.model.HistoryKind
import com.ayman.solosystem.domain.model.HistoryKind.*

/**
 * Log sub-tabs (All / XP / Quests / Training / Rewards). v1.2.2 fix: quest completions are logged as [XP] ("أكملت: …") and
 * undos as [XP_REVERSED], so the Quests tab missed every completed quest; raids, streak loss, curses etc. were also only in All.
 */
object HistoryFilter {
    enum class Tab { ALL, XP, QUESTS, TRAINING, REWARDS }
    private val XP_KINDS = setOf(XP, XP_REVERSED, LEVEL_UP, BONUS, BONUS_LOST, BONUS_BANKED, SECRET)
    private val QUEST_KINDS = setOf(XP, XP_REVERSED, BONUS, BONUS_LOST, SECRET, RAID, STREAK_LOST)
    private val TRAINING_KINDS = setOf(WORKOUT, PR, PAIN, PLAN, PROGRESSION, RAID)
    private val REWARD_KINDS = setOf(ACHIEVEMENT, PURCHASE, BONUS_BANKED, ATTRIBUTE, SECRET)

    fun matches(h: HistoryEvent, tab: Tab): Boolean = when (tab) {
        Tab.ALL -> true
        Tab.XP -> h.kind in XP_KINDS || h.xpDelta != 0
        Tab.QUESTS -> h.kind in QUEST_KINDS || h.kind.name.startsWith("QUEST") || h.kind.name.startsWith("PENALTY")
        Tab.TRAINING -> h.kind in TRAINING_KINDS
        Tab.REWARDS -> h.kind in REWARD_KINDS
    }
    fun filter(list: List<HistoryEvent>, tab: Tab) = list.filter { matches(it, tab) }
    @Suppress("unused") fun kindsCovered(): Set<HistoryKind> = XP_KINDS + QUEST_KINDS + TRAINING_KINDS + REWARD_KINDS
}
