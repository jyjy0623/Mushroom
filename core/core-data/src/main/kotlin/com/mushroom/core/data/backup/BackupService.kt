package com.mushroom.core.data.backup

import android.content.Context
import com.mushroom.core.data.db.MushroomDatabase
import com.mushroom.core.data.db.entity.*
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BACKUP"

@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: MushroomDatabase
) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val dao get() = db.backupDao()

    /** 导出所有用户数据为 JSON 文件，返回导出文件。 */
    suspend fun export(): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val exportFile = File(context.cacheDir, "mushroom_backup_$timestamp.json")
        val payload = exportPayload()
        exportFile.writeText(json.encodeToString(payload), Charsets.UTF_8)
        MushroomLogger.i(TAG, "Backup exported: ${exportFile.name} " +
                "(tasks=${payload.tasks.size}, checkIns=${payload.checkIns.size}, " +
                "ledger=${payload.mushroomLedger.size})")
        return exportFile
    }

    /** 构建 BackupPayload 到内存（供云端上传使用，不写文件）。 */
    suspend fun exportPayload(): BackupPayload {
        return BackupPayload(
            exportedAt = LocalDateTime.now().toString(),
            tasks = dao.getAllTasks().map { it.toBackup() },
            checkIns = dao.getAllCheckIns().map { it.toBackup() },
            mushroomLedger = dao.getAllLedger().map { it.toBackup() },
            deductionConfigs = dao.getAllDeductionConfigs().map { it.toBackup() },
            deductionRecords = dao.getAllDeductionRecords().map { it.toBackup() },
            rewards = dao.getAllRewards().map { it.toBackup() },
            rewardExchanges = dao.getAllExchanges().map { it.toBackup() },
            milestones = dao.getAllMilestones().map { it.toBackup() },
            scoringRules = dao.getAllScoringRules().map { it.toBackup() },
            keyDates = dao.getAllKeyDates().map { it.toBackup() }
        )
    }

    /**
     * 从 JSON 文件恢复数据。
     * 策略：清除用户生成数据（保留内置配置），然后插入备份数据。
     * 注意：不清除 task_templates 和 deduction_config（内置条目标识由 is_built_in 区分）。
     */
    suspend fun import(file: File): Result<Unit> = runCatching {
        val payload = json.decodeFromString<BackupPayload>(file.readText(Charsets.UTF_8))
        check(payload.schemaVersion == BackupPayload.SCHEMA_VERSION) {
            "不支持的备份版本 ${payload.schemaVersion}，当前版本 ${BackupPayload.SCHEMA_VERSION}"
        }

        // 清除可恢复的用户数据（先子表后父表）
        dao.clearCheckIns()
        dao.clearLedger()
        dao.clearDeductionRecords()
        dao.clearDeductionConfigs()
        dao.clearExchanges()
        dao.clearScoringRules()
        dao.clearMilestones()
        dao.clearKeyDates()
        dao.clearRewards()
        dao.clearTasks()

        // 插入备份数据（先父表后子表）
        if (payload.tasks.isNotEmpty()) dao.insertTasks(payload.tasks.map { it.toEntity() })
        if (payload.checkIns.isNotEmpty()) dao.insertCheckIns(payload.checkIns.map { it.toEntity() })
        if (payload.mushroomLedger.isNotEmpty()) dao.insertLedger(payload.mushroomLedger.map { it.toEntity() })
        if (payload.deductionConfigs.isNotEmpty()) dao.insertDeductionConfigs(payload.deductionConfigs.map { it.toEntity() })
        if (payload.deductionRecords.isNotEmpty()) dao.insertDeductionRecords(payload.deductionRecords.map { it.toEntity() })
        if (payload.rewards.isNotEmpty()) dao.insertRewards(payload.rewards.map { it.toEntity() })
        if (payload.rewardExchanges.isNotEmpty()) dao.insertExchanges(payload.rewardExchanges.map { it.toEntity() })
        if (payload.milestones.isNotEmpty()) dao.insertMilestones(payload.milestones.map { it.toEntity() })
        if (payload.scoringRules.isNotEmpty()) dao.insertScoringRules(payload.scoringRules.map { it.toEntity() })
        if (payload.keyDates.isNotEmpty()) dao.insertKeyDates(payload.keyDates.map { it.toEntity() })

        MushroomLogger.i(TAG, "Backup imported from ${file.name}, exportedAt=${payload.exportedAt}")
    }.onFailure { MushroomLogger.e(TAG, "Backup import failed", it) }

    /** 从内存中的 BackupPayload 恢复数据（供云端恢复使用）。 */
    suspend fun importPayload(payload: BackupPayload): Result<Unit> = runCatching {
        check(payload.schemaVersion == BackupPayload.SCHEMA_VERSION) {
            "不支持的备份版本 ${payload.schemaVersion}，当前版本 ${BackupPayload.SCHEMA_VERSION}"
        }

        dao.clearCheckIns()
        dao.clearLedger()
        dao.clearDeductionRecords()
        dao.clearDeductionConfigs()
        dao.clearExchanges()
        dao.clearScoringRules()
        dao.clearMilestones()
        dao.clearKeyDates()
        dao.clearRewards()
        dao.clearTasks()

        if (payload.tasks.isNotEmpty()) dao.insertTasks(payload.tasks.map { it.toEntity() })
        if (payload.checkIns.isNotEmpty()) dao.insertCheckIns(payload.checkIns.map { it.toEntity() })
        if (payload.mushroomLedger.isNotEmpty()) dao.insertLedger(payload.mushroomLedger.map { it.toEntity() })
        if (payload.deductionConfigs.isNotEmpty()) dao.insertDeductionConfigs(payload.deductionConfigs.map { it.toEntity() })
        if (payload.deductionRecords.isNotEmpty()) dao.insertDeductionRecords(payload.deductionRecords.map { it.toEntity() })
        if (payload.rewards.isNotEmpty()) dao.insertRewards(payload.rewards.map { it.toEntity() })
        if (payload.rewardExchanges.isNotEmpty()) dao.insertExchanges(payload.rewardExchanges.map { it.toEntity() })
        if (payload.milestones.isNotEmpty()) dao.insertMilestones(payload.milestones.map { it.toEntity() })
        if (payload.scoringRules.isNotEmpty()) dao.insertScoringRules(payload.scoringRules.map { it.toEntity() })
        if (payload.keyDates.isNotEmpty()) dao.insertKeyDates(payload.keyDates.map { it.toEntity() })

        MushroomLogger.i(TAG, "Cloud backup imported, exportedAt=${payload.exportedAt}")
    }.onFailure { MushroomLogger.e(TAG, "Cloud backup import failed", it) }

    // -------------------------------------------------------------------------
    // Entity → Backup
    // -------------------------------------------------------------------------

    private fun TaskEntity.toBackup() = TaskBackup(id, title, subject, estimatedMinutes,
        repeatRuleType, repeatRuleDays, date, deadlineAt, templateType, status, description)

    private fun CheckInEntity.toBackup() = CheckInBackup(id, taskId, date, checkedAt,
        isEarly, earlyMinutes, note, imageUris)

    private fun MushroomLedgerEntity.toBackup() = LedgerBackup(id, level, action, amount,
        sourceType, sourceId, note, createdAt)

    private fun DeductionConfigEntity.toBackup() = DeductionConfigBackup(id, name, mushroomLevel,
        defaultAmount, customAmount, isEnabled, isBuiltIn, maxPerDay)

    private fun DeductionRecordEntity.toBackup() = DeductionRecordBackup(id, configId, mushroomLevel,
        amount, reason, recordedAt, appealStatus, appealNote)

    private fun RewardEntity.toBackup() = RewardBackup(id, name, imageUri, type,
        requiredMushrooms, puzzlePieces, timeLimitConfig, status)

    private fun RewardExchangeEntity.toBackup() = RewardExchangeBackup(id, rewardId, mushroomLevel,
        mushroomCount, puzzlePiecesUnlocked, minutesGained, createdAt)

    private fun MilestoneEntity.toBackup() = MilestoneBackup(id, name, type, subject,
        scheduledDate, actualScore, status)

    private fun ScoringRuleEntity.toBackup() = ScoringRuleBackup(id, milestoneId, minScore,
        maxScore, rewardLevel, rewardAmount)

    private fun KeyDateEntity.toBackup() = KeyDateBackup(id, name, date, conditionType,
        conditionValue, rewardLevel, rewardAmount)

    // -------------------------------------------------------------------------
    // Backup → Entity
    // -------------------------------------------------------------------------

    private fun TaskBackup.toEntity() = TaskEntity(id, title, subject, estimatedMinutes,
        repeatRuleType, repeatRuleDays, date, deadlineAt, templateType, status, description)

    private fun CheckInBackup.toEntity() = CheckInEntity(id, taskId, date, checkedAt,
        isEarly, earlyMinutes, note, imageUris)

    private fun LedgerBackup.toEntity() = MushroomLedgerEntity(id, level, action, amount,
        sourceType, sourceId, note, createdAt)

    private fun DeductionConfigBackup.toEntity() = DeductionConfigEntity(id, name,
        mushroomLevel, defaultAmount, customAmount, isEnabled, isBuiltIn, maxPerDay)

    private fun DeductionRecordBackup.toEntity() = DeductionRecordEntity(id, configId,
        mushroomLevel, amount, reason, recordedAt, appealStatus, appealNote)

    private fun RewardBackup.toEntity() = RewardEntity(id, name, imageUri, type,
        requiredMushrooms, puzzlePieces, timeLimitConfig, status)

    private fun RewardExchangeBackup.toEntity() = RewardExchangeEntity(id, rewardId,
        mushroomLevel, mushroomCount, puzzlePiecesUnlocked, minutesGained, createdAt)

    private fun MilestoneBackup.toEntity() = MilestoneEntity(id, name, type, subject,
        scheduledDate, actualScore, status)

    private fun ScoringRuleBackup.toEntity() = ScoringRuleEntity(id, milestoneId, minScore,
        maxScore, rewardLevel, rewardAmount)

    private fun KeyDateBackup.toEntity() = KeyDateEntity(id, name, date, conditionType,
        conditionValue, rewardLevel, rewardAmount)
}
