package com.mushroom.feature.mushroom.di

import com.mushroom.core.data.repository.DeductionRepositoryImpl
import com.mushroom.core.data.repository.KeyDateRepositoryImpl
import com.mushroom.core.data.repository.MushroomRepositoryImpl
import com.mushroom.core.data.service.DeductionRuleEngineImpl
import com.mushroom.core.domain.repository.DeductionRepository
import com.mushroom.core.domain.repository.KeyDateRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.service.DeductionRuleEngine
import com.mushroom.core.domain.service.RewardRuleEngine
import com.mushroom.feature.mushroom.reward.RewardRuleChain
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MushroomModule {

    @Binds @Singleton
    abstract fun bindMushroomRepository(impl: MushroomRepositoryImpl): MushroomRepository

    @Binds @Singleton
    abstract fun bindDeductionRepository(impl: DeductionRepositoryImpl): DeductionRepository

    @Binds @Singleton
    abstract fun bindKeyDateRepository(impl: KeyDateRepositoryImpl): KeyDateRepository

    @Binds @Singleton
    abstract fun bindRewardRuleEngine(impl: RewardRuleChain): RewardRuleEngine

    @Binds @Singleton
    abstract fun bindDeductionRuleEngine(impl: DeductionRuleEngineImpl): DeductionRuleEngine
}
