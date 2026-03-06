package com.mushroom.feature.game.repository

import com.mushroom.core.data.db.dao.GameScoreDao
import com.mushroom.core.data.db.entity.GamePlayStateEntity
import com.mushroom.core.data.db.entity.GameScoreEntity
import com.mushroom.feature.game.entity.GameScore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GameRepositoryImpl @Inject constructor(
    private val dao: GameScoreDao
) : GameRepository {

    override fun getTopScores(limit: Int): Flow<List<GameScore>> =
        dao.getTopScores(limit).map { list ->
            list.map { e -> GameScore(e.id, e.score, LocalDateTime.parse(e.playedAt)) }
        }

    override suspend fun insertScore(score: GameScore): Long =
        dao.insert(GameScoreEntity(score = score.score, playedAt = score.playedAt.toString()))

    override suspend fun hasPlayedToday(date: LocalDate): Boolean =
        dao.getState("played_$date") == "true"

    override suspend fun markPlayedToday(date: LocalDate) =
        dao.setState(GamePlayStateEntity("played_$date", "true"))

    override suspend fun hasMilestoneRewarded(threshold: Int): Boolean =
        dao.getState("milestone_$threshold") == "true"

    override suspend fun markMilestoneRewarded(threshold: Int) =
        dao.setState(GamePlayStateEntity("milestone_$threshold", "true"))

    override suspend fun getHighScore(): Int =
        dao.getHighScore() ?: 0
}
