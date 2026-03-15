package com.mushroom.feature.task.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.logging.MushroomLogger
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
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
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.mushroom.feature.task.usecase.CheckInTaskUseCase
import com.mushroom.feature.task.usecase.ApplyTaskTemplateUseCase
import com.mushroom.feature.task.usecase.CopyTasksUseCase
import com.mushroom.feature.task.usecase.CreateTaskUseCase
import com.mushroom.feature.task.usecase.DeleteMode
import com.mushroom.feature.task.usecase.DeleteTaskUseCase
import com.mushroom.feature.task.usecase.GetDailyTasksUseCase
import com.mushroom.feature.task.usecase.GetTaskByIdUseCase
import com.mushroom.feature.task.usecase.GetTaskTemplatesUseCase
import com.mushroom.feature.task.usecase.DeleteTaskTemplateUseCase
import com.mushroom.feature.task.usecase.SaveCustomTemplateUseCase
import com.mushroom.feature.task.usecase.UpdateTaskTemplateUseCase
import com.mushroom.feature.task.usecase.UpdateTaskUseCase
import com.mushroom.core.domain.repository.CheckInRepository
import com.mushroom.core.domain.repository.MilestoneRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.core.domain.repository.TaskRepository
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.core.domain.service.NotificationService
import com.mushroom.feature.game.repository.GameRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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

private const val TAG = "DailyTaskViewModel"

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
    val memoStreak: Int = 0,
    val upcomingMilestones: List<Milestone> = emptyList()
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
    private val taskRepo: TaskRepository,
    private val milestoneRepository: MilestoneRepository,
    private val gameRepo: GameRepository,
    private val mushroomRepo: MushroomRepository,
    private val notificationService: NotificationService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    // 记录已展示过"全部完成"横幅的日期，避免重入页面重复显示
    private val celebrationShownDates = mutableSetOf<LocalDate>()
    // 连续打卡天数，初始0，每次打卡后刷新
    private val _currentStreak = MutableStateFlow(0)
    // 连续备忘录任务完成天数
    private val _memoStreak = MutableStateFlow(0)
    // 是否可以触发游戏（全勤 AND 今日未玩过）
    private val _canTriggerGame = MutableStateFlow(false)
    val canTriggerGame: StateFlow<Boolean> = _canTriggerGame

    // 倒计时状态：taskId → 剩余秒数（null=未计时，0=已结束）
    private val _timerStates = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val timerStates: StateFlow<Map<Long, Int>> = _timerStates
    private val timerJobs = mutableMapOf<Long, Job>()

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
                val uiModels = tasks.map { it.toUiModel(appContext) }
                Triple(date, tasks, uiModels)
            }
        },
        _currentStreak,
        _memoStreak,
        milestoneRepository.getAllMilestones().map { milestones ->
            val today = LocalDate.now()
            val cutoff = today.plusDays(90)
            milestones
                .filter { it.status == MilestoneStatus.PENDING && !it.scheduledDate.isBefore(today) && !it.scheduledDate.isAfter(cutoff) }
                .sortedBy { it.scheduledDate }
                .take(3)
        }
    ) { triple, streak, memoStreak, upcomingMilestones ->
        val date = triple.first
        val tasks = triple.second
        val uiModels = triple.third
        DailyTaskUiState(
            date = date,
            tasks = uiModels,
            domainTasks = tasks,
            completedCount = uiModels.count { it.isDone },
            totalCount = uiModels.size,
            celebrationShown = celebrationShownDates.contains(date),
            currentStreak = streak,
            memoStreak = memoStreak,
            upcomingMilestones = upcomingMilestones
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

    /**
     * 删除已完成任务，并扣回当时发放的蘑菇奖励。
     * 奖励计算与 CheckInTaskUseCase.buildRewardSummary 逻辑保持一致。
     */
    fun deleteCompletedTask(taskId: Long, mode: DeleteMode) {
        viewModelScope.launch {
            val task = taskRepo.getTaskById(taskId) ?: run {
                _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("删除失败"))
                return@launch
            }
            val checkIn = checkInRepo.getLatestCheckInForTask(taskId)
            val rewards = calcTaskRewards(task, checkIn?.isEarly == true)

            deleteTaskUseCase(taskId, mode).onFailure {
                _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("删除失败"))
                return@launch
            }

            if (rewards.isNotEmpty()) {
                val now = LocalDateTime.now()
                mushroomRepo.recordTransactions(rewards.map { (level, amount) ->
                    MushroomTransaction(
                        level = level,
                        action = MushroomAction.DEDUCT,
                        amount = amount,
                        sourceType = MushroomSource.TASK,
                        sourceId = null,
                        note = "删除已完成任务「${task.title}」扣回",
                        createdAt = now
                    )
                })
                val summary = rewards.joinToString("、") { (level, amount) -> "${level.themedDisplayName(appContext)}×$amount" }
                _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("已删除并扣回奖励：$summary"))
            } else {
                _viewEvent.emit(DailyTaskViewEvent.ShowSnackbar("已删除"))
            }
        }
    }

    /** 计算已完成任务将扣回的奖励描述（供确认框展示，逻辑与发放时一致） */
    suspend fun getCompletedTaskRewardSummary(taskId: Long): String {
        val task = taskRepo.getTaskById(taskId) ?: return ""
        val checkIn = checkInRepo.getLatestCheckInForTask(taskId)
        val rewards = calcTaskRewards(task, checkIn?.isEarly == true)
        return rewards.joinToString("、") { (level, amount) -> "${level.themedDisplayName(appContext)}×$amount" }
    }

    /**
     * 计算任务奖励列表（level, amount），与 RewardRules 中规则保持一致：
     * - 基础奖励（按模板类型或自定义配置）
     * - 提前完成奖励（isEarly=true 时追加）
     */
    private fun calcTaskRewards(task: Task, isEarly: Boolean): List<Pair<MushroomLevel, Int>> {
        val rewards = mutableListOf<Pair<MushroomLevel, Int>>()
        val baseConfig = task.customRewardConfig
        if (baseConfig != null) {
            rewards += baseConfig.level to baseConfig.amount
        } else {
            when (task.templateType) {
                TaskTemplateType.MORNING_READING    -> rewards += MushroomLevel.SMALL to 1
                TaskTemplateType.HOMEWORK_MEMO      -> rewards += MushroomLevel.SMALL to 1
                TaskTemplateType.HOMEWORK_AT_SCHOOL -> rewards += MushroomLevel.MEDIUM to 1
                null                               -> rewards += MushroomLevel.SMALL to 1
                else                               -> rewards += MushroomLevel.SMALL to 1
            }
        }
        if (isEarly) {
            val earlyConfig = task.customEarlyRewardConfig
            if (earlyConfig != null) {
                rewards += earlyConfig.level to earlyConfig.amount
            } else {
                rewards += MushroomLevel.SMALL to 1
            }
        }
        return rewards
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

    /** 检查今日全勤后是否可以触发游戏；同步返回结果并更新 StateFlow */
    suspend fun checkGameTrigger(): Boolean {
        val today = LocalDate.now()
        val hasPlayed = gameRepo.hasPlayedToday(today)
        val canTrigger = !hasPlayed
        MushroomLogger.w(TAG, "checkGameTrigger: today=$today hasPlayedToday=$hasPlayed canTrigger=$canTrigger")
        _canTriggerGame.value = canTrigger
        return canTrigger
    }

    /** UI 确认进入游戏后调用，防止重复触发 */
    fun markGameTriggered() {
        _canTriggerGame.value = false
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

    // ── 倒计时 ──────────────────────────────────────────────

    fun startTimer(taskId: Long, minutes: Int) {
        stopTimer(taskId)
        val totalSeconds = minutes * 60
        _timerStates.value = _timerStates.value + (taskId to totalSeconds)
        val task = uiState.value.domainTasks.find { it.id == taskId }
        timerJobs[taskId] = viewModelScope.launch {
            var remaining = totalSeconds
            while (remaining > 0) {
                delay(1_000)
                remaining--
                _timerStates.value = _timerStates.value + (taskId to remaining)
            }
            // 时间到
            notificationService.sendImmediateNotification(
                title = "专注时间到！",
                body = "「${task?.title ?: "任务"}」的专注时间已结束"
            )
            timerJobs.remove(taskId)
        }
    }

    fun stopTimer(taskId: Long) {
        timerJobs[taskId]?.cancel()
        timerJobs.remove(taskId)
        _timerStates.value = _timerStates.value - taskId
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
    val isReadOnly: Boolean = false,
    // 任务模板列表（新建时使用）
    val builtInTemplates: List<TaskTemplate> = emptyList(),
    val customTemplates: List<TaskTemplate> = emptyList()
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
    private val getTaskTemplatesUseCase: GetTaskTemplatesUseCase,
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
        } else {
            // 新建任务时加载模板列表
            viewModelScope.launch {
                getTaskTemplatesUseCase.invoke().collect { all ->
                    _uiState.value = _uiState.value.copy(
                        builtInTemplates = all.filter { it.isBuiltIn },
                        customTemplates = all.filter { !it.isBuiltIn }
                    )
                }
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

    fun applyTemplate(template: TaskTemplate?, date: LocalDate) {
        if (template == null) return
        val deadline = template.defaultDeadlineOffset?.let {
            date.atStartOfDay().plusMinutes(it.toLong())
        }
        val baseReward = template.rewardConfig.baseReward
        val bonusReward = template.rewardConfig.bonusReward
        _uiState.value = _uiState.value.copy(
            title = template.name,
            subject = template.subject,
            estimatedMinutes = template.estimatedMinutes,
            description = template.description,
            deadline = deadline,
            useCustomReward = baseReward != null,
            baseRewardLevel = baseReward?.level ?: MushroomLevel.SMALL,
            baseRewardAmount = baseReward?.amount ?: 1,
            useCustomEarlyReward = bonusReward != null,
            earlyRewardLevel = bonusReward?.level ?: MushroomLevel.SMALL,
            earlyRewardAmount = bonusReward?.amount ?: 1
        )
    }

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
    private val updateTaskTemplateUseCase: UpdateTaskTemplateUseCase,
    private val deleteTaskTemplateUseCase: DeleteTaskTemplateUseCase,
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
        val allTemplates = uiState.value.builtInTemplates + uiState.value.customTemplates
        val nameTaken = allTemplates.any { it.name == template.name && it.id != template.id }
        if (nameTaken) {
            viewModelScope.launch { _viewEvent.emit("模板名称「${template.name}」已存在") }
            return
        }
        viewModelScope.launch {
            saveCustomTemplateUseCase(template)
                .onSuccess { _viewEvent.emit("模板已保存") }
                .onFailure { _viewEvent.emit("保存失败") }
        }
    }

    fun updateTemplate(template: TaskTemplate) {
        val allTemplates = uiState.value.builtInTemplates + uiState.value.customTemplates
        val nameTaken = allTemplates.any { it.name == template.name && it.id != template.id }
        if (nameTaken) {
            viewModelScope.launch { _viewEvent.emit("模板名称「${template.name}」已存在") }
            return
        }
        viewModelScope.launch {
            updateTaskTemplateUseCase(template)
                .onSuccess { _viewEvent.emit("模板已更新") }
                .onFailure { _viewEvent.emit("更新失败") }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch {
            deleteTaskTemplateUseCase(id)
                .onSuccess { _viewEvent.emit("模板已删除") }
                .onFailure { _viewEvent.emit("删除失败") }
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
