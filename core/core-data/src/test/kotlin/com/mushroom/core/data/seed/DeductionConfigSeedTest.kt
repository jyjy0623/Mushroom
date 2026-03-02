package com.mushroom.core.data.seed

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.mushroom.core.data.db.MushroomDatabase
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
class DeductionConfigSeedTest {

    private lateinit var db: MushroomDatabase

    @BeforeEach
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MushroomDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @AfterEach
    fun tearDown() { db.close() }

    @Test
    fun `seed inserts 8 built-in deduction configs`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()

        val configs = db.deductionConfigDao().getAllConfigs().first()
        assertEquals(8, configs.size)
    }

    @Test
    fun `all seeded configs are marked as built-in`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()

        val configs = db.deductionConfigDao().getAllConfigs().first()
        assertTrue(configs.all { it.isBuiltIn })
    }

    @Test
    fun `all seeded configs are disabled by default`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()

        val configs = db.deductionConfigDao().getAllConfigs().first()
        assertTrue(configs.all { !it.isEnabled })
    }

    @Test
    fun `seed is idempotent — calling twice does not duplicate entries`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()
        seed.seed()

        val configs = db.deductionConfigDao().getAllConfigs().first()
        assertEquals(8, configs.size)
    }

    @Test
    fun `seeded configs have expected names`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()

        val names = db.deductionConfigDao().getAllConfigs().first().map { it.name }.toSet()
        assertTrue(names.contains("未完成作业"))
        assertTrue(names.contains("未完成打卡任务"))
        assertTrue(names.contains("超时使用电子设备"))
        assertTrue(names.contains("考试成绩退步"))
    }

    @Test
    fun `no enabled configs initially — getEnabledConfigs returns empty`() = runTest {
        val seed = DeductionConfigSeed(db.deductionConfigDao())
        seed.seed()

        val enabled = db.deductionConfigDao().getEnabledConfigs().first()
        assertTrue(enabled.isEmpty())
    }
}
