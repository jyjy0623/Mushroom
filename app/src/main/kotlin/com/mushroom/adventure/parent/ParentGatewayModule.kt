package com.mushroom.adventure.parent

import com.mushroom.core.domain.service.ParentGateway
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ParentGatewayModule {

    @Binds
    @Singleton
    abstract fun bindParentGateway(impl: ParentGatewayImpl): ParentGateway
}
