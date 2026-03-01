package com.mushroom.core.domain.entity

import java.time.LocalDateTime

data class DeductionConfig(
    val id: Long = 0,
    val name: String,
    val mushroomLevel: MushroomLevel,
    val defaultAmount: Int,
    val customAmount: Int,
    val isEnabled: Boolean = false,
    val isBuiltIn: Boolean = false,
    val maxPerDay: Int = 1
)

data class DeductionRecord(
    val id: Long = 0,
    val configId: Long,
    val mushroomLevel: MushroomLevel,
    val amount: Int,
    val reason: String,
    val recordedAt: LocalDateTime,
    val appealStatus: AppealStatus = AppealStatus.NONE,
    val appealNote: String?
)

enum class AppealStatus { NONE, PENDING, APPROVED, REJECTED }
