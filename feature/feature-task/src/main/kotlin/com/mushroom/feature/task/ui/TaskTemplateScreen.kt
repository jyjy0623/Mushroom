package com.mushroom.feature.task.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.feature.task.viewmodel.TaskTemplateViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTemplateScreen(
    onNavigateBack: () -> Unit,
    onTemplateApplied: (Long) -> Unit,
    onNavigateToMilestoneList: () -> Unit = {},
    onNavigateToMilestoneCreate: () -> Unit = {},
    viewModel: TaskTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf<TaskTemplate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            // 里程碑 Tab 时显示新建里程碑按钮
            if (selectedTab == 2) {
                FloatingActionButton(onClick = onNavigateToMilestoneCreate) {
                    Icon(Icons.Default.Add, contentDescription = "新建里程碑")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("系统预设") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("我的模板") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 },
                    text = { Text("里程碑") })
            }

            val builtIn = uiState.builtInTemplates
            val custom = uiState.customTemplates

            when (selectedTab) {
                0 -> TemplateList(templates = builtIn, onApply = { showDatePicker = it })
                1 -> TemplateList(
                    templates = custom,
                    onApply = { showDatePicker = it },
                    emptyMessage = "还没有自定义模板"
                )
                2 -> MilestoneShortcutPanel(
                    onNavigateToList = onNavigateToMilestoneList,
                    onNavigateToCreate = onNavigateToMilestoneCreate
                )
            }
        }
    }

    // 应用日期确认对话框（简化：直接应用到今天）
    showDatePicker?.let { template ->
        AlertDialog(
            onDismissRequest = { showDatePicker = null },
            title = { Text("应用模板") },
            text = { Text("将「${template.name}」应用到今天？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.applyToDate(template, LocalDate.now())
                    showDatePicker = null
                    onTemplateApplied(template.id)
                }) { Text("应用到今天") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun MilestoneShortcutPanel(
    onNavigateToList: () -> Unit,
    onNavigateToCreate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Text(
            "考试里程碑",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            "记录期中、期末等考试，成绩录入后自动发放蘑菇奖励",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onNavigateToCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("新建里程碑")
        }
        OutlinedButton(
            onClick = onNavigateToList,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("查看所有里程碑")
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun TemplateList(
    templates: List<TaskTemplate>,
    onApply: (TaskTemplate) -> Unit,
    emptyMessage: String = "暂无模板"
) {
    if (templates.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(emptyMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(templates, key = { it.id }) { template ->
            TemplateCard(template = template, onApply = { onApply(template) })
        }
    }
}

@Composable
private fun TemplateCard(template: TaskTemplate, onApply: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge
                )
                FilledTonalButton(onClick = onApply) { Text("应用") }
            }
            if (template.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = template.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            val base = template.rewardConfig.baseReward
            Text(
                text = "基础奖励：${base.level.displayName} × ${base.amount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            template.rewardConfig.bonusReward?.let { bonus ->
                Text(
                    text = "额外奖励：${bonus.level.displayName} × ${bonus.amount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}
