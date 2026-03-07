package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.RewardDao
import com.mushroom.core.data.db.dao.RewardExchangeDao
import com.mushroom.core.data.db.dao.TimeRewardUsageDao
import com.mushroom.core.data.db.entity.RewardExchangeEntity
import com.mushroom.core.data.db.entity.TimeRewardUsageEntity
import com.mushroom.core.data.mapper.RewardMapper
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.PuzzleProgress
import com.mushroom.core.domain.entity.Reward
import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.entity.TimeRewardBalance
import com.mushroom.core.domain.repository.RewardRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RewardRepositoryImpl @Inject constructor(
    private val rewardDao: RewardDao,
    private val rewardExchangeDao: RewardExchangeDao,
    private val timeRewardUsageDao: TimeRewardUsageDao
) : RewardRepository {

    override fun getActiveRewards(): Flow<List<Reward>> =
        rewardDao.getActiveRewards().map { list -> list.map(RewardMapper::toDomain) }

    override fun getAllNonArchived(): Flow<List<Reward>> =
        rewardDao.getAllNonArchived().map { list -> list.map(RewardMapper::toDomain) }

    override suspend fun getRewardById(id: Long): Reward? =
        rewardDao.getRewardById(id)?.let(RewardMapper::toDomain)

    override suspend fun insertReward(reward: Reward): Long =
        rewardDao.insert(RewardMapper.toDb(reward))

    override suspend fun updateReward(reward: Reward) =
        rewardDao.update(RewardMapper.toDb(reward))

    override fun getPuzzleProgress(rewardId: Long): Flow<PuzzleProgress> {
        val rewardFlow = rewardDao.getActiveRewards()
            .map { list -> list.firstOrNull { it.id == rewardId } }
        val exchangesFlow = rewardExchangeDao.getPhysicalExchanges(rewardId)
        return combine(rewardFlow, exchangesFlow) { entity, exchanges ->
            val totalPieces = entity?.puzzlePieces ?: 1
            // Build per-piece level list from exchange records in chronological order
            val pieceEmojis = exchanges.flatMap { ex ->
                val level = runCatching { MushroomLevel.valueOf(ex.mushroomLevel) }
                    .getOrDefault(MushroomLevel.SMALL)
                List(ex.puzzlePiecesUnlocked) { level }
            }
            PuzzleProgress(
                rewardId = rewardId,
                totalPieces = totalPieces,
                unlockedPieces = pieceEmojis.size,
                pieceEmojis = pieceEmojis
            )
        }
    }

    override suspend fun getTimeRewardBalance(rewardId: Long, periodStart: LocalDate): TimeRewardBalance? {
        val usage = timeRewardUsageDao.getUsage(rewardId, periodStart.toString()) ?: return null
        return TimeRewardBalance(
            rewardId = rewardId,
            periodStart = LocalDate.parse(usage.periodStart),
            maxTimes = usage.maxTimes,
            usedTimes = usage.usedTimes
        )
    }

    override suspend fun updateTimeRewardUsage(rewardId: Long, periodStart: LocalDate, usedTimes: Int) {
        val existing = timeRewardUsageDao.getUsage(rewardId, periodStart.toString())
        if (existing != null) {
            timeRewardUsageDao.updateUsedTimes(rewardId, periodStart.toString(), usedTimes)
        } else {
            val reward = rewardDao.getRewardById(rewardId)
            val maxTimes = reward?.let { r ->
                RewardMapper.toDomain(r).timeLimitConfig?.maxTimesPerPeriod
            }
            timeRewardUsageDao.upsert(
                TimeRewardUsageEntity(
                    rewardId = rewardId,
                    periodStart = periodStart.toString(),
                    maxTimes = maxTimes,
                    usedTimes = usedTimes
                )
            )
        }
    }

    override suspend fun insertExchange(exchange: RewardExchange): Long =
        rewardExchangeDao.insert(
            RewardExchangeEntity(
                rewardId = exchange.rewardId,
                mushroomLevel = exchange.mushroomLevel.name,
                mushroomCount = exchange.mushroomCount,
                puzzlePiecesUnlocked = exchange.puzzlePiecesUnlocked,
                minutesGained = exchange.minutesGained,
                createdAt = exchange.createdAt.toString()
            )
        )

    override suspend fun deleteActiveReward(id: Long): Map<MushroomLevel, Int> {
        // 汇总该奖品所有兑换记录中消耗的蘑菇，按等级求和
        val exchanges = rewardExchangeDao.getExchangesByRewardId(id)
        val refundMap = mutableMapOf<MushroomLevel, Int>()
        for (ex in exchanges) {
            val level = runCatching { MushroomLevel.valueOf(ex.mushroomLevel) }
                .getOrDefault(MushroomLevel.SMALL)
            refundMap[level] = (refundMap[level] ?: 0) + ex.mushroomCount
        }
        // 删除兑换记录、时长使用记录和奖品本体
        rewardExchangeDao.deleteByRewardId(id)
        timeRewardUsageDao.deleteByRewardId(id)
        rewardDao.deleteById(id)
        return refundMap
    }
}
