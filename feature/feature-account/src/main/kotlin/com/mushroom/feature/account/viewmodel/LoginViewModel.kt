package com.mushroom.feature.account.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.core.network.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LoginUiState(
    val phone: String = "",
    val code: String = "",
    val nickname: String = "",
    val isSendingCode: Boolean = false,
    val isLoggingIn: Boolean = false,
    val codeSent: Boolean = false,
    val cooldownSeconds: Int = 0,
    val error: String? = null
)

sealed class LoginEvent {
    object LoginSuccess : LoginEvent()
}

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<LoginEvent>()
    val event: SharedFlow<LoginEvent> = _event.asSharedFlow()

    fun updatePhone(phone: String) {
        val filtered = phone.filter { it.isDigit() }.take(11)
        _uiState.update { it.copy(phone = filtered, error = null) }
    }

    fun updateCode(code: String) {
        val filtered = code.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(code = filtered, error = null) }
    }

    fun updateNickname(nickname: String) {
        val trimmed = nickname.take(16)
        _uiState.update { it.copy(nickname = trimmed, error = null) }
    }

    fun sendCode() {
        val phone = _uiState.value.phone
        if (phone.length != 11) {
            _uiState.update { it.copy(error = "请输入11位手机号") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSendingCode = true, error = null) }
            authRepository.sendCode(phone)
                .onSuccess {
                    _uiState.update { it.copy(isSendingCode = false, codeSent = true, cooldownSeconds = 60) }
                    startCooldown()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSendingCode = false, error = "发送失败: ${e.message}") }
                }
        }
    }

    fun login() {
        val state = _uiState.value
        if (state.code.length != 6) {
            _uiState.update { it.copy(error = "请输入6位验证码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, error = null) }
            val nickname = state.nickname.takeIf { it.isNotBlank() }
            authRepository.login(state.phone, state.code, nickname)
                .onSuccess {
                    _uiState.update { it.copy(isLoggingIn = false) }
                    _event.emit(LoginEvent.LoginSuccess)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoggingIn = false, error = "登录失败: ${e.message}") }
                }
        }
    }

    private fun startCooldown() {
        viewModelScope.launch {
            var seconds = 60
            while (seconds > 0) {
                _uiState.update { it.copy(cooldownSeconds = seconds) }
                delay(1000)
                seconds--
            }
            _uiState.update { it.copy(cooldownSeconds = 0) }
        }
    }
}
