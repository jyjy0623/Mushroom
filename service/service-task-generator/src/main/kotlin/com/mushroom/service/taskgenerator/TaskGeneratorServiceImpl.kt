package com.mushroom.service.taskgenerator

import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.service.TaskGeneratorService
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TaskGeneratorService"

/**
 * 为每天启动时自动生成重复任务实例。
 *
 * 契约：幂等。同一日期调用多次，只生成一次。
 * 幂等保证方式：先查询 date 已有任务，按 title 去重，相同 title 已存在则跳过。
 */
@Singleton
class TaskGeneratorServiceImpl @Inject constructor(
    private val taskRepository: TaskRepository
) : TaskGeneratorService {

    override suspend fun generateForDate(date: LocalDate) {
        MushroomLogger.i(TAG, "generateForDate: $date")

        // 查询该日期已存在的任务标题（用于幂等判断）
        val existingTitles = taskRepository.getTasksByDate(date).first()
            .map { it.title }
            .toSet()

        // 查询前 30 天内所有任务，从中找出有重复规则的"模板行"
        // 策略：以 date-30 到 date-1 为窗口，找 repeatRule != None 的任务
        // 仅取每个 title 最新一条（避免重复模板）
        val windowFrom = date.minusDays(30)
        val windowTo = date.minusDays(1)
        val candidates = taskRepository.getTasksByDateRange(windowFrom, windowTo).first()
            .filter { it.repeatRule !is RepeatRule.None }
            .groupBy { it.title }
            .mapValues { (_, tasks) -> tasks.maxByOrNull { it.date }!! } // 每个 title 取最新
            .values

        var generated = 0
        for (template in candidates) {
            if (!shouldGenerateForDate(template.repeatRule, date)) continue
            if (template.title in existingTitles) continue

            val instance = template.copy(
                id = 0,
                date = date,
                status = TaskStatus.PENDING,
                deadline = template.deadline?.let { dl ->
                    // 保持截止时间的 hour:minute，但日期换为 date
                    date.atTime(dl.hour, dl.minute)
                }
            )
            taskRepository.insertTask(instance)
            generated++
            MushroomLogger.i(TAG, "generated task '${template.title}' for $date")
        }

        MushroomLogger.i(TAG, "generateForDate done: $generated tasks generated for $date")
    }

    private fun shouldGenerateForDate(rule: RepeatRule, date: LocalDate): Boolean =
        when (rule) {
            is RepeatRule.None -> false
            is RepeatRule.Daily -> true
            is RepeatRule.Weekdays -> date.dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            is RepeatRule.Custom -> date.dayOfWeek in rule.daysOfWeek
        }
}
