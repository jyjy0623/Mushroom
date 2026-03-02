package com.mushroom.feature.task.model

import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import java.time.LocalDate
import java.time.LocalDateTime
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
    val hasRepeat: Boolean
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
    hasRepeat = repeatRule !is RepeatRule.None
)

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
