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

        // 查询最近一条 EARN 记录作为旧奖励
        val notePattern = "里程碑「${milestone.name}」得分"
        val latestEarn = mushroomRepo.getLatestEarnBySource(MushroomSource.MILESTONE, event.milestoneId)

        // Fallback: 如果精确匹配找不到，尝试 note 匹配（用于兼容 source_id=NULL 的 legacy 数据）
        val oldEarn = latestEarn ?: mushroomRepo.getTransactionsBySource(MushroomSource.MILESTONE, event.milestoneId, notePattern)
            .filter { it.action == MushroomAction.EARN }
            .maxByOrNull { it.createdAt }

        val oldReward = oldEarn?.let {
            MushroomReward(
                level = it.level,
                amount = it.amount,
                reason = "",
                sourceType = MushroomSource.MILESTONE,
                sourceId = event.milestoneId
            )
        }

        // 计算新奖励
        val rewardEvent = RewardEvent.MilestoneAchieved(milestone)
        val newRewards = ruleEngine.calculate(rewardEvent)
        val newReward = newRewards.firstOrNull()

        // 如果新旧奖励完全相同（包括都是 null 或都是同等级/同数量），跳过调整
        if (oldReward == newReward) {
            return
        }

        val now = LocalDateTime.now()

        // 扣除旧奖励（只扣最近一条 EARN 记录的 amount）
        if (oldEarn != null) {
            mushroomRepo.recordTransaction(
                MushroomTransaction(
                    level = oldEarn.level,
                    action = MushroomAction.DEDUCT,
                    amount = oldEarn.amount,
                    sourceType = MushroomSource.MILESTONE,
                    sourceId = event.milestoneId,
                    note = "成绩更新，扣除旧奖励",
                    createdAt = now
                )
            )
        }

        // 发放新奖励
        if (newReward != null) {
            dispatchRewards(newRewards, newReward)
        }

        // 发送奖励调整事件（供 UI 显示）
        eventBus.emit(AppEvent.MilestoneRewardAdjusted(
            milestoneId = event.milestoneId,
            milestoneName = milestone.name,
            oldReward = oldReward,
            newReward = newReward
        ))
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
