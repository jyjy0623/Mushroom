package com.mushroom.feature.milestone.usecase

import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.MilestoneRepository
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MilestoneUseCasesTest {

    // -----------------------------------------------------------------------
    // DefaultScoringRules
    // -----------------------------------------------------------------------
    @Nested
    inner class DefaultScoringRulesTest {

        @Test
        fun `MINI_TEST has 4 scoring tiers`() {
            assertEquals(4, DefaultScoringRules.MINI_TEST.size)
        }

        @Test
        fun `MINI_TEST 90-100 gives MEDIUM x2`() {
            val rule = DefaultScoringRules.MINI_TEST.first { it.maxScore == 100 }
            assertEquals(MushroomLevel.MEDIUM, rule.rewardConfig.level)
            assertEquals(2, rule.rewardConfig.amount)
        }

        @Test
        fun `SCHOOL_EXAM 90-100 gives GOLD x5`() {
            val rule = DefaultScoringRules.SCHOOL_EXAM.first { it.maxScore == 100 }
            assertEquals(MushroomLevel.GOLD, rule.rewardConfig.level)
            assertEquals(5, rule.rewardConfig.amount)
        }

        @Test
        fun `MIDTERM 90-100 gives LEGEND x1`() {
            val rule = DefaultScoringRules.MIDTERM_FINAL.first { it.maxScore == 100 }
            assertEquals(MushroomLevel.LEGEND, rule.rewardConfig.level)
            assertEquals(1, rule.rewardConfig.amount)
        }

        @Test
        fun `forType returns correct rules for each type`() {
            assertEquals(DefaultScoringRules.MINI_TEST, DefaultScoringRules.forType(MilestoneType.MINI_TEST))
            assertEquals(DefaultScoringRules.MINI_TEST, DefaultScoringRules.forType(MilestoneType.WEEKLY_TEST))
            assertEquals(DefaultScoringRules.SCHOOL_EXAM, DefaultScoringRules.forType(MilestoneType.SCHOOL_EXAM))
            assertEquals(DefaultScoringRules.MIDTERM_FINAL, DefaultScoringRules.forType(MilestoneType.MIDTERM))
            assertEquals(DefaultScoringRules.MIDTERM_FINAL, DefaultScoringRules.forType(MilestoneType.FINAL))
        }
    }

    // -----------------------------------------------------------------------
    // RecordMilestoneScoreUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class RecordMilestoneScoreUseCaseTest {

        private val repo = mockk<MilestoneRepository>()
        private val eventBus = mockk<AppEventBus>()
        private val useCase = RecordMilestoneScoreUseCase(repo, eventBus)

        private fun buildMilestone(id: Long = 1L, score: Int? = null) = Milestone(
            id = id,
            name = "数学期中",
            type = MilestoneType.MIDTERM,
            subject = Subject.MATH,
            scheduledDate = LocalDate.of(2026, 3, 15),
            scoringRules = DefaultScoringRules.MIDTERM_FINAL,
            actualScore = score,
            status = if (score == null) MilestoneStatus.PENDING else MilestoneStatus.REWARDED
        )

        @Test
        fun `records score and emits MilestoneScored event`() = runTest {
            coEvery { repo.updateScore(any(), any(), any()) } just Runs
            every { repo.getAllMilestones() } returns flowOf(listOf(buildMilestone(score = 85)))
            val eventSlot = slot<AppEvent>()
            coEvery { eventBus.emit(capture(eventSlot)) } just Runs

            val result = useCase(1L, 85)

            assertTrue(result.isSuccess)
            val event = eventSlot.captured
            assertTrue(event is AppEvent.MilestoneScored)
            assertEquals(1L, (event as AppEvent.MilestoneScored).milestoneId)
            assertEquals(85, event.score)
        }

        @Test
        fun `updates milestone status to SCORED then REWARDED`() = runTest {
            coEvery { repo.updateScore(any(), any(), any()) } just Runs
            every { repo.getAllMilestones() } returns flowOf(listOf(buildMilestone(score = 92)))
            coEvery { eventBus.emit(any()) } just Runs

            useCase(1L, 92)

            coVerify { repo.updateScore(1L, 92, MilestoneStatus.SCORED) }
            coVerify { repo.updateScore(1L, 92, MilestoneStatus.REWARDED) }
        }

        @Test
        fun `score out of range returns failure`() = runTest {
            val result = useCase(1L, 150)  // > 100

            assertTrue(result.isFailure)
        }

        @Test
        fun `negative score returns failure`() = runTest {
            val result = useCase(1L, -1)

            assertTrue(result.isFailure)
        }

        @Test
        fun `boundary score 0 is valid`() = runTest {
            coEvery { repo.updateScore(any(), any(), any()) } just Runs
            every { repo.getAllMilestones() } returns flowOf(listOf(buildMilestone(score = 0)))
            coEvery { eventBus.emit(any()) } just Runs

            val result = useCase(1L, 0)

            assertTrue(result.isSuccess)
        }

        @Test
        fun `boundary score 100 is valid`() = runTest {
            coEvery { repo.updateScore(any(), any(), any()) } just Runs
            every { repo.getAllMilestones() } returns flowOf(listOf(buildMilestone(score = 100)))
            coEvery { eventBus.emit(any()) } just Runs

            val result = useCase(1L, 100)

            assertTrue(result.isSuccess)
        }
    }

    // -----------------------------------------------------------------------
    // GetMilestoneScoreHistoryUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class GetMilestoneScoreHistoryUseCaseTest {

        private val repo = mockk<MilestoneRepository>()
        private val useCase = GetMilestoneScoreHistoryUseCase(repo)

        @Test
        fun `returns only milestones with scores, sorted by date`() = runTest {
            val milestones = listOf(
                Milestone(1L, "测试1", MilestoneType.MINI_TEST, Subject.MATH,
                    LocalDate.of(2026, 2, 1), emptyList(), actualScore = 85, MilestoneStatus.REWARDED),
                Milestone(2L, "测试2", MilestoneType.SCHOOL_EXAM, Subject.MATH,
                    LocalDate.of(2026, 1, 15), emptyList(), actualScore = null, MilestoneStatus.PENDING),
                Milestone(3L, "测试3", MilestoneType.MIDTERM, Subject.MATH,
                    LocalDate.of(2026, 3, 1), emptyList(), actualScore = 92, MilestoneStatus.REWARDED)
            )
            every { repo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val history = mutableListOf<List<com.mushroom.core.domain.entity.MilestoneScorePoint>>()
            useCase(Subject.MATH).collect { history.add(it) }

            assertEquals(1, history.size)
            val points = history[0]
            assertEquals(2, points.size)  // Only scored ones
            assertEquals(LocalDate.of(2026, 2, 1), points[0].date)
            assertEquals(LocalDate.of(2026, 3, 1), points[1].date)
        }

        @Test
        fun `filters by type when type parameter provided`() = runTest {
            val milestones = listOf(
                Milestone(1L, "小测1", MilestoneType.MINI_TEST, Subject.MATH,
                    LocalDate.of(2026, 1, 10), emptyList(), actualScore = 75, MilestoneStatus.REWARDED),
                Milestone(2L, "期末", MilestoneType.FINAL, Subject.MATH,
                    LocalDate.of(2026, 1, 20), emptyList(), actualScore = 88, MilestoneStatus.REWARDED)
            )
            every { repo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val history = mutableListOf<List<com.mushroom.core.domain.entity.MilestoneScorePoint>>()
            useCase(Subject.MATH, MilestoneType.MINI_TEST).collect { history.add(it) }

            assertEquals(1, history[0].size)
            assertEquals(75, history[0][0].score)
        }

        @Test
        fun `returns empty list when no scored milestones`() = runTest {
            val milestones = listOf(
                Milestone(1L, "待考", MilestoneType.MINI_TEST, Subject.MATH,
                    LocalDate.of(2026, 4, 1), emptyList(), actualScore = null, MilestoneStatus.PENDING)
            )
            every { repo.getMilestonesBySubject(Subject.MATH) } returns flowOf(milestones)

            val history = mutableListOf<List<com.mushroom.core.domain.entity.MilestoneScorePoint>>()
            useCase(Subject.MATH).collect { history.add(it) }

            assertTrue(history[0].isEmpty())
        }
    }

    // -----------------------------------------------------------------------
    // CreateMilestoneUseCase
    // -----------------------------------------------------------------------
    @Nested
    inner class CreateMilestoneUseCaseTest {

        private val repo = mockk<MilestoneRepository>()
        private val useCase = CreateMilestoneUseCase(repo)

        private fun buildMilestone(
            type: MilestoneType = MilestoneType.MINI_TEST,
            scoringRules: List<com.mushroom.core.domain.entity.ScoringRule> = emptyList()
        ) = Milestone(
            id = 0L,
            name = "数学小测",
            type = type,
            subject = Subject.MATH,
            scheduledDate = LocalDate.of(2026, 4, 10),
            scoringRules = scoringRules,
            actualScore = null,
            status = MilestoneStatus.PENDING
        )

        @Test
        fun `create milestone succeeds and returns inserted id`() = runTest {
            val milestone = buildMilestone()
            coEvery { repo.insertMilestone(any()) } returns 10L

            val result = useCase(milestone)

            assertTrue(result.isSuccess)
            assertEquals(10L, result.getOrThrow())
        }

        @Test
        fun `auto-applies default scoring rules when rules are empty`() = runTest {
            val milestone = buildMilestone(type = MilestoneType.MINI_TEST, scoringRules = emptyList())
            coEvery { repo.insertMilestone(any()) } returns 1L

            useCase(milestone)

            // Should insert milestone with MINI_TEST default rules (not empty)
            coVerify {
                repo.insertMilestone(match { it.scoringRules == DefaultScoringRules.MINI_TEST })
            }
        }

        @Test
        fun `preserves custom scoring rules when provided`() = runTest {
            val customRules = DefaultScoringRules.MIDTERM_FINAL
            val milestone = buildMilestone(scoringRules = customRules)
            coEvery { repo.insertMilestone(any()) } returns 1L

            useCase(milestone)

            coVerify {
                repo.insertMilestone(match { it.scoringRules == customRules })
            }
        }

        @Test
        fun `applies SCHOOL_EXAM default rules for SCHOOL_EXAM type`() = runTest {
            val milestone = buildMilestone(type = MilestoneType.SCHOOL_EXAM, scoringRules = emptyList())
            coEvery { repo.insertMilestone(any()) } returns 1L

            useCase(milestone)

            coVerify {
                repo.insertMilestone(match { it.scoringRules == DefaultScoringRules.SCHOOL_EXAM })
            }
        }

        @Test
        fun `applies MIDTERM_FINAL rules for MIDTERM type`() = runTest {
            val milestone = buildMilestone(type = MilestoneType.MIDTERM, scoringRules = emptyList())
            coEvery { repo.insertMilestone(any()) } returns 1L

            useCase(milestone)

            coVerify {
                repo.insertMilestone(match { it.scoringRules == DefaultScoringRules.MIDTERM_FINAL })
            }
        }

        @Test
        fun `create fails when repository throws`() = runTest {
            val milestone = buildMilestone()
            coEvery { repo.insertMilestone(any()) } throws RuntimeException("DB error")

            val result = useCase(milestone)

            assertTrue(result.isFailure)
        }
    }
}
