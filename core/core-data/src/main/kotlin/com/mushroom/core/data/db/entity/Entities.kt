package com.mushroom.core.data.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val subject: String,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int,
    @ColumnInfo(name = "repeat_rule_type") val repeatRuleType: String,
    @ColumnInfo(name = "repeat_rule_days") val repeatRuleDays: String?,
    val date: String,
    @ColumnInfo(name = "deadline_at") val deadlineAt: String?,
    @ColumnInfo(name = "template_type") val templateType: String?,
    val status: String = "PENDING",
    val description: String = "",
    @ColumnInfo(name = "custom_reward_level") val customRewardLevel: String? = null,
    @ColumnInfo(name = "custom_reward_amount") val customRewardAmount: Int? = null,
    @ColumnInfo(name = "custom_early_reward_level") val customEarlyRewardLevel: String? = null,
    @ColumnInfo(name = "custom_early_reward_amount") val customEarlyRewardAmount: Int? = null
)

@Entity(tableName = "task_templates")
data class TaskTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val subject: String,
    @ColumnInfo(name = "estimated_minutes") val estimatedMinutes: Int,
    val description: String = "",
    @ColumnInfo(name = "default_deadline_offset") val defaultDeadlineOffset: Int?,
    @ColumnInfo(name = "base_reward_level") val baseRewardLevel: String,
    @ColumnInfo(name = "base_reward_amount") val baseRewardAmount: Int,
    @ColumnInfo(name = "bonus_reward_level") val bonusRewardLevel: String?,
    @ColumnInfo(name = "bonus_reward_amount") val bonusRewardAmount: Int?,
    @ColumnInfo(name = "bonus_condition_type") val bonusConditionType: String?,
    @ColumnInfo(name = "bonus_condition_value") val bonusConditionValue: Int?,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false
)

@Entity(
    tableName = "check_ins",
    foreignKeys = [ForeignKey(
        entity = TaskEntity::class,
        parentColumns = ["id"],
        childColumns = ["task_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("task_id")]
)
data class CheckInEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "task_id") val taskId: Long,
    val date: String,
    @ColumnInfo(name = "checked_at") val checkedAt: String,
    @ColumnInfo(name = "is_early") val isEarly: Boolean = false,
    @ColumnInfo(name = "early_minutes") val earlyMinutes: Int = 0,
    val note: String?,
    @ColumnInfo(name = "image_uris") val imageUris: String = "[]"
)

@Entity(tableName = "mushroom_ledger")
data class MushroomLedgerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val level: String,
    val action: String,
    val amount: Int,
    @ColumnInfo(name = "source_type") val sourceType: String,
    @ColumnInfo(name = "source_id") val sourceId: Long?,
    val note: String?,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(tableName = "mushroom_config")
data class MushroomConfigEntity(
    @PrimaryKey val level: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "exchange_points") val exchangePoints: Int
)

@Entity(tableName = "deduction_config")
data class DeductionConfigEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "mushroom_level") val mushroomLevel: String,
    @ColumnInfo(name = "default_amount") val defaultAmount: Int,
    @ColumnInfo(name = "custom_amount") val customAmount: Int,
    @ColumnInfo(name = "is_enabled") val isEnabled: Boolean = false,
    @ColumnInfo(name = "is_built_in") val isBuiltIn: Boolean = false,
    @ColumnInfo(name = "max_per_day") val maxPerDay: Int = 1
)

@Entity(
    tableName = "deduction_records",
    foreignKeys = [ForeignKey(
        entity = DeductionConfigEntity::class,
        parentColumns = ["id"],
        childColumns = ["config_id"]
    )],
    indices = [Index("config_id")]
)
data class DeductionRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "config_id") val configId: Long,
    @ColumnInfo(name = "mushroom_level") val mushroomLevel: String,
    val amount: Int,
    val reason: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: String,
    @ColumnInfo(name = "appeal_status") val appealStatus: String = "NONE",
    @ColumnInfo(name = "appeal_note") val appealNote: String?
)

@Entity(tableName = "rewards")
data class RewardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "image_uri") val imageUri: String = "",
    val type: String,
    @ColumnInfo(name = "required_mushrooms") val requiredMushrooms: String,
    @ColumnInfo(name = "puzzle_pieces") val puzzlePieces: Int = 1,
    @ColumnInfo(name = "time_limit_config") val timeLimitConfig: String?,
    val status: String = "ACTIVE"
)

@Entity(
    tableName = "reward_exchanges",
    foreignKeys = [ForeignKey(
        entity = RewardEntity::class,
        parentColumns = ["id"],
        childColumns = ["reward_id"]
    )],
    indices = [Index("reward_id")]
)
data class RewardExchangeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "reward_id") val rewardId: Long,
    @ColumnInfo(name = "mushroom_level") val mushroomLevel: String,
    @ColumnInfo(name = "mushroom_count") val mushroomCount: Int,
    @ColumnInfo(name = "puzzle_pieces_unlocked") val puzzlePiecesUnlocked: Int = 0,
    @ColumnInfo(name = "minutes_gained") val minutesGained: Int?,
    @ColumnInfo(name = "created_at") val createdAt: String
)

@Entity(
    tableName = "time_reward_usage",
    primaryKeys = ["reward_id", "period_start"],
    foreignKeys = [ForeignKey(
        entity = RewardEntity::class,
        parentColumns = ["id"],
        childColumns = ["reward_id"]
    )],
    indices = [Index("reward_id")]
)
data class TimeRewardUsageEntity(
    @ColumnInfo(name = "reward_id") val rewardId: Long,
    @ColumnInfo(name = "period_start") val periodStart: String,
    @ColumnInfo(name = "max_minutes") val maxMinutes: Int,
    @ColumnInfo(name = "used_minutes") val usedMinutes: Int = 0
)

@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String,
    val subject: String,
    @ColumnInfo(name = "scheduled_date") val scheduledDate: String,
    @ColumnInfo(name = "actual_score") val actualScore: Int?,
    val status: String = "PENDING"
)

@Entity(
    tableName = "scoring_rules",
    foreignKeys = [ForeignKey(
        entity = MilestoneEntity::class,
        parentColumns = ["id"],
        childColumns = ["milestone_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("milestone_id")]
)
data class ScoringRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "milestone_id") val milestoneId: Long,
    @ColumnInfo(name = "min_score") val minScore: Int,
    @ColumnInfo(name = "max_score") val maxScore: Int,
    @ColumnInfo(name = "reward_level") val rewardLevel: String,
    @ColumnInfo(name = "reward_amount") val rewardAmount: Int
)

@Entity(tableName = "key_dates")
data class KeyDateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val date: String,
    @ColumnInfo(name = "condition_type") val conditionType: String,
    @ColumnInfo(name = "condition_value") val conditionValue: String?,
    @ColumnInfo(name = "reward_level") val rewardLevel: String,
    @ColumnInfo(name = "reward_amount") val rewardAmount: Int
)

@Entity(tableName = "game_scores")
data class GameScoreEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val score: Int,
    @ColumnInfo(name = "played_at") val playedAt: String
)

@Entity(tableName = "game_play_state")
data class GamePlayStateEntity(
    @PrimaryKey val key: String,
    val value: String
)
