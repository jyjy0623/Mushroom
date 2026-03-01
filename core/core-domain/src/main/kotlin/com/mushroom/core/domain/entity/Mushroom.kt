package com.mushroom.core.domain.entity

import java.time.LocalDateTime

data class MushroomTransaction(
    val id: Long = 0,
    val level: MushroomLevel,
    val action: MushroomAction,
    val amount: Int,
    val sourceType: MushroomSource,
    val sourceId: Long?,
    val note: String?,
    val createdAt: LocalDateTime
)

enum class MushroomLevel(val exchangePoints: Int, val displayName: String) {
    SMALL(1, "小蘑菇"),
    MEDIUM(5, "中蘑菇"),
    LARGE(25, "大蘑菇"),
    GOLD(100, "金蘑菇"),
    LEGEND(500, "传说蘑菇")
}

enum class MushroomAction { EARN, DEDUCT, SPEND }

enum class MushroomSource {
    TASK, EARLY_BONUS, TEMPLATE_BONUS, CHECKIN_STREAK,
    MILESTONE, KEY_DATE, DEDUCTION, EXCHANGE, APPEAL_REFUND
}

data class MushroomRewardConfig(
    val level: MushroomLevel,
    val amount: Int
)

data class MushroomBalance(
    val balances: Map<MushroomLevel, Int>
) {
    fun totalPoints(): Int = balances.entries.sumOf { (level, count) -> level.exchangePoints * count }
    fun get(level: MushroomLevel): Int = balances.getOrDefault(level, 0)

    companion object {
        fun empty(): MushroomBalance = MushroomBalance(MushroomLevel.values().associateWith { 0 })
    }
}
