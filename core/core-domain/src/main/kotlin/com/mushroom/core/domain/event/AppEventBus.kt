package com.mushroom.core.domain.event

import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.PuzzleProgress
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.LocalDateTime

sealed class AppEvent {
    data class TaskCheckedIn(
        val taskId: Long,
        val checkInTime: LocalDateTime,
        val isEarly: Boolean,
        val earlyMinutes: Int
    ) : AppEvent()

    data class MilestoneScored(
        val milestoneId: Long,
        val score: Int
    ) : AppEvent()

    data class MushroomEarned(
        val transactions: List<MushroomTransaction>
    ) : AppEvent()

    data class MushroomDeducted(
        val record: DeductionRecord
    ) : AppEvent()

    data class RewardPuzzleUpdated(
        val rewardId: Long,
        val newProgress: PuzzleProgress
    ) : AppEvent()

    data class KeyDateReached(
        val keyDateId: Long
    ) : AppEvent()
}

interface AppEventBus {
    val events: SharedFlow<AppEvent>
    suspend fun emit(event: AppEvent)
}

class AppEventBusImpl : AppEventBus {
    private val _events = MutableSharedFlow<AppEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<AppEvent> = _events.asSharedFlow()

    override suspend fun emit(event: AppEvent) {
        _events.emit(event)
    }
}
