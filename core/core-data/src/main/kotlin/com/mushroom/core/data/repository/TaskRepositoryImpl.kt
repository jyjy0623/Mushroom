package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.TaskDao
import com.mushroom.core.data.mapper.TaskMapper
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao
) : TaskRepository {

    override fun getTasksByDate(date: LocalDate): Flow<List<Task>> =
        taskDao.getTasksByDate(date.toString()).map { list -> list.map(TaskMapper::toDomain) }

    override fun getTasksByDateRange(from: LocalDate, to: LocalDate): Flow<List<Task>> =
        taskDao.getTasksByDateRange(from.toString(), to.toString())
            .map { list -> list.map(TaskMapper::toDomain) }

    override suspend fun getTaskById(id: Long): Task? =
        taskDao.getTaskById(id)?.let(TaskMapper::toDomain)

    override suspend fun insertTask(task: Task): Long =
        taskDao.insert(TaskMapper.toDb(task))

    override suspend fun updateTask(task: Task) =
        taskDao.update(TaskMapper.toDb(task))

    override suspend fun deleteTask(id: Long) =
        taskDao.deleteById(id)

    /**
     * 为带重复规则的模板任务展开指定范围内的所有日期任务实例。
     * 幂等：同一日期已有同标题任务则跳过（OnConflictStrategy.IGNORE 在 DAO 层保证）。
     */
    override suspend fun generateRepeatTasks(templateTaskId: Long, until: LocalDate) {
        val template = taskDao.getTaskById(templateTaskId) ?: return
        val domainTemplate = TaskMapper.toDomain(template)
        if (domainTemplate.repeatRule is RepeatRule.None) return

        val allowedDays: Set<DayOfWeek> = when (val rule = domainTemplate.repeatRule) {
            is RepeatRule.Daily -> DayOfWeek.values().toSet()
            is RepeatRule.Weekdays -> setOf(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY
            )
            is RepeatRule.Custom -> rule.daysOfWeek
            else -> return
        }

        var current = domainTemplate.date.plusDays(1)
        while (!current.isAfter(until)) {
            if (current.dayOfWeek in allowedDays) {
                val instance = domainTemplate.copy(id = 0, date = current)
                taskDao.insert(TaskMapper.toDb(instance))
            }
            current = current.plusDays(1)
        }
    }
}
