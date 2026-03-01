package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class MushroomLedgerMapperTest {

    private val base = MushroomTransaction(
        id = 1L,
        level = MushroomLevel.SMALL,
        action = MushroomAction.EARN,
        amount = 2,
        sourceType = MushroomSource.TASK,
        sourceId = 100L,
        note = null,
        createdAt = LocalDateTime.of(2026, 3, 1, 20, 0, 0)
    )

    @Test
    fun `when_roundtrip_basic_transaction_should_equal_original`() {
        assertEquals(base, MushroomLedgerMapper.toDomain(MushroomLedgerMapper.toDb(base)))
    }

    @Test
    fun `when_all_MushroomLevel_values_should_roundtrip`() {
        MushroomLevel.values().forEach { level ->
            val t = base.copy(level = level)
            val restored = MushroomLedgerMapper.toDomain(MushroomLedgerMapper.toDb(t))
            assertEquals(level, restored.level, "Level $level 应正确往返转换")
        }
    }

    @Test
    fun `when_all_MushroomAction_values_should_roundtrip`() {
        MushroomAction.values().forEach { action ->
            val t = base.copy(action = action)
            val restored = MushroomLedgerMapper.toDomain(MushroomLedgerMapper.toDb(t))
            assertEquals(action, restored.action)
        }
    }

    @Test
    fun `when_all_MushroomSource_values_should_roundtrip`() {
        MushroomSource.values().forEach { source ->
            val t = base.copy(sourceType = source, sourceId = if (source == MushroomSource.DEDUCTION) null else 1L)
            val restored = MushroomLedgerMapper.toDomain(MushroomLedgerMapper.toDb(t))
            assertEquals(source, restored.sourceType, "Source $source 应正确往返转换")
        }
    }

    @Test
    fun `when_sourceId_is_null_should_remain_null`() {
        val t = base.copy(sourceId = null)
        val restored = MushroomLedgerMapper.toDomain(MushroomLedgerMapper.toDb(t))
        assertNull(restored.sourceId)
    }
}
