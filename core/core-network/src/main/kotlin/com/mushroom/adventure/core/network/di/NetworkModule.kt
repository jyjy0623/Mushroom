package com.mushroom.adventure.core.network.di

import android.content.Context
import com.mushroom.adventure.core.network.api.MushroomApi
import com.mushroom.adventure.core.network.client.NetworkClientFactory
import com.mushroom.adventure.core.network.config.ServerUrlManager
import com.mushroom.adventure.core.network.repository.ServerHealthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ServerUrl

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideServerUrlManager(
        @ApplicationContext context: Context,
        @ServerUrl defaultUrl: String
    ): ServerUrlManager = ServerUrlManager(context, defaultUrl)

    @Provides
    @Singleton
    fun provideMushroomApi(serverUrlManager: ServerUrlManager): MushroomApi {
        val retrofit = NetworkClientFactory.createRetrofit(serverUrlManager)
        return retrofit.create(MushroomApi::class.java)
    }

    @Provides
    @Singleton
    fun provideServerHealthRepository(api: MushroomApi): ServerHealthRepository {
        return ServerHealthRepository(api)
    }
}
