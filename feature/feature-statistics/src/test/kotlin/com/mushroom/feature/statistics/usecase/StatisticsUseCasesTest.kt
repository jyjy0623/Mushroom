package com.mushroom.feature.statistics.usecase

import com.mushroom.core.domain.entity.CheckIn
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneScorePoint
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.ScoreTrend
import com.mushroom.core.domain.entity.StatisticsPeriod
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.MilestoneRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.TaskRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class StatisticsUseCasesTest {

    // -----------------------------------------------------------------------
    // GetScoreStatisticsUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetScoreStatisticsUseCaseTest {

        private val milestoneRepo = mockk<MilestoneRepository>()
        private val useCase = GetScoreStatisticsUseCase(milestoneRepo)

        @Test
        fun `returns average and best score correctly`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 10), score = 70),
                buildMilestone(2L, LocalDate.of(2026, 2, 10), score = 80),
                buildMilestone(3L, LocalDate.of(2026, 3, 10), score = 90)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(1, results.size)
            val stats = results[0]
            assertEquals(3, stats.scorePoints.size)
            assertEquals(90, stats.bestScore)
            assertEquals(80f, stats.averageScore)
        }

        @Test
        fun `trend IMPROVING when recent scores are higher`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 1), score = 60),
                buildMilestone(2L, LocalDate.of(2026, 1, 15), score = 65),
                buildMilestone(3L, LocalDate.of(2026, 2, 1), score = 80),
                buildMilestone(4L, LocalDate.of(2026, 2, 15), score = 85),
                buildMilestone(5L, LocalDate.of(2026, 3, 1), score = 88)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(ScoreTrend.IMPROVING, results[0].trend)
        }

        @Test
        fun `trend DECLINING when recent scores are lower`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 1), score = 90),
                buildMilestone(2L, LocalDate.of(2026, 1, 15), score = 85),
                buildMilestone(3L, LocalDate.of(2026, 2, 1), score = 80),
                buildMilestone(4L, LocalDate.of(2026, 2, 15), score = 70),
                buildMilestone(5L, LocalDate.of(2026, 3, 1), score = 65)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(ScoreTrend.DECLINING, results[0].trend)
        }

        @Test
        fun `trend STABLE when score difference is within 5 points`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 1), score = 80),
                buildMilestone(2L, LocalDate.of(2026, 1, 15), score = 80),
                buildMilestone(3L, LocalDate.of(2026, 2, 1), score = 82),
                buildMilestone(4L, LocalDate.of(2026, 2, 15), score = 83),
                buildMilestone(5L, LocalDate.of(2026, 3, 1), score = 84)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(ScoreTrend.STABLE, results[0].trend)
        }

        @Test
        fun `trend STABLE when fewer than 2 scored milestones`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 1), score = 85)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(ScoreTrend.STABLE, results[0].trend)
        }

        @Test
        fun `empty milestones returns empty score points`() = runTest {
            every { milestoneRepo.getMilestonesBySubject(Subject.ENGLISH) } returns flowOf(emptyList())

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.ENGLISH).collect { results.add(it) }

            assertTrue(results[0].scorePoints.isEmpty())
            assertEquals(0f, results[0].averageScore)
            assertEquals(0, results[0].bestScore)
        }

        @Test
        fun `only milestones with actual scores are included`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 1, 1), score = 75),
                buildMilestone(2L, LocalDate.of(2026, 2, 1), score = null)  // pending
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            assertEquals(1, results[0].scorePoints.size)
        }

        @Test
        fun `score points sorted by date ascending`() = runTest {
            val milestones = listOf(
                buildMilestone(1L, LocalDate.of(2026, 3, 1), score = 90),
                buildMilestone(2L, LocalDate.of(2026, 1, 1), score = 70),
                buildMilestone(3L, LocalDate.of(2026, 2, 1), score = 80)
            )
            every { milestoneRepo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val results = mutableListOf<com.mushroom.core.domain.entity.ScoreStatistics>()
            useCase(Subject.MATH).collect { results.add(it) }

            val points = results[0].scorePoints
            assertEquals(LocalDate.of(2026, 1, 1), points[0].date)
            assertEquals(LocalDate.of(2026, 2, 1), points[1].date)
            assertEquals(LocalDate.of(2026, 3, 1), points[2].date)
        }

        private fun buildMilestone(id: Long, date: LocalDate, score: Int?) = Milestone(
            id = id,
            name = "数学测试$id",
            type = MilestoneType.MINI_TEST,
            subject = Subject.MATH,
            scheduledDate = date,
            scoringRules = emptyList(),
            actualScore = score,
            status = if (score != null) MilestoneStatus.REWARDED else MilestoneStatus.PENDING
        )
    }

    // -----------------------------------------------------------------------
    // GetMushroomStatisticsUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetMushroomStatisticsUseCaseTest {

        private val mushroomRepo = mockk<MushroomRepository>()
        private val useCase = GetMushroomStatisticsUseCase(mushroomRepo)

        @Test
        fun `correctly counts total earned per level`() = runTest {
            val ledger = listOf(
                buildTransaction(MushroomLevel.SMALL, MushroomAction.EARN, 3),
                buildTransaction(MushroomLevel.SMALL, MushroomAction.EARN, 2),
                buildTransaction(MushroomLevel.MEDIUM, MushroomAction.EARN, 1),
                buildTransaction(MushroomLevel.SMALL, MushroomAction.SPEND, 1)
            )
            val balance = MushroomBalance(mapOf(MushroomLevel.SMALL to 4, MushroomLevel.MEDIUM to 1))
            every { mushroomRepo.getBalance() } returns flowOf(balance)
            every { mushroomRepo.getLedgerByDateRange(any(), any()) } returns flowOf(ledger)

            val results = mutableListOf<com.mushroom.core.domain.entity.MushroomStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            val stats = results[0]
            assertEquals(5, stats.totalEarned[MushroomLevel.SMALL])  // 3+2
            assertEquals(1, stats.totalEarned[MushroomLevel.MEDIUM])
            assertEquals(1, stats.totalSpent[MushroomLevel.SMALL])
        }

        @Test
        fun `correctly counts total spent and deducted separately`() = runTest {
            val ledger = listOf(
                buildTransaction(MushroomLevel.GOLD, MushroomAction.EARN, 2),
                buildTransaction(MushroomLevel.GOLD, MushroomAction.SPEND, 1),
                buildTransaction(MushroomLevel.GOLD, MushroomAction.DEDUCT, 1)
            )
            val balance = MushroomBalance(mapOf(MushroomLevel.GOLD to 0))
            every { mushroomRepo.getBalance() } returns flowOf(balance)
            every { mushroomRepo.getLedgerByDateRange(any(), any()) } returns flowOf(ledger)

            val results = mutableListOf<com.mushroom.core.domain.entity.MushroomStatistics>()
            useCase(StatisticsPeriod.LAST_30_DAYS).collect { results.add(it) }

            val stats = results[0]
            assertEquals(2, stats.totalEarned[MushroomLevel.GOLD])
            assertEquals(1, stats.totalSpent[MushroomLevel.GOLD])
            assertEquals(1, stats.totalDeducted[MushroomLevel.GOLD])
        }

        @Test
        fun `empty ledger returns zero counts`() = runTest {
            val balance = MushroomBalance(emptyMap())
            every { mushroomRepo.getBalance() } returns flowOf(balance)
            every { mushroomRepo.getLedgerByDateRange(any(), any()) } returns flowOf(emptyList())

            val results = mutableListOf<com.mushroom.core.domain.entity.MushroomStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            val stats = results[0]
            assertTrue(stats.totalEarned.values.all { it == 0 })
            assertTrue(stats.totalSpent.values.all { it == 0 })
        }

        @Test
        fun `source breakdown counts EARN transactions by source`() = runTest {
            val ledger = listOf(
                buildTransaction(MushroomLevel.SMALL, MushroomAction.EARN, 2, MushroomSource.TASK),
                buildTransaction(MushroomLevel.SMALL, MushroomAction.EARN, 1, MushroomSource.CHECKIN_STREAK),
                buildTransaction(MushroomLevel.SMALL, MushroomAction.SPEND, 1, MushroomSource.EXCHANGE)
            )
            val balance = MushroomBalance(mapOf(MushroomLevel.SMALL to 2))
            every { mushroomRepo.getBalance() } returns flowOf(balance)
            every { mushroomRepo.getLedgerByDateRange(any(), any()) } returns flowOf(ledger)

            val results = mutableListOf<com.mushroom.core.domain.entity.MushroomStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            val stats = results[0]
            assertEquals(2, stats.sourceBreakdown[MushroomSource.TASK])
            assertEquals(1, stats.sourceBreakdown[MushroomSource.CHECKIN_STREAK])
            // SPEND is excluded from source breakdown
            assertEquals(0, stats.sourceBreakdown[MushroomSource.EXCHANGE] ?: 0)
        }

        private fun buildTransaction(
            level: MushroomLevel,
            action: MushroomAction,
            amount: Int,
            source: MushroomSource = MushroomSource.TASK
        ) = MushroomTransaction(
            id = 0,
            level = level,
            action = action,
            amount = amount,
            sourceType = source,
            sourceId = null,
            note = null,
            createdAt = LocalDateTime.now()
        )
    }

    // -----------------------------------------------------------------------
    // GetCheckInStatisticsUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetCheckInStatisticsUseCaseTest {

        private val checkInRepo = mockk<CheckInRepository>()
        private val taskRepo = mockk<TaskRepository>()
        private val useCase = GetCheckInStatisticsUseCase(checkInRepo, taskRepo)

        @Test
        fun `current streak counts consecutive days up to today`() = runTest {
            val today = LocalDate.now()
            // streak: today, yesterday, day before → streak = 3
            val checkIns = listOf(
                buildCheckIn(taskId = 1L, date = today),
                buildCheckIn(taskId = 1L, date = today.minusDays(1)),
                buildCheckIn(taskId = 1L, date = today.minusDays(2))
            )
            val tasks = listOf(buildTask(1L, today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(tasks)

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(3, results[0].currentStreak)
        }

        @Test
        fun `current streak is 0 when today has no check-in`() = runTest {
            val today = LocalDate.now()
            // check-ins from yesterday only, no today
            val checkIns = listOf(
                buildCheckIn(taskId = 1L, date = today.minusDays(1))
            )
            val tasks = listOf(buildTask(1L, today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(tasks)

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(0, results[0].currentStreak)
        }

        @Test
        fun `longest streak computed correctly with gap`() = runTest {
            val today = LocalDate.now()
            // days: today-6, today-5, today-4 (gap), today-1, today → longest=3
            val checkIns = listOf(
                buildCheckIn(taskId = 1L, date = today.minusDays(6)),
                buildCheckIn(taskId = 1L, date = today.minusDays(5)),
                buildCheckIn(taskId = 1L, date = today.minusDays(4)),
                buildCheckIn(taskId = 1L, date = today.minusDays(1)),
                buildCheckIn(taskId = 1L, date = today)
            )
            val tasks = listOf(buildTask(1L, today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(tasks)

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(3, results[0].longestStreak)
        }

        @Test
        fun `longest streak is 0 for empty check-ins`() = runTest {
            val today = LocalDate.now()
            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(emptyList())
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(emptyList())

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(0, results[0].longestStreak)
            assertEquals(0, results[0].currentStreak)
        }

        @Test
        fun `total checkins equals check-in count in period`() = runTest {
            val today = LocalDate.now()
            val checkIns = listOf(
                buildCheckIn(taskId = 1L, date = today),
                buildCheckIn(taskId = 2L, date = today),
                buildCheckIn(taskId = 1L, date = today.minusDays(1))
            )
            val tasks = listOf(buildTask(1L, today), buildTask(2L, today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(tasks)

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(3, results[0].totalCheckins)
        }

        @Test
        fun `subject breakdown is 1_0 when all tasks for subject are checked in`() = runTest {
            val today = LocalDate.now()
            val mathTask = buildTask(1L, today, subject = Subject.MATH)
            val checkIns = listOf(buildCheckIn(taskId = 1L, date = today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(listOf(mathTask))

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(1.0f, results[0].subjectBreakdown[Subject.MATH])
        }

        @Test
        fun `subject breakdown is 0_0 when no tasks for that subject`() = runTest {
            val today = LocalDate.now()
            val mathTask = buildTask(1L, today, subject = Subject.MATH)
            val checkIns = listOf(buildCheckIn(taskId = 1L, date = today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(listOf(mathTask))

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            assertEquals(0f, results[0].subjectBreakdown[Subject.ENGLISH])
        }

        @Test
        fun `daily completion rate computed per day`() = runTest {
            val today = LocalDate.now()
            val task1 = buildTask(1L, today)
            val task2 = buildTask(2L, today)
            // Only 1 out of 2 tasks checked in today → 0.5
            val checkIns = listOf(buildCheckIn(taskId = 1L, date = today))

            every { checkInRepo.getCheckInsByDateRange(any(), any()) } returns flowOf(checkIns)
            every { taskRepo.getTasksByDateRange(any(), any()) } returns flowOf(listOf(task1, task2))

            val results = mutableListOf<com.mushroom.core.domain.entity.CheckInStatistics>()
            useCase(StatisticsPeriod.LAST_7_DAYS).collect { results.add(it) }

            val todayRate = results[0].dailyCompletionRates.find { it.date == today }
            assertEquals(0.5f, todayRate?.completionRate)
        }

        private fun buildCheckIn(taskId: Long, date: LocalDate) = CheckIn(
            id = 0L,
            taskId = taskId,
            date = date,
            checkedAt = date.atStartOfDay(),
            isEarly = false,
            earlyMinutes = 0,
            note = null,
            imageUris = emptyList()
        )

        private fun buildTask(
            id: Long,
            date: LocalDate,
            subject: Subject = Subject.MATH
        ) = Task(
            id = id,
            title = "任务$id",
            subject = subject,
            estimatedMinutes = 30,
            repeatRule = RepeatRule.None,
            date = date,
            deadline = null,
            templateType = null,
            status = TaskStatus.PENDING
        )
    }
}
