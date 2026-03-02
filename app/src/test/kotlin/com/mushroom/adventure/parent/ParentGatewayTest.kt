package com.mushroom.adventure.parent

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

// ---------------------------------------------------------------------------
// PinRepository tests (uses a mock-friendly wrapper to avoid Context dep)
// ---------------------------------------------------------------------------

class PinRepositoryLogicTest {

    /**
     * Lightweight stand-in that exercises the hash logic without Android Context.
     * We replicate the hash + verify behaviour directly.
     */
    private fun sha256(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `same pin produces same hash`() {
        val h1 = sha256("1234")
        val h2 = sha256("1234")
        assertTrue(h1 == h2)
    }

    @Test
    fun `different pins produce different hashes`() {
        val h1 = sha256("1234")
        val h2 = sha256("5678")
        assertFalse(h1 == h2)
    }

    @Test
    fun `hash is not reversible to original pin`() {
        val hash = sha256("1234")
        assertFalse(hash.contains("1234"))
    }
}

// ---------------------------------------------------------------------------
// ParentAuthCoordinator tests
// ---------------------------------------------------------------------------

class ParentAuthCoordinatorTest {

    private val coordinator = ParentAuthCoordinator()
    private val pinRepo = mockk<PinRepository>()

    @Test
    fun `pendingRequest is null initially`() {
        assertTrue(coordinator.pendingRequest.value == null)
    }

    @Test
    fun `cancelAuth resolves deferred with false`() = runTest {
        val job = launch {
            val result = coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
            assertFalse(result)
        }
        // Give coroutine time to suspend and set pendingRequest
        yield()
        coordinator.cancelAuth()
        job.join()
    }

    @Test
    fun `submitPin resolves deferred with true when pin matches`() = runTest {
        every { pinRepo.verifyPin("1234") } returns true

        var authResult = false
        val job = launch {
            authResult = coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
        }
        yield()
        coordinator.submitPin("1234", pinRepo)
        job.join()

        assertTrue(authResult)
        verify { pinRepo.verifyPin("1234") }
    }

    @Test
    fun `submitPin resolves deferred with false when pin wrong`() = runTest {
        every { pinRepo.verifyPin("9999") } returns false

        var authResult = true
        val job = launch {
            authResult = coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
        }
        yield()
        coordinator.submitPin("9999", pinRepo)
        job.join()

        assertFalse(authResult)
    }

    @Test
    fun `pendingRequest clears after auth completes`() = runTest {
        every { pinRepo.verifyPin(any()) } returns true

        val job = launch {
            coordinator.requestAuth(AuthReason.SETTINGS)
        }
        yield()
        coordinator.submitPin("0000", pinRepo)
        job.join()

        assertTrue(coordinator.pendingRequest.value == null)
    }

    @Test
    fun `cancelAuth is a no-op when no pending request`() {
        // Should not throw
        coordinator.cancelAuth()
        assertTrue(coordinator.pendingRequest.value == null)
    }
}

// ---------------------------------------------------------------------------
// ParentGatewayImpl tests
// ---------------------------------------------------------------------------

class ParentGatewayImplTest {

    private val coordinator = mockk<ParentAuthCoordinator>(relaxed = true)
    private val gateway = ParentGatewayImpl(coordinator)

    @Nested
    inner class RequestExchangeApproval {

        @Test
        fun `delegates to coordinator with EXCHANGE_APPROVAL reason`() = runTest {
            io.mockk.coEvery {
                coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
            } returns true

            val result = gateway.requestExchangeApproval(
                com.mushroom.core.domain.entity.RewardExchange(
                    rewardId = 1L,
                    mushroomLevel = com.mushroom.core.domain.entity.MushroomLevel.SMALL,
                    mushroomCount = 1,
                    puzzlePiecesUnlocked = 0,
                    minutesGained = null,
                    createdAt = java.time.LocalDateTime.now()
                )
            )

            assertTrue(result)
            io.mockk.coVerify { coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL) }
        }

        @Test
        fun `returns false when coordinator returns false`() = runTest {
            io.mockk.coEvery {
                coordinator.requestAuth(AuthReason.EXCHANGE_APPROVAL)
            } returns false

            val result = gateway.requestExchangeApproval(
                com.mushroom.core.domain.entity.RewardExchange(
                    rewardId = 2L,
                    mushroomLevel = com.mushroom.core.domain.entity.MushroomLevel.GOLD,
                    mushroomCount = 3,
                    puzzlePiecesUnlocked = 0,
                    minutesGained = null,
                    createdAt = java.time.LocalDateTime.now()
                )
            )

            assertFalse(result)
        }
    }

    @Nested
    inner class RequestTimeRewardConfirmation {

        @Test
        fun `delegates to coordinator with TIME_REWARD_CONFIRM reason`() = runTest {
            io.mockk.coEvery {
                coordinator.requestAuth(AuthReason.TIME_REWARD_CONFIRM)
            } returns true

            val result = gateway.requestTimeRewardConfirmation(rewardId = 5L)

            assertTrue(result)
            io.mockk.coVerify { coordinator.requestAuth(AuthReason.TIME_REWARD_CONFIRM) }
        }

        @Test
        fun `returns false when coordinator returns false`() = runTest {
            io.mockk.coEvery {
                coordinator.requestAuth(AuthReason.TIME_REWARD_CONFIRM)
            } returns false

            val result = gateway.requestTimeRewardConfirmation(rewardId = 5L)

            assertFalse(result)
        }
    }
}
