package com.mushroom.core.data.backup

import kotlinx.serialization.Serializable

/**
 * 备份数据载荷。包含所有用户数据表的快照，使用 JSON 序列化。
 * 字段类型与 DB entity 完全一致（String/Int/Long/Boolean）。
 */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: String,                       // ISO-8601 时间戳
    val tasks: List<TaskBackup> = emptyList(),
    val checkIns: List<CheckInBackup> = emptyList(),
    val mushroomLedger: List<LedgerBackup> = emptyList(),
    val deductionConfigs: List<DeductionConfigBackup> = emptyList(),
    val deductionRecords: List<DeductionRecordBackup> = emptyList(),
    val rewards: List<RewardBackup> = emptyList(),
    val rewardExchanges: List<RewardExchangeBackup> = emptyList(),
    val milestones: List<MilestoneBackup> = emptyList(),
    val scoringRules: List<ScoringRuleBackup> = emptyList(),
    val keyDates: List<KeyDateBackup> = emptyList()
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

@Serializable
data class TaskBackup(
    val id: Long,
    val title: String,
    val subject: String,
    val estimatedMinutes: Int,
    val repeatRuleType: String,
    val repeatRuleDays: String?,
    val date: String,
    val deadlineAt: String?,
    val templateType: String?,
    val status: String
)

@Serializable
data class CheckInBackup(
    val id: Long,
    val taskId: Long,
    val date: String,
    val checkedAt: String,
    val isEarly: Boolean,
    val earlyMinutes: Int,
    val note: String?,
    val imageUris: String
)

@Serializable
data class LedgerBackup(
    val id: Long,
    val level: String,
    val action: String,
    val amount: Int,
    val sourceType: String,
    val sourceId: Long?,
    val note: String?,
    val createdAt: String
)

@Serializable
data class DeductionConfigBackup(
    val id: Long,
    val name: String,
    val mushroomLevel: String,
    val defaultAmount: Int,
    val customAmount: Int,
    val isEnabled: Boolean,
    val isBuiltIn: Boolean,
    val maxPerDay: Int
)

@Serializable
data class DeductionRecordBackup(
    val id: Long,
    val configId: Long,
    val mushroomLevel: String,
    val amount: Int,
    val reason: String,
    val recordedAt: String,
    val appealStatus: String,
    val appealNote: String?
)

@Serializable
data class RewardBackup(
    val id: Long,
    val name: String,
    val imageUri: String,
    val type: String,
    val requiredMushrooms: String,
    val puzzlePieces: Int,
    val timeLimitConfig: String?,
    val status: String
)

@Serializable
data class RewardExchangeBackup(
    val id: Long,
    val rewardId: Long,
    val mushroomLevel: String,
    val mushroomCount: Int,
    val puzzlePiecesUnlocked: Int,
    val minutesGained: Int?,
    val createdAt: String
)

@Serializable
data class MilestoneBackup(
    val id: Long,
    val name: String,
    val type: String,
    val subject: String,
    val scheduledDate: String,
    val actualScore: Int?,
    val status: String
)

@Serializable
data class ScoringRuleBackup(
    val id: Long,
    val milestoneId: Long,
    val minScore: Int,
    val maxScore: Int,
    val rewardLevel: String,
    val rewardAmount: Int
)

@Serializable
data class KeyDateBackup(
    val id: Long,
    val name: String,
    val date: String,
    val conditionType: String,
    val conditionValue: String?,
    val rewardLevel: String,
    val rewardAmount: Int
)
