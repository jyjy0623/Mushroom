package com.mushroom.feature.game.repository

import com.mushroom.feature.game.entity.GameScore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface GameRepository {
    fun getTopScores(limit: Int = 10): Flow<List<GameScore>>
    suspend fun insertScore(score: GameScore): Long
    suspend fun hasPlayedToday(date: LocalDate): Boolean
    suspend fun markPlayedToday(date: LocalDate)
    suspend fun hasMilestoneRewarded(threshold: Int): Boolean
    suspend fun markMilestoneRewarded(threshold: Int)
    suspend fun getHighScore(): Int
}
