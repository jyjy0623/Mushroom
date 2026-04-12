package com.mushroom.feature.reward.usecase

import android.content.Context
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.PuzzleProgress
import com.mushroom.core.domain.entity.Reward
import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.entity.RewardStatus
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.core.domain.entity.TimeRewardBalance
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.RewardRepository
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.feature.reward.puzzle.PuzzleCutter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.TemporalAdjusters
import javax.inject.Inject

private const val TAG = "RewardUseCases"

// -----------------------------------------------------------------------
// GetAllNonArchivedRewardsUseCase
class GetAllNonArchivedRewardsUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    operator fun invoke(): Flow<List<Reward>> = repo.getAllNonArchived()
}

// GetActiveRewardsUseCase
// -----------------------------------------------------------------------
class GetActiveRewardsUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    operator fun invoke(): Flow<List<Reward>> = repo.getActiveRewards()
}

// -----------------------------------------------------------------------
// CreateRewardUseCase
// -----------------------------------------------------------------------
class CreateRewardUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    suspend operator fun invoke(reward: Reward): Result<Long> {
        MushroomLogger.i(TAG, "CreateRewardUseCase: name=${reward.name} type=${reward.type}")
        return runCatching {
            repo.insertReward(reward)
        }.onFailure { MushroomLogger.e(TAG, "CreateRewardUseCase failed", it) }
    }
}

// -----------------------------------------------------------------------
// ExchangeMushroomsUseCase
// -----------------------------------------------------------------------
class ExchangeMushroomsUseCase @Inject constructor(
    private val rewardRepo: RewardRepository,
    private val mushroomRepo: MushroomRepository,
    private val eventBus: AppEventBus,
    @ApplicationContext private val appContext: Context
) {
    suspend operator fun invoke(
        rewardId: Long,
        mushroomLevel: MushroomLevel,
        amount: Int
    ): Result<PuzzleProgress> {
        MushroomLogger.i(TAG, "ExchangeMushroomsUseCase: rewardId=$rewardId level=$mushroomLevel amount=$amount")
        return runCatching {
            val reward = rewardRepo.getRewardById(rewardId)
                ?: error("奖品不存在 id=$rewardId")
            check(reward.status == RewardStatus.ACTIVE) { "奖品已完成或已下架" }

            when (reward.type) {
                RewardType.PHYSICAL -> exchangePhysical(reward, mushroomLevel, amount)
                RewardType.TIME_BASED -> exchangeTimeBased(reward, mushroomLevel, amount)
            }
        }.onFailure { MushroomLogger.e(TAG, "ExchangeMushroomsUseCase failed", it) }
    }

    private suspend fun exchangePhysical(
        reward: Reward,
        mushroomLevel: MushroomLevel,
        amount: Int
    ): PuzzleProgress {
        // 当前进度
        val progress = rewardRepo.getPuzzleProgress(reward.id).first()
        val remaining = reward.puzzlePieces - progress.unlockedPieces
        check(remaining > 0) { "拼图已完成" }

        // 按积分折算：贡献积分 = 蘑菇数量 × 该等级积分值
        val contributedPoints = amount * mushroomLevel.exchangePoints
        val pointsPerPiece = reward.pointsPerPiece
        val piecesToUnlock = minOf(contributedPoints / pointsPerPiece, remaining)
        check(piecesToUnlock > 0) {
            "积分不足：${mushroomLevel.themedDisplayName(appContext)}×$amount = ${contributedPoints}分，" +
            "解锁1块拼图需 ${pointsPerPiece}分"
        }

        // 余额检验：确保选中等级蘑菇余额足够
        val balance = mushroomRepo.getBalance().first()
        val available = balance.get(mushroomLevel)
        check(available >= amount) {
            "${mushroomLevel.themedDisplayName(appContext)}余额不足（需要 $amount，当前 $available）"
        }

        // 写入交换记录
        rewardRepo.insertExchange(
            RewardExchange(
                rewardId = reward.id,
                mushroomLevel = mushroomLevel,
                mushroomCount = amount,
                puzzlePiecesUnlocked = piecesToUnlock,
                minutesGained = null,
                createdAt = LocalDateTime.now()
            )
        )

        // 扣除蘑菇
        mushroomRepo.recordTransaction(
            MushroomTransaction(
                level = mushroomLevel,
                action = MushroomAction.SPEND,
                amount = amount,
                sourceType = MushroomSource.EXCHANGE,
                sourceId = reward.id,
                note = "兑换奖品「${reward.name}」拼图 +${piecesToUnlock}块",
                createdAt = LocalDateTime.now()
            )
        )

        val newProgress = rewardRepo.getPuzzleProgress(reward.id).first()

        // 如果拼图完成，更新奖品状态
        if (newProgress.isCompleted) {
            rewardRepo.updateReward(reward.copy(status = RewardStatus.COMPLETED))
        }

        eventBus.emit(AppEvent.RewardPuzzleUpdated(reward.id, newProgress))
        return newProgress
    }

    private suspend fun exchangeTimeBased(
        reward: Reward,
        mushroomLevel: MushroomLevel,
        amount: Int
    ): PuzzleProgress {
        val config = reward.timeLimitConfig ?: error("时长型奖品缺少配置")

        // 次数上限检查（periodType 为 null 表示不限）
        val periodType = config.periodType
        val maxTimes = config.maxTimesPerPeriod
        val periodStart = if (periodType != null) currentPeriodStart(periodType) else LocalDate.MIN
        val balance = rewardRepo.getTimeRewardBalance(reward.id, periodStart)
        val usedTimes = balance?.usedTimes ?: 0

        if (periodType != null && maxTimes != null) {
            check(usedTimes < maxTimes) {
                "本${if (periodType.name == "WEEKLY") "周" else "月"}已达兑换上限（${maxTimes}次）"
            }
        }

        rewardRepo.updateTimeRewardUsage(reward.id, periodStart, usedTimes + 1)

        if (config.isPointsBased) {
            // 新版积分兑换逻辑
            val contributedPoints = amount * mushroomLevel.exchangePoints
            check(contributedPoints >= config.costPoints!!) {
                "积分不足：${mushroomLevel.themedDisplayName(appContext)}×$amount = ${contributedPoints}分，兑换需要 ${config.costPoints} 分"
            }

            rewardRepo.insertExchange(
                RewardExchange(
                    rewardId = reward.id,
                    mushroomLevel = mushroomLevel,
                    mushroomCount = amount,
                    puzzlePiecesUnlocked = 0,
                    minutesGained = config.unitMinutes,
                    createdAt = LocalDateTime.now()
                )
            )

            mushroomRepo.recordTransaction(
                MushroomTransaction(
                    level = mushroomLevel,
                    action = MushroomAction.SPEND,
                    amount = amount,
                    sourceType = MushroomSource.EXCHANGE,
                    sourceId = reward.id,
                    note = "兑换积分奖品「${reward.name}」消耗 ${contributedPoints} 分，获得 ${config.unitMinutes} 积分",
                    createdAt = LocalDateTime.now()
                )
            )
        } else {
            // 旧版蘑菇兑换逻辑（兼容已有数据）
            val oldLevel = config.costMushroomLevel ?: MushroomLevel.SMALL
            val oldCount = config.costMushroomCount ?: 5
            val mushroomBalance = mushroomRepo.getBalance().first()
            val available = mushroomBalance.get(oldLevel)
            check(available >= oldCount) {
                "${oldLevel.themedDisplayName(appContext)}余额不足（需要 ${oldCount}，当前 $available）"
            }

            rewardRepo.insertExchange(
                RewardExchange(
                    rewardId = reward.id,
                    mushroomLevel = oldLevel,
                    mushroomCount = oldCount,
                    puzzlePiecesUnlocked = 0,
                    minutesGained = config.unitMinutes,
                    createdAt = LocalDateTime.now()
                )
            )

            mushroomRepo.recordTransaction(
                MushroomTransaction(
                    level = oldLevel,
                    action = MushroomAction.SPEND,
                    amount = oldCount,
                    sourceType = MushroomSource.EXCHANGE,
                    sourceId = reward.id,
                    note = "兑换时长奖品「${reward.name}」${config.unitMinutes}分钟",
                    createdAt = LocalDateTime.now()
                )
            )
        }

        return PuzzleProgress(rewardId = reward.id, totalPieces = 0, unlockedPieces = 0)
    }

    private fun currentPeriodStart(periodType: com.mushroom.core.domain.entity.PeriodType): LocalDate {
        val today = LocalDate.now()
        return when (periodType) {
            com.mushroom.core.domain.entity.PeriodType.WEEKLY ->
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            com.mushroom.core.domain.entity.PeriodType.MONTHLY ->
                today.withDayOfMonth(1)
        }
    }
}

// -----------------------------------------------------------------------
// GetPuzzleProgressUseCase
// -----------------------------------------------------------------------
class GetPuzzleProgressUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    operator fun invoke(rewardId: Long): Flow<PuzzleProgress> =
        repo.getPuzzleProgress(rewardId)
}

// -----------------------------------------------------------------------
// GetExchangeCountUseCase
// -----------------------------------------------------------------------
class GetExchangeCountUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    operator fun invoke(rewardId: Long): Flow<Int> =
        repo.getExchangeCount(rewardId)
}

// -----------------------------------------------------------------------
// GetTimeRewardBalanceUseCase
// -----------------------------------------------------------------------
class GetTimeRewardBalanceUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    suspend operator fun invoke(rewardId: Long): TimeRewardBalance? {
        val reward = repo.getRewardById(rewardId) ?: return null
        val config = reward.timeLimitConfig ?: return null
        val periodType = config.periodType
        val periodStart = if (periodType != null) currentPeriodStart(periodType) else LocalDate.MIN
        return repo.getTimeRewardBalance(rewardId, periodStart)
    }

    private fun currentPeriodStart(periodType: com.mushroom.core.domain.entity.PeriodType): LocalDate {
        val today = LocalDate.now()
        return when (periodType) {
            com.mushroom.core.domain.entity.PeriodType.WEEKLY ->
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            com.mushroom.core.domain.entity.PeriodType.MONTHLY ->
                today.withDayOfMonth(1)
        }
    }
}

// -----------------------------------------------------------------------
// ClaimRewardUseCase
// -----------------------------------------------------------------------
class ClaimRewardUseCase @Inject constructor(
    private val repo: RewardRepository
) {
    suspend operator fun invoke(rewardId: Long): Result<Unit> {
        MushroomLogger.i(TAG, "ClaimRewardUseCase: rewardId=$rewardId")
        return runCatching {
            val reward = repo.getRewardById(rewardId) ?: error("奖品不存在")
            check(reward.status == RewardStatus.COMPLETED) { "奖品拼图尚未完成，无法领取" }
            repo.updateReward(reward.copy(status = RewardStatus.CLAIMED))
        }.onFailure { MushroomLogger.e(TAG, "ClaimRewardUseCase failed", it) }
    }
}

// -----------------------------------------------------------------------
// DeleteRewardUseCase
// -----------------------------------------------------------------------
class DeleteRewardUseCase @Inject constructor(
    private val rewardRepo: RewardRepository,
    private val mushroomRepo: MushroomRepository,
    @ApplicationContext private val appContext: Context
) {
    suspend operator fun invoke(rewardId: Long): Result<Unit> {
        MushroomLogger.i(TAG, "DeleteRewardUseCase: rewardId=$rewardId")
        return runCatching {
            val reward = rewardRepo.getRewardById(rewardId) ?: error("奖品不存在")
            check(reward.status == RewardStatus.ACTIVE) { "只能删除尚未完成的奖品" }

            // 删除奖品并获取需要退还的蘑菇
            val refundMap = rewardRepo.deleteActiveReward(rewardId)

            // 退还蘑菇到账本
            val transactions = refundMap.map { (level, count) ->
                MushroomTransaction(
                    level = level,
                    action = MushroomAction.EARN,
                    amount = count,
                    sourceType = MushroomSource.APPEAL_REFUND,
                    sourceId = rewardId,
                    note = "删除奖品「${reward.name}」退还 ${level.themedDisplayName(appContext)}×$count",
                    createdAt = LocalDateTime.now()
                )
            }
            mushroomRepo.recordTransactions(transactions)

            MushroomLogger.i(TAG, "DeleteRewardUseCase: 已删除奖品 ${reward.name}，退还蘑菇 $refundMap")
        }.onFailure { MushroomLogger.e(TAG, "DeleteRewardUseCase failed", it) }
    }
}
