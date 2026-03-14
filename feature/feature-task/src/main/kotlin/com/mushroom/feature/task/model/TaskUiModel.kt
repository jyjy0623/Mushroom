package com.mushroom.feature.task.model

import android.content.Context
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.ui.R as CoreUiR
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.core.ui.themedEmoji
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
    val rewardPreview: String       // 奖励预览
) {
    val isDone: Boolean get() = status == TaskStatus.EARLY_DONE || status == TaskStatus.ON_TIME_DONE
}

fun Task.toUiModel(context: Context): TaskUiModel = TaskUiModel(
    id = id,
    title = title,
    subjectLabel = subject.displayName(),
    estimatedMinutes = estimatedMinutes,
    deadlineDisplay = deadline?.let { "截止 ${it.format(TIME_FMT)}" },
    status = status,
    isEarlyDone = status == TaskStatus.EARLY_DONE,
    hasRepeat = repeatRule !is RepeatRule.None,
    rewardPreview = computeRewardPreview(this, context)
)

/** 根据规则预估奖励文字（不写入DB，仅展示用，与 RewardRules.kt 保持一致） */
private fun computeRewardPreview(task: Task, context: Context): String {
    val smallName = context.getString(CoreUiR.string.level_small)
    val mediumName = context.getString(CoreUiR.string.level_medium)
    val emoji = context.getString(CoreUiR.string.level_emoji_small)
    val baseConfig = task.customRewardConfig
    val base = if (baseConfig != null) {
        "${baseConfig.level.themedDisplayName(context)}×${baseConfig.amount}"
    } else when (task.templateType) {
        TaskTemplateType.MORNING_READING    -> "${smallName}×1"
        TaskTemplateType.HOMEWORK_AT_SCHOOL -> "${mediumName}×1"
        TaskTemplateType.HOMEWORK_MEMO      -> "${smallName}×1"
        TaskTemplateType.CUSTOM, null       -> "${smallName}×1"
    }
    val earlyHint = if (task.deadline != null) {
        val earlyConfig = task.customEarlyRewardConfig
        if (earlyConfig != null) " + 截止前完成可得 ${earlyConfig.level.themedDisplayName(context)}×${earlyConfig.amount}"
        else " + 截止前完成可得提前奖励"
    } else ""
    return "$emoji $base$earlyHint"
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
