package com.mushroom.feature.milestone.ui

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.Milestone
import com.mushroom.core.domain.entity.MilestoneStatus
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.milestone.viewmodel.MilestoneListViewEvent
import com.mushroom.feature.milestone.viewmodel.MilestoneListViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val DATE_FMT = DateTimeFormatter.ofPattern("MM-dd")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MilestoneListScreen(
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    viewModel: MilestoneListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var tabIndex by remember { mutableStateOf(0) }

    // Dialog state
    var showScoreDialog by remember { mutableStateOf(false) }
    var scoringMilestone by remember { mutableStateOf<Milestone?>(null) }
    var scoreInputText by remember { mutableStateOf("") }

    // 删除确认弹窗
    var pendingDeleteMilestone by remember { mutableStateOf<Milestone?>(null) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is MilestoneListViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    if (showScoreDialog && scoringMilestone != null) {
        ScoreInputDialog(
            milestoneName = scoringMilestone!!.name,
            scoreText = scoreInputText,
            onScoreChange = { scoreInputText = it },
            onConfirm = {
                val score = scoreInputText.toIntOrNull()
                if (score != null && score in 0..100) {
                    viewModel.recordScore(scoringMilestone!!.id, score)
                    showScoreDialog = false
                }
            },
            onDismiss = { showScoreDialog = false }
        )
    }

    pendingDeleteMilestone?.let { milestone ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMilestone = null },
            title = { Text("确认删除") },
            text = { Text("确定删除「${milestone.name}」？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMilestone(milestone.id)
                        pendingDeleteMilestone = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMilestone = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("里程碑") }) },
        floatingActionButton = {},
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 学科筛选
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = uiState.selectedSubject == null,
                        onClick = { viewModel.selectSubject(null) },
                        label = { Text("全部") }
                    )
                }
                items(Subject.values()) { subject ->
                    FilterChip(
                        selected = uiState.selectedSubject == subject,
                        onClick = { viewModel.selectSubject(subject) },
                        label = { Text(subjectLabel(subject)) }
                    )
                }
            }

            // Tabs
            TabRow(selectedTabIndex = tabIndex) {
                Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("待确认") })
                Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("已关闭") })
            }

            val currentList = if (tabIndex == 0) uiState.upcomingMilestones else uiState.completedMilestones

            if (currentList.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (tabIndex == 0) "暂无待确认的里程碑" else "暂无已关闭的里程碑",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(currentList, key = { it.id }) { milestone ->
                        val today = LocalDate.now()
                        val isOverdue = milestone.status == MilestoneStatus.PENDING &&
                                milestone.scheduledDate.isBefore(today)
                        // Tab 0 和 Tab 1 都点击录入/修改成绩
                        val onClick: (() -> Unit)? = {
                            scoringMilestone = milestone
                            scoreInputText = milestone.actualScore?.toString() ?: ""
                            showScoreDialog = true
                        }
                        val onLongClick: (() -> Unit)? = if (tabIndex == 0) {
                            { pendingDeleteMilestone = milestone }
                        } else null
                        MilestoneCard(
                            milestone = milestone,
                            isOverdue = isOverdue,
                            onClick = onClick,
                            onLongClick = onLongClick
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreInputDialog(
    milestoneName: String,
    scoreText: String,
    onScoreChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isValid = scoreText.toIntOrNull()?.let { it in 0..100 } == true
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("录入成绩 — $milestoneName") },
        text = {
            OutlinedTextField(
                value = scoreText,
                onValueChange = { onScoreChange(it.filter { c -> c.isDigit() }) },
                label = { Text("分数（0～100）") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = isValid) {
                Text("确认录入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MilestoneCard(
    milestone: Milestone,
    isOverdue: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null
) {
    val today = LocalDate.now()
    val daysLeft = ChronoUnit.DAYS.between(today, milestone.scheduledDate)
    val containerColor = if (isOverdue) MaterialTheme.colorScheme.errorContainer
                         else MaterialTheme.colorScheme.surface

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        MilestoneCardContent(milestone, daysLeft, isOverdue)
    }
}

@Composable
private fun MilestoneCardContent(milestone: Milestone, daysLeft: Long, isOverdue: Boolean) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    milestone.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${subjectLabel(milestone.subject)} · ${milestoneTypeLabel(milestone.type)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    milestone.scheduledDate.format(DATE_FMT),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOverdue) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (milestone.status == MilestoneStatus.PENDING) {
                    Text(
                        if (daysLeft >= 0) "还有${daysLeft}天" else "已过期",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (daysLeft < 3) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        if (milestone.actualScore != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                "成绩：${milestone.actualScore} 分",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun subjectLabel(subject: Subject) = when (subject) {
    Subject.MATH -> "数学"
    Subject.CHINESE -> "语文"
    Subject.ENGLISH -> "英语"
    Subject.PHYSICS -> "物理"
    Subject.CHEMISTRY -> "化学"
    Subject.BIOLOGY -> "生物"
    Subject.HISTORY -> "历史"
    Subject.GEOGRAPHY -> "地理"
    Subject.OTHER -> "其他"
}

private fun milestoneTypeLabel(type: com.mushroom.core.domain.entity.MilestoneType) = when (type) {
    com.mushroom.core.domain.entity.MilestoneType.MINI_TEST   -> "小测"
    com.mushroom.core.domain.entity.MilestoneType.WEEKLY_TEST -> "周测"
    com.mushroom.core.domain.entity.MilestoneType.SCHOOL_EXAM -> "校测"
    com.mushroom.core.domain.entity.MilestoneType.MIDTERM     -> "期中"
    com.mushroom.core.domain.entity.MilestoneType.FINAL       -> "期末"
}
