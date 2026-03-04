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
import com.mushroom.core.domain.entity.BonusCondition
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.TemplateRewardConfig
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
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    val domainTasks: List<Task> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val isLoading: Boolean = false,
    val error: String? = null,
    // true 表示今天已展示过"全部完成"横幅，UI 不再重复展示
    val celebrationShown: Boolean = false,
    // 连续打卡天数（用于今日进度卡展示里程碑提示）
    val currentStreak: Int = 0,
    // 连续完成备忘录任务天数（用于今日进度卡提示）
    val memoStreak: Int = 0
)

sealed class DailyTaskViewEvent {
    data class ShowSnackbar(val message: String) : DailyTaskViewEvent()
    data class ShowRewardDialog(val rewardSummary: String) : DailyTaskViewEvent()
    object NavigateToAddTask : DailyTaskViewEvent()
}

@HiltViewModel
class DailyTaskViewModel @Inject constructor(
    private val getDailyTasksUseCase: GetDailyTasksUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val copyTasksUseCase: CopyTasksUseCase,
    private val applyTemplateUseCase: ApplyTaskTemplateUseCase,
    private val checkInTaskUseCase: CheckInTaskUseCase,
    private val checkInRepo: CheckInRepository,
    private val taskRepo: TaskRepository
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    // 记录已展示过"全部完成"横幅的日期，避免重入页面重复显示
    private val celebrationShownDates = mutableSetOf<LocalDate>()
    // 连续打卡天数，初始0，每次打卡后刷新
    private val _currentStreak = MutableStateFlow(0)
    // 连续备忘录任务完成天数
    private val _memoStreak = MutableStateFlow(0)

    init {
        // 初始化时加载连续打卡天数和备忘录连续天数
        viewModelScope.launch {
            _currentStreak.value = checkInRepo.getStreakCount(LocalDate.now())
            _memoStreak.value = computeMemoStreak(LocalDate.now())
        }
    }

    /** 计算截至 [until] 的连续备忘录任务完成天数 */
    private suspend fun computeMemoStreak(until: LocalDate): Int {
        val from = until.minusDays(60)
        val allTasks = taskRepo.getTasksByDateRange(from, until).first()
        // 按日期分组，检查每天是否有已完成的 HOMEWORK_MEMO 任务
        val doneMemoDates = allTasks
            .filter { it.templateType == TaskTemplateType.HOMEWORK_MEMO &&
                (it.status == TaskStatus.ON_TIME_DONE || it.status == TaskStatus.EARLY_DONE) }
            .map { it.date }
            .toSet()
        var streak = 0
        var current = until
        while (doneMemoDates.contains(current)) {
            streak++
            current = current.minusDays(1)
        }
        return streak
    }

    val uiState: StateFlow<DailyTaskUiState> = combine(
        _date.flatMapLatest { date ->
            getDailyTasksUseCase(date).map { tasks ->
                val uiModels = tasks.map { it.toUiModel() }
                Triple(date, tasks, uiModels)
            }
        },
        _currentStreak,
        _memoStreak
    ) { (date, tasks, uiModels), streak, memoStreak ->
        DailyTaskUiState(
            date = date,
            tasks = uiModels,
            domainTasks = tasks,
            completedCount = uiModels.count { it.isDone },
            totalCount = uiModels.size,
            celebrationShown = celebrationShownDates.contains(date),
            currentStreak = streak,
            memoStreak = memoStreak
        )
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
                .onSuccess { rewardSummary ->
                    _viewEvent.emit(DailyTaskViewEvent.ShowRewardDialog(rewardSummary))
                    // 打卡后刷新连续天数和备忘录连续天数
                    _currentStreak.value = checkInRepo.getStreakCount(LocalDate.now())
                    _memoStreak.value = computeMemoStreak(LocalDate.now())
                }
                .onFailure { _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("打卡失败，请重试")) }
        }
    }

    /** UI 调用：标记今天的"全部完成"横幅已展示，避免重入重复显示 */
    fun markCelebrationShown() {
        celebrationShownDates.add(_date.value)
    }

    fun copyTasksToDate(targetDate: LocalDate) {
        viewModelScope.launch {
            val tasks = uiState.value.domainTasks
            copyTasksUseCase(tasks, targetDate)
                .onSuccess { count ->
                    _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("已复制 $count 项任务到 $targetDate"))
                }
                .onFailure {
                    _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("复制失败，请重试"))
                }
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
    val validationErrors: Map<String, String> = emptyMap(),
    // 完成奖励（null = 使用规则引擎默认）
    val baseRewardLevel: MushroomLevel = MushroomLevel.SMALL,
    val baseRewardAmount: Int = 1,
    // 提前完成奖励（null = 使用规则引擎分级默认）
    val earlyRewardLevel: MushroomLevel = MushroomLevel.SMALL,
    val earlyRewardAmount: Int = 1,
    val useCustomReward: Boolean = false,
    val useCustomEarlyReward: Boolean = false,
    // 已完成任务只读，禁止编辑保存
    val isReadOnly: Boolean = false
)

sealed class TaskEditViewEvent {
    object SaveSuccess : TaskEditViewEvent()
    object SaveAsTemplateSuccess : TaskEditViewEvent()
    data class ShowError(val message: String) : TaskEditViewEvent()
}

@HiltViewModel
class TaskEditViewModel @Inject constructor(
    private val createTaskUseCase: CreateTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val getTaskByIdUseCase: GetTaskByIdUseCase,
    private val saveCustomTemplateUseCase: SaveCustomTemplateUseCase,
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
    fun toggleCustomReward(enabled: Boolean) { _uiState.value = _uiState.value.copy(useCustomReward = enabled) }
    fun toggleCustomEarlyReward(enabled: Boolean) { _uiState.value = _uiState.value.copy(useCustomEarlyReward = enabled) }
    fun updateBaseRewardLevel(level: MushroomLevel) { _uiState.value = _uiState.value.copy(baseRewardLevel = level) }
    fun updateBaseRewardAmount(amount: Int) { _uiState.value = _uiState.value.copy(baseRewardAmount = amount.coerceAtLeast(1)) }
    fun updateEarlyRewardLevel(level: MushroomLevel) { _uiState.value = _uiState.value.copy(earlyRewardLevel = level) }
    fun updateEarlyRewardAmount(amount: Int) { _uiState.value = _uiState.value.copy(earlyRewardAmount = amount.coerceAtLeast(1)) }

    fun loadTask(task: Task) {
        val isDone = task.status == TaskStatus.EARLY_DONE || task.status == TaskStatus.ON_TIME_DONE
        _uiState.value = TaskEditUiState(
            taskId = task.id,
            title = task.title,
            subject = task.subject,
            estimatedMinutes = task.estimatedMinutes,
            deadline = task.deadline,
            repeatRule = task.repeatRule,
            description = task.description,
            useCustomReward = task.customRewardConfig != null,
            baseRewardLevel = task.customRewardConfig?.level ?: MushroomLevel.SMALL,
            baseRewardAmount = task.customRewardConfig?.amount ?: 1,
            useCustomEarlyReward = task.customEarlyRewardConfig != null,
            earlyRewardLevel = task.customEarlyRewardConfig?.level ?: MushroomLevel.SMALL,
            earlyRewardAmount = task.customEarlyRewardConfig?.amount ?: 1,
            isReadOnly = isDone
        )
    }

    fun save(date: LocalDate) {
        val state = _uiState.value
        if (state.isReadOnly) return   // 已完成任务不允许保存
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(validationErrors = errors)
            viewModelScope.launch {
                _viewEvent.emit(TaskEditViewEvent.ShowError("请检查标红的必填项"))
            }
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
                status = TaskStatus.PENDING,
                description = state.description,
                customRewardConfig = if (state.useCustomReward)
                    MushroomRewardConfig(state.baseRewardLevel, state.baseRewardAmount) else null,
                customEarlyRewardConfig = if (state.useCustomEarlyReward && state.deadline != null)
                    MushroomRewardConfig(state.earlyRewardLevel, state.earlyRewardAmount) else null
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

    fun saveAsTemplate() {
        val state = _uiState.value
        val errors = validate(state)
        if (errors.isNotEmpty()) {
            _uiState.value = state.copy(validationErrors = errors)
            viewModelScope.launch {
                _viewEvent.emit(TaskEditViewEvent.ShowError("请检查标红的必填项"))
            }
            return
        }
        viewModelScope.launch {
            val deadlineOffset = state.deadline?.let {
                it.hour * 60 + it.minute
            }
            val template = TaskTemplate(
                id = 0,
                name = state.title.trim(),
                type = TaskTemplateType.CUSTOM,
                subject = state.subject,
                estimatedMinutes = state.estimatedMinutes,
                description = "",
                defaultDeadlineOffset = deadlineOffset,
                rewardConfig = TemplateRewardConfig(
                    baseReward = MushroomRewardConfig(state.baseRewardLevel, state.baseRewardAmount),
                    bonusReward = if (state.deadline != null)
                        MushroomRewardConfig(state.earlyRewardLevel, state.earlyRewardAmount) else null,
                    bonusCondition = null
                ),
                isBuiltIn = false
            )
            saveCustomTemplateUseCase(template)
                .onSuccess { _viewEvent.emit(TaskEditViewEvent.SaveAsTemplateSuccess) }
                .onFailure { _viewEvent.emit(TaskEditViewEvent.ShowError("保存模板失败")) }
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
