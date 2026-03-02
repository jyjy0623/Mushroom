package com.mushroom.feature.milestone.di

import com.mushroom.core.data.repository.MilestoneRepositoryImpl
import com.mushroom.core.domain.repository.MilestoneRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MilestoneModule {

    @Binds
    @Singleton
    abstract fun bindMilestoneRepository(impl: MilestoneRepositoryImpl): MilestoneRepository
}
