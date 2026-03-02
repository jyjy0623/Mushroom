package com.mushroom.feature.reward.usecase

import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.PeriodType
import com.mushroom.core.domain.entity.PuzzleProgress
import com.mushroom.core.domain.entity.Reward
import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.entity.RewardStatus
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.core.domain.entity.TimeLimitConfig
import com.mushroom.core.domain.entity.TimeRewardBalance
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.RewardRepository
import com.mushroom.core.domain.service.ParentGateway
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

private fun buildReward(
    id: Long = 1L,
    type: RewardType = RewardType.PHYSICAL,
    puzzlePieces: Int = 10,
    status: RewardStatus = RewardStatus.ACTIVE,
    timeLimitConfig: TimeLimitConfig? = null
) = Reward(
    id = id,
    name = "测试奖品",
    imageUri = "",
    type = type,
    requiredMushrooms = mapOf(MushroomLevel.SMALL to 10),
    puzzlePieces = puzzlePieces,
    timeLimitConfig = timeLimitConfig,
    status = status
)

private fun buildTimeLimitConfig(
    unitMinutes: Int = 30,
    periodType: PeriodType = PeriodType.WEEKLY,
    maxMinutesPerPeriod: Int = 120,
    cooldownDays: Int = 0,
    requireParentConfirm: Boolean = false
) = TimeLimitConfig(
    unitMinutes = unitMinutes,
    periodType = periodType,
    maxMinutesPerPeriod = maxMinutesPerPeriod,
    cooldownDays = cooldownDays,
    requireParentConfirm = requireParentConfirm
)

// -----------------------------------------------------------------------
// ExchangeMushroomsUseCase — physical path
// -----------------------------------------------------------------------
class ExchangeMushroomsUseCaseTest {

    private val rewardRepo = mockk<RewardRepository>()
    private val mushroomRepo = mockk<MushroomRepository>()
    private val parentGateway = mockk<ParentGateway>()
    private val eventBus = mockk<AppEventBus>()

    private val useCase = ExchangeMushroomsUseCase(
        rewardRepo = rewardRepo,
        mushroomRepo = mushroomRepo,
        parentGateway = parentGateway,
        eventBus = eventBus
    )

    @Test
    fun `physical reward exchange unlocks correct number of pieces`() = runTest {
        val reward = buildReward(puzzlePieces = 10)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        every { rewardRepo.getPuzzleProgress(1L) } returns flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 3)
        ) andThen flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 6)
        )
        coEvery { rewardRepo.insertExchange(any()) } returns 1L
        coEvery { mushroomRepo.recordTransaction(any()) } just Runs
        coEvery { eventBus.emit(any()) } just Runs

        val result = useCase(1L, MushroomLevel.SMALL, 3)

        assertTrue(result.isSuccess)
        val progress = result.getOrThrow()
        assertEquals(6, progress.unlockedPieces)
    }

    @Test
    fun `exchange caps pieces at remaining when amount exceeds remaining`() = runTest {
        val reward = buildReward(puzzlePieces = 10)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        every { rewardRepo.getPuzzleProgress(1L) } returns flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 8)
        ) andThen flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 10)
        )
        coEvery { rewardRepo.insertExchange(any()) } returns 1L
        coEvery { mushroomRepo.recordTransaction(any()) } just Runs
        coEvery { rewardRepo.updateReward(any()) } just Runs
        coEvery { eventBus.emit(any()) } just Runs

        val result = useCase(1L, MushroomLevel.SMALL, 5)
        assertTrue(result.isSuccess)
        coVerify {
            rewardRepo.insertExchange(match { it.puzzlePiecesUnlocked == 2 })
        }
    }

    @Test
    fun `exchange fails when reward not found`() = runTest {
        coEvery { rewardRepo.getRewardById(99L) } returns null

        val result = useCase(99L, MushroomLevel.SMALL, 1)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("不存在") == true)
    }

    @Test
    fun `exchange fails when reward is not ACTIVE`() = runTest {
        val reward = buildReward(status = RewardStatus.CLAIMED)
        coEvery { rewardRepo.getRewardById(1L) } returns reward

        val result = useCase(1L, MushroomLevel.SMALL, 1)
        assertTrue(result.isFailure)
    }

    @Test
    fun `completed puzzle triggers reward status update to COMPLETED`() = runTest {
        val reward = buildReward(puzzlePieces = 5)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        every { rewardRepo.getPuzzleProgress(1L) } returns flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 5, unlockedPieces = 4)
        ) andThen flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 5, unlockedPieces = 5)
        )
        coEvery { rewardRepo.insertExchange(any()) } returns 1L
        coEvery { mushroomRepo.recordTransaction(any()) } just Runs
        coEvery { rewardRepo.updateReward(any()) } just Runs
        coEvery { eventBus.emit(any()) } just Runs

        useCase(1L, MushroomLevel.SMALL, 1)

        coVerify { rewardRepo.updateReward(match { it.status == RewardStatus.COMPLETED }) }
    }

    @Test
    fun `incomplete puzzle does not update reward status`() = runTest {
        val reward = buildReward(puzzlePieces = 10)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        every { rewardRepo.getPuzzleProgress(1L) } returns flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 3)
        ) andThen flowOf(
            PuzzleProgress(rewardId = 1L, totalPieces = 10, unlockedPieces = 6)
        )
        coEvery { rewardRepo.insertExchange(any()) } returns 1L
        coEvery { mushroomRepo.recordTransaction(any()) } just Runs
        coEvery { eventBus.emit(any()) } just Runs

        useCase(1L, MushroomLevel.SMALL, 3)

        coVerify(exactly = 0) { rewardRepo.updateReward(any()) }
    }

    // -----------------------------------------------------------------------
    // ExchangeMushroomsUseCase — time-based path
    // -----------------------------------------------------------------------
    @Nested
    inner class TimeBasedExchangeTest {

        @Test
        fun `time-based exchange succeeds when within period quota`() = runTest {
            val config = buildTimeLimitConfig(unitMinutes = 30, maxMinutesPerPeriod = 120)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)
            val periodStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns
                TimeRewardBalance(rewardId = 1L, periodStart = periodStart, maxMinutes = 120, usedMinutes = 60)
            coEvery { rewardRepo.updateTimeRewardUsage(any(), any(), any()) } just Runs
            coEvery { rewardRepo.insertExchange(any()) } returns 1L
            coEvery { mushroomRepo.recordTransaction(any()) } just Runs

            val result = useCase(1L, MushroomLevel.SMALL, 1)

            assertTrue(result.isSuccess)
            // Returns virtual PuzzleProgress (totalPieces=0) for time-based rewards
            assertEquals(0, result.getOrThrow().totalPieces)
        }

        @Test
        fun `time-based exchange fails when period quota exceeded`() = runTest {
            val config = buildTimeLimitConfig(unitMinutes = 30, maxMinutesPerPeriod = 120)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)
            val periodStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            // Already used 100 minutes, adding 30 would exceed 120
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns
                TimeRewardBalance(rewardId = 1L, periodStart = periodStart, maxMinutes = 120, usedMinutes = 100)

            val result = useCase(1L, MushroomLevel.SMALL, 1)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("上限") == true)
        }

        @Test
        fun `time-based exchange with null balance treats used as 0`() = runTest {
            val config = buildTimeLimitConfig(unitMinutes = 30, maxMinutesPerPeriod = 120)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns null  // first use
            coEvery { rewardRepo.updateTimeRewardUsage(any(), any(), any()) } just Runs
            coEvery { rewardRepo.insertExchange(any()) } returns 1L
            coEvery { mushroomRepo.recordTransaction(any()) } just Runs

            val result = useCase(1L, MushroomLevel.SMALL, 1)

            assertTrue(result.isSuccess)
        }

        @Test
        fun `time-based exchange calls parent confirm when required`() = runTest {
            val config = buildTimeLimitConfig(requireParentConfirm = true)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns null
            coEvery { parentGateway.requestTimeRewardConfirmation(any()) } returns true
            coEvery { rewardRepo.updateTimeRewardUsage(any(), any(), any()) } just Runs
            coEvery { rewardRepo.insertExchange(any()) } returns 1L
            coEvery { mushroomRepo.recordTransaction(any()) } just Runs

            useCase(1L, MushroomLevel.SMALL, 1)

            coVerify { parentGateway.requestTimeRewardConfirmation(1L) }
        }

        @Test
        fun `time-based exchange updates usage with correct new total`() = runTest {
            val config = buildTimeLimitConfig(unitMinutes = 30, maxMinutesPerPeriod = 120)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)
            val periodStart = LocalDate.now().with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns
                TimeRewardBalance(rewardId = 1L, periodStart = periodStart, maxMinutes = 120, usedMinutes = 60)
            coEvery { rewardRepo.updateTimeRewardUsage(any(), any(), any()) } just Runs
            coEvery { rewardRepo.insertExchange(any()) } returns 1L
            coEvery { mushroomRepo.recordTransaction(any()) } just Runs

            useCase(1L, MushroomLevel.SMALL, 1)

            // 60 used + 30 unit = 90
            coVerify { rewardRepo.updateTimeRewardUsage(1L, any(), 90) }
        }

        @Test
        fun `monthly period type computes correct period start`() = runTest {
            val config = buildTimeLimitConfig(periodType = PeriodType.MONTHLY)
            val reward = buildReward(type = RewardType.TIME_BASED, timeLimitConfig = config)

            coEvery { rewardRepo.getRewardById(1L) } returns reward
            coEvery { rewardRepo.getTimeRewardBalance(1L, any()) } returns null
            coEvery { rewardRepo.updateTimeRewardUsage(any(), any(), any()) } just Runs
            coEvery { rewardRepo.insertExchange(any()) } returns 1L
            coEvery { mushroomRepo.recordTransaction(any()) } just Runs

            val result = useCase(1L, MushroomLevel.SMALL, 1)

            assertTrue(result.isSuccess)
            // Verify period start is the 1st of the current month
            val expectedStart = LocalDate.now().withDayOfMonth(1)
            coVerify { rewardRepo.getTimeRewardBalance(1L, expectedStart) }
        }
    }
}

// -----------------------------------------------------------------------
// ClaimRewardUseCase
// -----------------------------------------------------------------------
class ClaimRewardUseCaseTest {

    private val rewardRepo = mockk<RewardRepository>()
    private val parentGateway = mockk<ParentGateway>()
    private val useCase = ClaimRewardUseCase(rewardRepo, parentGateway)

    @Test
    fun `claim succeeds when puzzle is completed and parent approves`() = runTest {
        val reward = buildReward(status = RewardStatus.COMPLETED)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        coEvery { parentGateway.requestExchangeApproval(any()) } returns true
        coEvery { rewardRepo.updateReward(any()) } just Runs

        val result = useCase(1L)

        assertTrue(result.isSuccess)
        coVerify { rewardRepo.updateReward(match { it.status == RewardStatus.CLAIMED }) }
    }

    @Test
    fun `claim fails when reward not found`() = runTest {
        coEvery { rewardRepo.getRewardById(99L) } returns null

        val result = useCase(99L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("不存在") == true)
    }

    @Test
    fun `claim fails when puzzle is not yet completed`() = runTest {
        val reward = buildReward(status = RewardStatus.ACTIVE)
        coEvery { rewardRepo.getRewardById(1L) } returns reward

        val result = useCase(1L)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("尚未完成") == true)
    }

    @Test
    fun `claim updates status to CLAIMED`() = runTest {
        val reward = buildReward(status = RewardStatus.COMPLETED)
        coEvery { rewardRepo.getRewardById(1L) } returns reward
        coEvery { parentGateway.requestExchangeApproval(any()) } returns true
        coEvery { rewardRepo.updateReward(any()) } just Runs

        useCase(1L)

        coVerify { rewardRepo.updateReward(match { it.status == RewardStatus.CLAIMED && it.id == 1L }) }
    }
}

// -----------------------------------------------------------------------
// CreateRewardUseCase
// -----------------------------------------------------------------------
class CreateRewardUseCaseTest {

    private val rewardRepo = mockk<RewardRepository>()
    private val parentGateway = mockk<ParentGateway>()
    private val useCase = CreateRewardUseCase(rewardRepo, parentGateway)

    @Test
    fun `create reward succeeds and returns inserted id`() = runTest {
        val reward = buildReward()
        coEvery { parentGateway.requestExchangeApproval(any()) } returns true
        coEvery { rewardRepo.insertReward(reward) } returns 42L

        val result = useCase(reward)

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrThrow())
    }

    @Test
    fun `create reward calls parent approval before inserting`() = runTest {
        val reward = buildReward()
        coEvery { parentGateway.requestExchangeApproval(any()) } returns true
        coEvery { rewardRepo.insertReward(any()) } returns 1L

        useCase(reward)

        coVerify(ordering = io.mockk.Ordering.ORDERED) {
            parentGateway.requestExchangeApproval(any())
            rewardRepo.insertReward(any())
        }
    }

    @Test
    fun `create reward fails when repository throws`() = runTest {
        val reward = buildReward()
        coEvery { parentGateway.requestExchangeApproval(any()) } returns true
        coEvery { rewardRepo.insertReward(any()) } throws RuntimeException("DB error")

        val result = useCase(reward)

        assertTrue(result.isFailure)
    }
}
