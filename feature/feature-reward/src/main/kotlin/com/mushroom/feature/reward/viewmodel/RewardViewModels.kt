package com.mushroom.feature.reward.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.MushroomBalance
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.PeriodType
import com.mushroom.core.domain.entity.PuzzleProgress
import com.mushroom.core.domain.entity.Reward
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.core.domain.entity.TimeLimitConfig
import com.mushroom.core.domain.entity.TimeRewardBalance
import com.mushroom.feature.reward.usecase.ClaimRewardUseCase
import com.mushroom.feature.reward.usecase.CreateRewardUseCase
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
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
    getActiveRewards: GetActiveRewardsUseCase,
    private val getPuzzleProgressUseCase: GetPuzzleProgressUseCase,
    private val getTimeRewardBalanceUseCase: GetTimeRewardBalanceUseCase
) : ViewModel() {

    val uiState: StateFlow<RewardListUiState> = getActiveRewards()
        .flatMapLatest { rewards ->
            if (rewards.isEmpty()) return@flatMapLatest flowOf(RewardListUiState())

            // 对每个奖品构建一个携带进度的 Flow
            val perRewardFlows = rewards.map { reward ->
                when (reward.type) {
                    RewardType.PHYSICAL ->
                        getPuzzleProgressUseCase(reward.id).map { progress ->
                            RewardUiModel(reward = reward, puzzleProgress = progress)
                        }
                    RewardType.TIME_BASED ->
                        // suspend → 包成只 emit 一次的 Flow
                        kotlinx.coroutines.flow.flow {
                            val balance = getTimeRewardBalanceUseCase(reward.id)
                            emit(RewardUiModel(reward = reward, timeBalance = balance))
                        }
                }
            }
            combine(perRewardFlows) { models -> RewardListUiState(rewards = models.toList()) }
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

// -----------------------------------------------------------------------
// RewardCreateViewModel
// -----------------------------------------------------------------------

data class RewardCreateUiState(
    val name: String = "",
    val type: RewardType = RewardType.PHYSICAL,
    // PHYSICAL fields
    val puzzlePieces: Int = 9,
    val requiredLevel: MushroomLevel = MushroomLevel.SMALL,
    val requiredAmount: Int = 1,
    // TIME_BASED fields
    val unitMinutes: Int = 30,
    val periodType: PeriodType = PeriodType.WEEKLY,
    val maxMinutesPerPeriod: Int = 120,
    val cooldownDays: Int = 0,
    val requireParentConfirm: Boolean = true,
    // form state
    val isSaving: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap()
)

sealed class RewardCreateViewEvent {
    object SaveSuccess : RewardCreateViewEvent()
    data class ShowError(val message: String) : RewardCreateViewEvent()
}

@HiltViewModel
class RewardCreateViewModel @Inject constructor(
    private val createRewardUseCase: CreateRewardUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardCreateUiState())
    val uiState: StateFlow<RewardCreateUiState> = _uiState

    private val _viewEvent = MutableSharedFlow<RewardCreateViewEvent>()
    val viewEvent: SharedFlow<RewardCreateViewEvent> = _viewEvent.asSharedFlow()

    fun updateName(value: String) = _uiState.update { it.copy(name = value) }
    fun updateType(value: RewardType) = _uiState.update { it.copy(type = value) }
    fun updatePuzzlePieces(value: Int) = _uiState.update { it.copy(puzzlePieces = value) }
    fun updateRequiredLevel(value: MushroomLevel) = _uiState.update { it.copy(requiredLevel = value) }
    fun updateRequiredAmount(value: Int) = _uiState.update { it.copy(requiredAmount = value) }
    fun updateUnitMinutes(value: Int) = _uiState.update { it.copy(unitMinutes = value) }
    fun updatePeriodType(value: PeriodType) = _uiState.update { it.copy(periodType = value) }
    fun updateMaxMinutesPerPeriod(value: Int) = _uiState.update { it.copy(maxMinutesPerPeriod = value) }
    fun updateCooldownDays(value: Int) = _uiState.update { it.copy(cooldownDays = value) }
    fun updateRequireParentConfirm(value: Boolean) = _uiState.update { it.copy(requireParentConfirm = value) }

    fun save() {
        val state = _uiState.value
        val errors = mutableMapOf<String, String>()
        if (state.name.isBlank()) errors["name"] = "请输入奖品名称"
        if (state.type == RewardType.PHYSICAL && state.puzzlePieces < 1) errors["puzzlePieces"] = "拼图块数至少为 1"
        if (state.requiredAmount < 1) errors["requiredAmount"] = "兑换数量至少为 1"
        if (state.type == RewardType.TIME_BASED) {
            if (state.unitMinutes < 1) errors["unitMinutes"] = "每次时长至少为 1 分钟"
            if (state.maxMinutesPerPeriod < state.unitMinutes) errors["maxMinutesPerPeriod"] = "周期上限不能小于单次时长"
        }
        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }

        _uiState.update { it.copy(isSaving = true, validationErrors = emptyMap()) }
        viewModelScope.launch {
            val reward = Reward(
                name = state.name.trim(),
                type = state.type,
                requiredMushrooms = mapOf(state.requiredLevel to state.requiredAmount),
                puzzlePieces = if (state.type == RewardType.PHYSICAL) state.puzzlePieces else 0,
                timeLimitConfig = if (state.type == RewardType.TIME_BASED) {
                    TimeLimitConfig(
                        unitMinutes = state.unitMinutes,
                        periodType = state.periodType,
                        maxMinutesPerPeriod = state.maxMinutesPerPeriod,
                        cooldownDays = state.cooldownDays,
                        requireParentConfirm = state.requireParentConfirm
                    )
                } else null
            )
            createRewardUseCase(reward)
                .onSuccess { _viewEvent.emit(RewardCreateViewEvent.SaveSuccess) }
                .onFailure { e ->
                    _viewEvent.emit(RewardCreateViewEvent.ShowError(e.message ?: "创建失败"))
                }
            _uiState.update { it.copy(isSaving = false) }
        }
    }
}
