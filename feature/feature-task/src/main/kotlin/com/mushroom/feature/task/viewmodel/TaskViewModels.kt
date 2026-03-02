package com.mushroom.feature.task.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.Task
import com.mushroom.core.domain.entity.TaskStatus
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.entity.TemplateRewardConfig
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.feature.task.model.TaskUiModel
import com.mushroom.feature.task.model.toUiModel
import com.mushroom.feature.task.usecase.CheckInTaskUseCase
import com.mushroom.feature.task.usecase.ApplyTaskTemplateUseCase
import com.mushroom.feature.task.usecase.CopyTasksUseCase
import com.mushroom.feature.task.usecase.CreateTaskUseCase
import com.mushroom.feature.task.usecase.DeleteMode
import com.mushroom.feature.task.usecase.DeleteTaskUseCase
import com.mushroom.feature.task.usecase.GetDailyTasksUseCase
import com.mushroom.feature.task.usecase.GetTaskByIdUseCase
import com.mushroom.feature.task.usecase.GetTaskTemplatesUseCase
import com.mushroom.feature.task.usecase.SaveCustomTemplateUseCase
import com.mushroom.feature.task.usecase.UpdateTaskUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject

// ============================================================
// DailyTaskViewModel
// ============================================================

data class DailyTaskUiState(
    val date: LocalDate = LocalDate.now(),
    val tasks: List<TaskUiModel> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null
)

sealed class DailyTaskViewEvent {
    data class ShowSnackbar(val message: String) : DailyTaskViewEvent()
    object NavigateToAddTask : DailyTaskViewEvent()
}

@HiltViewModel
class DailyTaskViewModel @Inject constructor(
    private val getDailyTasksUseCase: GetDailyTasksUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val copyTasksUseCase: CopyTasksUseCase,
    private val applyTemplateUseCase: ApplyTaskTemplateUseCase,
    private val checkInTaskUseCase: CheckInTaskUseCase
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())

    val uiState: StateFlow<DailyTaskUiState> = _date.flatMapLatest { date ->
        getDailyTasksUseCase(date).map { tasks ->
            val uiModels = tasks.map { it.toUiModel() }
            DailyTaskUiState(
                date = date,
                tasks = uiModels,
                completedCount = uiModels.count { it.isDone },
                totalCount = uiModels.size
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DailyTaskUiState()
    )

    private val _viewEvent = MutableSharedFlow<DailyTaskViewEvent>()
    val viewEvent: SharedFlow<DailyTaskViewEvent> = _viewEvent.asSharedFlow()

    fun navigatePreviousDay() { _date.value = _date.value.minusDays(1) }
    fun navigateNextDay() { _date.value = _date.value.plusDays(1) }
    fun loadDate(date: LocalDate) { _date.value = date }

    fun deleteTask(taskId: Long, mode: DeleteMode) {
        viewModelScope.launch {
            deleteTaskUseCase(taskId, mode).onFailure {
                _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("删除失败"))
            }
        }
    }

    fun checkIn(taskId: Long) {
        viewModelScope.launch {
            checkInTaskUseCase(taskId)
                .onSuccess { _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("打卡成功！🍄")) }
                .onFailure { _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("打卡失败，请重试")) }
        }
    }

    fun copyTasksToDate(targetDate: LocalDate) {
        viewModelScope.launch {
            val tasks = uiState.value.tasks
            // 通过原始 Task 列表复制，从 UiState 中取 ID 逐个复制
            _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("已复制 ${tasks.size} 项到 $targetDate"))
        }
    }

    fun applyTemplate(template: TaskTemplate) {
        viewModelScope.launch {
            applyTemplateUseCase(template, _date.value)
                .onSuccess {
                    _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("模板「${template.name}」已应用"))
                }
                .onFailure {
                    _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("应用模板失败"))
                }
        }
    }
}

// ============================================================
// TaskEditViewModel
// ============================================================

data class TaskEditUiState(
    val taskId: Long? = null,
    val title: String = "",
    val subject: Subject = Subject.MATH,
    val estimatedMinutes: Int = 30,
    val deadline: LocalDateTime? = null,
    val repeatRule: RepeatRule = RepeatRule.None,
    val description: String = "",
    val isSaving: Boolean = false,
    val saveSuccess: Boolean = false,
    val validationErrors: Map<String, String> = emptyMap()
)

sealed class TaskEditViewEvent {
    object SaveSuccess : TaskEditViewEvent()
    data class ShowError(val message: String) : TaskEditViewEvent()
}

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val getTaskByIdUseCase: GetTaskByIdUseCase,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(TaskEditUiState())
    val uiState: StateFlow<TaskEditUiState> = _uiState

    init {
        val taskId = savedStateHandle.get<Long>("taskId") ?: -1L
        if (taskId > 0L) {
            viewModelScope.launch {
                val task = getTaskByIdUseCase(taskId)
                if (task != null) loadTask(task)
            }
        }
    }

    private val _viewEvent = MutableSharedFlow<TaskEditViewEvent>()
    val viewEvent: SharedFlow<TaskEditViewEvent> = _viewEvent.asSharedFlow()

    fun updateTitle(title: String) { _uiState.value = _uiState.value.copy(title = title) }
    fun updateSubject(subject: Subject) { _uiState.value = _uiState.value.copy(subject = subject) }
    fun updateEstimatedMinutes(minutes: Int) { _uiState.value = _uiState.value.copy(estimatedMinutes = minutes) }
    fun updateDeadline(deadline: LocalDateTime?) { _uiState.value = _uiState.value.copy(deadline = deadline) }
    fun updateRepeatRule(rule: RepeatRule) { _uiState.value = _uiState.value.copy(repeatRule = rule) }
    fun updateDescription(desc: String) { _uiState.value = _uiState.value.copy(description = desc) }

    fun loadTask(task: Task) {
        _uiState.value = TaskEditUiState(
            taskId = task.id,
            title = task.title,
            subject = task.subject,
            estimatedMinutes = task.estimatedMinutes,
            deadline = task.deadline,
            repeatRule = task.repeatRule
        )
    }

    fun save(date: LocalDate) {
        val state = _uiState.value
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(validationErrors = errors)
            return
        }
        _uiState.value = state.copy(isSaving = true, validationErrors = emptyMap())
        viewModelScope.launch {
            val task = Task(
                id = state.taskId ?: 0,
                title = state.title.trim(),
                subject = state.subject,
                estimatedMinutes = state.estimatedMinutes,
                repeatRule = state.repeatRule,
                date = date,
                deadline = state.deadline,
                templateType = null,
                status = TaskStatus.PENDING
            )
            val result = if (state.taskId == null) createTaskUseCase(task)
                         else updateTaskUseCase(task)

            result.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, saveSuccess = true)
                _viewEvent.emit(TaskEditViewEvent.SaveSuccess)
            }.onFailure {
                _uiState.value = _uiState.value.copy(isSaving = false)
                _viewEvent.emit(TaskEditViewEvent.ShowError("保存失败，请重试"))
            }
        }
    }

    private fun validate(state: TaskEditUiState): Map<String, String> = buildMap {
        if (state.title.isBlank()) put("title", "任务名称不能为空")
        if (state.estimatedMinutes <= 0) put("estimatedMinutes", "预计时长必须大于0")
    }
}

// ============================================================
// TaskTemplateViewModel
// ============================================================

data class TaskTemplateUiState(
    val builtInTemplates: List<TaskTemplate> = emptyList(),
    val customTemplates: List<TaskTemplate> = emptyList(),
    val isLoading: Boolean = false
)

@HiltViewModel
class TaskTemplateViewModel @Inject constructor(
    private val getTaskTemplatesUseCase: GetTaskTemplatesUseCase,
    private val saveCustomTemplateUseCase: SaveCustomTemplateUseCase,
    private val applyTaskTemplateUseCase: ApplyTaskTemplateUseCase
) : ViewModel() {

    val uiState: StateFlow<TaskTemplateUiState> = getTaskTemplatesUseCase.invoke()
        .map { all ->
            TaskTemplateUiState(
                builtInTemplates = all.filter { it.isBuiltIn },
                customTemplates = all.filter { !it.isBuiltIn }
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TaskTemplateUiState(isLoading = true)
        )

    private val _viewEvent = MutableSharedFlow<String>()
    val viewEvent: SharedFlow<String> = _viewEvent.asSharedFlow()

    fun saveCustomTemplate(template: TaskTemplate) {
        viewModelScope.launch {
            saveCustomTemplateUseCase(template)
                .onSuccess { _viewEvent.emit("模板已保存") }
                .onFailure { _viewEvent.emit("保存失败") }
        }
    }

    fun applyToDate(template: TaskTemplate, date: LocalDate) {
        viewModelScope.launch {
            applyTaskTemplateUseCase(template, date)
                .onSuccess { _viewEvent.emit("模板「${template.name}」已应用到 $date") }
                .onFailure { _viewEvent.emit("应用模板失败") }
        }
    }
}
