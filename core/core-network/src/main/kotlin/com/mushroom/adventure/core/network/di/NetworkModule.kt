package com.mushroom.adventure.core.network.di

import com.mushroom.adventure.core.network.api.MushroomApi
import com.mushroom.adventure.core.network.client.NetworkClientFactory
import com.mushroom.adventure.core.network.repository.ServerHealthRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    @ServerUrl
    fun provideServerBaseUrl(): String {
        // 从 app module 的 BuildConfig 获取服务器地址
        // 这需要在 AppModule 中提供实际的 URL
        return "http://192.168.31.174:8080" // 默认值
    }

    @Provides
    @Singleton
    fun provideMushroomApi(@ServerUrl baseUrl: String): MushroomApi {
        val retrofit = NetworkClientFactory.createRetrofit(baseUrl)
        return retrofit.create(MushroomApi::class.java)
    }

    @Provides
    @Singleton
    fun provideServerHealthRepository(api: MushroomApi): ServerHealthRepository {
        return ServerHealthRepository(api)
    }
}
