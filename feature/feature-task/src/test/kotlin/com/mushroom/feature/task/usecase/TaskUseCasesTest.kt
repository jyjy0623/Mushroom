package com.mushroom.feature.task.usecase

import app.cash.turbine.test
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.entity.TemplateRewardConfig
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.repository.TaskTemplateRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TaskUseCasesTest {

    private lateinit var taskRepo: TaskRepository
    private lateinit var templateRepo: TaskTemplateRepository

    @BeforeEach
    fun setUp() {
        taskRepo = mockk(relaxed = true)
        templateRepo = mockk(relaxed = true)
    }

    // -----------------------------------------------------------------------
    // GetDailyTasksUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetDailyTasksUseCaseTest {
        @Test
        fun `should delegate to repository with given date`() = runTest {
            val date = LocalDate.of(2026, 3, 1)
            val tasks = listOf(buildTask(date = date))
            every { taskRepo.getTasksByDate(date) } returns flowOf(tasks)

            val useCase = GetDailyTasksUseCase(taskRepo)
            useCase(date).test {
                assertEquals(tasks, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
    }

    // -----------------------------------------------------------------------
    // CreateTaskUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class CreateTaskUseCaseTest {
        @Test
        fun `should insert task and return generated id`() = runTest {
            val task = buildTask()
            coEvery { taskRepo.insertTask(task) } returns 42L

            val result = CreateTaskUseCase(taskRepo)(task)
            assertTrue(result.isSuccess)
            assertEquals(42L, result.getOrNull())
        }

        @Test
        fun `should return failure when repository throws`() = runTest {
            val task = buildTask()
            coEvery { taskRepo.insertTask(task) } throws RuntimeException("db error")

            val result = CreateTaskUseCase(taskRepo)(task)
            assertTrue(result.isFailure)
        }
    }

    // -----------------------------------------------------------------------
    // GetTaskByIdUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetTaskByIdUseCaseTest {
        @Test
        fun `should return task when found`() = runTest {
            val task = buildTask(id = 42)
            coEvery { taskRepo.getTaskById(42L) } returns task

            val result = GetTaskByIdUseCase(taskRepo)(42L)
            assertEquals(task, result)
        }

        @Test
        fun `should return null when not found`() = runTest {
            coEvery { taskRepo.getTaskById(999L) } returns null

            val result = GetTaskByIdUseCase(taskRepo)(999L)
            assertNull(result)
        }
    }

    // -----------------------------------------------------------------------
    // UpdateTaskUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class UpdateTaskUseCaseTest {
        @Test
        fun `should call updateTask on repository`() = runTest {
            val task = buildTask(id = 5)
            coEvery { taskRepo.updateTask(task) } returns Unit

            val result = UpdateTaskUseCase(taskRepo)(task)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { taskRepo.updateTask(task) }
        }

        @Test
        fun `should call generateRepeatTasks when repeatRule is Daily`() = runTest {
            val task = buildTask(id = 5, repeatRule = RepeatRule.Daily)
            coEvery { taskRepo.updateTask(task) } returns Unit

            val result = UpdateTaskUseCase(taskRepo)(task)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { taskRepo.generateRepeatTasks(5L, task.date.plusDays(30)) }
        }

        @Test
        fun `should call generateRepeatTasks when repeatRule is Weekdays`() = runTest {
            val task = buildTask(id = 6, repeatRule = RepeatRule.Weekdays)
            coEvery { taskRepo.updateTask(task) } returns Unit

            val result = UpdateTaskUseCase(taskRepo)(task)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { taskRepo.generateRepeatTasks(6L, task.date.plusDays(30)) }
        }

        @Test
        fun `should NOT call generateRepeatTasks when repeatRule is None`() = runTest {
            val task = buildTask(id = 7, repeatRule = RepeatRule.None)
            coEvery { taskRepo.updateTask(task) } returns Unit

            val result = UpdateTaskUseCase(taskRepo)(task)
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { taskRepo.generateRepeatTasks(any(), any()) }
        }
    }

    // -----------------------------------------------------------------------
    // DeleteTaskUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class DeleteTaskUseCaseTest {
        @Test
        fun `SINGLE mode should delete non-repeating task`() = runTest {
            val task = buildTask(id = 7, repeatRule = RepeatRule.None)
            coEvery { taskRepo.getTaskById(7L) } returns task

            val result = DeleteTaskUseCase(taskRepo)(taskId = 7L, deleteMode = DeleteMode.SINGLE)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { taskRepo.deleteTask(7L) }
            coVerify(exactly = 0) { taskRepo.skipTask(any()) }
        }

        @Test
        fun `SINGLE mode should skip repeating task instead of deleting`() = runTest {
            val task = buildTask(id = 8, repeatRule = RepeatRule.Daily)
            coEvery { taskRepo.getTaskById(8L) } returns task

            val result = DeleteTaskUseCase(taskRepo)(taskId = 8L, deleteMode = DeleteMode.SINGLE)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { taskRepo.skipTask(8L) }
            coVerify(exactly = 0) { taskRepo.deleteTask(any()) }
        }

        @Test
        fun `ALL_RECURRING mode on non-existing task should do nothing`() = runTest {
            coEvery { taskRepo.getTaskById(99L) } returns null

            val result = DeleteTaskUseCase(taskRepo)(taskId = 99L, deleteMode = DeleteMode.ALL_RECURRING)
            assertTrue(result.isSuccess)
            coVerify(exactly = 0) { taskRepo.deleteRecurringByTitle(any(), any()) }
        }

        @Test
        fun `ALL_RECURRING mode calls deleteRecurringByTitle with task title and date`() = runTest {
            val task = buildTask(id = 10, date = LocalDate.of(2026, 3, 1))
            coEvery { taskRepo.getTaskById(10L) } returns task

            val result = DeleteTaskUseCase(taskRepo)(taskId = 10L, deleteMode = DeleteMode.ALL_RECURRING)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) {
                taskRepo.deleteRecurringByTitle("测试任务", LocalDate.of(2026, 3, 1))
            }
        }

        @Test
        fun `ALL_RECURRING mode should NOT call deleteTask (single)`() = runTest {
            val task = buildTask(id = 10)
            coEvery { taskRepo.getTaskById(10L) } returns task

            DeleteTaskUseCase(taskRepo)(taskId = 10L, deleteMode = DeleteMode.ALL_RECURRING)
            coVerify(exactly = 0) { taskRepo.deleteTask(any()) }
        }
    }

    // -----------------------------------------------------------------------
    // CopyTasksUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class CopyTasksUseCaseTest {
        @Test
        fun `should copy only PENDING tasks to target date`() = runTest {
            val target = LocalDate.of(2026, 3, 2)
            val tasks = listOf(
                buildTask(id = 1, status = TaskStatus.PENDING),
                buildTask(id = 2, status = TaskStatus.ON_TIME_DONE)   // 已完成，不复制
            )
            coEvery { taskRepo.insertTask(any()) } returns 100L

            val result = CopyTasksUseCase(taskRepo)(tasks, target)
            assertTrue(result.isSuccess)
            assertEquals(1, result.getOrNull())  // 只复制 1 个 PENDING 任务
            coVerify(exactly = 1) { taskRepo.insertTask(any()) }
        }

        @Test
        fun `copied task should have target date and id=0`() = runTest {
            val target = LocalDate.of(2026, 3, 5)
            val original = buildTask(id = 3)
            coEvery { taskRepo.insertTask(any()) } returns 200L

            CopyTasksUseCase(taskRepo)(listOf(original), target)

            coVerify {
                taskRepo.insertTask(match { it.date == target && it.id == 0L })
            }
        }
    }

    // -----------------------------------------------------------------------
    // ApplyTaskTemplateUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class ApplyTaskTemplateUseCaseTest {
        private val template = buildTemplate(defaultDeadlineOffset = 20 * 60)  // 20:00

        @Test
        fun `should create task with template name and subject`() = runTest {
            val date = LocalDate.of(2026, 3, 1)
            coEvery { taskRepo.insertTask(any()) } returns 55L

            val result = ApplyTaskTemplateUseCase(taskRepo)(template, date)
            assertTrue(result.isSuccess)
            coVerify {
                taskRepo.insertTask(match { task ->
                    task.title == template.name && task.subject == template.subject
                })
            }
        }

        @Test
        fun `should set deadline based on defaultDeadlineOffset`() = runTest {
            val date = LocalDate.of(2026, 3, 1)
            coEvery { taskRepo.insertTask(any()) } returns 55L

            ApplyTaskTemplateUseCase(taskRepo)(template, date)

            coVerify {
                taskRepo.insertTask(match { task ->
                    // offset = 1200 min = 20h → deadline = 2026-03-01T20:00
                    task.deadline == date.atStartOfDay().plusMinutes(1200)
                })
            }
        }

        @Test
        fun `template without deadline offset should create task with null deadline`() = runTest {
            val t = buildTemplate(defaultDeadlineOffset = null)
            val date = LocalDate.of(2026, 3, 1)
            coEvery { taskRepo.insertTask(any()) } returns 56L

            ApplyTaskTemplateUseCase(taskRepo)(t, date)

            coVerify {
                taskRepo.insertTask(match { it.deadline == null })
            }
        }
    }

    // -----------------------------------------------------------------------
    // SaveCustomTemplateUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class SaveCustomTemplateUseCaseTest {
        @Test
        fun `should insert non-builtin template`() = runTest {
            val template = buildTemplate(isBuiltIn = false)
            coEvery { templateRepo.insertTemplate(any()) } returns 1L

            val result = SaveCustomTemplateUseCase(templateRepo)(template)
            assertTrue(result.isSuccess)
            coVerify(exactly = 1) { templateRepo.insertTemplate(any()) }
        }

        @Test
        fun `should fail when template is built-in`() = runTest {
            val template = buildTemplate(isBuiltIn = true)
            val result = SaveCustomTemplateUseCase(templateRepo)(template)
            assertTrue(result.isFailure)
            coVerify(exactly = 0) { templateRepo.insertTemplate(any()) }
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private fun buildTask(
        id: Long = 0,
        date: LocalDate = LocalDate.of(2026, 3, 1),
        status: TaskStatus = TaskStatus.PENDING,
        repeatRule: RepeatRule = RepeatRule.None
    ) = Task(
        id = id,
        title = "测试任务",
        subject = Subject.MATH,
        estimatedMinutes = 60,
        repeatRule = repeatRule,
        date = date,
        deadline = null,
        templateType = null,
        status = status
    )

    private fun buildTemplate(
        defaultDeadlineOffset: Int? = null,
        isBuiltIn: Boolean = false
    ) = TaskTemplate(
        id = 1L,
        name = "晨读",
        type = TaskTemplateType.MORNING_READING,
        subject = Subject.CHINESE,
        estimatedMinutes = 20,
        defaultDeadlineOffset = defaultDeadlineOffset,
        rewardConfig = TemplateRewardConfig(
            baseReward = MushroomRewardConfig(MushroomLevel.SMALL, 1),
            bonusReward = null,
            bonusCondition = null
        ),
        isBuiltIn = isBuiltIn
    )
}
