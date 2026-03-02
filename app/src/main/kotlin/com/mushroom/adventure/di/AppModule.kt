package com.mushroom.adventure.di

import com.mushroom.core.domain.event.AppEventBus
import com.mushroom.core.domain.event.AppEventBusImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppEventBus(): AppEventBus = AppEventBusImpl()
}
