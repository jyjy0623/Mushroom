package com.mushroom.feature.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.feature.task.model.TaskUiModel
import com.mushroom.feature.task.usecase.DeleteMode
import com.mushroom.feature.task.viewmodel.DailyTaskViewEvent
import com.mushroom.feature.task.viewmodel.DailyTaskViewModel
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.ui.R as CoreUiR
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import com.mushroom.core.logging.MushroomLogger
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private const val TAG = "DailyTaskListScreen"

private val DATE_FMT = DateTimeFormatter.ofPattern("MM月dd日 EEEE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTaskListScreen(
    onNavigateToAddTask: (dateIso: String) -> Unit,
    onNavigateToEditTask: (Long) -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToAddMilestone: () -> Unit = {},
    onNavigateToCheckInHistory: () -> Unit = {},
    onNavigateToMilestoneList: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToFriendManagement: () -> Unit = {},
    viewModel: DailyTaskViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val canTriggerGame by viewModel.canTriggerGame.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    // 待确认删除的任务
    var pendingDelete by remember { mutableStateOf<TaskUiModel?>(null) }

    // 复制任务日期选择器
    var showCopyDatePicker by remember { mutableStateOf(false) }

    // 打卡奖励弹窗
    var rewardDialogText by remember { mutableStateOf<String?>(null) }

    // 删除已完成任务确认框（含扣回奖励提示）
    var pendingDeleteCompleted by remember { mutableStateOf<TaskUiModel?>(null) }
    var pendingDeleteCompletedReward by remember { mutableStateOf("") }

    // 庆祝横幅：全部完成且当天未展示过才显示，3 秒后自动消失
    var showCelebration by remember { mutableStateOf(false) }
    // 游戏解锁弹窗
    var showGameUnlockDialog by remember { mutableStateOf(false) }
    // 本地标记：当前日期是否已触发过横幅（rememberSaveable 防止导航返回后重置）
    var celebrationFiredDate by rememberSaveable { mutableStateOf<String?>(null) }
    // 本地标记：当前日期是否已触发过游戏检查（防止重复弹窗）
    var gameTriggerFiredDate by rememberSaveable { mutableStateOf<String?>(null) }
    val isAllDone = uiState.totalCount > 0 && uiState.completedCount == uiState.totalCount

    // 庆祝横幅（只弹一次）
    LaunchedEffect(isAllDone, uiState.date) {
        MushroomLogger.w(TAG, "isAllDone=$isAllDone date=${uiState.date} celebrationFiredDate=$celebrationFiredDate celebrationShown=${uiState.celebrationShown} total=${uiState.totalCount} completed=${uiState.completedCount}")
        if (isAllDone && celebrationFiredDate != uiState.date.toString() && !uiState.celebrationShown) {
            celebrationFiredDate = uiState.date.toString()
            viewModel.markCelebrationShown()
            showCelebration = true
            delay(3_000)
            showCelebration = false
        } else if (!isAllDone) {
            showCelebration = false
        }
    }

    // 游戏触发（独立于庆祝横幅，全部完成+今天+未玩过即触发）
    LaunchedEffect(isAllDone, uiState.date) {
        if (isAllDone && uiState.date == LocalDate.now() && gameTriggerFiredDate != uiState.date.toString()) {
            MushroomLogger.w(TAG, "all tasks done today, checking game trigger...")
            val canTrigger = viewModel.checkGameTrigger()
            MushroomLogger.w(TAG, "checkGameTrigger result=$canTrigger")
            if (canTrigger) {
                gameTriggerFiredDate = uiState.date.toString()
                // 等庆祝横幅结束后再弹游戏弹窗
                if (showCelebration) delay(3_200)
                MushroomLogger.w(TAG, "showing game unlock dialog")
                showGameUnlockDialog = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DailyTaskViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is DailyTaskViewEvent.ShowRewardDialog -> rewardDialogText = event.rewardSummary
                is DailyTaskViewEvent.NavigateToAddTask -> onNavigateToAddTask(uiState.date.toString())
            }
        }
    }

    Scaffold(
        topBar = {
            val date = uiState.date
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onNavigateToFriendManagement) {
                        Icon(Icons.Filled.Person, contentDescription = "好友管理")
                    }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.navigatePreviousDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "前一天")
                        }
                        Text(
                            text = date.format(DATE_FMT),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { viewModel.navigateNextDay() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "后一天")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCheckInHistory) {
                        Icon(Icons.Filled.DateRange, contentDescription = "打卡历史")
                    }
                }
            )
        },
        floatingActionButton = {
            val isPastDate = uiState.date.isBefore(LocalDate.now())
            if (!isPastDate) {
                FloatingActionButton(onClick = { onNavigateToAddTask(uiState.date.toString()) }) {
                    Icon(Icons.Filled.Add, contentDescription = "新建")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val state = uiState
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 80.dp)
            ) {
                if (state.upcomingMilestones.isNotEmpty()) {
                    item {
                        UpcomingMilestonesCard(
                            milestones = state.upcomingMilestones,
                            onNavigateToMilestoneList = onNavigateToMilestoneList,
                            modifier = Modifier.padding(horizontal = 0.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                item {
                    if (state.totalCount > 0) {
                        TaskProgressCard(state.completedCount, state.totalCount, state.currentStreak, state.memoStreak)
                    }
                }
                if (state.tasks.isEmpty()) {
                    item { EmptyTasksPlaceholder(onNavigateToAddTask) }
                } else {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onEdit = if (task.isDone) null else { { onNavigateToEditTask(task.id) } },
                            onCheckIn = { viewModel.checkIn(task.id) },
                            onDelete = {
                                if (task.isDone) {
                                    // 已完成任务：弹确认框并预加载扣回奖励描述
                                    pendingDeleteCompleted = task
                                    scope.launch {
                                        pendingDeleteCompletedReward =
                                            viewModel.getCompletedTaskRewardSummary(task.id)
                                    }
                                } else if (task.hasRepeat) {
                                    pendingDelete = task
                                } else {
                                    viewModel.deleteTask(task.id, DeleteMode.SINGLE)
                                }
                            }
                        )
                    }
                }
            }

            // 左下角：复制任务按钮
            FloatingActionButton(
                onClick = { showCopyDatePicker = true },
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp),
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text("复制", style = MaterialTheme.typography.labelSmall)
            }

            // 庆祝横幅（全部完成动画，3s）
            AnimatedVisibility(
                visible = showCelebration,
                enter = fadeIn(animationSpec = tween(400)) +
                        scaleIn(initialScale = 0.7f, animationSpec = tween(400, easing = FastOutSlowInEasing)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                CelebrationBanner()
            }
        }
    }

    // 重复任务删除确认弹窗
    pendingDelete?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除「${task.title}」") },
            text = { Text("这是一个重复任务，请选择删除范围：") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(task.id, DeleteMode.ALL_RECURRING)
                    pendingDelete = null
                }) { Text("删除全部重复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.deleteTask(task.id, DeleteMode.SINGLE)
                    pendingDelete = null
                }) { Text("只删今天") }
            }
        )
    }

    // 已完成任务删除确认弹窗（含扣回奖励提示）
    pendingDeleteCompleted?.let { task ->
        AlertDialog(
            onDismissRequest = { pendingDeleteCompleted = null },
            title = { Text("删除已完成任务") },
            text = {
                Column {
                    Text("确认删除「${task.title}」？")
                    if (pendingDeleteCompletedReward.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "将同时扣回奖励：$pendingDeleteCompletedReward",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val mode = if (task.hasRepeat) DeleteMode.SINGLE else DeleteMode.SINGLE
                    viewModel.deleteCompletedTask(task.id, mode)
                    pendingDeleteCompleted = null
                }) { Text("确认删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteCompleted = null }) { Text("取消") }
            }
        )
    }

    // 打卡奖励弹窗
    rewardDialogText?.let { summary ->
        AlertDialog(
            onDismissRequest = { rewardDialogText = null },
            title = { Text("打卡成功！") },
            text = {
                Column {
                    Text("🍄 获得奖励：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { rewardDialogText = null }) { Text("太棒了！") }
            }
        )
    }

    // 游戏解锁弹窗
    if (showGameUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showGameUnlockDialog = false },
            title = { Text("🎮 解锁游戏！") },
            text = {
                Text(
                    stringResource(CoreUiR.string.game_unlock_message),
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showGameUnlockDialog = false
                    viewModel.markGameTriggered()
                    onNavigateToGame()
                }) { Text("去玩！") }
            },
            dismissButton = {
                TextButton(onClick = { showGameUnlockDialog = false }) { Text("稍后再说") }
            }
        )
    }

    // 复制任务日期选择器
    if (showCopyDatePicker) {
        val tomorrowMillis = remember {
            LocalDate.now().plusDays(1)
                .atTime(12, 0).toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = tomorrowMillis)
        DatePickerDialog(
            onDismissRequest = { showCopyDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val targetDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.copyTasksToDate(targetDate)
                    }
                    showCopyDatePicker = false
                }) { Text("复制") }
            },
            dismissButton = {
                TextButton(onClick = { showCopyDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun UpcomingMilestonesCard(
    milestones: List<Milestone>,
    onNavigateToMilestoneList: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    Card(
        onClick = onNavigateToMilestoneList,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "近期里程碑",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "查看全部里程碑",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Spacer(Modifier.height(6.dp))
            milestones.forEach { milestone ->
                val daysLeft = ChronoUnit.DAYS.between(today, milestone.scheduledDate).toInt()
                val daysText = when (daysLeft) {
                    0 -> "今天"
                    1 -> "明天"
                    else -> "还有 $daysLeft 天"
                }
                val subjectName = when (milestone.subject) {
                    com.mushroom.core.domain.entity.Subject.MATH -> "数学"
                    com.mushroom.core.domain.entity.Subject.CHINESE -> "语文"
                    com.mushroom.core.domain.entity.Subject.ENGLISH -> "英语"
                    com.mushroom.core.domain.entity.Subject.PHYSICS -> "物理"
                    com.mushroom.core.domain.entity.Subject.CHEMISTRY -> "化学"
                    com.mushroom.core.domain.entity.Subject.BIOLOGY -> "生物"
                    com.mushroom.core.domain.entity.Subject.HISTORY -> "历史"
                    com.mushroom.core.domain.entity.Subject.GEOGRAPHY -> "地理"
                    com.mushroom.core.domain.entity.Subject.OTHER -> "其他"
                }
                Text(
                    text = "${milestone.name} · $subjectName · $daysText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

@Composable
private fun CelebrationBanner() {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎉", fontSize = 48.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                "全部完成！",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
            Text(
                "今天的任务全部搞定了 🍄",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer
            )
        }
    }
}

@Composable
private fun TaskProgressCard(completed: Int, total: Int, currentStreak: Int, memoStreak: Int) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
    val allDone = total > 0 && completed == total
    // 下一个里程碑天数
    val nextMilestone = listOf(7, 30, 100).firstOrNull { it > currentStreak }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "今日进度 $completed / $total",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress >= 1f) MaterialTheme.colorScheme.tertiary
                        else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            // 全勤奖提示
            val mediumEmoji = stringResource(CoreUiR.string.level_emoji_medium)
            val mediumName = stringResource(CoreUiR.string.level_medium)
            if (allDone) {
                Text(
                    text = stringResource(CoreUiR.string.attendance_earned, mediumEmoji, mediumName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            } else if (total > 0) {
                Text(
                    text = stringResource(CoreUiR.string.attendance_hint, mediumEmoji, mediumName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 连续打卡里程碑提示
            if (currentStreak > 0) {
                Spacer(Modifier.height(2.dp))
                val streakText = if (nextMilestone != null) {
                    "🔥 连续打卡 ${currentStreak} 天 · 距 ${nextMilestone} 天里程碑还差 ${nextMilestone - currentStreak} 天"
                } else {
                    "🔥 连续打卡 ${currentStreak} 天 · 已达成全部里程碑！"
                }
                Text(
                    text = streakText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 备忘录连续天数提示
            if (memoStreak > 0) {
                Spacer(Modifier.height(2.dp))
                val memoText = if (memoStreak >= 5) {
                    "📖 已连续完成备忘录任务 ${memoStreak} 天 · 已达成里程碑！"
                } else {
                    "📖 已连续完成备忘录任务 ${memoStreak} 天 · 距 5 天里程碑还差 ${5 - memoStreak} 天"
                }
                Text(
                    text = memoText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    task: TaskUiModel,
    onEdit: (() -> Unit)?,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit
) {
    val containerColor = when {
        task.isEarlyDone -> MaterialTheme.colorScheme.tertiaryContainer
        task.isDone -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onEdit?.invoke() },
                onLongClick = onDelete
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) { TaskCardContent(task, onCheckIn) }
}

@Composable
private fun TaskCardContent(
    task: TaskUiModel,
    onCheckIn: () -> Unit
) {
    // 完成时图标做一个缩放弹跳动画
    val iconScale by animateFloatAsState(
        targetValue = if (task.isDone) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "done_icon_scale"
    )
    Column(modifier = Modifier.padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (task.isEarlyDone) {
                Text("⚡", fontSize = 20.sp, modifier = Modifier.padding(end = 8.dp))
            } else {
                // 任务完成打勾动画
                Text(
                    "✓", fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .scale(iconScale)
                        .padding(end = 8.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (task.isDone) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectChip(task.subjectLabel)
                    if (task.hasRepeat) SubjectChip("🔄")
                    task.deadlineDisplay?.let { SubjectChip(it) }
                }
            }
            // 打卡按钮（仅未完成任务显示）
            if (!task.isDone) {
                IconButton(onClick = onCheckIn) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = "打卡完成",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        // 奖励预览（始终显示；未完成时为预估，完成后为实际获得）
        Spacer(Modifier.height(4.dp))
        Text(
            text = task.rewardPreview,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
        )
    }
}

@Composable
private fun SubjectChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun EmptyTasksPlaceholder(onAdd: (String) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(stringResource(CoreUiR.string.empty_tasks_placeholder), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = { onAdd(java.time.LocalDate.now().toString()) }) { Text("添加任务") }
    }
}
