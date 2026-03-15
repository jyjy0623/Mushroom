package com.mushroom.feature.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.core.network.repository.FriendRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FriendViewModel @Inject constructor(
    private val friendRepo: FriendRepository
) : ViewModel() {

    private val _friendsState = MutableStateFlow(FriendsState())
    val friendsState: StateFlow<FriendsState> = _friendsState.asStateFlow()

    private val _friendStats = MutableStateFlow(FriendStatsState())
    val friendStats: StateFlow<FriendStatsState> = _friendStats.asStateFlow()

    init {
        loadFriends()
    }

    fun loadFriends() {
        viewModelScope.launch {
            _friendsState.update { it.copy(isLoading = true, error = null) }
            friendRepo.getFriendList()
                .onSuccess { list ->
                    _friendsState.update {
                        it.copy(isLoading = false, friends = list.friends, error = null)
                    }
                }
                .onFailure { e ->
                    _friendsState.update {
                        it.copy(isLoading = false, error = "加载失败: ${e.message}")
                    }
                }
        }
    }

    fun addFriend(phone: String) {
        viewModelScope.launch {
            _friendsState.update { it.copy(addResult = null) }
            friendRepo.addFriend(phone)
                .onSuccess { response ->
                    _friendsState.update { it.copy(addResult = response.message) }
                    if (response.success) loadFriends()
                }
                .onFailure { e ->
                    _friendsState.update { it.copy(addResult = "操作失败: ${e.message}") }
                }
        }
    }

    fun removeFriend(userId: Int) {
        viewModelScope.launch {
            friendRepo.removeFriend(userId)
                .onSuccess { loadFriends() }
                .onFailure { e ->
                    _friendsState.update { it.copy(error = "删除失败: ${e.message}") }
                }
        }
    }

    fun clearAddResult() {
        _friendsState.update { it.copy(addResult = null) }
    }

    fun loadFriendStats(userId: Int) {
        viewModelScope.launch {
            _friendStats.update { FriendStatsState(isLoading = true) }
            friendRepo.getFriendStats(userId)
                .onSuccess { stats ->
                    _friendStats.update { FriendStatsState(stats = stats) }
                }
                .onFailure { e ->
                    _friendStats.update { FriendStatsState(error = "加载失败: ${e.message}") }
                }
        }
    }
}
