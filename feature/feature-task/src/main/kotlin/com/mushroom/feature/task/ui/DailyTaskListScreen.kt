package com.mushroom.feature.task.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val DATE_FMT = DateTimeFormatter.ofPattern("MM月dd日 EEEE")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyTaskListScreen(
    onNavigateToAddTask: () -> Unit,
    onNavigateToEditTask: (Long) -> Unit,
    onNavigateToTemplates: () -> Unit,
    onNavigateToAddMilestone: () -> Unit = {},
    viewModel: DailyTaskViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 待确认删除的任务
    var pendingDelete by remember { mutableStateOf<TaskUiModel?>(null) }

    // 复制任务日期选择器
    var showCopyDatePicker by remember { mutableStateOf(false) }

    // FAB 展开状态
    var fabExpanded by remember { mutableStateOf(false) }

    // 打卡奖励弹窗
    var rewardDialogText by remember { mutableStateOf<String?>(null) }

    // 庆祝横幅：全部完成且当天未展示过才显示，3 秒后自动消失
    var showCelebration by remember { mutableStateOf(false) }
    val isAllDone = uiState.totalCount > 0 && uiState.completedCount == uiState.totalCount

    LaunchedEffect(isAllDone, uiState.date) {
        if (isAllDone && !uiState.celebrationShown) {
            viewModel.markCelebrationShown()
            showCelebration = true
            delay(3_000)
            showCelebration = false
        } else if (!isAllDone) {
            showCelebration = false
        }
    }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DailyTaskViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                is DailyTaskViewEvent.ShowRewardDialog -> rewardDialogText = event.rewardSummary
                is DailyTaskViewEvent.NavigateToAddTask -> onNavigateToAddTask()
            }
        }
    }

    Scaffold(
        topBar = {
            val date = uiState.date
            CenterAlignedTopAppBar(
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
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // 展开后显示的次级按钮
                if (fabExpanded) {
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            onNavigateToAddMilestone()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = "添加里程碑")
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "添加里程碑",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    SmallFloatingActionButton(
                        onClick = onNavigateToTemplates,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text("模板", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    SmallFloatingActionButton(
                        onClick = {
                            fabExpanded = false
                            showCopyDatePicker = true
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text("复制", style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FloatingActionButton(onClick = {
                    if (fabExpanded) {
                        fabExpanded = false
                        onNavigateToAddTask()
                    } else {
                        fabExpanded = true
                    }
                }) {
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
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                item {
                    if (state.totalCount > 0) {
                        TaskProgressCard(state.completedCount, state.totalCount)
                    }
                }
                if (state.tasks.isEmpty()) {
                    item { EmptyTasksPlaceholder(onNavigateToAddTask) }
                } else {
                    items(state.tasks, key = { it.id }) { task ->
                        TaskCard(
                            task = task,
                            onEdit = { onNavigateToEditTask(task.id) },
                            onCheckIn = { viewModel.checkIn(task.id) },
                            onDelete = {
                                if (task.hasRepeat) {
                                    pendingDelete = task
                                } else {
                                    viewModel.deleteTask(task.id, DeleteMode.SINGLE)
                                }
                            }
                        )
                    }
                }
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
private fun TaskProgressCard(completed: Int, total: Int) {
    val progress = if (total > 0) completed.toFloat() / total else 0f
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskCard(
    task: TaskUiModel,
    onEdit: () -> Unit,
    onCheckIn: () -> Unit,
    onDelete: () -> Unit
) {
    // 完成时图标做一个缩放弹跳动画
    val iconScale by animateFloatAsState(
        targetValue = if (task.isDone) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "done_icon_scale"
    )

    val containerColor = when {
        task.isEarlyDone -> MaterialTheme.colorScheme.tertiaryContainer
        task.isDone -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        onClick = onEdit,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
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
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Delete, contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.error)
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
private fun EmptyTasksPlaceholder(onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("今天还没有任务 🍄", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onAdd) { Text("添加任务") }
    }
}
