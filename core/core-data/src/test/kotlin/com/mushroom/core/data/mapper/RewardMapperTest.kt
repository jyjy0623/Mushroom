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
        requiredMushrooms = mapOf(MushroomLevel.GOLD to 2, MushroomLevel.LEGEND to 1),
        puzzlePieces = 9,
        timeLimitConfig = null,
        status = RewardStatus.ACTIVE
    )

    @Test
    fun `when_roundtrip_physical_reward_should_equal_original`() {
        assertEquals(base, RewardMapper.toDomain(RewardMapper.toDb(base)))
    }

    @Test
    fun `when_requiredMushrooms_has_multiple_levels_should_roundtrip`() {
        val restored = RewardMapper.toDomain(RewardMapper.toDb(base))
        assertEquals(2, restored.requiredMushrooms[MushroomLevel.GOLD])
        assertEquals(1, restored.requiredMushrooms[MushroomLevel.LEGEND])
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
            periodType = PeriodType.WEEKLY,
            maxMinutesPerPeriod = 120,
            cooldownDays = 1,
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
        assertEquals(120, restored.timeLimitConfig!!.maxMinutesPerPeriod)
        assertEquals(1, restored.timeLimitConfig!!.cooldownDays)
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
