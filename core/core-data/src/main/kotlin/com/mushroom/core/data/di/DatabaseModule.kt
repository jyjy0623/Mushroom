package com.mushroom.core.data.di

import android.content.Context
import androidx.room.Room
import com.mushroom.core.data.db.MushroomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideMushroomDatabase(@ApplicationContext context: Context): MushroomDatabase =
        Room.databaseBuilder(
            context,
            MushroomDatabase::class.java,
            "mushroom_adventure.db"
        )
            .addMigrations(MushroomDatabase.MIGRATION_1_2, MushroomDatabase.MIGRATION_2_3, MushroomDatabase.MIGRATION_3_4, MushroomDatabase.MIGRATION_4_5, MushroomDatabase.MIGRATION_5_6, MushroomDatabase.MIGRATION_6_7, MushroomDatabase.MIGRATION_7_8)
            .build()

    @Provides fun provideTaskDao(db: MushroomDatabase) = db.taskDao()
    @Provides fun provideTaskTemplateDao(db: MushroomDatabase) = db.taskTemplateDao()
    @Provides fun provideCheckInDao(db: MushroomDatabase) = db.checkInDao()
    @Provides fun provideMushroomLedgerDao(db: MushroomDatabase) = db.mushroomLedgerDao()
    @Provides fun provideMushroomConfigDao(db: MushroomDatabase) = db.mushroomConfigDao()
    @Provides fun provideDeductionConfigDao(db: MushroomDatabase) = db.deductionConfigDao()
    @Provides fun provideDeductionRecordDao(db: MushroomDatabase) = db.deductionRecordDao()
    @Provides fun provideRewardDao(db: MushroomDatabase) = db.rewardDao()
    @Provides fun provideRewardExchangeDao(db: MushroomDatabase) = db.rewardExchangeDao()
    @Provides fun provideTimeRewardUsageDao(db: MushroomDatabase) = db.timeRewardUsageDao()
    @Provides fun provideMilestoneDao(db: MushroomDatabase) = db.milestoneDao()
    @Provides fun provideScoringRuleDao(db: MushroomDatabase) = db.scoringRuleDao()
    @Provides fun provideKeyDateDao(db: MushroomDatabase) = db.keyDateDao()
    @Provides fun provideBackupDao(db: MushroomDatabase) = db.backupDao()
    @Provides fun provideGameScoreDao(db: MushroomDatabase) = db.gameScoreDao()
    @Provides fun provideScoringRuleTemplateDao(db: MushroomDatabase) = db.scoringRuleTemplateDao()
}
