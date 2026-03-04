package com.mushroom.feature.milestone.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.milestone.usecase.CreateMilestoneUseCase
import com.mushroom.feature.milestone.usecase.DefaultScoringRules
import com.mushroom.feature.milestone.usecase.GetMilestonesUseCase
import com.mushroom.feature.milestone.usecase.RecordMilestoneScoreUseCase
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
import java.time.LocalDate
import javax.inject.Inject

// -----------------------------------------------------------------------
// MilestoneListViewModel
// -----------------------------------------------------------------------

data class MilestoneListUiState(
    val upcomingMilestones: List<Milestone> = emptyList(),
    val completedMilestones: List<Milestone> = emptyList(),
    val selectedSubject: Subject? = null,
    val isLoading: Boolean = false
)

sealed class MilestoneListViewEvent {
    data class ShowSnackbar(val message: String) : MilestoneListViewEvent()
}

@HiltViewModel
class MilestoneListViewModel @Inject constructor(
    private val getMilestonesUseCase: GetMilestonesUseCase,
    private val recordScoreUseCase: RecordMilestoneScoreUseCase
) : ViewModel() {

    private val _selectedSubject = MutableStateFlow<Subject?>(null)

    val uiState: StateFlow<MilestoneListUiState> = getMilestonesUseCase.all()
        .map { milestones ->
            val filtered = _selectedSubject.value?.let { subject ->
                milestones.filter { it.subject == subject }
            } ?: milestones
            MilestoneListUiState(
                upcomingMilestones = filtered.filter { it.status == MilestoneStatus.PENDING },
                completedMilestones = filtered.filter { it.status != MilestoneStatus.PENDING },
                selectedSubject = _selectedSubject.value
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MilestoneListUiState(isLoading = true))

    private val _viewEvent = MutableSharedFlow<MilestoneListViewEvent>()
    val viewEvent: SharedFlow<MilestoneListViewEvent> = _viewEvent.asSharedFlow()

    fun selectSubject(subject: Subject?) {
        _selectedSubject.value = subject
    }

    fun recordScore(milestoneId: Long, score: Int) {
        viewModelScope.launch {
            recordScoreUseCase(milestoneId, score)
                .onSuccess {
                    _viewEvent.emit(MilestoneListViewEvent.ShowSnackbar("成绩已录入，获得蘑菇奖励！"))
                }
                .onFailure { e ->
                    _viewEvent.emit(MilestoneListViewEvent.ShowSnackbar(e.message ?: "录入失败"))
                }
        }
    }
}

// -----------------------------------------------------------------------
// MilestoneEditViewModel
// -----------------------------------------------------------------------

data class MilestoneEditUiState(
    val name: String = "",
    val type: MilestoneType = MilestoneType.MINI_TEST,
    val subject: Subject = Subject.MATH,
    val scheduledDate: LocalDate = LocalDate.now(),
    val scoringRules: List<ScoringRule> = DefaultScoringRules.forType(MilestoneType.MINI_TEST),
    val isUsingDefaultRules: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false
)

sealed class MilestoneEditViewEvent {
    object SaveSuccess : MilestoneEditViewEvent()
    data class ShowError(val message: String) : MilestoneEditViewEvent()
}

@HiltViewModel
class MilestoneEditViewModel @Inject constructor(
    private val createMilestoneUseCase: CreateMilestoneUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(MilestoneEditUiState())
    val uiState: StateFlow<MilestoneEditUiState> = _uiState

    private val _viewEvent = MutableSharedFlow<MilestoneEditViewEvent>()
    val viewEvent: SharedFlow<MilestoneEditViewEvent> = _viewEvent.asSharedFlow()

    fun updateName(name: String) { _uiState.update { it.copy(name = name) } }
    fun updateType(type: MilestoneType) {
        _uiState.update { state ->
            val rules = if (state.isUsingDefaultRules) DefaultScoringRules.forType(type)
                        else state.scoringRules
            state.copy(type = type, scoringRules = rules)
        }
    }
    fun updateSubject(subject: Subject) { _uiState.update { it.copy(subject = subject) } }
    fun updateScheduledDate(date: LocalDate) { _uiState.update { it.copy(scheduledDate = date) } }
    fun applyDefaultRules() {
        _uiState.update { state ->
            state.copy(
                scoringRules = DefaultScoringRules.forType(state.type),
                isUsingDefaultRules = true
            )
        }
    }

    /** 修改某档分数段的奖励数量（index 对应 scoringRules 列表下标） */
    fun updateRuleAmount(index: Int, amountText: String) {
        val amount = amountText.toIntOrNull()?.coerceAtLeast(0) ?: return
        _uiState.update { state ->
            val updated = state.scoringRules.toMutableList()
            if (index in updated.indices) {
                updated[index] = updated[index].copy(
                    rewardConfig = updated[index].rewardConfig.copy(amount = amount)
                )
            }
            state.copy(scoringRules = updated, isUsingDefaultRules = false)
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            viewModelScope.launch {
                _viewEvent.emit(MilestoneEditViewEvent.ShowError("名称不能为空"))
            }
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val milestone = Milestone(
                name = state.name.trim(),
                type = state.type,
                subject = state.subject,
                scheduledDate = state.scheduledDate,
                scoringRules = state.scoringRules,
                actualScore = null,
                status = MilestoneStatus.PENDING
            )
            createMilestoneUseCase(milestone)
                .onSuccess {
                    _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                    _viewEvent.emit(MilestoneEditViewEvent.SaveSuccess)
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false) }
                    _viewEvent.emit(MilestoneEditViewEvent.ShowError(e.message ?: "保存失败"))
                }
        }
    }
}
