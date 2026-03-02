package com.mushroom.feature.mushroom.reward

import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.event.RewardEvent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class RewardRulesTest {

    private val chain = RewardRuleChain()

    // -----------------------------------------------------------------------
    // DailyTaskCompleteRule
    // -----------------------------------------------------------------------
    @Nested
    inner class DailyTaskCompleteRuleTest {
        @Test
        fun `should give SMALL x1 for normal task completion`() {
            val event = buildTaskCompleted(templateType = null, isEarly = false)
            val rewards = DailyTaskCompleteRule().calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(1, rewards[0].amount)
            assertEquals(MushroomSource.TASK, rewards[0].sourceType)
        }

        @Test
        fun `should NOT apply to template tasks`() {
            listOf(
                TaskTemplateType.MORNING_READING,
                TaskTemplateType.HOMEWORK_MEMO,
                TaskTemplateType.HOMEWORK_AT_SCHOOL,
                TaskTemplateType.CUSTOM
            ).forEach { type ->
                val event = buildTaskCompleted(templateType = type, isEarly = false)
                assertFalse(DailyTaskCompleteRule().applies(event))
            }
        }
    }

    // -----------------------------------------------------------------------
    // EarlyCompletionRule
    // -----------------------------------------------------------------------
    @Nested
    inner class EarlyCompletionRuleTest {
        @Test
        fun `should NOT apply when not early`() {
            val event = buildTaskCompleted(isEarly = false)
            val rule = EarlyCompletionRule()
            assertTrue(!rule.applies(event))
        }

        @Test
        fun `earlyMinutes less than 60 should give SMALL x1`() {
            val event = buildTaskCompleted(isEarly = true, earlyMinutes = 30)
            val rewards = EarlyCompletionRule().calculate(event)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }

        @Test
        fun `earlyMinutes 60-180 should give SMALL x2`() {
            val event = buildTaskCompleted(isEarly = true, earlyMinutes = 120)
            val rewards = EarlyCompletionRule().calculate(event)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(2, rewards[0].amount)
        }

        @Test
        fun `earlyMinutes more than 180 should give MEDIUM x1`() {
            val event = buildTaskCompleted(isEarly = true, earlyMinutes = 200)
            val rewards = EarlyCompletionRule().calculate(event)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }
    }

    // -----------------------------------------------------------------------
    // MorningReadingRule
    // -----------------------------------------------------------------------
    @Nested
    inner class MorningReadingRuleTest {
        @Test
        fun `should give SMALL x1 for morning reading template`() {
            val event = buildTaskCompleted(templateType = TaskTemplateType.MORNING_READING)
            val rewards = MorningReadingRule().calculate(event)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(1, rewards[0].amount)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
        }

        @Test
        fun `should NOT apply to non-morning-reading task`() {
            val event = buildTaskCompleted(templateType = null)
            assertFalse(MorningReadingRule().applies(event))
        }
    }

    // -----------------------------------------------------------------------
    // HomeworkMemoRule
    // -----------------------------------------------------------------------
    @Nested
    inner class HomeworkMemoRuleTest {
        @Test
        fun `should give SMALL x1 for homework memo template`() {
            val event = buildTaskCompleted(templateType = TaskTemplateType.HOMEWORK_MEMO)
            val rewards = HomeworkMemoRule().calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(1, rewards[0].amount)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
        }

        @Test
        fun `should NOT apply to non-homework-memo task`() {
            val event = buildTaskCompleted(templateType = null)
            assertFalse(HomeworkMemoRule().applies(event))
        }
    }

    // -----------------------------------------------------------------------
    // HomeworkAtSchoolRule
    // -----------------------------------------------------------------------
    @Nested
    inner class HomeworkAtSchoolRuleTest {
        @Test
        fun `should give MEDIUM x1 for homework at school template`() {
            val event = buildTaskCompleted(templateType = TaskTemplateType.HOMEWORK_AT_SCHOOL)
            val rewards = HomeworkAtSchoolRule().calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
            assertEquals(1, rewards[0].amount)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
        }

        @Test
        fun `should NOT apply to non-homework-at-school task`() {
            val event = buildTaskCompleted(templateType = null)
            assertFalse(HomeworkAtSchoolRule().applies(event))
        }
    }

    // -----------------------------------------------------------------------
    // AllTasksDoneRule
    // -----------------------------------------------------------------------
    @Nested
    inner class AllTasksDoneRuleTest {
        @Test
        fun `should give MEDIUM x1 for all tasks done`() {
            val event = RewardEvent.AllDailyTasksDone(LocalDate.of(2026, 3, 1))
            val rewards = AllTasksDoneRule().calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }
    }

    // -----------------------------------------------------------------------
    // StreakRule
    // -----------------------------------------------------------------------
    @Nested
    inner class StreakRuleTest {
        @Test
        fun `7-day streak should give MEDIUM x1`() {
            val event = RewardEvent.StreakReached(7)
            val rewards = StreakRule().calculate(event)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
        }

        @Test
        fun `30-day streak should give LARGE x1`() {
            val event = RewardEvent.StreakReached(30)
            val rewards = StreakRule().calculate(event)
            assertEquals(MushroomLevel.LARGE, rewards[0].level)
        }

        @Test
        fun `100-day streak should give GOLD x1`() {
            val event = RewardEvent.StreakReached(100)
            val rewards = StreakRule().calculate(event)
            assertEquals(MushroomLevel.GOLD, rewards[0].level)
        }

        @Test
        fun `non-milestone streak should NOT apply`() {
            val event = RewardEvent.StreakReached(5)
            assertFalse(StreakRule().applies(event))
        }
    }

    // -----------------------------------------------------------------------
    // MilestoneScoreRule
    // -----------------------------------------------------------------------
    @Nested
    inner class MilestoneScoreRuleTest {
        @Test
        fun `should give reward matching scoring rule`() {
            val milestone = buildMilestone(score = 85)
            val event = RewardEvent.MilestoneAchieved(milestone)
            val rewards = MilestoneScoreRule().calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }

        @Test
        fun `should return empty when score does not match any rule`() {
            val milestone = buildMilestone(score = 50)  // below all rules
            val event = RewardEvent.MilestoneAchieved(milestone)
            val rewards = MilestoneScoreRule().calculate(event)
            assertTrue(rewards.isEmpty())
        }
    }

    // -----------------------------------------------------------------------
    // RewardRuleChain integration
    // -----------------------------------------------------------------------
    @Nested
    inner class RewardRuleChainTest {

        @Test
        fun `non-template non-early task should give only base reward`() {
            val event = buildTaskCompleted(templateType = null, isEarly = false)
            val rewards = chain.calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomSource.TASK, rewards[0].sourceType)
        }

        @Test
        fun `non-template early task should give base + early bonus`() {
            val event = buildTaskCompleted(templateType = null, isEarly = true, earlyMinutes = 30)
            val rewards = chain.calculate(event)
            assertEquals(2, rewards.size)
            assertTrue(rewards.any { it.sourceType == MushroomSource.TASK })
            assertTrue(rewards.any { it.sourceType == MushroomSource.EARLY_BONUS })
        }

        @Test
        fun `morning reading non-early should give SMALL x1 only — no base reward`() {
            val event = buildTaskCompleted(
                templateType = TaskTemplateType.MORNING_READING,
                isEarly = false
            )
            val rewards = chain.calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
            assertEquals(MushroomLevel.SMALL, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }

        @Test
        fun `morning reading early should give template bonus + early bonus only`() {
            val event = buildTaskCompleted(
                templateType = TaskTemplateType.MORNING_READING,
                isEarly = true,
                earlyMinutes = 30
            )
            val rewards = chain.calculate(event)
            // MorningReadingRule + EarlyCompletionRule，不含 DailyTaskCompleteRule
            assertEquals(2, rewards.size)
            assertTrue(rewards.any { it.sourceType == MushroomSource.TEMPLATE_BONUS })
            assertTrue(rewards.any { it.sourceType == MushroomSource.EARLY_BONUS })
        }

        @Test
        fun `homework memo non-early should give SMALL x1 template bonus only`() {
            val event = buildTaskCompleted(
                templateType = TaskTemplateType.HOMEWORK_MEMO,
                isEarly = false
            )
            val rewards = chain.calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
            assertEquals(1, rewards[0].amount)
        }

        @Test
        fun `homework at school non-early should give MEDIUM x1 template bonus only`() {
            val event = buildTaskCompleted(
                templateType = TaskTemplateType.HOMEWORK_AT_SCHOOL,
                isEarly = false
            )
            val rewards = chain.calculate(event)
            assertEquals(1, rewards.size)
            assertEquals(MushroomSource.TEMPLATE_BONUS, rewards[0].sourceType)
            assertEquals(MushroomLevel.MEDIUM, rewards[0].level)
            assertEquals(1, rewards[0].amount)
        }
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------
    private fun buildTaskCompleted(
        templateType: TaskTemplateType? = null,
        isEarly: Boolean = false,
        earlyMinutes: Int = 0
    ): RewardEvent.TaskCompleted {
        val task = Task(
            id = 1L,
            title = "测试任务",
            subject = Subject.MATH,
            estimatedMinutes = 60,
            repeatRule = RepeatRule.None,
            date = LocalDate.of(2026, 3, 1),
            deadline = if (isEarly) LocalDateTime.now().plusMinutes(earlyMinutes.toLong()) else null,
            templateType = templateType,
            status = TaskStatus.EARLY_DONE
        )
        val checkIn = CheckIn(
            id = 10L,
            taskId = 1L,
            date = LocalDate.of(2026, 3, 1),
            checkedAt = LocalDateTime.now(),
            isEarly = isEarly,
            earlyMinutes = earlyMinutes,
            note = null,
            imageUris = emptyList()
        )
        return RewardEvent.TaskCompleted(task = task, checkIn = checkIn)
    }

    private fun buildMilestone(score: Int) = Milestone(
        id = 1L,
        name = "期中考试",
        type = MilestoneType.MIDTERM,
        subject = Subject.MATH,
        scheduledDate = LocalDate.of(2026, 3, 15),
        scoringRules = listOf(
            ScoringRule(minScore = 80, maxScore = 100,
                rewardConfig = MushroomRewardConfig(MushroomLevel.MEDIUM, 1)),
            ScoringRule(minScore = 60, maxScore = 79,
                rewardConfig = MushroomRewardConfig(MushroomLevel.SMALL, 2))
        ),
        actualScore = score,
        status = MilestoneStatus.SCORED
    )
}

private fun assertFalse(value: Boolean) {
    assertTrue(!value)
}
