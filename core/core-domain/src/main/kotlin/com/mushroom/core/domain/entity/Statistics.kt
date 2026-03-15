package com.mushroom.core.domain.entity

import java.time.LocalDate

data class DailyRate(
    val date: LocalDate,
    val completionRate: Float   // 0.0 - 1.0
)

data class DailyMushroomEarning(
    val date: LocalDate,
    val amounts: Map<MushroomLevel, Int>
) {
    val totalPoints: Int get() = amounts.entries.sumOf { (level, cnt) -> level.exchangePoints * cnt }
}

enum class StatisticsPeriod { THIS_WEEK, THIS_MONTH, THIS_SEMESTER }

enum class ScoreTrend { IMPROVING, STABLE, DECLINING }

data class CheckInStatistics(
    val currentStreak: Int,
    val longestStreak: Int,
    val totalCheckins: Int,
    val averageDailyCompletion: Float,
    val dailyCompletionRates: List<DailyRate>,
    val subjectBreakdown: Map<Subject, Float>
)

data class MushroomStatistics(
    val currentBalance: MushroomBalance,
    val totalEarned: Map<MushroomLevel, Int>,
    val totalSpent: Map<MushroomLevel, Int>,
    val totalDeducted: Map<MushroomLevel, Int>,
    val earningTrend: List<DailyMushroomEarning>,
    val sourceBreakdown: Map<MushroomSource, Int>
)

data class ScoreStatistics(
    val subject: Subject,
    val scorePoints: List<MilestoneScorePoint>,
    val averageScore: Float,
    val bestScore: Int,
    val trend: ScoreTrend
)
