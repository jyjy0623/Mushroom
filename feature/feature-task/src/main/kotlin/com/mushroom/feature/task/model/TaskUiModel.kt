package com.mushroom.feature.task.model

import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplateType
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")

data class TaskUiModel(
    val id: Long,
    val title: String,
    val subjectLabel: String,
    val estimatedMinutes: Int,
    val deadlineDisplay: String?,   // "截止 20:00"，null 表示无截止
    val status: TaskStatus,
    val isEarlyDone: Boolean,
    val hasRepeat: Boolean,
    val rewardPreview: String       // "🍄 小蘑菇×1" 等奖励预览
) {
    val isDone: Boolean get() = status == TaskStatus.EARLY_DONE || status == TaskStatus.ON_TIME_DONE
}

fun Task.toUiModel(): TaskUiModel = TaskUiModel(
    id = id,
    title = title,
    subjectLabel = subject.displayName(),
    estimatedMinutes = estimatedMinutes,
    deadlineDisplay = deadline?.let { "截止 ${it.format(TIME_FMT)}" },
    status = status,
    isEarlyDone = status == TaskStatus.EARLY_DONE,
    hasRepeat = repeatRule !is RepeatRule.None,
    rewardPreview = computeRewardPreview(this)
)

/** 根据规则预估奖励文字（不写入DB，仅展示用，与 RewardRules.kt 保持一致） */
private fun computeRewardPreview(task: Task): String {
    val base = when (task.templateType) {
        TaskTemplateType.MORNING_READING    -> "小蘑菇×1"   // MorningReadingRule
        TaskTemplateType.HOMEWORK_AT_SCHOOL -> "中蘑菇×1"   // HomeworkAtSchoolRule
        TaskTemplateType.HOMEWORK_MEMO      -> "小蘑菇×1"   // HomeworkMemoRule
        TaskTemplateType.CUSTOM, null       -> "小蘑菇×1"   // DailyTaskCompleteRule
    }
    val earlyHint = if (task.deadline != null) " + 截止前完成可得提前奖励" else ""
    return "🍄 $base$earlyHint"
}

private fun Subject.displayName(): String = when (this) {
    Subject.MATH -> "数学"
    Subject.CHINESE -> "语文"
    Subject.ENGLISH -> "英语"
    Subject.PHYSICS -> "物理"
    Subject.CHEMISTRY -> "化学"
    Subject.BIOLOGY -> "生物"
    Subject.HISTORY -> "历史"
    Subject.GEOGRAPHY -> "地理"
    Subject.OTHER -> "其他"
}
