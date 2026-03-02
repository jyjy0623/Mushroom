package com.mushroom.core.data.db

import androidx.room.Database
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import com.mushroom.core.data.db.dao.*
import com.mushroom.core.data.db.entity.*

@Database(
    entities = [
        TaskEntity::class,
        TaskTemplateEntity::class,
        CheckInEntity::class,
        MushroomLedgerEntity::class,
        MushroomConfigEntity::class,
        DeductionConfigEntity::class,
        DeductionRecordEntity::class,
        RewardEntity::class,
        RewardExchangeEntity::class,
        TimeRewardUsageEntity::class,
        MilestoneEntity::class,
        ScoringRuleEntity::class,
        KeyDateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MushroomDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun taskTemplateDao(): TaskTemplateDao
    abstract fun checkInDao(): CheckInDao
    abstract fun mushroomLedgerDao(): MushroomLedgerDao
    abstract fun mushroomConfigDao(): MushroomConfigDao
    abstract fun deductionConfigDao(): DeductionConfigDao
    abstract fun deductionRecordDao(): DeductionRecordDao
    abstract fun rewardDao(): RewardDao
    abstract fun rewardExchangeDao(): RewardExchangeDao
    abstract fun timeRewardUsageDao(): TimeRewardUsageDao
    abstract fun milestoneDao(): MilestoneDao
    abstract fun scoringRuleDao(): ScoringRuleDao
    abstract fun keyDateDao(): KeyDateDao
    abstract fun backupDao(): BackupDao

    companion object {
        /**
         * v1 → v2：清理 task_templates 中因 Seed 重复插入产生的重复内置模板。
         * 每种 type 只保留 id 最小的那条（最早插入的），删除其余重复项。
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    DELETE FROM task_templates
                    WHERE id NOT IN (
                        SELECT MIN(id) FROM task_templates GROUP BY type
                    )
                    AND is_built_in = 1
                """.trimIndent())
            }
        }

        /**
         * v2 → v3：tasks 表新增自定义奖励字段，默认 NULL（沿用规则引擎计算）。
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN custom_reward_level TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN custom_reward_amount INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN custom_early_reward_level TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN custom_early_reward_amount INTEGER")
            }
        }
    }
}
