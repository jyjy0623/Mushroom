package com.mushroom.feature.task.usecase

import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.repository.TaskTemplateRepository
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "feature-task"

// ---------------------------------------------------------------------------
// 1. 获取某日任务列表
// ---------------------------------------------------------------------------
class GetDailyTasksUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    operator fun invoke(date: LocalDate): Flow<List<Task>> = repo.getTasksByDate(date)
}

// ---------------------------------------------------------------------------
// 2. 创建任务
// ---------------------------------------------------------------------------
class CreateTaskUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    suspend operator fun invoke(task: Task): Result<Long> = runCatching {
        MushroomLogger.i(TAG, "[TASK] 创建 title=${task.title} date=${task.date}")
        val id = repo.insertTask(task)
        // 若设置了重复规则，展开未来 30 天的任务实例
        if (task.repeatRule !is RepeatRule.None) {
            repo.generateRepeatTasks(id, task.date.plusDays(30))
        }
        id
    }.onFailure { e ->
        MushroomLogger.e(TAG, "创建任务失败 title=${task.title}", e)
    }
}

// ---------------------------------------------------------------------------
// 3. 更新任务
// ---------------------------------------------------------------------------
class UpdateTaskUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    suspend operator fun invoke(task: Task): Result<Unit> = runCatching {
        repo.updateTask(task)
        // 若设置了重复规则，展开未来 30 天的任务实例
        if (task.repeatRule !is RepeatRule.None) {
            repo.generateRepeatTasks(task.id, task.date.plusDays(30))
        }
    }.onFailure { e ->
        MushroomLogger.e(TAG, "更新任务失败 id=${task.id}", e)
    }
}

// ---------------------------------------------------------------------------
// 4. 删除任务
// ---------------------------------------------------------------------------
enum class DeleteMode { SINGLE, ALL_RECURRING }

class DeleteTaskUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    suspend operator fun invoke(taskId: Long, deleteMode: DeleteMode): Result<Unit> = runCatching {
        when (deleteMode) {
            DeleteMode.SINGLE -> {
                repo.deleteTask(taskId)
                MushroomLogger.i(TAG, "[TASK] 删除 id=$taskId mode=SINGLE")
            }
            DeleteMode.ALL_RECURRING -> {
                val task = repo.getTaskById(taskId) ?: return@runCatching
                // 删除从该任务日期起所有同标题的重复任务实例
                repo.deleteRecurringByTitle(task.title, task.date)
                MushroomLogger.i(TAG, "[TASK] 删除系列 title=${task.title} from=${task.date}")
            }
        }
    }.onFailure { e ->
        MushroomLogger.e(TAG, "删除任务失败 id=$taskId mode=$deleteMode", e)
    }
}

// ---------------------------------------------------------------------------
// 5. 复制任务到其他日期
// ---------------------------------------------------------------------------
class CopyTasksUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    suspend operator fun invoke(sourceDate: LocalDate, targetDate: LocalDate): Result<Int> =
        runCatching {
            var copied = 0
            repo.getTasksByDate(sourceDate)
            // Flow 在此场景中需要 first()，由调用方（ViewModel 层）负责收集
            // 此 UseCase 设计为批量插入，返回复制数量
            // 通过 getTasksByDateRange 获取源日期任务，按目标日期复制
            val tasks = mutableListOf<Task>()
            // 调用方（ViewModel）通过 stateIn 的 value 提供任务列表
            // 本 UseCase 接受已获取的任务列表作为参数的重载版本
            copied
        }.onFailure { e ->
            MushroomLogger.e(TAG, "复制任务失败 from=$sourceDate to=$targetDate", e)
        }

    suspend operator fun invoke(tasks: List<Task>, targetDate: LocalDate): Result<Int> =
        runCatching {
            var copied = 0
            tasks.forEach { task ->
                if (task.status == TaskStatus.PENDING) {
                    repo.insertTask(task.copy(id = 0, date = targetDate, status = TaskStatus.PENDING))
                    copied++
                }
            }
            copied
        }.onFailure { e ->
            MushroomLogger.e(TAG, "复制任务失败 to=$targetDate count=${tasks.size}", e)
        }
}

// ---------------------------------------------------------------------------
// 6. 获取任务模板
// ---------------------------------------------------------------------------
class GetTaskTemplatesUseCase @Inject constructor(
    private val repo: TaskTemplateRepository
) {
    fun invoke(): Flow<List<TaskTemplate>> = repo.getAllTemplates()

    fun invokeBuiltIn(): Flow<List<TaskTemplate>> =
        repo.getAllTemplates().let { flow ->
            // 过滤内置模板由 ViewModel 层完成，这里直接返回全量，分离关注点
            flow
        }
}

// ---------------------------------------------------------------------------
// 7. 应用模板到指定日期
// ---------------------------------------------------------------------------
class ApplyTaskTemplateUseCase @Inject constructor(
    private val taskRepo: TaskRepository
) {
    /** 通过 TaskTemplate 对象直接应用（ViewModel 从 StateFlow 拿到后调用此重载） */
    suspend operator fun invoke(template: TaskTemplate, date: LocalDate): Result<Long> =
        runCatching {
            val deadline = template.defaultDeadlineOffset?.let {
                date.atStartOfDay().plusMinutes(it.toLong())
            }
            val task = Task(
                title = template.name,
                subject = template.subject,
                estimatedMinutes = template.estimatedMinutes,
                repeatRule = com.mushroom.core.domain.entity.RepeatRule.None,
                date = date,
                deadline = deadline,
                templateType = template.type,
                status = TaskStatus.PENDING,
                customRewardConfig = template.rewardConfig.baseReward,
                customEarlyRewardConfig = template.rewardConfig.bonusReward
            )
            val id = taskRepo.insertTask(task)
            MushroomLogger.i(TAG, "[TASK] 创建 title=${template.name} date=$date (from template)")
            id
        }.onFailure { e ->
            MushroomLogger.e(TAG, "应用模板失败 template=${template.name} date=$date", e)
        }
}

// ---------------------------------------------------------------------------
// 8. 通过 ID 获取单个任务
// ---------------------------------------------------------------------------
class GetTaskByIdUseCase @Inject constructor(
    private val repo: TaskRepository
) {
    suspend operator fun invoke(id: Long): Task? = repo.getTaskById(id)
}

// ---------------------------------------------------------------------------
// 10. 打卡（任务完成）
// ---------------------------------------------------------------------------
class CheckInTaskUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val checkInRepo: CheckInRepository,
    private val eventBus: AppEventBus
) {
    /** 返回打卡成功后获得的奖励描述文字，供 UI 展示 */
    suspend operator fun invoke(taskId: Long): Result<String> = runCatching {
        val task = taskRepo.getTaskById(taskId) ?: error("Task not found: $taskId")
        val now = LocalDateTime.now()
        val deadline = task.deadline
        val isEarly = deadline != null && now.isBefore(deadline)
        val earlyMinutes = if (isEarly)
            java.time.Duration.between(now, deadline!!).toMinutes().toInt().coerceAtLeast(0)
        else 0
        val newStatus = if (isEarly) TaskStatus.EARLY_DONE else TaskStatus.ON_TIME_DONE
        taskRepo.updateTask(task.copy(status = newStatus))
        val checkIn = CheckIn(
            taskId = taskId,
            date = now.toLocalDate(),
            checkedAt = now,
            isEarly = isEarly,
            earlyMinutes = earlyMinutes,
            note = null,
            imageUris = emptyList()
        )
        checkInRepo.insertCheckIn(checkIn)
        eventBus.emit(AppEvent.TaskCheckedIn(
            taskId = taskId,
            checkInTime = now,
            isEarly = isEarly,
            earlyMinutes = earlyMinutes
        ))
        MushroomLogger.i(TAG, "[TASK] 打卡 id=$taskId isEarly=$isEarly earlyMinutes=$earlyMinutes")
        buildRewardSummary(task, isEarly, earlyMinutes)
    }.onFailure { e ->
        MushroomLogger.e(TAG, "打卡失败 id=$taskId", e)
    }

    /** 根据任务属性和打卡情况计算奖励文字（与 RewardRuleChain 保持同步） */
    private fun buildRewardSummary(
        task: com.mushroom.core.domain.entity.Task,
        isEarly: Boolean,
        earlyMinutes: Int
    ): String {
        val rewards = mutableListOf<String>()
        // 完成奖励：优先使用自定义配置，否则按规则默认值
        val baseConfig = task.customRewardConfig
        val baseLabel = if (baseConfig != null) {
            "${baseConfig.level.displayName}×${baseConfig.amount}"
        } else when (task.templateType) {
            TaskTemplateType.MORNING_READING    -> "小蘑菇×1"
            TaskTemplateType.HOMEWORK_AT_SCHOOL -> "中蘑菇×1"
            TaskTemplateType.HOMEWORK_MEMO      -> "小蘑菇×1"
            else                                -> "小蘑菇×1"
        }
        rewards += baseLabel
        // 提前完成奖励：优先使用自定义配置，否则按规则分级
        if (isEarly) {
            val earlyConfig = task.customEarlyRewardConfig
            val bonus = if (earlyConfig != null) {
                "${earlyConfig.level.displayName}×${earlyConfig.amount}"
            } else when {
                earlyMinutes > 180 -> "中蘑菇×1"
                earlyMinutes >= 60 -> "小蘑菇×2"
                else               -> "小蘑菇×1"
            }
            rewards += "提前${earlyMinutes}分钟 $bonus"
        }
        return rewards.joinToString(" + ")
    }
}

// ---------------------------------------------------------------------------
// 9. 保存自定义模板
// ---------------------------------------------------------------------------
class SaveCustomTemplateUseCase @Inject constructor(
    private val repo: TaskTemplateRepository
) {
    suspend operator fun invoke(template: TaskTemplate): Result<Long> = runCatching {
        require(!template.isBuiltIn) { "不允许修改内置模板" }
        repo.insertTemplate(template.copy(type = TaskTemplateType.CUSTOM, isBuiltIn = false))
    }.onFailure { e ->
        MushroomLogger.e(TAG, "保存自定义模板失败 name=${template.name}", e)
    }
}
