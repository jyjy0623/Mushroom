package com.mushroom.adventure.parent

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.RewardExchange
import java.time.LocalDateTime

class ParentGatewayImplTest {

    private val gateway = ParentGatewayImpl()

    @Test
    fun `requestExchangeApproval always returns true`() = runTest {
        val result = gateway.requestExchangeApproval(
            RewardExchange(
                rewardId = 1L,
                mushroomLevel = MushroomLevel.SMALL,
                mushroomCount = 1,
                puzzlePiecesUnlocked = 0,
                minutesGained = null,
                createdAt = LocalDateTime.now()
            )
        )
        assertTrue(result)
    }

    @Test
    fun `requestTimeRewardConfirmation always returns true`() = runTest {
        val result = gateway.requestTimeRewardConfirmation(rewardId = 1L)
        assertTrue(result)
    }
}
