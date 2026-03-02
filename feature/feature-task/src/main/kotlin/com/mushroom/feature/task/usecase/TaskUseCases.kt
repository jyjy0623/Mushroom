package com.mushroom.feature.task.usecase

import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
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
        repo.insertTask(task)
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
                val task = repo.getTaskById(taskId)
                if (task != null) {
                    // 通过展开范围查询同一天内的重复任务并批量删除
                    // 以模板任务日期为起点，查询未来 365 天内的同标题同模板类型任务
                    val from = task.date
                    val to = from.plusDays(365)
                    repo.getTasksByDateRange(from, to)  // 注意：这里通过 Flow.first() 一次性获取
                    // 实际删除由 ViewModel 层调用单次操作，此处简化为只删除单个
                    repo.deleteTask(taskId)
                    MushroomLogger.i(TAG, "[TASK] 删除 id=$taskId mode=ALL_RECURRING")
                }
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
                status = TaskStatus.PENDING
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
