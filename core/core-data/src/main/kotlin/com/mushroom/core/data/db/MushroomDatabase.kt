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
        KeyDateEntity::class,
        GameScoreEntity::class,
        GamePlayStateEntity::class,
        ScoringRuleTemplateEntity::class,
        ScoringRuleTemplateItemEntity::class
    ],
    version = 8,
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
    abstract fun gameScoreDao(): GameScoreDao
    abstract fun scoringRuleTemplateDao(): ScoringRuleTemplateDao

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
        /**
         * v3 → v4：tasks 表新增 description 字段，默认为空字符串。
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN description TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v4 → v5：新增 game_scores 和 game_play_state 表。
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_scores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        score INTEGER NOT NULL,
                        played_at TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS game_play_state (
                        key TEXT PRIMARY KEY NOT NULL,
                        value TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
        /**
         * v5 → v6：time_reward_usage 表重建，用次数（used_times/max_times）替代分钟（used_minutes/max_minutes）。
         * TimeLimitConfig JSON 字段改为存 costMushroomLevel/costMushroomCount/maxTimesPerPeriod，
         * 旧数据通过 Mapper 的 ignoreUnknownKeys 兼容读取（旧字段忽略，新字段取默认值）。
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 重建 time_reward_usage 表（SQLite 不支持 DROP COLUMN，用重建方式）
                db.execSQL("DROP TABLE IF EXISTS time_reward_usage")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS time_reward_usage (
                        reward_id INTEGER NOT NULL,
                        period_start TEXT NOT NULL,
                        max_times INTEGER,
                        used_times INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (reward_id, period_start),
                        FOREIGN KEY (reward_id) REFERENCES rewards(id)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_time_reward_usage_reward_id ON time_reward_usage(reward_id)")
            }
        }

        /**
         * v6 → v7：新增评分规则模板表（scoring_rule_templates 和 scoring_rule_template_items）。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scoring_rule_templates (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS scoring_rule_template_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        template_id INTEGER NOT NULL,
                        min_score INTEGER NOT NULL,
                        max_score INTEGER NOT NULL,
                        reward_level TEXT NOT NULL,
                        reward_amount INTEGER NOT NULL,
                        FOREIGN KEY (template_id) REFERENCES scoring_rule_templates(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_scoring_rule_template_items_template_id ON scoring_rule_template_items(template_id)")
            }
        }
        /**
         * v7 → v8：scoring_rule_templates 新增 is_built_in 列，支持内置模板。
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE scoring_rule_templates ADD COLUMN is_built_in INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
