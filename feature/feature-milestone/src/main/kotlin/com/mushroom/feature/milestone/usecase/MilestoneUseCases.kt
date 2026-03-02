package com.mushroom.feature.milestone.usecase

import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneScorePoint
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.MilestoneRepository
import com.mushroom.core.domain.service.ParentGateway
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "MilestoneUseCases"

// -----------------------------------------------------------------------
// Default scoring rules
// -----------------------------------------------------------------------
object DefaultScoringRules {

    val MINI_TEST = listOf(
        ScoringRule(90, 100, MushroomRewardConfig(MushroomLevel.MEDIUM, 2)),
        ScoringRule(80,  89, MushroomRewardConfig(MushroomLevel.MEDIUM, 1)),
        ScoringRule(60,  79, MushroomRewardConfig(MushroomLevel.SMALL,  1)),
        ScoringRule(0,   59, MushroomRewardConfig(MushroomLevel.SMALL,  0))  // 无奖励
    )

    val SCHOOL_EXAM = listOf(
        ScoringRule(90, 100, MushroomRewardConfig(MushroomLevel.GOLD,   5)),
        ScoringRule(80,  89, MushroomRewardConfig(MushroomLevel.GOLD,   3)),
        ScoringRule(60,  79, MushroomRewardConfig(MushroomLevel.LARGE,  1)),
        ScoringRule(0,   59, MushroomRewardConfig(MushroomLevel.SMALL,  0))
    )

    val MIDTERM_FINAL = listOf(
        ScoringRule(90, 100, MushroomRewardConfig(MushroomLevel.LEGEND, 1)),
        ScoringRule(80,  89, MushroomRewardConfig(MushroomLevel.GOLD,   8)),
        ScoringRule(60,  79, MushroomRewardConfig(MushroomLevel.LARGE,  2)),
        ScoringRule(0,   59, MushroomRewardConfig(MushroomLevel.SMALL,  0))
    )

    fun forType(type: MilestoneType): List<ScoringRule> = when (type) {
        MilestoneType.MINI_TEST    -> MINI_TEST
        MilestoneType.WEEKLY_TEST  -> MINI_TEST     // 周自测同小测规则
        MilestoneType.SCHOOL_EXAM  -> SCHOOL_EXAM
        MilestoneType.MIDTERM      -> MIDTERM_FINAL
        MilestoneType.FINAL        -> MIDTERM_FINAL
    }
}

// -----------------------------------------------------------------------
// CreateMilestoneUseCase
// -----------------------------------------------------------------------
class CreateMilestoneUseCase @Inject constructor(
    private val repo: MilestoneRepository,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(milestone: Milestone): Result<Long> {
        MushroomLogger.i(TAG, "CreateMilestoneUseCase: name=${milestone.name} type=${milestone.type}")
        return runCatching {
            parentGateway.requestExchangeApproval(
                RewardExchange(rewardId = 0, mushroomLevel = MushroomLevel.SMALL, mushroomCount = 0,
                    puzzlePiecesUnlocked = 0, minutesGained = null, createdAt = LocalDateTime.now())
            )  // 家长权限验证
            val withRules = if (milestone.scoringRules.isEmpty()) {
                milestone.copy(scoringRules = DefaultScoringRules.forType(milestone.type))
            } else {
                milestone
            }
            repo.insertMilestone(withRules)
        }.onFailure { MushroomLogger.e(TAG, "CreateMilestoneUseCase failed", it) }
    }
}

// -----------------------------------------------------------------------
// RecordMilestoneScoreUseCase
// -----------------------------------------------------------------------
class RecordMilestoneScoreUseCase @Inject constructor(
    private val repo: MilestoneRepository,
    private val eventBus: AppEventBus,
    private val parentGateway: ParentGateway
) {
    suspend operator fun invoke(milestoneId: Long, score: Int): Result<Milestone> {
        MushroomLogger.i(TAG, "RecordMilestoneScoreUseCase: id=$milestoneId score=$score")
        return runCatching {
            require(score in 0..100) { "分数必须在0~100之间" }
            parentGateway.requestExchangeApproval(
                RewardExchange(rewardId = milestoneId, mushroomLevel = MushroomLevel.SMALL, mushroomCount = 0,
                    puzzlePiecesUnlocked = 0, minutesGained = null, createdAt = LocalDateTime.now())
            )   // 家长PIN验证

            // 保存成绩，更新状态为 SCORED
            repo.updateScore(milestoneId, score, MilestoneStatus.SCORED)

            // 发布事件 → feature-mushroom 处理奖励
            eventBus.emit(AppEvent.MilestoneScored(milestoneId, score))

            // 奖励发放后更新为 REWARDED
            repo.updateScore(milestoneId, score, MilestoneStatus.REWARDED)

            // 返回更新后的里程碑
            repo.getAllMilestones().first().first { it.id == milestoneId }
        }.onFailure { MushroomLogger.e(TAG, "RecordMilestoneScoreUseCase failed", it) }
    }
}

// -----------------------------------------------------------------------
// GetMilestonesUseCase
// -----------------------------------------------------------------------
class GetMilestonesUseCase @Inject constructor(
    private val repo: MilestoneRepository
) {
    fun all(): Flow<List<Milestone>> = repo.getAllMilestones()
    fun bySubject(subject: Subject): Flow<List<Milestone>> = repo.getMilestonesBySubject(subject)
}

// -----------------------------------------------------------------------
// GetMilestoneScoreHistoryUseCase
// -----------------------------------------------------------------------
class GetMilestoneScoreHistoryUseCase @Inject constructor(
    private val repo: MilestoneRepository
) {
    operator fun invoke(subject: Subject, type: MilestoneType? = null): Flow<List<MilestoneScorePoint>> =
        repo.getMilestonesBySubject(subject).map { milestones ->
            milestones
                .filter { it.actualScore != null && (type == null || it.type == type) }
                .sortedBy { it.scheduledDate }
                .map { m ->
                    MilestoneScorePoint(
                        date = m.scheduledDate,
                        score = m.actualScore!!,
                        type = m.type,
                        name = m.name
                    )
                }
        }
}
