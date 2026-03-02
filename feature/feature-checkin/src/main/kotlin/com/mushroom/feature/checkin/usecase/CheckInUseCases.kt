package com.mushroom.feature.checkin.usecase

import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.event.RewardEvent
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.DeductionRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "CheckInUseCases"

// ---------------------------------------------------------------------------
// CheckInTaskUseCase
// ---------------------------------------------------------------------------

/**
 * 核心打卡流程：
 * 1. 查询任务，验证存在
 * 2. 计算 isEarly / earlyMinutes（与 deadline 比较）
 * 3. 更新任务状态（ON_TIME_DONE 或 EARLY_DONE）
 * 4. 写入 CheckIn 记录
 * 5. 发送 AppEvent.TaskCheckedIn（让 feature-mushroom 监听并触发奖励）
 */
class CheckInTaskUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val checkInRepo: CheckInRepository,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(taskId: Long, note: String? = null): Result<Long> {
        MushroomLogger.i(TAG, "CheckInTaskUseCase: taskId=$taskId")
        return runCatching {
            val task = taskRepo.getTaskById(taskId)
                ?: error("Task not found: $taskId")

            val now = LocalDateTime.now()
            val deadline = task.deadline
            val isEarly = deadline != null && now.isBefore(deadline)
            val earlyMinutes = if (isEarly && deadline != null)
                java.time.Duration.between(now, deadline).toMinutes().toInt().coerceAtLeast(0)
            else 0

            val newStatus = if (isEarly) TaskStatus.EARLY_DONE else TaskStatus.ON_TIME_DONE
            taskRepo.updateTask(task.copy(status = newStatus))

            val checkIn = CheckIn(
                taskId = taskId,
                date = now.toLocalDate(),
                checkedAt = now,
                isEarly = isEarly,
                earlyMinutes = earlyMinutes,
                note = note,
                imageUris = emptyList()
            )
            val checkInId = checkInRepo.insertCheckIn(checkIn)

            eventBus.emit(AppEvent.TaskCheckedIn(
                taskId = taskId,
                checkInTime = now,
                isEarly = isEarly,
                earlyMinutes = earlyMinutes
            ))

            MushroomLogger.i(TAG, "CheckInTaskUseCase: success, checkInId=$checkInId, isEarly=$isEarly")
            checkInId
        }.onFailure { MushroomLogger.e(TAG, "CheckInTaskUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// GetCheckInHistoryUseCase
// ---------------------------------------------------------------------------

data class DayCheckInSummary(
    val date: LocalDate,
    val checkInCount: Int,
    val hasEarly: Boolean
)

class GetCheckInHistoryUseCase @Inject constructor(
    private val checkInRepo: CheckInRepository
) {
    operator fun invoke(from: LocalDate, to: LocalDate): Flow<Map<LocalDate, DayCheckInSummary>> {
        MushroomLogger.i(TAG, "GetCheckInHistoryUseCase: $from → $to")
        return checkInRepo.getCheckInsByDateRange(from, to).map { checkIns ->
            checkIns.groupBy { it.date }.mapValues { (date, items) ->
                DayCheckInSummary(
                    date = date,
                    checkInCount = items.size,
                    hasEarly = items.any { it.isEarly }
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// GetStreakUseCase
// ---------------------------------------------------------------------------

class GetStreakUseCase @Inject constructor(
    private val checkInRepo: CheckInRepository
) {
    suspend fun currentStreak(): Int {
        val today = LocalDate.now()
        return checkInRepo.getStreakCount(today)
    }

    suspend fun longestStreak(): Int {
        // Simple approach: pull last 365 days and compute the longest run
        val from = LocalDate.now().minusDays(365)
        val to = LocalDate.now()
        val dates = checkInRepo.getCheckInsByDateRange(from, to).first()
            .map { it.date }
            .toSortedSet()

        var longest = 0
        var current = 0
        var prev: LocalDate? = null
        for (date in dates) {
            current = if (prev != null && date == prev!!.plusDays(1)) current + 1 else 1
            if (current > longest) longest = current
            prev = date
        }
        return longest
    }
}

// ---------------------------------------------------------------------------
// CheckAllTasksDoneUseCase
// ---------------------------------------------------------------------------

class CheckAllTasksDoneUseCase @Inject constructor(
    private val taskRepo: TaskRepository,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(date: LocalDate): Boolean {
        MushroomLogger.i(TAG, "CheckAllTasksDoneUseCase: $date")
        val tasks = taskRepo.getTasksByDate(date).first()
        if (tasks.isEmpty()) return false
        val allDone = tasks.all { it.status == TaskStatus.ON_TIME_DONE || it.status == TaskStatus.EARLY_DONE }
        if (allDone) {
            eventBus.emit(AppEvent.TaskCheckedIn(
                taskId = -1L,
                checkInTime = LocalDateTime.now(),
                isEarly = false,
                earlyMinutes = 0
            ))
        }
        return allDone
    }
}
