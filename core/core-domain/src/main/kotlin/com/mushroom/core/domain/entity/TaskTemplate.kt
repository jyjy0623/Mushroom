package com.mushroom.core.domain.entity

data class TaskTemplate(
    val id: Long = 0,
    val name: String,
    val type: TaskTemplateType,
    val subject: Subject,
    val estimatedMinutes: Int,
    val description: String = "",
    val defaultDeadlineOffset: Int?,  // 距当天0点的分钟偏移
    val rewardConfig: TemplateRewardConfig,
    val isBuiltIn: Boolean = false
)

data class TemplateRewardConfig(
    val baseReward: MushroomRewardConfig,
    val bonusReward: MushroomRewardConfig?,
    val bonusCondition: BonusCondition?
)

sealed class BonusCondition {
    data class WithinMinutesAfterStart(val minutes: Int) : BonusCondition()
    data class ConsecutiveDays(val days: Int) : BonusCondition()
    object AllItemsDone : BonusCondition()
}
