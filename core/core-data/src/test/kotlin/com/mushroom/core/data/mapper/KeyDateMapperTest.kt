package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class KeyDateMapperTest {

    private val baseReward = MushroomRewardConfig(MushroomLevel.GOLD, 1)

    @Test
    fun `when_condition_ConsecutiveCheckinDays_should_roundtrip`() {
        val keyDate = KeyDate(
            id = 1L,
            name = "连续30天",
            date = LocalDate.of(2026, 3, 31),
            condition = KeyDateCondition.ConsecutiveCheckinDays(30),
            rewardConfig = baseReward
        )
        val restored = KeyDateMapper.toDomain(KeyDateMapper.toDb(keyDate))
        assertEquals(keyDate, restored)
        val condition = restored.condition as KeyDateCondition.ConsecutiveCheckinDays
        assertEquals(30, condition.days)
    }

    @Test
    fun `when_condition_MilestoneScore_should_roundtrip_all_fields`() {
        val keyDate = KeyDate(
            id = 2L,
            name = "数学满分",
            date = LocalDate.of(2026, 5, 1),
            condition = KeyDateCondition.MilestoneScore(milestoneId = 99L, minScore = 95),
            rewardConfig = baseReward
        )
        val restored = KeyDateMapper.toDomain(KeyDateMapper.toDb(keyDate))
        val condition = restored.condition as KeyDateCondition.MilestoneScore
        assertEquals(99L, condition.milestoneId)
        assertEquals(95, condition.minScore)
    }

    @Test
    fun `when_condition_ManualTrigger_should_roundtrip`() {
        val keyDate = KeyDate(
            id = 3L,
            name = "手动触发",
            date = LocalDate.of(2026, 6, 1),
            condition = KeyDateCondition.ManualTrigger,
            rewardConfig = baseReward
        )
        val restored = KeyDateMapper.toDomain(KeyDateMapper.toDb(keyDate))
        assertTrue(restored.condition is KeyDateCondition.ManualTrigger)
    }

    @Test
    fun `when_rewardConfig_fields_should_roundtrip`() {
        val keyDate = KeyDate(
            id = 4L,
            name = "特殊日期",
            date = LocalDate.of(2026, 12, 31),
            condition = KeyDateCondition.ManualTrigger,
            rewardConfig = MushroomRewardConfig(MushroomLevel.LEGEND, 3)
        )
        val restored = KeyDateMapper.toDomain(KeyDateMapper.toDb(keyDate))
        assertEquals(MushroomLevel.LEGEND, restored.rewardConfig.level)
        assertEquals(3, restored.rewardConfig.amount)
    }
}
