package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class DeductionMapperTest {

    private val baseConfig = DeductionConfig(
        id = 1L,
        name = "忘带作业",
        mushroomLevel = MushroomLevel.SMALL,
        defaultAmount = 1,
        customAmount = 1,
        isEnabled = false,
        isBuiltIn = true,
        maxPerDay = 1
    )

    private val baseRecord = DeductionRecord(
        id = 1L,
        configId = 1L,
        mushroomLevel = MushroomLevel.SMALL,
        amount = 1,
        reason = "忘带数学作业",
        recordedAt = LocalDateTime.of(2026, 3, 1, 8, 0, 0),
        appealStatus = AppealStatus.NONE,
        appealNote = null
    )

    @Test
    fun `when_roundtrip_config_should_equal_original`() {
        assertEquals(baseConfig, DeductionMapper.toConfigDomain(DeductionMapper.toConfigDb(baseConfig)))
    }

    @Test
    fun `when_config_isEnabled_true_should_roundtrip`() {
        val config = baseConfig.copy(isEnabled = true)
        assertEquals(config, DeductionMapper.toConfigDomain(DeductionMapper.toConfigDb(config)))
    }

    @Test
    fun `when_roundtrip_record_should_equal_original`() {
        assertEquals(baseRecord, DeductionMapper.toRecordDomain(DeductionMapper.toRecordDb(baseRecord)))
    }

    @Test
    fun `when_all_AppealStatus_values_should_roundtrip`() {
        AppealStatus.values().forEach { status ->
            val record = baseRecord.copy(appealStatus = status)
            val restored = DeductionMapper.toRecordDomain(DeductionMapper.toRecordDb(record))
            assertEquals(status, restored.appealStatus, "AppealStatus.$status 应正确往返转换")
        }
    }

    @Test
    fun `when_appealNote_is_null_should_remain_null`() {
        val restored = DeductionMapper.toRecordDomain(DeductionMapper.toRecordDb(baseRecord.copy(appealNote = null)))
        assertNull(restored.appealNote)
    }

    @Test
    fun `when_appealNote_has_content_should_roundtrip`() {
        val record = baseRecord.copy(appealNote = "我真的交了！")
        val restored = DeductionMapper.toRecordDomain(DeductionMapper.toRecordDb(record))
        assertEquals("我真的交了！", restored.appealNote)
    }
}
