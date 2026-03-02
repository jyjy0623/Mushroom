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

/** 根据规则预估奖励文字（不写入DB，仅展示用） */
private fun computeRewardPreview(task: Task): String {
    val parts = mutableListOf<String>()
    // 基础完成奖励（所有任务）
    parts += "小蘑菇×1"
    // 模板类型额外奖励
    if (task.templateType == TaskTemplateType.MORNING_READING) {
        parts[0] = "小蘑菇×2"   // MorningReadingRule 覆盖基础为 ×2
    }
    // 有截止时间 → 可能有提前完成奖励
    if (task.deadline != null) {
        parts += "提前奖励最高 中蘑菇×1"
    }
    return "🍄 " + parts.joinToString(" + ")
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
