package com.mushroom.feature.reward.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.PuzzleProgress
import com.mushroom.core.domain.entity.Reward
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.core.domain.entity.TimeRewardBalance
import com.mushroom.feature.reward.usecase.ClaimRewardUseCase
import com.mushroom.feature.reward.usecase.ExchangeMushroomsUseCase
import com.mushroom.feature.reward.usecase.GetActiveRewardsUseCase
import com.mushroom.feature.reward.usecase.GetPuzzleProgressUseCase
import com.mushroom.feature.reward.usecase.GetTimeRewardBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// -----------------------------------------------------------------------
// RewardListViewModel
// -----------------------------------------------------------------------

data class RewardUiModel(
    val reward: Reward,
    val puzzleProgress: PuzzleProgress? = null,
    val timeBalance: TimeRewardBalance? = null
)

data class RewardListUiState(
    val rewards: List<RewardUiModel> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class RewardListViewModel @Inject constructor(
    getActiveRewards: GetActiveRewardsUseCase
) : ViewModel() {

    val uiState: StateFlow<RewardListUiState> = getActiveRewards()
        .map { rewards ->
            RewardListUiState(rewards = rewards.map { RewardUiModel(reward = it) })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RewardListUiState(isLoading = true))
}

// -----------------------------------------------------------------------
// RewardDetailViewModel
// -----------------------------------------------------------------------

data class RewardDetailUiState(
    val reward: Reward? = null,
    val puzzleProgress: PuzzleProgress? = null,
    val timeBalance: TimeRewardBalance? = null,
    val currentBalance: MushroomBalance = MushroomBalance(emptyMap()),
    val isExchanging: Boolean = false,
    val celebrationTrigger: Boolean = false,
    val error: String? = null
)

sealed class RewardDetailViewEvent {
    data class ShowSnackbar(val message: String) : RewardDetailViewEvent()
    object ExchangeSuccess : RewardDetailViewEvent()
    object ClaimSuccess : RewardDetailViewEvent()
}

@HiltViewModel
class RewardDetailViewModel @Inject constructor(
    private val getPuzzleProgressUseCase: GetPuzzleProgressUseCase,
    private val getTimeRewardBalanceUseCase: GetTimeRewardBalanceUseCase,
    private val exchangeMushroomsUseCase: ExchangeMushroomsUseCase,
    private val claimRewardUseCase: ClaimRewardUseCase,
    private val getActiveRewardsUseCase: GetActiveRewardsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardDetailUiState())
    val uiState: StateFlow<RewardDetailUiState> = _uiState

    private val _viewEvent = MutableSharedFlow<RewardDetailViewEvent>()
    val viewEvent: SharedFlow<RewardDetailViewEvent> = _viewEvent.asSharedFlow()

    fun loadReward(rewardId: Long) {
        viewModelScope.launch {
            getActiveRewardsUseCase().collect { rewards ->
                val reward = rewards.firstOrNull { it.id == rewardId } ?: return@collect
                _uiState.update { it.copy(reward = reward) }
            }
        }
        viewModelScope.launch {
            getPuzzleProgressUseCase(rewardId).collect { progress ->
                val wasDone = _uiState.value.puzzleProgress?.isCompleted == false
                val isDone = progress.isCompleted
                _uiState.update { state ->
                    state.copy(
                        puzzleProgress = progress,
                        celebrationTrigger = wasDone && isDone
                    )
                }
            }
        }
        viewModelScope.launch {
            val balance = getTimeRewardBalanceUseCase(rewardId)
            _uiState.update { it.copy(timeBalance = balance) }
        }
    }

    fun exchange(rewardId: Long, level: MushroomLevel, amount: Int) {
        _uiState.update { it.copy(isExchanging = true, error = null) }
        viewModelScope.launch {
            val result = exchangeMushroomsUseCase(rewardId, level, amount)
            result.onSuccess { progress ->
                _uiState.update { it.copy(isExchanging = false, puzzleProgress = progress) }
                _viewEvent.emit(RewardDetailViewEvent.ExchangeSuccess)
            }.onFailure { e ->
                _uiState.update { it.copy(isExchanging = false, error = e.message) }
                _viewEvent.emit(RewardDetailViewEvent.ShowSnackbar(e.message ?: "兑换失败"))
            }
        }
    }

    fun claimReward(rewardId: Long) {
        viewModelScope.launch {
            claimRewardUseCase(rewardId)
                .onSuccess { _viewEvent.emit(RewardDetailViewEvent.ClaimSuccess) }
                .onFailure { e ->
                    _viewEvent.emit(RewardDetailViewEvent.ShowSnackbar(e.message ?: "领取失败"))
                }
        }
    }

    fun dismissCelebration() {
        _uiState.update { it.copy(celebrationTrigger = false) }
    }
}
