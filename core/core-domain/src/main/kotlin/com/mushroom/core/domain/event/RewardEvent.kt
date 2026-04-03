package com.mushroom.core.domain.event

import com.mushroom.core.domain.entity.*
import java.time.LocalDate

sealed class RewardEvent {
    data class TaskCompleted(val task: Task, val checkIn: CheckIn) : RewardEvent()
    data class AllDailyTasksDone(val date: LocalDate) : RewardEvent()
    data class StreakReached(val days: Int) : RewardEvent()
    data class MilestoneAchieved(val milestone: Milestone) : RewardEvent()
    data class KeyDateAchieved(val keyDate: KeyDate) : RewardEvent()
}

data class MushroomReward(
    val level: MushroomLevel,
    val amount: Int,
    val reason: String,
    val sourceType: MushroomSource,
    val sourceId: Long? = null
)
