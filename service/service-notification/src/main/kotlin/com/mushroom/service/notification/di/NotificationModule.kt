package com.mushroom.service.notification.di

import com.mushroom.core.domain.service.NotificationService
import com.mushroom.service.notification.NotificationServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NotificationModule {

    @Binds
    @Singleton
    abstract fun bindNotificationService(impl: NotificationServiceImpl): NotificationService
}
