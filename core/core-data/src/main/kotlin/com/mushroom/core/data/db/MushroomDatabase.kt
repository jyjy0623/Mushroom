package com.mushroom.core.data.db

import androidx.room.Database
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
    version = 1,
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
}
