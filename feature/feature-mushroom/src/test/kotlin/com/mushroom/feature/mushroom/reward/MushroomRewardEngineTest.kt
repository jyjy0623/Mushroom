package com.mushroom.feature.mushroom.reward

import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.event.MushroomReward
import com.mushroom.core.domain.event.RewardEvent
import com.mushroom.core.domain.repository.MilestoneRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.service.RewardRuleEngine
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class MushroomRewardEngineTest {

    private lateinit var mushroomRepo: MushroomRepository
    private lateinit var milestoneRepo: MilestoneRepository
    private lateinit var eventBus: AppEventBus
    private lateinit var engine: TestableMushroomRewardEngine

    private val milestoneId = 8L
    private val milestoneName = "数学小测 3月13日"

    @BeforeEach
    fun setup() {
        mushroomRepo = mockk(relaxed = true)
        milestoneRepo = mockk(relaxed = true)
        val ruleEngine: RewardRuleEngine = mockk(relaxed = true)
        eventBus = mockk(relaxed = true)

        engine = TestableMushroomRewardEngine(
            eventBus = eventBus,
            milestoneRepo = milestoneRepo,
            mushroomRepo = mushroomRepo,
            ruleEngine = ruleEngine
        )
    }

    private fun buildMilestone(score: Int?): Milestone = Milestone(
        id = milestoneId,
        name = milestoneName,
        type = MilestoneType.MINI_TEST,
        subject = Subject.MATH,
        scheduledDate = LocalDate.of(2026, 3, 13),
        scoringRules = listOf(
            ScoringRule(80, 100, MushroomRewardConfig(MushroomLevel.MEDIUM, 1)),
            ScoringRule(60, 79, MushroomRewardConfig(MushroomLevel.SMALL, 1)),
            ScoringRule(0, 59, MushroomRewardConfig(MushroomLevel.SMALL, 0))
        ),
        actualScore = score,
        status = if (score != null) MilestoneStatus.SCORED else MilestoneStatus.PENDING
    )

    private fun buildEarnTransaction(level: MushroomLevel, amount: Int, createdAt: LocalDateTime): MushroomTransaction =
        MushroomTransaction(
            id = 0,
            level = level,
            action = MushroomAction.EARN,
            amount = amount,
            sourceType = MushroomSource.MILESTONE,
            sourceId = milestoneId,
            note = "里程碑「$milestoneName」得分",
            createdAt = createdAt
        )

    @Nested
    inner class HandleMilestoneScoredTest {

        @Test
        @DisplayName("第一次录入成绩67分，应发放 SMALL x1，不扣除")
        suspend fun `first score 67 should reward SMALL x1 no deduction`() {
            // Given: 里程碑，67分
            val milestone = buildMilestone(score = 67)
            every { milestoneRepo.getAllMilestones() } returns flowOf(listOf(milestone))

            // 没有旧奖励记录
            coEvery { mushroomRepo.getLatestEarnBySource(MushroomSource.MILESTONE, milestoneId) } returns null
            coEvery {
                mushroomRepo.getTransactionsBySource(
                    MushroomSource.MILESTONE,
                    milestoneId,
                    any()
                )
            } returns emptyList()

            // When
            engine.handleMilestoneScored(milestoneId, 67)

            // Then: 应发放新奖励，不扣除
            coVerify { mushroomRepo.recordTransaction(match<MushroomTransaction> {
                it.action == MushroomAction.EARN && it.level == MushroomLevel.SMALL && it.amount == 1
            }) }
        }

        @Test
        @DisplayName("成绩从67改为85，奖励提升，应扣除旧的1个SMALL再发放1个MEDIUM")
        suspend fun `score 67 to 85 reward increases should deduct old and dispatch new`() {
            // Given
            val milestone = buildMilestone(score = 85)
            every { milestoneRepo.getAllMilestones() } returns flowOf(listOf(milestone))

            // 旧奖励 SMALL x1
            val oldEarn = buildEarnTransaction(
                level = MushroomLevel.SMALL,
                amount = 1,
                createdAt = LocalDateTime.now().minusHours(1)
            )
            coEvery { mushroomRepo.getLatestEarnBySource(MushroomSource.MILESTONE, milestoneId) } returns oldEarn

            // When: 改为85分
            engine.handleMilestoneScored(milestoneId, 85)

            // Then: 扣除1个SMALL，发放1个MEDIUM
            coVerify { mushroomRepo.recordTransaction(match<MushroomTransaction> {
                it.action == MushroomAction.DEDUCT && it.level == MushroomLevel.SMALL && it.amount == 1
            }) }
            coVerify { mushroomRepo.recordTransaction(match<MushroomTransaction> {
                it.action == MushroomAction.EARN && it.level == MushroomLevel.MEDIUM && it.amount == 1
            }) }
        }

        @Test
        @DisplayName("成绩从85改为67，奖励降低，应扣除旧的1个MEDIUM再发放1个SMALL")
        suspend fun `score 85 to 67 reward decreases should deduct old and dispatch new`() {
            // Given
            val milestone = buildMilestone(score = 85)
            every { milestoneRepo.getAllMilestones() } returns flowOf(listOf(milestone))

            // 旧奖励 MEDIUM x1
            val oldEarn = buildEarnTransaction(
                level = MushroomLevel.MEDIUM,
                amount = 1,
                createdAt = LocalDateTime.now().minusHours(1)
            )
            coEvery { mushroomRepo.getLatestEarnBySource(MushroomSource.MILESTONE, milestoneId) } returns oldEarn

            // When: 改为67分
            engine.handleMilestoneScored(milestoneId, 67)

            // Then: 扣除1个MEDIUM，发放1个SMALL
            coVerify { mushroomRepo.recordTransaction(match<MushroomTransaction> {
                it.action == MushroomAction.DEDUCT && it.level == MushroomLevel.MEDIUM && it.amount == 1
            }) }
            coVerify { mushroomRepo.recordTransaction(match<MushroomTransaction> {
                it.action == MushroomAction.EARN && it.level == MushroomLevel.SMALL && it.amount == 1
            }) }
        }
    }

    /**
     * 可测试版本的 MushroomRewardEngine
     */
    class TestableMushroomRewardEngine(
        private val eventBus: AppEventBus,
        private val milestoneRepo: MilestoneRepository,
        private val mushroomRepo: MushroomRepository,
        private val ruleEngine: RewardRuleEngine
    ) {
        suspend fun handleMilestoneScored(milestoneId: Long, newScore: Int) {
            val milestone = milestoneRepo.getAllMilestones().first()
                .firstOrNull { it.id == milestoneId } ?: return

            val notePattern = "里程碑「${milestone.name}」得分"
            val latestEarn = mushroomRepo.getLatestEarnBySource(MushroomSource.MILESTONE, milestoneId)

            val oldEarn = latestEarn ?: mushroomRepo.getTransactionsBySource(
                MushroomSource.MILESTONE, milestoneId, notePattern
            )
                .filter { it.action == MushroomAction.EARN }
                .maxByOrNull { it.createdAt }

            val oldReward = oldEarn?.let {
                MushroomReward(
                    level = it.level,
                    amount = it.amount,
                    reason = "",
                    sourceType = MushroomSource.MILESTONE,
                    sourceId = milestoneId
                )
            }

            val updatedMilestone = milestone.copy(actualScore = newScore)
            val rewardEvent = RewardEvent.MilestoneAchieved(updatedMilestone)
            val newRewards = ruleEngine.calculate(rewardEvent)
            val newReward = newRewards.firstOrNull()

            if (oldReward == newReward) {
                return
            }

            val now = LocalDateTime.now()

            if (oldEarn != null) {
                mushroomRepo.recordTransaction(
                    MushroomTransaction(
                        level = oldEarn.level,
                        action = MushroomAction.DEDUCT,
                        amount = oldEarn.amount,
                        sourceType = MushroomSource.MILESTONE,
                        sourceId = milestoneId,
                        note = "成绩更新，扣除旧奖励",
                        createdAt = now
                    )
                )
            }

            if (newReward != null) {
                val transactions = newRewards.map { reward ->
                    MushroomTransaction(
                        level = reward.level,
                        action = MushroomAction.EARN,
                        amount = reward.amount,
                        sourceType = reward.sourceType,
                        sourceId = reward.sourceId,
                        note = reward.reason,
                        createdAt = now
                    )
                }
                mushroomRepo.recordTransactions(transactions)
            }

            eventBus.emit(AppEvent.MilestoneRewardAdjusted(
                milestoneId = milestoneId,
                milestoneName = milestone.name,
                oldReward = oldReward,
                newReward = newReward
            ))
        }
    }
}