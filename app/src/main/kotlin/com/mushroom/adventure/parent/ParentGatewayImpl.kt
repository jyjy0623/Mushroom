package com.mushroom.adventure.parent

import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.service.ParentGateway
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ParentGatewayImpl @Inject constructor() : ParentGateway {

    override suspend fun requestExchangeApproval(exchange: RewardExchange): Boolean = true

    override suspend fun requestTimeRewardConfirmation(rewardId: Long): Boolean = true
}
