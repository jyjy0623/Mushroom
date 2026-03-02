package com.mushroom.adventure.parent

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates the parent PIN verification flow between the domain layer
 * (ParentGatewayImpl, which suspends) and the UI layer (MainActivity,
 * which observes and shows the PIN dialog).
 */
@Singleton
class ParentAuthCoordinator @Inject constructor() {

    private val _pendingRequest = MutableStateFlow<PendingAuthRequest?>(null)
    val pendingRequest: StateFlow<PendingAuthRequest?> = _pendingRequest.asStateFlow()

    /**
     * Suspends until the user completes PIN entry.
     * Returns true if verified, false if cancelled/wrong.
     */
    suspend fun requestAuth(reason: AuthReason): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        _pendingRequest.value = PendingAuthRequest(reason = reason, deferred = deferred)
        return try {
            deferred.await()
        } finally {
            _pendingRequest.value = null
        }
    }

    /** Called by the PIN dialog UI when the user submits a PIN. */
    fun submitPin(pin: String, pinRepository: PinRepository) {
        val request = _pendingRequest.value ?: return
        val result = pinRepository.verifyPin(pin)
        request.deferred.complete(result)
    }

    /** Called by the PIN dialog UI when the user dismisses / cancels. */
    fun cancelAuth() {
        val request = _pendingRequest.value ?: return
        request.deferred.complete(false)
    }
}

enum class AuthReason {
    EXCHANGE_APPROVAL,
    TIME_REWARD_CONFIRM,
    SCORE_ENTRY,
    CREATE_MILESTONE,
    SETTINGS
}

data class PendingAuthRequest(
    val reason: AuthReason,
    val deferred: CompletableDeferred<Boolean>
)
