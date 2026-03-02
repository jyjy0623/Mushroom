package com.mushroom.core.domain.service

import com.mushroom.core.domain.entity.*
import com.mushroom.core.domain.event.MushroomReward
import com.mushroom.core.domain.event.RewardEvent
import java.time.LocalDate

interface RewardRuleEngine {
    /**
     * 根据奖励事件计算应发放的蘑菇列表。
     * 契约：本方法为纯计算，不产生任何副作用（不写库、不发事件）。
     */
    fun calculate(event: RewardEvent): List<MushroomReward>
}

interface DeductionRuleEngine {
    /**
     * 校验本次扣分是否合法。
     * 返回 true 表示可以执行扣分；返回 false 表示已达上限，应阻止操作。
     */
    suspend fun canDeduct(configId: Long, date: LocalDate): Boolean
}

interface TaskGeneratorService {
    /**
     * 为所有带重复规则的任务生成指定日期的任务实例。
     * 契约：幂等——同一日期重复调用不会产生重复记录。
     */
    suspend fun generateForDate(date: LocalDate)
}

interface NotificationService {
    suspend fun scheduleDeadlineReminder(task: Task)
    suspend fun cancelDeadlineReminder(taskId: Long)
    suspend fun sendImmediateNotification(title: String, body: String)
}

interface ParentGateway {
    /**
     * 请求家长审批奖励兑换。
     * 契约：本方法挂起直到家长操作完成（批准或拒绝），超时视为拒绝。
     */
    suspend fun requestExchangeApproval(exchange: RewardExchange): Boolean

    /**
     * 请求家长确认开始使用时效奖励。
     * 返回 true 表示家长已确认；false 表示拒绝或超时。
     */
    suspend fun requestTimeRewardConfirmation(rewardId: Long): Boolean
}
