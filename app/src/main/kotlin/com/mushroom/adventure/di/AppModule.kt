package com.mushroom.adventure.di

import com.mushroom.adventure.BuildConfig
import com.mushroom.adventure.core.network.di.ServerUrl
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

    @Provides
    @Singleton
    @ServerUrl
    fun provideServerUrl(): String = BuildConfig.SERVER_URL
}
