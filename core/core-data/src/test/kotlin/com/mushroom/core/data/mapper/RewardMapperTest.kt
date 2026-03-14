package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class RewardMapperTest {

    private val base = Reward(
        id = 1L,
        name = "高达模型",
        imageUri = "content://images/gundam.jpg",
        type = RewardType.PHYSICAL,
        requiredPoints = 100,
        puzzlePieces = 9,
        timeLimitConfig = null,
        status = RewardStatus.ACTIVE
    )

    @Test
    fun `when_roundtrip_physical_reward_should_equal_original`() {
        assertEquals(base, RewardMapper.toDomain(RewardMapper.toDb(base)))
    }

    @Test
    fun `when_requiredPoints_roundtrip_should_preserve_value`() {
        val restored = RewardMapper.toDomain(RewardMapper.toDb(base))
        assertEquals(100, restored.requiredPoints)
    }

    @Test
    fun `when_timeLimitConfig_is_null_should_remain_null`() {
        val restored = RewardMapper.toDomain(RewardMapper.toDb(base.copy(timeLimitConfig = null)))
        assertNull(restored.timeLimitConfig)
    }

    @Test
    fun `when_timeLimitConfig_is_set_should_roundtrip_all_fields`() {
        val config = TimeLimitConfig(
            unitMinutes = 30,
            costMushroomLevel = MushroomLevel.SMALL,
            costMushroomCount = 5,
            periodType = PeriodType.WEEKLY,
            maxTimesPerPeriod = 4,
            requireParentConfirm = true
        )
        val reward = base.copy(
            type = RewardType.TIME_BASED,
            timeLimitConfig = config
        )
        val restored = RewardMapper.toDomain(RewardMapper.toDb(reward))
        assertNotNull(restored.timeLimitConfig)
        assertEquals(30, restored.timeLimitConfig!!.unitMinutes)
        assertEquals(PeriodType.WEEKLY, restored.timeLimitConfig!!.periodType)
        assertEquals(4, restored.timeLimitConfig!!.maxTimesPerPeriod)
        assertEquals(MushroomLevel.SMALL, restored.timeLimitConfig!!.costMushroomLevel)
        assertEquals(5, restored.timeLimitConfig!!.costMushroomCount)
        assertTrue(restored.timeLimitConfig!!.requireParentConfirm)
    }

    @Test
    fun `when_all_RewardStatus_values_should_roundtrip`() {
        RewardStatus.values().forEach { status ->
            val restored = RewardMapper.toDomain(RewardMapper.toDb(base.copy(status = status)))
            assertEquals(status, restored.status, "RewardStatus.$status 应正确往返转换")
        }
    }
}
