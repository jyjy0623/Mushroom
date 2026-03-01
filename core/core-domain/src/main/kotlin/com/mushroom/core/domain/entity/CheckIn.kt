package com.mushroom.core.domain.entity

import java.time.LocalDate
import java.time.LocalDateTime

data class CheckIn(
    val id: Long = 0,
    val taskId: Long,
    val date: LocalDate,
    val checkedAt: LocalDateTime,
    val isEarly: Boolean,
    val earlyMinutes: Int,
    val note: String?,
    val imageUris: List<String>
)
