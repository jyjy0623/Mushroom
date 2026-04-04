package com.mushroom.feature.mushroom.reward

import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.event.MushroomReward
import com.mushroom.core.domain.event.RewardEvent
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.MilestoneRepository
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
    private val milestoneRepo: MilestoneRepository,
    private val mushroomRepo: MushroomRepository,
    private val ruleEngine: RewardRuleEngine
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            eventBus.events.collect { event ->
                when (event) {
                    is AppEvent.TaskCheckedIn  -> handleTaskCheckedIn(event)
                    is AppEvent.MilestoneScored -> handleMilestoneScored(event)
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
            dispatchRewards(ruleEngine.calculate(rewardEvent), null)
            return
        }

        val task = taskRepo.getTaskById(event.taskId) ?: return
        val checkIn = checkInRepo.getLatestCheckInForTask(event.taskId) ?: return

        val rewardEvent = RewardEvent.TaskCompleted(task = task, checkIn = checkIn)
        val rewards = ruleEngine.calculate(rewardEvent)
        dispatchRewards(rewards, null)

        MushroomLogger.i(TAG, "Dispatched ${rewards.size} rewards for task ${event.taskId}")
    }

    private suspend fun handleMilestoneScored(event: AppEvent.MilestoneScored) {
        val milestone = milestoneRepo.getAllMilestones().first()
            .firstOrNull { it.id == event.milestoneId } ?: return

        // 获取旧的奖励记录（如有）
        val oldTransactions = mushroomRepo.getTransactionsBySource(MushroomSource.MILESTONE, event.milestoneId)
        MushroomLogger.i(TAG, ">>> handleMilestoneScored: milestoneId=${event.milestoneId}, score=${event.score}, oldTransactions.size=${oldTransactions.size}, oldTransactions=$oldTransactions")

        val oldReward = if (oldTransactions.isNotEmpty()) {
            val reward = MushroomReward(
                level = oldTransactions.first().level,
                amount = oldTransactions.sumOf { it.amount },
                reason = "",
                sourceType = MushroomSource.MILESTONE,
                sourceId = event.milestoneId
            )
            MushroomLogger.i(TAG, ">>> oldReward calculated: $reward")
            reward
        } else {
            MushroomLogger.i(TAG, ">>> oldReward is null (no transactions found)")
            null
        }

        // 计算新奖励
        val rewardEvent = RewardEvent.MilestoneAchieved(milestone)
        val newRewards = ruleEngine.calculate(rewardEvent)
        val newReward = newRewards.firstOrNull()
        MushroomLogger.i(TAG, ">>> newReward: $newReward")

        // 如果新旧奖励完全相同（包括都是 null 或都是同等级/同数量），跳过调整
        if (oldReward == newReward) {
            MushroomLogger.i(TAG, ">>> reward unchanged, skipping adjustment")
            return
        }

        // 扣除旧奖励
        if (oldTransactions.isNotEmpty()) {
            MushroomLogger.i(TAG, ">>> deducting ${oldTransactions.size} old transactions, total amount=${oldTransactions.sumOf { it.amount }}")
            val now = LocalDateTime.now()
            val deductTransactions = oldTransactions.map { old ->
                MushroomTransaction(
                    level = old.level,
                    action = MushroomAction.DEDUCT,
                    amount = old.amount,
                    sourceType = MushroomSource.MILESTONE,
                    sourceId = event.milestoneId,
                    note = "成绩更新，扣除旧奖励",
                    createdAt = now
                )
            }
            mushroomRepo.recordTransactions(deductTransactions)
        } else {
            MushroomLogger.i(TAG, ">>> no old transactions to deduct")
        }

        // 发放新奖励
        MushroomLogger.i(TAG, ">>> dispatching new rewards: $newRewards")
        dispatchRewards(newRewards, newReward)

        // 发送奖励调整事件（供 UI 显示）
        eventBus.emit(AppEvent.MilestoneRewardAdjusted(
            milestoneId = event.milestoneId,
            milestoneName = milestone.name,
            oldReward = oldReward,
            newReward = newReward
        ))

        MushroomLogger.i(TAG, ">>> Milestone ${event.milestoneId} reward adjustment completed")
    }

    private suspend fun dispatchRewards(rewards: List<MushroomReward>, primaryReward: MushroomReward?) {
        if (rewards.isEmpty()) return
        val now = LocalDateTime.now()
        val transactions = rewards.map { reward ->
            MushroomTransaction(
                level = reward.level,
                action = MushroomAction.EARN,
                amount = reward.amount,
                sourceType = reward.sourceType,
                sourceId = reward.sourceId,
                note = reward.reason,
                createdAt = now
            )
        }
        mushroomRepo.recordTransactions(transactions)
        eventBus.emit(AppEvent.MushroomEarned(transactions))
    }
}
