package com.mushroom.feature.checkin.usecase

import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CheckInUseCasesTest {

    private lateinit var taskRepo: TaskRepository
    private lateinit var checkInRepo: CheckInRepository
    private lateinit var eventBus: AppEventBus

    @BeforeEach
    fun setUp() {
        taskRepo = mockk(relaxed = true)
        checkInRepo = mockk(relaxed = true)
        eventBus = mockk(relaxed = true)
    }

    // -----------------------------------------------------------------------
    // CheckInTaskUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class CheckInTaskUseCaseTest {

        @Test
        fun `should return failure when task not found`() = runTest {
            coEvery { taskRepo.getTaskById(99L) } returns null

            val result = CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(99L)
            assertTrue(result.isFailure)
        }

        @Test
        fun `should set ON_TIME_DONE when no deadline`() = runTest {
            val task = buildTask(deadline = null)
            coEvery { taskRepo.getTaskById(1L) } returns task
            coEvery { checkInRepo.insertCheckIn(any()) } returns 10L

            val result = CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(1L)
            assertTrue(result.isSuccess)

            val updatedTaskSlot = slot<Task>()
            coVerify { taskRepo.updateTask(capture(updatedTaskSlot)) }
            assertEquals(TaskStatus.ON_TIME_DONE, updatedTaskSlot.captured.status)
        }

        @Test
        fun `should set EARLY_DONE when completed before deadline`() = runTest {
            val futureDeadline = LocalDateTime.now().plusHours(2)
            val task = buildTask(deadline = futureDeadline)
            coEvery { taskRepo.getTaskById(1L) } returns task
            coEvery { checkInRepo.insertCheckIn(any()) } returns 10L

            val result = CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(1L)
            assertTrue(result.isSuccess)

            val updatedTaskSlot = slot<Task>()
            coVerify { taskRepo.updateTask(capture(updatedTaskSlot)) }
            assertEquals(TaskStatus.EARLY_DONE, updatedTaskSlot.captured.status)
        }

        @Test
        fun `should emit TaskCheckedIn event`() = runTest {
            val task = buildTask(deadline = null)
            coEvery { taskRepo.getTaskById(1L) } returns task
            coEvery { checkInRepo.insertCheckIn(any()) } returns 10L

            CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(1L)

            val eventSlot = slot<AppEvent.TaskCheckedIn>()
            coVerify { eventBus.emit(capture(eventSlot)) }
            assertEquals(1L, eventSlot.captured.taskId)
        }

        @Test
        fun `isEarly should be true and earlyMinutes positive for early checkin`() = runTest {
            val deadline = LocalDateTime.now().plusMinutes(90)
            val task = buildTask(deadline = deadline)
            coEvery { taskRepo.getTaskById(1L) } returns task
            val checkInSlot = slot<CheckIn>()
            coEvery { checkInRepo.insertCheckIn(capture(checkInSlot)) } returns 10L

            CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(1L)

            assertTrue(checkInSlot.captured.isEarly)
            assertTrue(checkInSlot.captured.earlyMinutes > 0)
        }

        @Test
        fun `isEarly should be false for past deadline`() = runTest {
            val pastDeadline = LocalDateTime.now().minusMinutes(30)
            val task = buildTask(deadline = pastDeadline)
            coEvery { taskRepo.getTaskById(1L) } returns task
            val checkInSlot = slot<CheckIn>()
            coEvery { checkInRepo.insertCheckIn(capture(checkInSlot)) } returns 10L

            CheckInTaskUseCase(taskRepo, checkInRepo, eventBus)(1L)

            assertFalse(checkInSlot.captured.isEarly)
            assertEquals(0, checkInSlot.captured.earlyMinutes)
        }
    }

    // -----------------------------------------------------------------------
    // GetCheckInHistoryUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetCheckInHistoryUseCaseTest {
        @Test
        fun `should group check-ins by date`() = runTest {
            val date1 = LocalDate.of(2026, 3, 1)
            val date2 = LocalDate.of(2026, 3, 2)
            val checkIns = listOf(
                buildCheckIn(date = date1, isEarly = false),
                buildCheckIn(date = date1, isEarly = true),
                buildCheckIn(date = date2, isEarly = false)
            )
            every {
                checkInRepo.getCheckInsByDateRange(date1, date2)
            } returns flowOf(checkIns)

            val map = mutableMapOf<LocalDate, DayCheckInSummary>()
            GetCheckInHistoryUseCase(checkInRepo)(date1, date2).collect { map.putAll(it) }

            assertEquals(2, map[date1]?.checkInCount)
            assertTrue(map[date1]?.hasEarly == true)
            assertEquals(1, map[date2]?.checkInCount)
            assertFalse(map[date2]?.hasEarly == true)
        }
    }

    // -----------------------------------------------------------------------
    // GetStreakUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetStreakUseCaseTest {
        @Test
        fun `currentStreak should count consecutive days from today`() = runTest {
            val today = LocalDate.now()
            val dates = listOf(today, today.minusDays(1), today.minusDays(2))
            val checkIns = dates.map { buildCheckIn(date = it) }
            coEvery { checkInRepo.getStreakCount(today) } returns 3

            val streak = GetStreakUseCase(checkInRepo).currentStreak()
            assertEquals(3, streak)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private fun buildTask(deadline: LocalDateTime? = null) = Task(
        id = 1L,
        title = "测试任务",
        subject = Subject.MATH,
        estimatedMinutes = 60,
        repeatRule = RepeatRule.None,
        date = LocalDate.now(),
        deadline = deadline,
        templateType = null,
        status = TaskStatus.PENDING
    )

    private fun buildCheckIn(date: LocalDate, isEarly: Boolean = false) = CheckIn(
        id = 0L,
        taskId = 1L,
        date = date,
        checkedAt = date.atTime(10, 0),
        isEarly = isEarly,
        earlyMinutes = if (isEarly) 30 else 0,
        note = null,
        imageUris = emptyList()
    )
}
