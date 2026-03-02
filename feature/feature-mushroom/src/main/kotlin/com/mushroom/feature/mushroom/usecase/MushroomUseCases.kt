package com.mushroom.feature.mushroom.usecase

import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.DeductionConfig
import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.event.AppEvent
import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.repository.DeductionRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.service.DeductionRuleEngine
import com.mushroom.core.logging.MushroomLogger
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

private const val TAG = "MushroomUseCases"

// ---------------------------------------------------------------------------
// GetMushroomBalanceUseCase
// ---------------------------------------------------------------------------

class GetMushroomBalanceUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository
) {
    operator fun invoke(): Flow<MushroomBalance> = mushroomRepo.getBalance()
}

// ---------------------------------------------------------------------------
// GetMushroomLedgerUseCase
// ---------------------------------------------------------------------------

class GetMushroomLedgerUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository
) {
    operator fun invoke(limit: Int = 100): Flow<List<MushroomTransaction>> =
        mushroomRepo.getLedger(limit)
}

// ---------------------------------------------------------------------------
// EarnMushroomUseCase
// ---------------------------------------------------------------------------

class EarnMushroomUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(transaction: MushroomTransaction): Result<Unit> {
        MushroomLogger.i(TAG, "EarnMushroomUseCase: level=${transaction.level}, amount=${transaction.amount}")
        return runCatching {
            mushroomRepo.recordTransaction(transaction)
            eventBus.emit(AppEvent.MushroomEarned(listOf(transaction)))
        }.onFailure { MushroomLogger.e(TAG, "EarnMushroomUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// SpendMushroomUseCase
// ---------------------------------------------------------------------------

class SpendMushroomUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository
) {
    suspend operator fun invoke(
        level: com.mushroom.core.domain.entity.MushroomLevel,
        amount: Int,
        sourceId: Long?,
        note: String?
    ): Result<Unit> {
        MushroomLogger.i(TAG, "SpendMushroomUseCase: level=$level, amount=$amount")
        return runCatching {
            mushroomRepo.recordTransaction(
                MushroomTransaction(
                    level = level,
                    action = MushroomAction.SPEND,
                    amount = amount,
                    sourceType = MushroomSource.EXCHANGE,
                    sourceId = sourceId,
                    note = note,
                    createdAt = LocalDateTime.now()
                )
            )
        }.onFailure { MushroomLogger.e(TAG, "SpendMushroomUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// DeductMushroomUseCase
// ---------------------------------------------------------------------------

class DeductMushroomUseCase @Inject constructor(
    private val mushroomRepo: MushroomRepository,
    private val deductionRepo: DeductionRepository,
    private val deductionRuleEngine: DeductionRuleEngine,
    private val eventBus: AppEventBus
) {
    suspend operator fun invoke(config: DeductionConfig, reason: String): Result<Long> {
        MushroomLogger.i(TAG, "DeductMushroomUseCase: configId=${config.id}, reason=$reason")
        return runCatching {
            val canDeduct = deductionRuleEngine.canDeduct(config.id, LocalDate.now())
            check(canDeduct) { "今日已达扣除上限" }

            val amount = if (config.customAmount > 0) config.customAmount else config.defaultAmount
            val record = DeductionRecord(
                configId = config.id,
                mushroomLevel = config.mushroomLevel,
                amount = amount,
                reason = reason,
                recordedAt = LocalDateTime.now(),
                appealStatus = AppealStatus.NONE,
                appealNote = null
            )
            val recordId = deductionRepo.insertRecord(record)

            mushroomRepo.recordTransaction(
                MushroomTransaction(
                    level = config.mushroomLevel,
                    action = MushroomAction.DEDUCT,
                    amount = amount,
                    sourceType = MushroomSource.DEDUCTION,
                    sourceId = recordId,
                    note = reason,
                    createdAt = LocalDateTime.now()
                )
            )

            eventBus.emit(AppEvent.MushroomDeducted(record.copy(id = recordId)))
            MushroomLogger.i(TAG, "DeductMushroomUseCase: success, recordId=$recordId")
            recordId
        }.onFailure { MushroomLogger.e(TAG, "DeductMushroomUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// AppealDeductionUseCase
// ---------------------------------------------------------------------------

class AppealDeductionUseCase @Inject constructor(
    private val deductionRepo: DeductionRepository
) {
    suspend operator fun invoke(recordId: Long, appealNote: String): Result<Unit> {
        MushroomLogger.i(TAG, "AppealDeductionUseCase: recordId=$recordId")
        return runCatching {
            deductionRepo.updateAppealStatus(recordId, AppealStatus.PENDING, appealNote)
        }.onFailure { MushroomLogger.e(TAG, "AppealDeductionUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// ReviewAppealUseCase
// ---------------------------------------------------------------------------

class ReviewAppealUseCase @Inject constructor(
    private val deductionRepo: DeductionRepository,
    private val mushroomRepo: MushroomRepository
) {
    suspend operator fun invoke(
        recordId: Long,
        approved: Boolean,
        reviewNote: String?
    ): Result<Unit> {
        MushroomLogger.i(TAG, "ReviewAppealUseCase: recordId=$recordId, approved=$approved")
        return runCatching {
            val newStatus = if (approved) AppealStatus.APPROVED else AppealStatus.REJECTED
            deductionRepo.updateAppealStatus(recordId, newStatus, reviewNote)

            if (approved) {
                // 退还蘑菇：需要从记录中获取级别和数量
                // 简化实现：由 ViewModel 传入 refund info
            }
        }.onFailure { MushroomLogger.e(TAG, "ReviewAppealUseCase failed", it) }
    }

    /** 带退款信息的审批 */
    suspend operator fun invoke(
        recordId: Long,
        approved: Boolean,
        reviewNote: String?,
        refundLevel: com.mushroom.core.domain.entity.MushroomLevel,
        refundAmount: Int
    ): Result<Unit> {
        MushroomLogger.i(TAG, "ReviewAppealUseCase: recordId=$recordId, approved=$approved")
        return runCatching {
            val newStatus = if (approved) AppealStatus.APPROVED else AppealStatus.REJECTED
            deductionRepo.updateAppealStatus(recordId, newStatus, reviewNote)

            if (approved) {
                mushroomRepo.recordTransaction(
                    MushroomTransaction(
                        level = refundLevel,
                        action = MushroomAction.EARN,
                        amount = refundAmount,
                        sourceType = MushroomSource.APPEAL_REFUND,
                        sourceId = recordId,
                        note = "申诉通过退款",
                        createdAt = LocalDateTime.now()
                    )
                )
            }
        }.onFailure { MushroomLogger.e(TAG, "ReviewAppealUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// GetDeductionConfigsUseCase
// ---------------------------------------------------------------------------

class GetDeductionConfigsUseCase @Inject constructor(
    private val deductionRepo: DeductionRepository
) {
    operator fun invoke(): Flow<List<DeductionConfig>> = deductionRepo.getAllConfigs()
    fun enabled(): Flow<List<DeductionConfig>> = deductionRepo.getEnabledConfigs()
}

// ---------------------------------------------------------------------------
// UpdateDeductionConfigUseCase
// ---------------------------------------------------------------------------

class UpdateDeductionConfigUseCase @Inject constructor(
    private val deductionRepo: DeductionRepository
) {
    suspend operator fun invoke(config: DeductionConfig): Result<Unit> {
        MushroomLogger.i(TAG, "UpdateDeductionConfigUseCase: id=${config.id}")
        return runCatching {
            deductionRepo.updateConfig(config)
        }.onFailure { MushroomLogger.e(TAG, "UpdateDeductionConfigUseCase failed", it) }
    }
}

// ---------------------------------------------------------------------------
// GetDeductionHistoryUseCase
// ---------------------------------------------------------------------------

class GetDeductionHistoryUseCase @Inject constructor(
    private val deductionRepo: DeductionRepository
) {
    operator fun invoke(): Flow<List<DeductionRecord>> = deductionRepo.getAllRecords()
}
