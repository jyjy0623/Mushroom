package com.mushroom.adventure.parent.ui

import androidx.lifecycle.ViewModel
import com.mushroom.adventure.parent.PinRepository
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject

private const val TAG = "PinSetupViewModel"

sealed class PinSetupEvent {
    object Success : PinSetupEvent()
    data class Error(val message: String) : PinSetupEvent()
}

@HiltViewModel
class PinSetupViewModel @Inject constructor(
    private val pinRepository: PinRepository
) : ViewModel() {

    val isPinAlreadySet: Boolean get() = pinRepository.isPinSet()

    private val _event = MutableSharedFlow<PinSetupEvent>(extraBufferCapacity = 1)
    val event: SharedFlow<PinSetupEvent> = _event.asSharedFlow()

    fun savePin(newPin: String, confirmPin: String) {
        if (newPin.length < 4) {
            _event.tryEmit(PinSetupEvent.Error("PIN 码至少需要 4 位"))
            return
        }
        if (newPin != confirmPin) {
            _event.tryEmit(PinSetupEvent.Error("两次输入的 PIN 码不一致"))
            return
        }
        pinRepository.setPin(newPin)
        MushroomLogger.i(TAG, "Parent PIN saved (length=${newPin.length})")
        _event.tryEmit(PinSetupEvent.Success)
    }
}
