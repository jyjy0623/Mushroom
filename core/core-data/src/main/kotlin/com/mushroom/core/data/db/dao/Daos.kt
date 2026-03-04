package com.mushroom.core.data.db.dao

import androidx.room.*
import com.mushroom.core.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :date ORDER BY id")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date >= :from AND date <= :to ORDER BY date, status")
    fun getTasksByDateRange(from: String, to: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM tasks WHERE title = :title AND repeat_rule_type != 'NONE' AND date >= :fromDate")
    suspend fun deleteRecurringByTitle(title: String, fromDate: String)

    @Query("SELECT * FROM tasks WHERE date = :date AND repeat_rule_type != 'NONE'")
    suspend fun getRepeatTasksForDate(date: String): List<TaskEntity>
}

@Dao
interface TaskTemplateDao {
    @Query("SELECT * FROM task_templates ORDER BY is_built_in DESC, id")
    fun getAllTemplates(): Flow<List<TaskTemplateEntity>>

    @Query("SELECT * FROM task_templates WHERE type = :type LIMIT 1")
    suspend fun getTemplateByType(type: String): TaskTemplateEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(template: TaskTemplateEntity): Long

    @Update
    suspend fun update(template: TaskTemplateEntity)

    @Query("DELETE FROM task_templates WHERE id = :id AND is_built_in = 0")
    suspend fun deleteById(id: Long)
}

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE date = :date")
    fun getCheckInsByDate(date: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE date >= :from AND date <= :to ORDER BY date DESC")
    fun getCheckInsByDateRange(from: String, to: String): Flow<List<CheckInEntity>>

    @Query("SELECT * FROM check_ins WHERE task_id = :taskId ORDER BY checked_at DESC LIMIT 1")
    suspend fun getLatestCheckInForTask(taskId: Long): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(checkIn: CheckInEntity): Long

    @Query("SELECT DISTINCT date FROM check_ins ORDER BY date DESC")
    suspend fun getAllCheckinDates(): List<String>
}

@Dao
interface MushroomLedgerDao {
    @Query("""
        SELECT level, SUM(CASE WHEN action = 'EARN' THEN amount ELSE 0 END) -
               SUM(CASE WHEN action IN ('DEDUCT', 'SPEND') THEN amount ELSE 0 END) AS balance
        FROM mushroom_ledger
        GROUP BY level
    """)
    fun getBalanceByLevel(): Flow<List<LevelBalance>>

    @Query("SELECT * FROM mushroom_ledger ORDER BY created_at DESC LIMIT :limit")
    fun getLedger(limit: Int): Flow<List<MushroomLedgerEntity>>

    @Query("SELECT * FROM mushroom_ledger WHERE date(created_at) >= :from AND date(created_at) <= :to ORDER BY created_at ASC")
    fun getLedgerByDateRange(from: String, to: String): Flow<List<MushroomLedgerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: MushroomLedgerEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<MushroomLedgerEntity>)
}

data class LevelBalance(val level: String, val balance: Int)

@Dao
interface MushroomConfigDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(configs: List<MushroomConfigEntity>)
}

@Dao
interface DeductionConfigDao {
    @Query("SELECT * FROM deduction_config ORDER BY is_built_in DESC, id")
    fun getAllConfigs(): Flow<List<DeductionConfigEntity>>

    @Query("SELECT * FROM deduction_config WHERE is_enabled = 1 ORDER BY is_built_in DESC, id")
    fun getEnabledConfigs(): Flow<List<DeductionConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(config: DeductionConfigEntity): Long

    @Update
    suspend fun update(config: DeductionConfigEntity)
}

@Dao
interface DeductionRecordDao {
    @Query("SELECT * FROM deduction_records ORDER BY recorded_at DESC")
    fun getAllRecords(): Flow<List<DeductionRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: DeductionRecordEntity): Long

    @Query("UPDATE deduction_records SET appeal_status = :status, appeal_note = :note WHERE id = :id")
    suspend fun updateAppealStatus(id: Long, status: String, note: String?)

    @Query("""
        SELECT COUNT(*) FROM deduction_records
        WHERE config_id = :configId AND date(recorded_at) = date(:today)
    """)
    suspend fun getTodayCountByConfigId(configId: Long, today: String): Int
}

@Dao
interface RewardDao {
    @Query("SELECT * FROM rewards WHERE status = 'ACTIVE'")
    fun getActiveRewards(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getRewardById(id: Long): RewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reward: RewardEntity): Long

    @Update
    suspend fun update(reward: RewardEntity)
}

@Dao
interface RewardExchangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchange: RewardExchangeEntity): Long

    @Query("SELECT SUM(puzzle_pieces_unlocked) FROM reward_exchanges WHERE reward_id = :rewardId")
    fun getUnlockedPieces(rewardId: Long): Flow<Int?>

    @Query("SELECT * FROM reward_exchanges WHERE reward_id = :rewardId AND puzzle_pieces_unlocked > 0 ORDER BY created_at ASC")
    fun getPhysicalExchanges(rewardId: Long): Flow<List<RewardExchangeEntity>>
}

@Dao
interface TimeRewardUsageDao {
    @Query("SELECT * FROM time_reward_usage WHERE reward_id = :rewardId AND period_start = :periodStart")
    suspend fun getUsage(rewardId: Long, periodStart: String): TimeRewardUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: TimeRewardUsageEntity)

    @Query("UPDATE time_reward_usage SET used_minutes = :usedMinutes WHERE reward_id = :rewardId AND period_start = :periodStart")
    suspend fun updateUsedMinutes(rewardId: Long, periodStart: String, usedMinutes: Int)
}

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones ORDER BY scheduled_date DESC")
    fun getAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE subject = :subject ORDER BY scheduled_date DESC")
    fun getMilestonesBySubject(subject: String): Flow<List<MilestoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: MilestoneEntity): Long

    @Query("UPDATE milestones SET actual_score = :score, status = :status WHERE id = :id")
    suspend fun updateScore(id: Long, score: Int, status: String)
}

@Dao
interface ScoringRuleDao {
    @Query("SELECT * FROM scoring_rules WHERE milestone_id = :milestoneId")
    suspend fun getRulesForMilestone(milestoneId: Long): List<ScoringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ScoringRuleEntity>)
}

@Dao
interface KeyDateDao {
    @Query("SELECT * FROM key_dates ORDER BY date ASC")
    fun getAllKeyDates(): Flow<List<KeyDateEntity>>

    @Query("SELECT * FROM key_dates WHERE date >= :today AND date <= :until ORDER BY date ASC")
    fun getUpcomingKeyDates(today: String, until: String): Flow<List<KeyDateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(keyDate: KeyDateEntity): Long

    @Query("DELETE FROM key_dates WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface BackupDao {
    @Query("SELECT * FROM tasks")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM task_templates")
    suspend fun getAllTemplates(): List<TaskTemplateEntity>

    @Query("SELECT * FROM check_ins")
    suspend fun getAllCheckIns(): List<CheckInEntity>

    @Query("SELECT * FROM mushroom_ledger")
    suspend fun getAllLedger(): List<MushroomLedgerEntity>

    @Query("SELECT * FROM deduction_config")
    suspend fun getAllDeductionConfigs(): List<DeductionConfigEntity>

    @Query("SELECT * FROM deduction_records")
    suspend fun getAllDeductionRecords(): List<DeductionRecordEntity>

    @Query("SELECT * FROM rewards")
    suspend fun getAllRewards(): List<RewardEntity>

    @Query("SELECT * FROM reward_exchanges")
    suspend fun getAllExchanges(): List<RewardExchangeEntity>

    @Query("SELECT * FROM milestones")
    suspend fun getAllMilestones(): List<MilestoneEntity>

    @Query("SELECT * FROM scoring_rules")
    suspend fun getAllScoringRules(): List<ScoringRuleEntity>

    @Query("SELECT * FROM key_dates")
    suspend fun getAllKeyDates(): List<KeyDateEntity>

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM check_ins")
    suspend fun clearCheckIns()

    @Query("DELETE FROM mushroom_ledger")
    suspend fun clearLedger()

    @Query("DELETE FROM deduction_records")
    suspend fun clearDeductionRecords()

    @Query("DELETE FROM rewards")
    suspend fun clearRewards()

    @Query("DELETE FROM reward_exchanges")
    suspend fun clearExchanges()

    @Query("DELETE FROM milestones")
    suspend fun clearMilestones()

    @Query("DELETE FROM scoring_rules")
    suspend fun clearScoringRules()

    @Query("DELETE FROM key_dates")
    suspend fun clearKeyDates()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCheckIns(checkIns: List<CheckInEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedger(entries: List<MushroomLedgerEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDeductionRecords(records: List<DeductionRecordEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRewards(rewards: List<RewardEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExchanges(exchanges: List<RewardExchangeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestones(milestones: List<MilestoneEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScoringRules(rules: List<ScoringRuleEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyDates(keyDates: List<KeyDateEntity>)
}
