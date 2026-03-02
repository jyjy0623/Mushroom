package com.mushroom.core.data.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mushroom.core.data.db.MushroomDatabase
import com.mushroom.core.data.db.entity.MushroomLedgerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [34])
class MushroomLedgerDaoTest {

    private lateinit var db: MushroomDatabase

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MushroomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    @Test
    fun `when_insert_earn_transaction_balance_should_increase`() = runTest {
        db.mushroomLedgerDao().insert(buildLedger("SMALL", "EARN", 3))

        val balances = db.mushroomLedgerDao().getBalanceByLevel().first()
        val smallBalance = balances.firstOrNull { it.level == "SMALL" }
        assertNotNull(smallBalance)
        assertEquals(3, smallBalance!!.balance)
    }

    @Test
    fun `when_earn_then_spend_balance_should_be_difference`() = runTest {
        db.mushroomLedgerDao().insert(buildLedger("MEDIUM", "EARN", 5))
        db.mushroomLedgerDao().insert(buildLedger("MEDIUM", "SPEND", 2))

        val balances = db.mushroomLedgerDao().getBalanceByLevel().first()
        val mediumBalance = balances.firstOrNull { it.level == "MEDIUM" }
        assertEquals(3, mediumBalance?.balance)
    }

    @Test
    fun `when_earn_then_deduct_balance_should_be_difference`() = runTest {
        db.mushroomLedgerDao().insert(buildLedger("SMALL", "EARN", 10))
        db.mushroomLedgerDao().insert(buildLedger("SMALL", "DEDUCT", 3))

        val balances = db.mushroomLedgerDao().getBalanceByLevel().first()
        val smallBalance = balances.firstOrNull { it.level == "SMALL" }
        assertEquals(7, smallBalance?.balance)
    }

    @Test
    fun `when_getLedger_should_return_limited_and_sorted_by_desc`() = runTest {
        repeat(5) { i ->
            db.mushroomLedgerDao().insert(
                buildLedger("SMALL", "EARN", 1, createdAt = "2026-03-0${i + 1}T10:00:00")
            )
        }

        val ledger = db.mushroomLedgerDao().getLedger(3).first()
        assertEquals(3, ledger.size)
        // 按 created_at 降序，最新的在前
        assertTrue(ledger[0].createdAt >= ledger[1].createdAt)
    }

    private fun buildLedger(
        level: String = "SMALL",
        action: String = "EARN",
        amount: Int = 1,
        createdAt: String = "2026-03-01T10:00:00"
    ) = MushroomLedgerEntity(
        level = level,
        action = action,
        amount = amount,
        sourceType = "TASK",
        sourceId = null,
        note = null,
        createdAt = createdAt
    )
}
