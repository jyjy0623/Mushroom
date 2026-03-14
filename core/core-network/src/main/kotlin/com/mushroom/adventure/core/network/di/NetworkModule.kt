package com.mushroom.adventure.core.network.di

import android.content.Context
import com.mushroom.adventure.core.network.api.AuthApi
import com.mushroom.adventure.core.network.api.FriendApi
import com.mushroom.adventure.core.network.api.LeaderboardApi
import com.mushroom.adventure.core.network.api.MushroomApi
import com.mushroom.adventure.core.network.api.UserApi
import com.mushroom.adventure.core.network.client.NetworkClientFactory
import com.mushroom.adventure.core.network.config.DeviceIdProvider
import com.mushroom.adventure.core.network.config.ServerUrlManager
import com.mushroom.adventure.core.network.interceptor.AuthInterceptor
import com.mushroom.adventure.core.network.interceptor.TokenRefreshAuthenticator
import com.mushroom.adventure.core.network.repository.AuthRepository
import com.mushroom.adventure.core.network.repository.CloudBackupRepository
import com.mushroom.adventure.core.network.repository.FriendRepository
import com.mushroom.adventure.core.network.repository.LeaderboardRepository
import com.mushroom.adventure.core.network.repository.ServerHealthRepository
import com.mushroom.adventure.core.network.token.EncryptedTokenStore
import com.mushroom.adventure.core.network.token.TokenStore
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

    @Provides
    @Singleton
    fun provideCloudBackupRepository(api: MushroomApi): CloudBackupRepository {
        return CloudBackupRepository(api)
    }

    @Provides
    @Singleton
    fun provideTokenStore(@ApplicationContext context: Context): TokenStore {
        return EncryptedTokenStore(context)
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(tokenStore: TokenStore): AuthInterceptor {
        return AuthInterceptor(tokenStore)
    }

    @Provides
    @Singleton
    fun provideTokenRefreshAuthenticator(
        tokenStore: TokenStore,
        serverUrlManager: ServerUrlManager,
        authRepository: dagger.Lazy<AuthRepository>
    ): TokenRefreshAuthenticator {
        val refreshClient = NetworkClientFactory.createRefreshOkHttpClient(serverUrlManager)
        return TokenRefreshAuthenticator(
            tokenStore = tokenStore,
            refreshClient = refreshClient,
            getBaseUrl = { serverUrlManager.currentUrl.value },
            onSessionExpired = { authRepository.get().onSessionExpired() }
        )
    }

    @Provides
    @Singleton
    fun provideAuthApi(serverUrlManager: ServerUrlManager): AuthApi {
        val retrofit = NetworkClientFactory.createRetrofit(serverUrlManager)
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideUserApi(
        serverUrlManager: ServerUrlManager,
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): UserApi {
        val retrofit = NetworkClientFactory.createAuthenticatedRetrofit(serverUrlManager, authInterceptor, authenticator)
        return retrofit.create(UserApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAuthRepository(
        authApi: AuthApi,
        userApi: UserApi,
        tokenStore: TokenStore,
        @ApplicationContext context: Context
    ): AuthRepository {
        val deviceId = DeviceIdProvider.getDeviceId(context)
        return AuthRepository(authApi, userApi, tokenStore, deviceId)
    }

    @Provides
    @Singleton
    fun provideLeaderboardApi(
        serverUrlManager: ServerUrlManager,
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): LeaderboardApi {
        val retrofit = NetworkClientFactory.createAuthenticatedRetrofit(serverUrlManager, authInterceptor, authenticator)
        return retrofit.create(LeaderboardApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLeaderboardRepository(api: LeaderboardApi): LeaderboardRepository {
        return LeaderboardRepository(api)
    }

    @Provides
    @Singleton
    fun provideFriendApi(
        serverUrlManager: ServerUrlManager,
        authInterceptor: AuthInterceptor,
        authenticator: TokenRefreshAuthenticator
    ): FriendApi {
        val retrofit = NetworkClientFactory.createAuthenticatedRetrofit(serverUrlManager, authInterceptor, authenticator)
        return retrofit.create(FriendApi::class.java)
    }

    @Provides
    @Singleton
    fun provideFriendRepository(api: FriendApi): FriendRepository {
        return FriendRepository(api)
    }
}
