package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.CheckIn
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CheckInMapperTest {

    private val base = CheckIn(
        id = 10L,
        taskId = 1L,
        date = LocalDate.of(2026, 3, 1),
        checkedAt = LocalDateTime.of(2026, 3, 1, 19, 30, 0),
        isEarly = true,
        earlyMinutes = 30,
        note = null,
        imageUris = emptyList()
    )

    @Test
    fun `when_roundtrip_basic_checkin_should_equal_original`() {
        assertEquals(base, CheckInMapper.toDomain(CheckInMapper.toDb(base)))
    }

    @Test
    fun `when_imageUris_is_empty_should_roundtrip_as_empty_list`() {
        val restored = CheckInMapper.toDomain(CheckInMapper.toDb(base))
        assertTrue(restored.imageUris.isEmpty())
    }

    @Test
    fun `when_imageUris_has_multiple_values_should_roundtrip_correctly`() {
        val uris = listOf("content://images/1", "content://images/2")
        val restored = CheckInMapper.toDomain(CheckInMapper.toDb(base.copy(imageUris = uris)))
        assertEquals(2, restored.imageUris.size)
        assertEquals(uris, restored.imageUris)
    }

    @Test
    fun `when_isEarly_false_and_earlyMinutes_zero_should_roundtrip`() {
        val notEarly = base.copy(isEarly = false, earlyMinutes = 0)
        val restored = CheckInMapper.toDomain(CheckInMapper.toDb(notEarly))
        assertFalse(restored.isEarly)
        assertEquals(0, restored.earlyMinutes)
    }

    @Test
    fun `when_note_is_null_should_remain_null`() {
        val restored = CheckInMapper.toDomain(CheckInMapper.toDb(base.copy(note = null)))
        assertNull(restored.note)
    }

    @Test
    fun `when_note_has_content_should_roundtrip`() {
        val restored = CheckInMapper.toDomain(CheckInMapper.toDb(base.copy(note = "做完了！")))
        assertEquals("做完了！", restored.note)
    }
}
