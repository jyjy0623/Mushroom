package com.mushroom.feature.account.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.core.network.repository.AuthRepository
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.feature.account.util.ImageCompressor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "ProfileVM"

data class ProfileUiState(
    val isLoading: Boolean = true,
    val phone: String = "",
    val nickname: String = "",
    val avatarUrl: String = "",
    val editingNickname: String = "",
    val isEditing: Boolean = false,
    val isSaving: Boolean = false,
    val isUploadingAvatar: Boolean = false,
    val error: String? = null
)

sealed class ProfileEvent {
    object LogoutSuccess : ProfileEvent()
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    private val _event = MutableSharedFlow<ProfileEvent>()
    val event: SharedFlow<ProfileEvent> = _event.asSharedFlow()

    init {
        loadProfile()
    }

    fun loadProfile() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.fetchProfile()
                .onSuccess { profile ->
                    val localAvatar = authRepository.getLocalAvatarPath()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            phone = profile.phone,
                            nickname = profile.nickname,
                            avatarUrl = localAvatar ?: profile.avatarUrl,
                            editingNickname = profile.nickname
                        )
                    }
                }
                .onFailure { e ->
                    // 网络失败时，按优先级回退：内存缓存 → 持久化数据 → 错误页面
                    val cached = authRepository.currentUser.value
                    val lastPhone = authRepository.getLastPhone()
                    val lastNickname = authRepository.getLastNickname()
                    if (cached != null) {
                        val localAvatar = authRepository.getLocalAvatarPath()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                phone = cached.phone,
                                nickname = cached.nickname,
                                avatarUrl = localAvatar ?: cached.avatarUrl,
                                editingNickname = cached.nickname,
                                error = "网络不可用，显示的是缓存数据"
                            )
                        }
                    } else if (lastPhone != null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                phone = lastPhone,
                                nickname = lastNickname ?: "",
                                editingNickname = lastNickname ?: "",
                                error = "网络不可用，显示的是缓存数据"
                            )
                        }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "加载失败: ${e.message}") }
                    }
                }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true, editingNickname = it.nickname) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, editingNickname = it.nickname, error = null) }
    }

    fun updateNickname(nickname: String) {
        _uiState.update { it.copy(editingNickname = nickname.take(12)) }
    }

    fun saveProfile() {
        val nickname = _uiState.value.editingNickname.trim()
        if (nickname.length < 2) {
            _uiState.update { it.copy(error = "昵称至少2个字符") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            authRepository.updateProfile(nickname = nickname, avatarUrl = null)
                .onSuccess { profile ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isEditing = false,
                            nickname = profile.nickname,
                            editingNickname = profile.nickname
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = "保存失败: ${e.message}") }
                }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            MushroomLogger.i(TAG, "uploadAvatar: start, uri=$uri")
            _uiState.update { it.copy(isUploadingAvatar = true, error = null) }

            val bytes = ImageCompressor.compressAvatar(context, uri)
            if (bytes == null) {
                MushroomLogger.w(TAG, "uploadAvatar: ImageCompressor returned null, uri=$uri")
                _uiState.update { it.copy(isUploadingAvatar = false, error = "图片处理失败") }
                return@launch
            }

            val fileName = "avatar_${System.currentTimeMillis()}.jpg"
            MushroomLogger.i(TAG, "uploadAvatar: compressed, bytes=${bytes.size}, fileName=$fileName")
            authRepository.uploadAvatar(bytes, fileName)
                .onSuccess { profile ->
                    val localAvatar = authRepository.getLocalAvatarPath()
                    MushroomLogger.i(TAG, "uploadAvatar: success, avatarUrl=${profile.avatarUrl}, localAvatar=$localAvatar")
                    _uiState.update {
                        it.copy(isUploadingAvatar = false, avatarUrl = localAvatar ?: profile.avatarUrl)
                    }
                }
                .onFailure { e ->
                    MushroomLogger.e(TAG, "uploadAvatar: failed", e)
                    _uiState.update {
                        it.copy(isUploadingAvatar = false, error = "上传失败: ${e.message}")
                    }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _event.emit(ProfileEvent.LogoutSuccess)
        }
    }
}
