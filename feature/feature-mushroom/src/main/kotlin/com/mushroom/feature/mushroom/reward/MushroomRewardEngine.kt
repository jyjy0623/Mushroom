package com.mushroom.feature.mushroom.reward

import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.event.MushroomReward
import com.mushroom.core.domain.event.RewardEvent
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.service.RewardRuleEngine
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "MushroomRewardEngine"

@Singleton
class MushroomRewardEngine @Inject constructor(
    private val eventBus: AppEventBus,
    private val taskRepo: TaskRepository,
    private val checkInRepo: CheckInRepository,
    private val mushroomRepo: MushroomRepository,
    private val ruleEngine: RewardRuleEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.TaskCheckedIn -> handleTaskCheckedIn(event)
                    else -> Unit
                }
            }
        }
    }

    private suspend fun handleTaskCheckedIn(event: AppEvent.TaskCheckedIn) {
        // taskId == -1L is the "all tasks done" sentinel from CheckAllTasksDoneUseCase
        if (event.taskId == -1L) {
            val date = event.checkInTime.toLocalDate()
            val rewardEvent = RewardEvent.AllDailyTasksDone(date)
            dispatchRewards(ruleEngine.calculate(rewardEvent))
            return
        }

        val task = taskRepo.getTaskById(event.taskId) ?: return
        val checkIn = checkInRepo.getLatestCheckInForTask(event.taskId) ?: return

        val rewardEvent = RewardEvent.TaskCompleted(task = task, checkIn = checkIn)
        val rewards = ruleEngine.calculate(rewardEvent)
        dispatchRewards(rewards)

        MushroomLogger.i(TAG, "Dispatched ${rewards.size} rewards for task ${event.taskId}")
    }

    private suspend fun dispatchRewards(rewards: List<MushroomReward>) {
        if (rewards.isEmpty()) return
        val now = LocalDateTime.now()
        val transactions = rewards.map { reward ->
            MushroomTransaction(
                level = reward.level,
                action = MushroomAction.EARN,
                amount = reward.amount,
                sourceType = reward.sourceType,
                sourceId = null,
                note = reward.reason,
                createdAt = now
            )
        }
        mushroomRepo.recordTransactions(transactions)
        eventBus.emit(AppEvent.MushroomEarned(transactions))
    }
}
