package com.mushroom.feature.reward.di

import com.mushroom.core.data.repository.RewardRepositoryImpl
import com.mushroom.core.domain.repository.RewardRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RewardModule {

    @Binds
    @Singleton
    abstract fun bindRewardRepository(impl: RewardRepositoryImpl): RewardRepository
}
