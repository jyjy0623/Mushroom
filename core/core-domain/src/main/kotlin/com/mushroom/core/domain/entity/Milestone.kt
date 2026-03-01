package com.mushroom.core.domain.entity

import java.time.LocalDate

data class Milestone(
    val id: Long = 0,
    val name: String,
    val type: MilestoneType,
    val subject: Subject,
    val scheduledDate: LocalDate,
    val scoringRules: List<ScoringRule>,
    val actualScore: Int?,
    val status: MilestoneStatus = MilestoneStatus.PENDING
)

enum class MilestoneType { MINI_TEST, WEEKLY_TEST, SCHOOL_EXAM, MIDTERM, FINAL }

enum class MilestoneStatus { PENDING, SCORED, REWARDED }

data class ScoringRule(
    val minScore: Int,
    val maxScore: Int,
    val rewardConfig: MushroomRewardConfig
)

data class KeyDate(
    val id: Long = 0,
    val name: String,
    val date: LocalDate,
    val condition: KeyDateCondition,
    val rewardConfig: MushroomRewardConfig
)

sealed class KeyDateCondition {
    data class ConsecutiveCheckinDays(val days: Int) : KeyDateCondition()
    data class MilestoneScore(val milestoneId: Long, val minScore: Int) : KeyDateCondition()
    object ManualTrigger : KeyDateCondition()
}
