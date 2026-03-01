package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime

class TaskMapperTest {

    private val baseTask = Task(
        id = 1L,
        title = "做数学作业",
        subject = Subject.MATH,
        estimatedMinutes = 60,
        repeatRule = RepeatRule.None,
        date = LocalDate.of(2026, 3, 1),
        deadline = null,
        templateType = null,
        status = TaskStatus.PENDING
    )

    @Test
    fun `when_roundtrip_simple_task_domain_should_equal_original`() {
        val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask))
        assertEquals(baseTask, restored)
    }

    @Test
    fun `when_deadline_is_null_should_map_to_null_without_exception`() {
        val entity = TaskMapper.toDb(baseTask.copy(deadline = null))
        assertNull(entity.deadlineAt)
        val restored = TaskMapper.toDomain(entity)
        assertNull(restored.deadline)
    }

    @Test
    fun `when_deadline_is_set_should_preserve_precision_to_seconds`() {
        val deadline = LocalDateTime.of(2026, 3, 1, 20, 30, 0)
        val entity = TaskMapper.toDb(baseTask.copy(deadline = deadline))
        val restored = TaskMapper.toDomain(entity)
        assertEquals(deadline, restored.deadline)
    }

    @Test
    fun `when_repeatRule_is_None_should_roundtrip`() {
        val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask.copy(repeatRule = RepeatRule.None)))
        assertEquals(RepeatRule.None, restored.repeatRule)
    }

    @Test
    fun `when_repeatRule_is_Daily_should_roundtrip`() {
        val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask.copy(repeatRule = RepeatRule.Daily)))
        assertEquals(RepeatRule.Daily, restored.repeatRule)
    }

    @Test
    fun `when_repeatRule_is_Weekdays_should_roundtrip`() {
        val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask.copy(repeatRule = RepeatRule.Weekdays)))
        assertEquals(RepeatRule.Weekdays, restored.repeatRule)
    }

    @Test
    fun `when_repeatRule_is_Custom_with_multiple_days_should_roundtrip_correctly`() {
        val custom = RepeatRule.Custom(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY))
        val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask.copy(repeatRule = custom)))
        assertEquals(custom, restored.repeatRule)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            (restored.repeatRule as RepeatRule.Custom).daysOfWeek
        )
    }

    @Test
    fun `when_templateType_is_set_should_roundtrip`() {
        val task = baseTask.copy(templateType = TaskTemplateType.MORNING_READING)
        val restored = TaskMapper.toDomain(TaskMapper.toDb(task))
        assertEquals(TaskTemplateType.MORNING_READING, restored.templateType)
    }

    @Test
    fun `when_all_TaskStatus_values_should_roundtrip`() {
        TaskStatus.values().forEach { status ->
            val restored = TaskMapper.toDomain(TaskMapper.toDb(baseTask.copy(status = status)))
            assertEquals(status, restored.status, "Status $status 应正确往返转换")
        }
    }
}
