package com.mushroom.feature.milestone.viewmodel

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

    val uiState: StateFlow<MilestoneListUiState> = combine(
        getMilestonesUseCase.all(),
        _selectedSubject
    ) { milestones, selectedSubject ->
        val filtered = selectedSubject?.let { subject ->
            milestones.filter { it.subject == subject }
        } ?: milestones
        MilestoneListUiState(
            upcomingMilestones = filtered
                .filter { it.status == MilestoneStatus.PENDING }
                .sortedBy { it.scheduledDate },
            completedMilestones = filtered
                .filter { it.status != MilestoneStatus.PENDING }
                .sortedByDescending { it.scheduledDate },
            selectedSubject = selectedSubject
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MilestoneListUiState(isLoading = true))

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
    val milestoneId: Long? = null,          // null = 新建，非null = 编辑
    val name: String = "",
    val type: MilestoneType = MilestoneType.MINI_TEST,
    val subject: Subject = Subject.MATH,
    val scheduledDate: LocalDate = LocalDate.now(),
    val scoringRules: List<ScoringRule> = DefaultScoringRules.forType(MilestoneType.MINI_TEST),
    val ruleAmountTexts: List<String> = DefaultScoringRules.forType(MilestoneType.MINI_TEST).map { it.rewardConfig.amount.toString() },
    val isUsingDefaultRules: Boolean = true,
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    // 用户是否已手动编辑过名称，true 时不再自动覆盖
    val nameManuallyEdited: Boolean = false
)

sealed class MilestoneEditViewEvent {
    object SaveSuccess : MilestoneEditViewEvent()
    data class ShowError(val message: String) : MilestoneEditViewEvent()
}

@HiltViewModel
class MilestoneEditViewModel @Inject constructor(
    private val createMilestoneUseCase: CreateMilestoneUseCase,
    private val milestoneRepository: com.mushroom.core.domain.repository.MilestoneRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        MilestoneEditUiState().let { initial ->
            initial.copy(name = autoName(initial.subject, initial.type, initial.scheduledDate))
        }
    )
    val uiState: StateFlow<MilestoneEditUiState> = _uiState

    init {
        val milestoneId = savedStateHandle.get<Long>("milestoneId") ?: -1L
        if (milestoneId > 0L) {
            viewModelScope.launch {
                val milestone = milestoneRepository.getMilestoneById(milestoneId)
                if (milestone != null) {
                    _uiState.update { _ ->
                        MilestoneEditUiState(
                            milestoneId = milestone.id,
                            name = milestone.name,
                            type = milestone.type,
                            subject = milestone.subject,
                            scheduledDate = milestone.scheduledDate,
                            scoringRules = milestone.scoringRules,
                            ruleAmountTexts = milestone.scoringRules.map { it.rewardConfig.amount.toString() },
                            nameManuallyEdited = true  // 编辑模式不自动覆盖名称
                        )
                    }
                }
            }
        }
    }

    private val _viewEvent = MutableSharedFlow<MilestoneEditViewEvent>()
    val viewEvent: SharedFlow<MilestoneEditViewEvent> = _viewEvent.asSharedFlow()

    fun updateName(name: String) {
        _uiState.update { it.copy(name = name, nameManuallyEdited = true) }
    }
    fun updateType(type: MilestoneType) {
        _uiState.update { state ->
            val rules = if (state.isUsingDefaultRules) DefaultScoringRules.forType(type)
                        else state.scoringRules
            state.copy(
                type = type,
                scoringRules = rules,
                ruleAmountTexts = rules.map { it.rewardConfig.amount.toString() },
                name = if (state.nameManuallyEdited) state.name
                       else autoName(state.subject, type, state.scheduledDate)
            )
        }
    }
    fun updateSubject(subject: Subject) {
        _uiState.update { state ->
            state.copy(
                subject = subject,
                name = if (state.nameManuallyEdited) state.name
                       else autoName(subject, state.type, state.scheduledDate)
            )
        }
    }
    fun updateScheduledDate(date: LocalDate) {
        _uiState.update { state ->
            state.copy(
                scheduledDate = date,
                name = if (state.nameManuallyEdited) state.name
                       else autoName(state.subject, state.type, date)
            )
        }
    }
    fun applyDefaultRules() {
        _uiState.update { state ->
            val rules = DefaultScoringRules.forType(state.type)
            state.copy(
                scoringRules = rules,
                ruleAmountTexts = rules.map { it.rewardConfig.amount.toString() },
                isUsingDefaultRules = true
            )
        }
    }

    /** 修改某档分数段的奖励数量（index 对应 scoringRules 列表下标） */
    fun updateRuleAmount(index: Int, amountText: String) {
        _uiState.update { state ->
            // 先更新原始文本，保证空字符串可以显示（退格不卡在"1"）
            val updatedTexts = state.ruleAmountTexts.toMutableList()
            if (index in updatedTexts.indices) updatedTexts[index] = amountText

            // 只在能解析为合法整数时同步更新 scoringRules
            val amount = amountText.toIntOrNull()?.coerceAtLeast(0)
            val updatedRules = if (amount != null) {
                state.scoringRules.toMutableList().also { rules ->
                    if (index in rules.indices) {
                        rules[index] = rules[index].copy(
                            rewardConfig = rules[index].rewardConfig.copy(amount = amount)
                        )
                    }
                }
            } else {
                state.scoringRules
            }

            state.copy(
                scoringRules = updatedRules,
                ruleAmountTexts = updatedTexts,
                isUsingDefaultRules = false
            )
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
                id = state.milestoneId ?: 0L,
                name = state.name.trim(),
                type = state.type,
                subject = state.subject,
                scheduledDate = state.scheduledDate,
                scoringRules = state.scoringRules,
                actualScore = null,
                status = MilestoneStatus.PENDING
            )
            val result = if (state.milestoneId == null) {
                createMilestoneUseCase(milestone).map { }
            } else {
                runCatching { milestoneRepository.updateMilestone(milestone) }
            }
            result.onSuccess {
                _uiState.update { it.copy(isSaving = false, saveSuccess = true) }
                _viewEvent.emit(MilestoneEditViewEvent.SaveSuccess)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false) }
                _viewEvent.emit(MilestoneEditViewEvent.ShowError(e.message ?: "保存失败"))
            }
        }
    }
}

private val DATE_LABEL_FMT = DateTimeFormatter.ofPattern("M月d日")

private fun autoName(subject: Subject, type: MilestoneType, date: LocalDate): String {
    val subjectStr = when (subject) {
        Subject.MATH      -> "数学"
        Subject.CHINESE   -> "语文"
        Subject.ENGLISH   -> "英语"
        Subject.PHYSICS   -> "物理"
        Subject.CHEMISTRY -> "化学"
        Subject.BIOLOGY   -> "生物"
        Subject.HISTORY   -> "历史"
        Subject.GEOGRAPHY -> "地理"
        Subject.OTHER     -> "其他"
    }
    val typeStr = when (type) {
        MilestoneType.MINI_TEST   -> "小测"
        MilestoneType.WEEKLY_TEST -> "周测"
        MilestoneType.SCHOOL_EXAM -> "校测"
        MilestoneType.MIDTERM     -> "期中"
        MilestoneType.FINAL       -> "期末"
    }
    return "$subjectStr$typeStr ${date.format(DATE_LABEL_FMT)}"
}
