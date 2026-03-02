package com.mushroom.feature.task.di

import com.mushroom.core.data.repository.TaskRepositoryImpl
import com.mushroom.core.data.repository.TaskTemplateRepositoryImpl
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.repository.TaskTemplateRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TaskModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(impl: TaskRepositoryImpl): TaskRepository

    @Binds
    @Singleton
    abstract fun bindTaskTemplateRepository(impl: TaskTemplateRepositoryImpl): TaskTemplateRepository
}
