package com.ayman.solosystem

import com.ayman.solosystem.domain.game.GameEngine
import com.ayman.solosystem.domain.model.*
import java.time.LocalDate

object Fx {
    val MON: Long = LocalDate.of(2026, 10, 26).toEpochDay()
    const val NOW = 1_800_000_000_000L
    fun profile(eq: Set<Equipment> = setOf(Equipment.NONE), days: Int = 3, lim: Set<Limitation> = emptySet(), fit: FitnessLevel = FitnessLevel.NOVICE, min: Int = 45) =
        PlayerProfile("Ayman", 24, 178f, 75f, fit, Experience.UNDER_6M, eq, days, min, Goal.GENERAL, lim)
    fun state(p: PlayerProfile = profile(), day: Long = MON, settings: Settings = Settings()): AppState = GameEngine.onboard(p, settings, day, NOW, 42L).state
    fun completeAllManual(s0: AppState, day: Long): AppState {
        var s = s0
        s.quests.filter { it.day == day && it.status == QuestStatus.PENDING && it.verify == Verify.MANUAL && it.type == QuestType.DAILY }.forEach { s = GameEngine.completeQuest(s, it.id, day, NOW).state }
        return s
    }
    /** Completes the anime GOAL rows (the required daily quest since 1.1.0). */
    fun completeGoals(s0: AppState, day: Long, factor: Int = 1): AppState {
        var s = s0
        s.quests.filter { it.day == day && it.verify == Verify.COUNT && it.type == QuestType.DAILY }.forEach { s = GameEngine.logGoal(s, it.id, it.target * factor, day, NOW).state }
        return s
    }
}
