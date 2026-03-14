package com.mushroom.service.taskgenerator

import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.repository.TaskRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class TaskGeneratorServiceImplTest {

    private val taskRepository = mockk<TaskRepository>()
    private val service = TaskGeneratorServiceImpl(taskRepository)

    // 目标日期：2026-03-04（星期三）
    private val wednesday = LocalDate.of(2026, 3, 4)
    // 前一天（模板来源窗口内）
    private val tuesday = wednesday.minusDays(1)

    private fun makeTask(
        id: Long = 1L,
        title: String = "Test Task",
        repeatRule: RepeatRule = RepeatRule.Daily,
        date: LocalDate = tuesday,
        deadline: LocalDateTime? = null
    ) = Task(
        id = id,
        title = title,
        subject = Subject.MATH,
        estimatedMinutes = 30,
        repeatRule = repeatRule,
        date = date,
        deadline = deadline,
        templateType = null,
        status = TaskStatus.PENDING
    )

    @BeforeEach
    fun setup() {
        // 默认当天没有任何任务
        coEvery { taskRepository.getAllTaskTitlesByDate(wednesday) } returns emptyList()
        coEvery { taskRepository.insertTask(any()) } returns 99L
    }

    // -----------------------------------------------------------------------
    // Daily rule
    // -----------------------------------------------------------------------
    @Nested
    inner class DailyRule {

        @Test
        fun `Daily task is generated for any weekday`() = runTest {
            val template = makeTask(repeatRule = RepeatRule.Daily)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(wednesday)

            coVerify(exactly = 1) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `Daily task is generated for weekend`() = runTest {
            val saturday = LocalDate.of(2026, 3, 7)
            coEvery { taskRepository.getAllTaskTitlesByDate(saturday) } returns emptyList()
            val template = makeTask(repeatRule = RepeatRule.Daily)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(saturday)

            coVerify(exactly = 1) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `generated task has correct date`() = runTest {
            val template = makeTask(repeatRule = RepeatRule.Daily)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            val inserted = slot<Task>()
            coEvery { taskRepository.insertTask(capture(inserted)) } returns 99L

            service.generateForDate(wednesday)

            assertEquals(wednesday, inserted.captured.date)
        }

        @Test
        fun `generated task id is reset to 0`() = runTest {
            val template = makeTask(id = 42L, repeatRule = RepeatRule.Daily)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            val inserted = slot<Task>()
            coEvery { taskRepository.insertTask(capture(inserted)) } returns 99L

            service.generateForDate(wednesday)

            assertEquals(0L, inserted.captured.id)
        }
    }

    // -----------------------------------------------------------------------
    // Weekdays rule
    // -----------------------------------------------------------------------
    @Nested
    inner class WeekdaysRule {

        @Test
        fun `Weekdays task is generated on Wednesday`() = runTest {
            val template = makeTask(repeatRule = RepeatRule.Weekdays)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(wednesday)

            coVerify(exactly = 1) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `Weekdays task is NOT generated on Saturday`() = runTest {
            val saturday = LocalDate.of(2026, 3, 7)
            coEvery { taskRepository.getAllTaskTitlesByDate(saturday) } returns emptyList()
            val template = makeTask(repeatRule = RepeatRule.Weekdays)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(saturday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `Weekdays task is NOT generated on Sunday`() = runTest {
            val sunday = LocalDate.of(2026, 3, 8)
            coEvery { taskRepository.getAllTaskTitlesByDate(sunday) } returns emptyList()
            val template = makeTask(repeatRule = RepeatRule.Weekdays)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(sunday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }
    }

    // -----------------------------------------------------------------------
    // Custom rule
    // -----------------------------------------------------------------------
    @Nested
    inner class CustomRule {

        @Test
        fun `Custom task is generated when date matches daysOfWeek`() = runTest {
            // wednesday = DayOfWeek.WEDNESDAY
            val rule = RepeatRule.Custom(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
            val template = makeTask(repeatRule = rule)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(wednesday)

            coVerify(exactly = 1) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `Custom task is NOT generated when date does not match`() = runTest {
            // thursday
            val thursday = LocalDate.of(2026, 3, 5)
            coEvery { taskRepository.getAllTaskTitlesByDate(thursday) } returns emptyList()
            val rule = RepeatRule.Custom(setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
            val template = makeTask(repeatRule = rule)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(thursday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }
    }

    // -----------------------------------------------------------------------
    // Idempotency
    // -----------------------------------------------------------------------
    @Nested
    inner class Idempotency {

        @Test
        fun `task with same title already existing is skipped`() = runTest {
            val template = makeTask(title = "Morning Reading", repeatRule = RepeatRule.Daily)
            // 当天已有同名任务（包括 SKIPPED 状态）
            coEvery { taskRepository.getAllTaskTitlesByDate(wednesday) } returns listOf("Morning Reading")
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(wednesday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `None repeat rule task in window is not used as template`() = runTest {
            val noneTask = makeTask(repeatRule = RepeatRule.None)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(noneTask))

            service.generateForDate(wednesday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }

        @Test
        fun `deadline time-of-day is preserved but date is updated`() = runTest {
            val deadline = tuesday.atTime(20, 0)
            val template = makeTask(repeatRule = RepeatRule.Daily, deadline = deadline)
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            val inserted = slot<Task>()
            coEvery { taskRepository.insertTask(capture(inserted)) } returns 99L

            service.generateForDate(wednesday)

            val capturedDeadline = inserted.captured.deadline!!
            assertEquals(wednesday, capturedDeadline.toLocalDate())
            assertEquals(20, capturedDeadline.hour)
            assertEquals(0, capturedDeadline.minute)
        }

        @Test
        fun `skipped task title prevents regeneration after process kill`() = runTest {
            val template = makeTask(title = "Morning Reading", repeatRule = RepeatRule.Daily)
            // 当天存在 SKIPPED 状态的同名任务（用户删除后标记为 SKIPPED）
            coEvery { taskRepository.getAllTaskTitlesByDate(wednesday) } returns listOf("Morning Reading")
            coEvery { taskRepository.getTasksByDateRange(any(), any()) } returns flowOf(listOf(template))

            service.generateForDate(wednesday)

            coVerify(exactly = 0) { taskRepository.insertTask(any()) }
        }
    }
}
