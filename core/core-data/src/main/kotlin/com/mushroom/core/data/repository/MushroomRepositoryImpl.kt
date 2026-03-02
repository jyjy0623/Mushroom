package com.mushroom.core.data.repository

import com.mushroom.core.data.db.dao.MushroomLedgerDao
import com.mushroom.core.data.mapper.MushroomLedgerMapper
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
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

    override suspend fun recordTransaction(transaction: MushroomTransaction) {
        ledgerDao.insert(MushroomLedgerMapper.toDb(transaction))
    }

    override suspend fun recordTransactions(transactions: List<MushroomTransaction>) {
        ledgerDao.insertAll(transactions.map(MushroomLedgerMapper::toDb))
    }
}
