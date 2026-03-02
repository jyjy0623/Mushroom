package com.mushroom.adventure.parent

import com.mushroom.core.domain.entity.RewardExchange
import com.mushroom.core.domain.service.ParentGateway
import com.mushroom.core.logging.MushroomLogger
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ParentGatewayImpl"

@Singleton
class ParentGatewayImpl @Inject constructor(
    private val coordinator: ParentAuthCoordinator
) : ParentGateway {

    override suspend fun requestExchangeApproval(exchange: RewardExchange): Boolean {
        MushroomLogger.i(TAG, "requestExchangeApproval: rewardId=${exchange.rewardId}")
        return coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
    }

    override suspend fun requestTimeRewardConfirmation(rewardId: Long): Boolean {
        MushroomLogger.i(TAG, "requestTimeRewardConfirmation: rewardId=$rewardId")
        return coordinator.requestAuth(AuthReason.TIME_REWARD_CONFIRM)
    }
}
