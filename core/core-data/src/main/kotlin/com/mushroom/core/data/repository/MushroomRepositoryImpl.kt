package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.MushroomLedgerDao
import com.mushroom.core.data.mapper.MushroomLedgerMapper
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.repository.MushroomRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MushroomRepositoryImpl @Inject constructor(
    private val ledgerDao: MushroomLedgerDao
) : MushroomRepository {

    override fun getBalance(): Flow<MushroomBalance> =
        ledgerDao.getBalanceByLevel().map { rows ->
            val balanceMap = MushroomLevel.values().associateWith { 0 }.toMutableMap()
            rows.forEach { row ->
                val level = runCatching { MushroomLevel.valueOf(row.level) }.getOrNull() ?: return@forEach
                balanceMap[level] = maxOf(0, row.balance)
            }
            MushroomBalance(balanceMap)
        }

    override fun getLedger(limit: Int): Flow<List<MushroomTransaction>> =
        ledgerDao.getLedger(limit).map { list -> list.map(MushroomLedgerMapper::toDomain) }

    override fun getLedgerByDateRange(from: LocalDate, to: LocalDate): Flow<List<MushroomTransaction>> =
        ledgerDao.getLedgerByDateRange(from.toString(), to.toString())
            .map { list -> list.map(MushroomLedgerMapper::toDomain) }

    override suspend fun getTransactionsBySource(sourceType: MushroomSource, sourceId: Long, milestoneNotePattern: String?): List<MushroomTransaction> {
        val exactMatch = ledgerDao.getBySource(sourceType.name, sourceId).map(MushroomLedgerMapper::toDomain)
        if (exactMatch.isNotEmpty()) return exactMatch

        // Fallback for legacy data where source_id was NULL
        if (milestoneNotePattern != null && sourceType == MushroomSource.MILESTONE) {
            val fallback = ledgerDao.getBySourceWithNullSourceIdAndNote(sourceType.name, milestoneNotePattern)
                .map(MushroomLedgerMapper::toDomain)
            if (fallback.isNotEmpty()) {
                return fallback
            }
        }
        return exactMatch
    }

    override suspend fun recordTransaction(transaction: MushroomTransaction) {
        // SPEND/DEDUCT 前检查余额，不允许扣成负数
        if (transaction.action == com.mushroom.core.domain.entity.MushroomAction.SPEND ||
            transaction.action == com.mushroom.core.domain.entity.MushroomAction.DEDUCT) {
            val rows = ledgerDao.getBalanceByLevelSnapshot()
            val currentBalance = rows.find { row ->
                runCatching { com.mushroom.core.domain.entity.MushroomLevel.valueOf(row.level) }.getOrNull() == transaction.level
            }?.balance ?: 0
            val newBalance = currentBalance - transaction.amount
            check(newBalance >= 0) {
                "${transaction.level.name} 余额不足（需要 ${transaction.amount}，当前 $currentBalance）"
            }
        }
        val entity = MushroomLedgerMapper.toDb(transaction)
        MushroomLogger.w("MushroomRepo", "recordTransaction: level=${entity.level} action=${entity.action} amount=${entity.amount}")
        ledgerDao.insert(entity)
    }

    override suspend fun recordTransactions(transactions: List<MushroomTransaction>) {
        // 逐条检查 SPEND/DEDUCT，平衡了再一起写入
        for (tx in transactions) {
            if (tx.action == com.mushroom.core.domain.entity.MushroomAction.SPEND ||
                tx.action == com.mushroom.core.domain.entity.MushroomAction.DEDUCT) {
                val rows = ledgerDao.getBalanceByLevelSnapshot()
                val currentBalance = rows.find { row ->
                    runCatching { com.mushroom.core.domain.entity.MushroomLevel.valueOf(row.level) }.getOrNull() == tx.level
                }?.balance ?: 0
                val newBalance = currentBalance - tx.amount
                check(newBalance >= 0) {
                    "${tx.level.name} 余额不足（需要 ${tx.amount}，当前 $currentBalance）"
                }
            }
        }
        val entities = transactions.map { MushroomLedgerMapper.toDb(it) }
        entities.forEach { e ->
            MushroomLogger.w("MushroomRepo", "recordTransactions: level=${e.level} action=${e.action} amount=${e.amount}")
        }
        ledgerDao.insertAll(entities)
    }

    override suspend fun getLatestEarnBySource(sourceType: MushroomSource, sourceId: Long): MushroomTransaction? =
        ledgerDao.getLatestEarnBySource(sourceType.name, sourceId)?.let(MushroomLedgerMapper::toDomain)

    override suspend fun getBalanceSnapshot(): MushroomBalance {
        val rows = ledgerDao.getBalanceByLevelSnapshot()
        val balanceMap = MushroomLevel.values().associateWith { 0 }.toMutableMap()
        rows.forEach { row ->
            val level = runCatching { MushroomLevel.valueOf(row.level) }.getOrNull() ?: return@forEach
            val bal = maxOf(0, row.balance)
            balanceMap[level] = bal
            com.mushroom.core.logging.MushroomLogger.w("MushroomRepo", "getBalanceSnapshot: level=${row.level} rawBalance=${row.balance} finalBalance=$bal")
        }
        return MushroomBalance(balanceMap)
    }
}
