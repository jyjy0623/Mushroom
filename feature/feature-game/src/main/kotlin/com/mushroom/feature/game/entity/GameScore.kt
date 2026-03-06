package com.mushroom.feature.game.entity

import java.time.LocalDate
import java.time.LocalDateTime

data class GameScore(
    val id: Long = 0,
    val score: Int,
    val playedAt: LocalDateTime
) {
    val playedDate: LocalDate get() = playedAt.toLocalDate()
}
