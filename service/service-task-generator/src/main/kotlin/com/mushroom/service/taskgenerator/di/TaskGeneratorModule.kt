package com.mushroom.service.taskgenerator.di

import com.mushroom.core.domain.service.TaskGeneratorService
import com.mushroom.service.taskgenerator.TaskGeneratorServiceImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskGeneratorModule {

    @Binds
    @Singleton
    abstract fun bindTaskGeneratorService(impl: TaskGeneratorServiceImpl): TaskGeneratorService
}
