package com.mushroom.core.data.db.dao

import androidx.room.*
import com.mushroom.core.data.db.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE date = :date AND status != 'SKIPPED' ORDER BY id")
    fun getTasksByDate(date: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE date >= :from AND date <= :to ORDER BY date, status")
    fun getTasksByDateRange(from: String, to: String): Flow<List<TaskEntity>>

    @Query("SELECT title FROM tasks WHERE date = :date")
    suspend fun getAllTaskTitlesByDate(date: String): List<String>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Long): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET status = 'SKIPPED' WHERE id = :id")
    suspend fun markSkipped(id: Long)

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

    @Query("DELETE FROM deduction_config WHERE id = :id AND is_built_in = 0")
    suspend fun deleteCustomById(id: Long)
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

    @Query("SELECT * FROM rewards WHERE status != 'ARCHIVED' ORDER BY status ASC, id DESC")
    fun getAllNonArchived(): Flow<List<RewardEntity>>

    @Query("SELECT * FROM rewards WHERE id = :id")
    suspend fun getRewardById(id: Long): RewardEntity?

    @Query("SELECT * FROM rewards WHERE id = :id")
    fun getRewardByIdFlow(id: Long): Flow<RewardEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reward: RewardEntity): Long

    @Update
    suspend fun update(reward: RewardEntity)

    @Query("DELETE FROM rewards WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface RewardExchangeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(exchange: RewardExchangeEntity): Long

    @Query("SELECT SUM(puzzle_pieces_unlocked) FROM reward_exchanges WHERE reward_id = :rewardId")
    fun getUnlockedPieces(rewardId: Long): Flow<Int?>

    @Query("SELECT * FROM reward_exchanges WHERE reward_id = :rewardId AND puzzle_pieces_unlocked > 0 ORDER BY created_at ASC")
    fun getPhysicalExchanges(rewardId: Long): Flow<List<RewardExchangeEntity>>

    @Query("SELECT COUNT(*) FROM reward_exchanges WHERE reward_id = :rewardId")
    fun getExchangeCount(rewardId: Long): Flow<Int>

    @Query("SELECT * FROM reward_exchanges WHERE reward_id = :rewardId")
    suspend fun getExchangesByRewardId(rewardId: Long): List<RewardExchangeEntity>

    @Query("DELETE FROM reward_exchanges WHERE reward_id = :rewardId")
    suspend fun deleteByRewardId(rewardId: Long)
}

@Dao
interface TimeRewardUsageDao {
    @Query("SELECT * FROM time_reward_usage WHERE reward_id = :rewardId AND period_start = :periodStart")
    suspend fun getUsage(rewardId: Long, periodStart: String): TimeRewardUsageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(usage: TimeRewardUsageEntity)

    @Query("UPDATE time_reward_usage SET used_times = :usedTimes WHERE reward_id = :rewardId AND period_start = :periodStart")
    suspend fun updateUsedTimes(rewardId: Long, periodStart: String, usedTimes: Int)

    @Query("DELETE FROM time_reward_usage WHERE reward_id = :rewardId")
    suspend fun deleteByRewardId(rewardId: Long)
}

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones ORDER BY scheduled_date DESC")
    fun getAllMilestones(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE subject = :subject ORDER BY scheduled_date DESC")
    fun getMilestonesBySubject(subject: String): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun getById(id: Long): MilestoneEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(milestone: MilestoneEntity): Long

    @Update
    suspend fun update(milestone: MilestoneEntity)

    @Query("UPDATE milestones SET actual_score = :score, status = :status WHERE id = :id")
    suspend fun updateScore(id: Long, score: Int, status: String)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface ScoringRuleDao {
    @Query("SELECT * FROM scoring_rules WHERE milestone_id = :milestoneId")
    suspend fun getRulesForMilestone(milestoneId: Long): List<ScoringRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rules: List<ScoringRuleEntity>)

    @Query("DELETE FROM scoring_rules WHERE milestone_id = :milestoneId")
    suspend fun deleteByMilestoneId(milestoneId: Long)
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

    @Query("DELETE FROM deduction_config")
    suspend fun clearDeductionConfigs()

    @Query("DELETE FROM rewards")
    suspend fun clearRewards()

    @Query("DELETE FROM reward_exchanges")
    suspend fun clearExchanges()

    @Query("DELETE FROM time_reward_usage")
    suspend fun clearTimeRewardUsage()

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
    suspend fun insertDeductionConfigs(configs: List<DeductionConfigEntity>)

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

@Dao
interface GameScoreDao {
    @Query("SELECT * FROM game_scores ORDER BY score DESC LIMIT :limit")
    fun getTopScores(limit: Int): Flow<List<com.mushroom.core.data.db.entity.GameScoreEntity>>

    @Query("SELECT MAX(score) FROM game_scores")
    suspend fun getHighScore(): Int?

    @Insert
    suspend fun insert(entity: com.mushroom.core.data.db.entity.GameScoreEntity): Long

    @Query("SELECT value FROM game_play_state WHERE key = :key")
    suspend fun getState(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setState(entity: com.mushroom.core.data.db.entity.GamePlayStateEntity)
}

@Dao
interface ScoringRuleTemplateDao {
    @Query("SELECT * FROM scoring_rule_templates ORDER BY id")
    fun getAll(): Flow<List<com.mushroom.core.data.db.entity.ScoringRuleTemplateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTemplate(template: com.mushroom.core.data.db.entity.ScoringRuleTemplateEntity): Long

    @Update
    suspend fun updateTemplate(template: com.mushroom.core.data.db.entity.ScoringRuleTemplateEntity)

    @Query("DELETE FROM scoring_rule_templates WHERE id = :id")
    suspend fun deleteTemplateById(id: Long)

    @Query("SELECT * FROM scoring_rule_template_items WHERE template_id = :templateId")
    suspend fun getItemsByTemplateId(templateId: Long): List<com.mushroom.core.data.db.entity.ScoringRuleTemplateItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<com.mushroom.core.data.db.entity.ScoringRuleTemplateItemEntity>)

    @Query("DELETE FROM scoring_rule_template_items WHERE template_id = :templateId")
    suspend fun deleteItemsByTemplateId(templateId: Long)
}
