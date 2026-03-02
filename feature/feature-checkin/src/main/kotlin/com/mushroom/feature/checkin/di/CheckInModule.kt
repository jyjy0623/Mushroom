package com.mushroom.feature.checkin.di

import com.mushroom.core.data.repository.CheckInRepositoryImpl
import com.mushroom.core.domain.repository.CheckInRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CheckInModule {

    @Binds
    @Singleton
    abstract fun bindCheckInRepository(impl: CheckInRepositoryImpl): CheckInRepository
}
