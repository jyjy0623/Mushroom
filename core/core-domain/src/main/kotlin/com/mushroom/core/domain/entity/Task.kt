package com.mushroom.core.domain.entity

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

data class Task(
    val id: Long = 0,
    val title: String,
    val subject: Subject,
    val estimatedMinutes: Int,
    val repeatRule: RepeatRule,
    val date: LocalDate,
    val deadline: LocalDateTime?,
    val templateType: TaskTemplateType?,
    val status: TaskStatus,
    /** 用户自定义完成奖励，null 表示使用规则引擎默认值 */
    val customRewardConfig: MushroomRewardConfig? = null,
    /** 用户自定义提前完成奖励，null 表示使用规则引擎默认值 */
    val customEarlyRewardConfig: MushroomRewardConfig? = null
)

enum class TaskStatus { PENDING, EARLY_DONE, ON_TIME_DONE, SKIPPED }

enum class TaskTemplateType { MORNING_READING, HOMEWORK_MEMO, HOMEWORK_AT_SCHOOL, CUSTOM }

enum class Subject {
    MATH, CHINESE, ENGLISH, PHYSICS, CHEMISTRY, BIOLOGY, HISTORY, GEOGRAPHY, OTHER
}

sealed class RepeatRule {
    object None : RepeatRule()
    object Daily : RepeatRule()
    object Weekdays : RepeatRule()
    data class Custom(val daysOfWeek: Set<DayOfWeek>) : RepeatRule()
}
