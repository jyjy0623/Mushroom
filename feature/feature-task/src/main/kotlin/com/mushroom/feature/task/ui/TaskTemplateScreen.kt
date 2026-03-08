package com.mushroom.feature.task.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.domain.entity.ScoringRule
import com.mushroom.core.domain.entity.ScoringRuleTemplate
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.domain.entity.TaskTemplateType
import com.mushroom.core.domain.entity.TemplateRewardConfig
import com.mushroom.feature.task.viewmodel.ScoringRuleTemplateViewModel
import com.mushroom.feature.task.viewmodel.TaskTemplateViewModel
import kotlinx.coroutines.flow.collectLatest

private fun mushroomEmoji(level: MushroomLevel) = when (level) {
    MushroomLevel.SMALL -> "🍄"
    MushroomLevel.MEDIUM -> "🍄‍🟫"
    MushroomLevel.LARGE -> "🌟"
    MushroomLevel.GOLD -> "✨"
    MushroomLevel.LEGEND -> "👑"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskTemplateScreen(
    onNavigateBack: () -> Unit,
    onTemplateApplied: (Long) -> Unit,
    onNavigateToMilestoneList: () -> Unit = {},
    onNavigateToMilestoneCreate: () -> Unit = {},
    viewModel: TaskTemplateViewModel = hiltViewModel(),
    scoringRuleTemplateViewModel: ScoringRuleTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scoringTemplates by scoringRuleTemplateViewModel.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // Task template edit dialog state
    var showTaskTemplateDialog by remember { mutableStateOf<TaskTemplate?>(null) }
    var showNewTaskTemplateDialog by remember { mutableStateOf(false) }

    // Scoring rule template dialog state
    var showScoringTemplateDialog by remember { mutableStateOf<ScoringRuleTemplate?>(null) }
    var showNewScoringTemplateDialog by remember { mutableStateOf(false) }

    // Delete confirmation state
    var pendingDeleteTaskTemplate by remember { mutableStateOf<TaskTemplate?>(null) }
    var pendingDeleteScoringTemplate by remember { mutableStateOf<ScoringRuleTemplate?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.viewEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }
    LaunchedEffect(scoringRuleTemplateViewModel) {
        scoringRuleTemplateViewModel.viewEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("模板配置管理") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            when (selectedTab) {
                0 -> FloatingActionButton(onClick = { showNewTaskTemplateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建任务模板")
                }
                1 -> FloatingActionButton(onClick = { showNewScoringTemplateDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "新建评分规则模板")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("配置任务模板") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("配置里程碑模板") })
            }

            when (selectedTab) {
                0 -> TaskTemplateManageTab(
                    templates = uiState.builtInTemplates + uiState.customTemplates,
                    onEdit = { showTaskTemplateDialog = it },
                    onRequestDelete = { template ->
                        if (!template.isBuiltIn) {
                            pendingDeleteTaskTemplate = template
                        }
                    }
                )
                1 -> ScoringRuleTemplateTab(
                    templates = scoringTemplates,
                    onEdit = { showScoringTemplateDialog = it },
                    onRequestDelete = { pendingDeleteScoringTemplate = it },
                    onNavigateToMilestoneList = onNavigateToMilestoneList
                )
            }
        }
    }

    pendingDeleteTaskTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTaskTemplate = null },
            title = { Text("删除模板「${template.name}」") },
            text = { Text("确认删除这个任务模板吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTemplate(template.id)
                    pendingDeleteTaskTemplate = null
                }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTaskTemplate = null }) {
                    Text("取消")
                }
            }
        )
    }

    pendingDeleteScoringTemplate?.let { template ->
        AlertDialog(
            onDismissRequest = { pendingDeleteScoringTemplate = null },
            title = { Text("删除模板「${template.name}」") },
            text = { Text("确认删除这个里程碑评分模板吗？") },
            confirmButton = {
                TextButton(onClick = {
                    scoringRuleTemplateViewModel.delete(template.id)
                    pendingDeleteScoringTemplate = null
                }) {
                    Text("确认删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteScoringTemplate = null }) {
                    Text("取消")
                }
            }
        )
    }

    // Task template edit dialog (for editing existing)
    showTaskTemplateDialog?.let { template ->
        TaskTemplateEditDialog(
            template = template,
            allTemplates = uiState.builtInTemplates + uiState.customTemplates,
            onDismiss = { showTaskTemplateDialog = null },
            onSave = { updated ->
                viewModel.updateTemplate(updated)
                showTaskTemplateDialog = null
            }
        )
    }

    // Task template new dialog
    if (showNewTaskTemplateDialog) {
        TaskTemplateEditDialog(
            template = null,
            allTemplates = uiState.builtInTemplates + uiState.customTemplates,
            onDismiss = { showNewTaskTemplateDialog = false },
            onSave = { newTemplate ->
                viewModel.saveCustomTemplate(newTemplate)
                showNewTaskTemplateDialog = false
            }
        )
    }

    // Scoring rule template edit dialog
    showScoringTemplateDialog?.let { template ->
        ScoringRuleTemplateEditDialog(
            template = template,
            allTemplates = scoringTemplates,
            onDismiss = { showScoringTemplateDialog = null },
            onSave = { updated ->
                scoringRuleTemplateViewModel.save(updated)
                showScoringTemplateDialog = null
            }
        )
    }

    // Scoring rule template new dialog
    if (showNewScoringTemplateDialog) {
        ScoringRuleTemplateEditDialog(
            template = null,
            allTemplates = scoringTemplates,
            onDismiss = { showNewScoringTemplateDialog = false },
            onSave = { newTemplate ->
                scoringRuleTemplateViewModel.save(newTemplate)
                showNewScoringTemplateDialog = false
            }
        )
    }
}

// ---------------------------------------------------------------------------
// Tab[0] — 任务模板管理
// ---------------------------------------------------------------------------
@Composable
private fun TaskTemplateManageTab(
    templates: List<TaskTemplate>,
    onEdit: (TaskTemplate) -> Unit,
    onRequestDelete: (TaskTemplate) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (templates.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("暂无模板", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(templates, key = { it.id }) { template ->
            TaskTemplateManageCard(
                template = template,
                onEdit = { onEdit(template) },
                onRequestDelete = { onRequestDelete(template) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskTemplateManageCard(
    template: TaskTemplate,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onDoubleClick = onRequestDelete
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = template.name,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
            }
            Text(
                "预估时长：${template.estimatedMinutes} 分钟",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val base = template.rewardConfig.baseReward
            Text(
                "基础奖励：${mushroomEmoji(base.level)} ${base.level.displayName} × ${base.amount}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            template.rewardConfig.bonusReward?.let { bonus ->
                Text(
                    "额外奖励：${mushroomEmoji(bonus.level)} ${bonus.level.displayName} × ${bonus.amount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tab[1] — 里程碑评分规则模板管理
// ---------------------------------------------------------------------------
@Composable
private fun ScoringRuleTemplateTab(
    templates: List<ScoringRuleTemplate>,
    onEdit: (ScoringRuleTemplate) -> Unit,
    onRequestDelete: (ScoringRuleTemplate) -> Unit,
    onNavigateToMilestoneList: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (templates.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("还没有评分规则模板，点击右下角 + 新建", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(templates, key = { it.id }) { template ->
            ScoringRuleTemplateCard(
                template = template,
                onEdit = { onEdit(template) },
                onRequestDelete = { onRequestDelete(template) }
            )
        }
        item {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            OutlinedButton(
                onClick = onNavigateToMilestoneList,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("查看所有里程碑")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScoringRuleTemplateCard(
    template: ScoringRuleTemplate,
    onEdit: () -> Unit,
    onRequestDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onEdit,
                onDoubleClick = onRequestDelete
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                template.name,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.height(4.dp))
            template.rules.forEach { rule ->
                Text(
                    "${rule.minScore}-${rule.maxScore}分：${mushroomEmoji(rule.rewardConfig.level)} ${rule.rewardConfig.level.displayName} × ${rule.rewardConfig.amount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// TaskTemplateEditDialog
// ---------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskTemplateEditDialog(
    template: TaskTemplate?,
    allTemplates: List<TaskTemplate>,
    onDismiss: () -> Unit,
    onSave: (TaskTemplate) -> Unit
) {
    val isNew = template == null
    var name by remember { mutableStateOf(template?.name ?: "") }
    var estimatedMinutes by remember { mutableStateOf(template?.estimatedMinutes?.toString() ?: "30") }
    var baseLevel by remember { mutableStateOf(template?.rewardConfig?.baseReward?.level ?: MushroomLevel.SMALL) }
    var baseAmount by remember { mutableStateOf(template?.rewardConfig?.baseReward?.amount?.toString() ?: "1") }
    var enableBonus by remember { mutableStateOf(template?.rewardConfig?.bonusReward != null) }
    var bonusLevel by remember { mutableStateOf(template?.rewardConfig?.bonusReward?.level ?: MushroomLevel.SMALL) }
    var bonusAmount by remember { mutableStateOf(template?.rewardConfig?.bonusReward?.amount?.toString() ?: "1") }
    var nameError by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建任务模板" else "编辑任务模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = "" },
                    label = { Text("模板名称") },
                    singleLine = true,
                    isError = nameError.isNotEmpty(),
                    supportingText = if (nameError.isNotEmpty()) {{ Text(nameError) }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
                // 预估时长
                OutlinedTextField(
                    value = estimatedMinutes,
                    onValueChange = { estimatedMinutes = it },
                    label = { Text("预估时长（分钟）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                // 基础奖励
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MushroomLevelDropdown(
                        selected = baseLevel,
                        onSelect = { baseLevel = it },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = baseAmount,
                        onValueChange = { baseAmount = it },
                        label = { Text("×数量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(0.4f)
                    )
                }
                // 额外奖励开关
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("开启额外奖励", modifier = Modifier.weight(1f))
                    Switch(checked = enableBonus, onCheckedChange = { enableBonus = it })
                }
                if (enableBonus) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MushroomLevelDropdown(
                            selected = bonusLevel,
                            onSelect = { bonusLevel = it },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bonusAmount,
                            onValueChange = { bonusAmount = it },
                            label = { Text("×数量") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.4f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val trimmedName = name.trim()
                if (trimmedName.isBlank()) { nameError = "名称不能为空"; return@TextButton }
                val duplicate = allTemplates.any { it.name == trimmedName && it.id != (template?.id ?: -1L) }
                if (duplicate) { nameError = "名称已存在"; return@TextButton }
                val minutes = estimatedMinutes.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val baseAmt = baseAmount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val bonusAmt = bonusAmount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val saved = if (template != null) {
                    template.copy(
                        name = trimmedName,
                        estimatedMinutes = minutes,
                        rewardConfig = TemplateRewardConfig(
                            baseReward = MushroomRewardConfig(baseLevel, baseAmt),
                            bonusReward = if (enableBonus) MushroomRewardConfig(bonusLevel, bonusAmt) else null,
                            bonusCondition = template.rewardConfig.bonusCondition
                        )
                    )
                } else {
                    TaskTemplate(
                        id = 0,
                        name = trimmedName,
                        type = TaskTemplateType.CUSTOM,
                        subject = Subject.OTHER,
                        estimatedMinutes = minutes,
                        description = "",
                        defaultDeadlineOffset = null,
                        rewardConfig = TemplateRewardConfig(
                            baseReward = MushroomRewardConfig(baseLevel, baseAmt),
                            bonusReward = if (enableBonus) MushroomRewardConfig(bonusLevel, bonusAmt) else null,
                            bonusCondition = null
                        ),
                        isBuiltIn = false
                    )
                }
                onSave(saved)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MushroomLevelSelector(
    selected: MushroomLevel,
    onSelect: (MushroomLevel) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MushroomLevel.values().forEach { level ->
            val isSelected = selected == level
            FilterChip(
                selected = isSelected,
                onClick = { onSelect(level) },
                label = { Text("${mushroomEmoji(level)} ${level.displayName}") }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// ScoringRuleTemplateEditDialog
// ---------------------------------------------------------------------------
data class RuleRow(
    val minScore: String = "",
    val maxScore: String = "",
    val level: MushroomLevel = MushroomLevel.SMALL,
    val amount: String = "1"
)

@Composable
private fun ScoringRuleTemplateEditDialog(
    template: ScoringRuleTemplate?,
    allTemplates: List<ScoringRuleTemplate>,
    onDismiss: () -> Unit,
    onSave: (ScoringRuleTemplate) -> Unit
) {
    val isNew = template == null
    var name by remember { mutableStateOf(template?.name ?: "") }
    var nameError by remember { mutableStateOf("") }
    val initialRows = remember(template) {
        val existing = template?.rules?.map { rule ->
            RuleRow(
                minScore = rule.minScore.toString(),
                maxScore = rule.maxScore.toString(),
                level = rule.rewardConfig.level,
                amount = rule.rewardConfig.amount.toString()
            )
        } ?: emptyList()
        // 固定 3 个档位，不足时补空行
        (existing + List(3) { RuleRow() }).take(3)
    }
    var rows by remember { mutableStateOf(initialRows) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新建评分规则模板" else "编辑评分规则模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = "" },
                    label = { Text("模板名称") },
                    singleLine = true,
                    isError = nameError.isNotEmpty(),
                    supportingText = if (nameError.isNotEmpty()) {{ Text(nameError) }} else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("评分规则（3个档位）", style = MaterialTheme.typography.labelMedium)
                rows.forEachIndexed { index, row ->
                    ScoringRuleRowEditor(
                        row = row,
                        onRowChange = { updated ->
                            rows = rows.toMutableList().also { it[index] = updated }
                        },
                        onRemove = null
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    if (trimmedName.isBlank()) { nameError = "名称不能为空"; return@TextButton }
                    val duplicate = allTemplates.any { it.name == trimmedName && it.id != (template?.id ?: -1L) }
                    if (duplicate) { nameError = "名称已存在"; return@TextButton }
                    val rules = rows.mapNotNull { row ->
                        val min = row.minScore.toIntOrNull() ?: return@mapNotNull null
                        val max = row.maxScore.toIntOrNull() ?: return@mapNotNull null
                        val amt = row.amount.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        ScoringRule(minScore = min, maxScore = max, rewardConfig = MushroomRewardConfig(row.level, amt))
                    }
                    if (rules.isEmpty()) return@TextButton
                    val saved = ScoringRuleTemplate(
                        id = template?.id ?: 0L,
                        name = trimmedName,
                        rules = rules
                    )
                    onSave(saved)
                },
                enabled = rows.isNotEmpty()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ScoringRuleRowEditor(
    row: RuleRow,
    onRowChange: (RuleRow) -> Unit,
    onRemove: (() -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = row.minScore,
                onValueChange = { onRowChange(row.copy(minScore = it)) },
                label = { Text("最低分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            Text("-", style = MaterialTheme.typography.bodyLarge)
            OutlinedTextField(
                value = row.maxScore,
                onValueChange = { onRowChange(row.copy(maxScore = it)) },
                label = { Text("最高分") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            if (onRemove != null) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "删除规则行", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp)
        ) {
            MushroomLevelDropdown(
                selected = row.level,
                onSelect = { onRowChange(row.copy(level = it)) },
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = row.amount,
                onValueChange = { onRowChange(row.copy(amount = it)) },
                label = { Text("×数量") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(0.4f)
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}
