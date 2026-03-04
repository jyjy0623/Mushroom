package com.mushroom.core.domain.entity

import java.time.LocalDate
import java.time.LocalDateTime

data class Reward(
    val id: Long = 0,
    val name: String,
    val imageUri: String = "",
    val type: RewardType,
    val requiredMushrooms: Map<MushroomLevel, Int>,
    val puzzlePieces: Int = 1,
    val timeLimitConfig: TimeLimitConfig?,
    val status: RewardStatus = RewardStatus.ACTIVE
)

enum class RewardType { PHYSICAL, TIME_BASED }

enum class RewardStatus { ACTIVE, COMPLETED, CLAIMED, ARCHIVED }

data class TimeLimitConfig(
    val unitMinutes: Int,
    val periodType: PeriodType,
    val maxMinutesPerPeriod: Int,
    val cooldownDays: Int,
    val requireParentConfirm: Boolean
)

enum class PeriodType { WEEKLY, MONTHLY }

data class PuzzleProgress(
    val rewardId: Long,
    val totalPieces: Int,
    val unlockedPieces: Int,
    val pieceEmojis: List<MushroomLevel> = emptyList()
) {
    val percentage: Float get() = if (totalPieces == 0) 0f else unlockedPieces.toFloat() / totalPieces
    val isCompleted: Boolean get() = unlockedPieces >= totalPieces
}

data class TimeRewardBalance(
    val rewardId: Long,
    val periodStart: LocalDate,
    val maxMinutes: Int,
    val usedMinutes: Int
) {
    val remainingMinutes: Int get() = maxOf(0, maxMinutes - usedMinutes)
}

data class RewardExchange(
    val id: Long = 0,
    val rewardId: Long,
    val mushroomLevel: MushroomLevel,
    val mushroomCount: Int,
    val puzzlePiecesUnlocked: Int,
    val minutesGained: Int?,
    val createdAt: LocalDateTime
)
