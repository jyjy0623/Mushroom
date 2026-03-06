package com.mushroom.core.domain.repository

import com.mushroom.core.domain.entity.*
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface TaskRepository {
    fun getTasksByDate(date: LocalDate): Flow<List<Task>>
    fun getTasksByDateRange(from: LocalDate, to: LocalDate): Flow<List<Task>>
    suspend fun getTaskById(id: Long): Task?
    suspend fun insertTask(task: Task): Long
    suspend fun updateTask(task: Task)
    suspend fun deleteTask(id: Long)
    suspend fun deleteRecurringByTitle(title: String, fromDate: LocalDate)
    suspend fun generateRepeatTasks(templateTaskId: Long, until: LocalDate)
}

interface TaskTemplateRepository {
    fun getAllTemplates(): Flow<List<TaskTemplate>>
    suspend fun getTemplateByType(type: TaskTemplateType): TaskTemplate?
    suspend fun insertTemplate(template: TaskTemplate): Long
    suspend fun updateTemplate(template: TaskTemplate)
    suspend fun deleteTemplate(id: Long)
}

interface CheckInRepository {
    fun getCheckInsByDate(date: LocalDate): Flow<List<CheckIn>>
    fun getCheckInsByDateRange(from: LocalDate, to: LocalDate): Flow<List<CheckIn>>
    suspend fun getLatestCheckInForTask(taskId: Long): CheckIn?
    suspend fun insertCheckIn(checkIn: CheckIn): Long
    suspend fun getStreakCount(until: LocalDate): Int
}

interface MushroomRepository {
    fun getBalance(): Flow<MushroomBalance>
    fun getLedger(limit: Int = 100): Flow<List<MushroomTransaction>>
    fun getLedgerByDateRange(from: LocalDate, to: LocalDate): Flow<List<MushroomTransaction>>
    suspend fun recordTransaction(transaction: MushroomTransaction)
    suspend fun recordTransactions(transactions: List<MushroomTransaction>)
}

interface DeductionRepository {
    fun getAllConfigs(): Flow<List<DeductionConfig>>
    fun getEnabledConfigs(): Flow<List<DeductionConfig>>
    suspend fun updateConfig(config: DeductionConfig)
    fun getAllRecords(): Flow<List<DeductionRecord>>
    suspend fun insertRecord(record: DeductionRecord): Long
    suspend fun updateAppealStatus(id: Long, status: AppealStatus, note: String?)
    suspend fun getTodayCountByConfigId(configId: Long): Int
}

interface RewardRepository {
    fun getActiveRewards(): Flow<List<Reward>>
    fun getAllNonArchived(): Flow<List<Reward>>
    suspend fun getRewardById(id: Long): Reward?
    suspend fun insertReward(reward: Reward): Long
    suspend fun updateReward(reward: Reward)
    fun getPuzzleProgress(rewardId: Long): Flow<PuzzleProgress>
    suspend fun getTimeRewardBalance(rewardId: Long, periodStart: LocalDate): TimeRewardBalance?
    suspend fun updateTimeRewardUsage(rewardId: Long, periodStart: LocalDate, usedMinutes: Int)
    suspend fun insertExchange(exchange: RewardExchange): Long
    /** 删除尚未完成的奖品，并返回需要退还给用户的蘑菇列表（level -> count） */
    suspend fun deleteActiveReward(id: Long): Map<MushroomLevel, Int>
}

interface MilestoneRepository {
    fun getAllMilestones(): Flow<List<Milestone>>
    fun getMilestonesBySubject(subject: Subject): Flow<List<Milestone>>
    suspend fun getMilestoneById(id: Long): Milestone?
    suspend fun insertMilestone(milestone: Milestone): Long
    suspend fun updateMilestone(milestone: Milestone)
    suspend fun updateScore(id: Long, score: Int, status: MilestoneStatus)
}

interface KeyDateRepository {
    fun getAllKeyDates(): Flow<List<KeyDate>>
    fun getUpcomingKeyDates(within: Int): Flow<List<KeyDate>>
    suspend fun insertKeyDate(keyDate: KeyDate): Long
    suspend fun deleteKeyDate(id: Long)
}
