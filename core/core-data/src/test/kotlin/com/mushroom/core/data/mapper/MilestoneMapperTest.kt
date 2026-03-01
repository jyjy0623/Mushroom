package com.mushroom.core.data.mapper

import com.mushroom.core.domain.entity.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestoneMapperTest {

    private val rules = listOf(
        ScoringRule(0, 59, MushroomRewardConfig(MushroomLevel.SMALL, 0)),
        ScoringRule(60, 89, MushroomRewardConfig(MushroomLevel.MEDIUM, 1)),
        ScoringRule(90, 100, MushroomRewardConfig(MushroomLevel.LARGE, 2))
    )

    private val base = Milestone(
        id = 1L,
        name = "数学期中考试",
        type = MilestoneType.MIDTERM,
        subject = Subject.MATH,
        scheduledDate = LocalDate.of(2026, 4, 1),
        scoringRules = rules,
        actualScore = null,
        status = MilestoneStatus.PENDING
    )

    @Test
    fun `when_roundtrip_milestone_should_equal_original`() {
        val entity = MilestoneMapper.toDb(base)
        val ruleEntities = MilestoneMapper.rulesToDb(base.id, base.scoringRules)
        val restored = MilestoneMapper.toDomain(entity, ruleEntities)
        assertEquals(base, restored)
    }

    @Test
    fun `when_actualScore_is_null_should_remain_null`() {
        val restored = MilestoneMapper.toDomain(
            MilestoneMapper.toDb(base.copy(actualScore = null)),
            MilestoneMapper.rulesToDb(base.id, base.scoringRules)
        )
        assertNull(restored.actualScore)
    }

    @Test
    fun `when_actualScore_is_set_should_roundtrip`() {
        val withScore = base.copy(actualScore = 92, status = MilestoneStatus.SCORED)
        val restored = MilestoneMapper.toDomain(
            MilestoneMapper.toDb(withScore),
            MilestoneMapper.rulesToDb(withScore.id, withScore.scoringRules)
        )
        assertEquals(92, restored.actualScore)
        assertEquals(MilestoneStatus.SCORED, restored.status)
    }

    @Test
    fun `when_scoring_rules_list_should_preserve_size_and_values`() {
        val ruleEntities = MilestoneMapper.rulesToDb(1L, rules)
        assertEquals(3, ruleEntities.size)
        assertEquals(60, ruleEntities[1].minScore)
        assertEquals(89, ruleEntities[1].maxScore)
        assertEquals(MushroomLevel.MEDIUM.name, ruleEntities[1].rewardLevel)
        assertEquals(1, ruleEntities[1].rewardAmount)
    }

    @Test
    fun `when_all_MilestoneStatus_values_should_roundtrip`() {
        MilestoneStatus.values().forEach { status ->
            val m = base.copy(status = status)
            val restored = MilestoneMapper.toDomain(
                MilestoneMapper.toDb(m),
                MilestoneMapper.rulesToDb(m.id, m.scoringRules)
            )
            assertEquals(status, restored.status, "MilestoneStatus.$status 应正确往返转换")
        }
    }
}
