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
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.feature.reward.usecase.ClaimRewardUseCase
import com.mushroom.feature.reward.usecase.CreateRewardUseCase
import com.mushroom.feature.reward.usecase.DeleteRewardUseCase
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mushroom.core.ui.R as CoreUiR
import com.mushroom.feature.reward.usecase.ExchangeMushroomsUseCase
import com.mushroom.core.domain.entity.RewardStatus
import com.mushroom.feature.reward.usecase.GetAllNonArchivedRewardsUseCase
import com.mushroom.feature.reward.usecase.GetExchangeCountUseCase
import com.mushroom.feature.reward.usecase.GetPuzzleProgressUseCase
import com.mushroom.feature.reward.usecase.GetTimeRewardBalanceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import java.time.LocalDate

// -----------------------------------------------------------------------
// RewardListViewModel
// -----------------------------------------------------------------------

data class RewardUiModel(
    val reward: Reward,
    val puzzleProgress: PuzzleProgress? = null,
    val timeBalance: TimeRewardBalance? = null
)

data class RewardListUiState(
    val activeRewards: List<RewardUiModel> = emptyList(),
    val completedRewards: List<RewardUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val pendingDeleteRewardId: Long? = null
)

sealed class RewardListViewEvent {
    data class ShowSnackbar(val message: String) : RewardListViewEvent()
}

@HiltViewModel
class RewardListViewModel @Inject constructor(
    getAllNonArchived: GetAllNonArchivedRewardsUseCase,
    private val getPuzzleProgressUseCase: GetPuzzleProgressUseCase,
    private val getTimeRewardBalanceUseCase: GetTimeRewardBalanceUseCase,
    private val getExchangeCountUseCase: GetExchangeCountUseCase,
    private val deleteRewardUseCase: DeleteRewardUseCase,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _viewEvent = MutableSharedFlow<RewardListViewEvent>()
    val viewEvent: SharedFlow<RewardListViewEvent> = _viewEvent.asSharedFlow()

    private val _pendingDeleteRewardId = MutableStateFlow<Long?>(null)

    val uiState: StateFlow<RewardListUiState> = combine(
        getAllNonArchived()
            .flatMapLatest { rewards ->
                if (rewards.isEmpty()) return@flatMapLatest flowOf(RewardListUiState())

                val perRewardFlows = rewards.map { reward ->
                    when (reward.type) {
                        RewardType.PHYSICAL ->
                            getPuzzleProgressUseCase(reward.id).map { progress ->
                                RewardUiModel(reward = reward, puzzleProgress = progress)
                            }
                        RewardType.TIME_BASED ->
                            getExchangeCountUseCase(reward.id).map { count ->
                                val balance = getTimeRewardBalanceUseCase(reward.id)
                                val effectiveBalance = balance ?: TimeRewardBalance(
                                    rewardId = reward.id,
                                    periodStart = LocalDate.MIN,
                                    maxTimes = null,
                                    usedTimes = count
                                )
                                RewardUiModel(reward = reward, timeBalance = effectiveBalance)
                            }
                    }
                }
                combine(perRewardFlows) { models ->
                    val active = models.filter { it.reward.status == RewardStatus.ACTIVE }
                    val completed = models.filter {
                        it.reward.status == RewardStatus.COMPLETED || it.reward.status == RewardStatus.CLAIMED
                    }
                    RewardListUiState(activeRewards = active, completedRewards = completed)
                }
            },
        _pendingDeleteRewardId
    ) { state, pendingId ->
        state.copy(pendingDeleteRewardId = pendingId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RewardListUiState(isLoading = true))

    fun showDeleteConfirmation(rewardId: Long) {
        _pendingDeleteRewardId.value = rewardId
    }

    fun dismissDeleteConfirmation() {
        _pendingDeleteRewardId.value = null
    }

    fun confirmDelete() {
        val rewardId = _pendingDeleteRewardId.value ?: return
        _pendingDeleteRewardId.value = null
        viewModelScope.launch {
            deleteRewardUseCase(rewardId)
                .onSuccess {
                    _viewEvent.emit(RewardListViewEvent.ShowSnackbar(appContext.getString(CoreUiR.string.reward_deleted_refunded)))
                }
                .onFailure { e ->
                    _viewEvent.emit(RewardListViewEvent.ShowSnackbar(e.message ?: "删除失败"))
                }
        }
    }
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
    private val getAllNonArchivedRewardsUseCase: GetAllNonArchivedRewardsUseCase,
    private val mushroomRepo: MushroomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RewardDetailUiState())
    val uiState: StateFlow<RewardDetailUiState> = _uiState

    private val _viewEvent = MutableSharedFlow<RewardDetailViewEvent>()
    val viewEvent: SharedFlow<RewardDetailViewEvent> = _viewEvent.asSharedFlow()

    fun loadReward(rewardId: Long) {
        viewModelScope.launch {
            getAllNonArchivedRewardsUseCase().collect { rewards ->
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
        viewModelScope.launch {
            val mushroomBalance = mushroomRepo.getBalance().first()
            _uiState.update { it.copy(currentBalance = mushroomBalance) }
        }
    }

    fun exchange(rewardId: Long, level: MushroomLevel, amount: Int) {
        _uiState.update { it.copy(isExchanging = true, error = null) }
        viewModelScope.launch {
            val result = exchangeMushroomsUseCase(rewardId, level, amount)
            result.onSuccess { progress ->
                _uiState.update { it.copy(isExchanging = false, puzzleProgress = progress) }
                refreshTimeBalance(rewardId)
                refreshMushroomBalance()
                _viewEvent.emit(RewardDetailViewEvent.ExchangeSuccess)
            }.onFailure { e ->
                _uiState.update { it.copy(isExchanging = false, error = e.message) }
                _viewEvent.emit(RewardDetailViewEvent.ShowSnackbar(e.message ?: "兑换失败"))
            }
        }
    }

    private suspend fun refreshTimeBalance(rewardId: Long) {
        val balance = getTimeRewardBalanceUseCase(rewardId)
        _uiState.update { it.copy(timeBalance = balance) }
    }

    private suspend fun refreshMushroomBalance() {
        val balance = mushroomRepo.getBalance().first()
        _uiState.update { it.copy(currentBalance = balance) }
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
    val imageUri: String = "",
    // PHYSICAL fields
    val puzzlePiecesText: String = "20",
    val requiredPointsText: String = "100",
    // TIME_BASED fields
    val unitMinutesText: String = "30",
    val costPointsText: String = "5",
    val periodType: PeriodType? = null,             // null = 不限次数
    val maxTimesPerPeriodText: String = "",          // 空 = 不限
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
    fun updateImageUri(value: String) = _uiState.update { it.copy(imageUri = value) }
    fun updatePuzzlePiecesText(value: String) = _uiState.update { it.copy(puzzlePiecesText = value) }
    fun updateRequiredPointsText(value: String) = _uiState.update { it.copy(requiredPointsText = value) }
    fun updateUnitMinutesText(value: String) = _uiState.update { it.copy(unitMinutesText = value) }
    fun updateCostPointsText(value: String) = _uiState.update { it.copy(costPointsText = value) }
    fun updatePeriodType(value: PeriodType?) = _uiState.update { it.copy(periodType = value) }
    fun updateMaxTimesPerPeriodText(value: String) = _uiState.update { it.copy(maxTimesPerPeriodText = value) }

    fun save() {
        val state = _uiState.value
        val errors = mutableMapOf<String, String>()

        val puzzlePieces = state.puzzlePiecesText.toIntOrNull() ?: 0
        val requiredPoints = state.requiredPointsText.toIntOrNull() ?: 0
        val unitMinutes = state.unitMinutesText.toIntOrNull() ?: 0
        val costPoints = state.costPointsText.toIntOrNull() ?: 0
        val maxTimesPerPeriod = state.maxTimesPerPeriodText.toIntOrNull()

        if (state.name.isBlank()) errors["name"] = "请输入奖品名称"
        if (state.type == RewardType.PHYSICAL) {
            if (puzzlePieces < 1) errors["puzzlePieces"] = "拼图块数至少为 1"
            if (requiredPoints < 1) errors["requiredPoints"] = "所需积分至少为 1"
        }
        if (state.type == RewardType.TIME_BASED) {
            if (unitMinutes < 1) errors["unitMinutes"] = "每次获得积分至少为 1"
            if (costPoints < 1) errors["costPoints"] = "每次消耗积分至少为 1"
            if (state.periodType != null && state.maxTimesPerPeriodText.isNotBlank() && (maxTimesPerPeriod == null || maxTimesPerPeriod < 1))
                errors["maxTimesPerPeriod"] = "次数上限至少为 1"
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
                imageUri = state.imageUri,
                requiredPoints = if (state.type == RewardType.PHYSICAL) requiredPoints else 0,
                puzzlePieces = if (state.type == RewardType.PHYSICAL) puzzlePieces else 0,
                timeLimitConfig = if (state.type == RewardType.TIME_BASED) {
                    TimeLimitConfig(
                        unitMinutes = unitMinutes,
                        costPoints = costPoints,
                        periodType = state.periodType,
                        maxTimesPerPeriod = if (state.periodType != null && state.maxTimesPerPeriodText.isNotBlank()) maxTimesPerPeriod else null,
                        requireParentConfirm = false
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
