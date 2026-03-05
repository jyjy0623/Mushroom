package com.mushroom.adventure.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.milestone.viewmodel.MilestoneEditViewEvent
import com.mushroom.feature.milestone.viewmodel.MilestoneEditViewModel
import com.mushroom.feature.task.ui.DeadlineSection
import com.mushroom.feature.task.ui.MushroomLevelDropdown
import com.mushroom.feature.task.ui.RepeatRuleSection
import com.mushroom.feature.task.ui.RewardSection
import com.mushroom.feature.task.ui.SubjectDropdown
import com.mushroom.feature.task.ui.TemplateDropdown
import com.mushroom.feature.task.viewmodel.TaskEditViewEvent
import com.mushroom.feature.task.viewmodel.TaskEditViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 新建任务/里程碑合并入口页。
 * 顶部共享日期字段，TabRow 切换「任务」/「里程碑」两个表单。
 * 编辑/只读模式（taskId != null）由 TaskEditScreen 单独处理，不经过此页面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScreen(
    date: LocalDate = LocalDate.now(),
    initialTab: Int = 0,
    onNavigateBack: () -> Unit,
    taskViewModel: TaskEditViewModel = hiltViewModel(),
    milestoneViewModel: MilestoneEditViewModel = hiltViewModel()
) {
    val taskUiState by taskViewModel.uiState.collectAsStateWithLifecycle()
    val milestoneUiState by milestoneViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedDate by remember { mutableStateOf(date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val dateFmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    // 日期变化时同步到里程碑 ViewModel
    LaunchedEffect(selectedDate) {
        milestoneViewModel.updateScheduledDate(selectedDate)
    }

    LaunchedEffect(Unit) {
        taskViewModel.viewEvent.collectLatest { event ->
            when (event) {
                is TaskEditViewEvent.SaveSuccess -> onNavigateBack()
                is TaskEditViewEvent.SaveAsTemplateSuccess ->
                    snackbarHostState.showSnackbar("已保存为自定义模板")
                is TaskEditViewEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    LaunchedEffect(Unit) {
        milestoneViewModel.viewEvent.collectLatest { event ->
            when (event) {
                MilestoneEditViewEvent.SaveSuccess -> onNavigateBack()
                is MilestoneEditViewEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("新建任务或里程碑") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 任务 Tab：顶部保存按钮
                    if (selectedTab == 0) {
                        val isSaving = taskUiState.isSaving
                        TextButton(
                            onClick = { taskViewModel.save(selectedDate) },
                            enabled = !isSaving
                        ) {
                            if (isSaving) CircularProgressIndicator(Modifier.size(16.dp))
                            else Text("保存")
                        }
                    }
                    // 里程碑 Tab：无顶部按钮（页内全宽按钮）
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate
                    .atStartOfDay(ZoneId.of("Asia/Shanghai"))
                    .toInstant().toEpochMilli()
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val picked = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.of("Asia/Shanghai"))
                                .toLocalDate()
                            if (!picked.isBefore(today)) selectedDate = picked
                        }
                        showDatePicker = false
                    }) { Text("确定") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("取消") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ── 共享日期字段 ──
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = selectedDate.format(dateFmt),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("日期") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true },
                    trailingIcon = {
                        TextButton(onClick = { showDatePicker = true }) { Text("修改") }
                    },
                    singleLine = true,
                    enabled = false
                )
                Spacer(Modifier.height(8.dp))
            }

            // ── Tab 栏 ──
            TabRow(selectedTabIndex = selectedTab) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("任务") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("里程碑") }
                )
            }

            // ── Tab 内容 ──
            when (selectedTab) {
                0 -> {
                    // 任务表单
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        val builtIn = taskUiState.builtInTemplates
                        val custom = taskUiState.customTemplates
                        if (builtIn.isNotEmpty() || custom.isNotEmpty()) {
                            TemplateDropdown(
                                builtInTemplates = builtIn,
                                customTemplates = custom,
                                onSelect = { template ->
                                    taskViewModel.applyTemplate(template, selectedDate)
                                }
                            )
                        }

                        OutlinedTextField(
                            value = taskUiState.title,
                            onValueChange = taskViewModel::updateTitle,
                            label = { Text("任务名称 *") },
                            modifier = Modifier.fillMaxWidth(),
                            isError = taskUiState.validationErrors["title"] != null,
                            supportingText = taskUiState.validationErrors["title"]?.let { { Text(it) } },
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = taskUiState.description,
                            onValueChange = taskViewModel::updateDescription,
                            label = { Text("任务说明（选填）") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            maxLines = 4
                        )

                        SubjectDropdown(
                            selected = taskUiState.subject,
                            onSelect = taskViewModel::updateSubject
                        )

                        OutlinedTextField(
                            value = taskUiState.estimatedMinutes.toString(),
                            onValueChange = { it.toIntOrNull()?.let(taskViewModel::updateEstimatedMinutes) },
                            label = { Text("预计时长（分钟）") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            isError = taskUiState.validationErrors["estimatedMinutes"] != null,
                            supportingText = taskUiState.validationErrors["estimatedMinutes"]
                                ?.let { { Text(it) } },
                            singleLine = true
                        )

                        DeadlineSection(
                            deadline = taskUiState.deadline,
                            date = selectedDate,
                            onDeadlineChange = taskViewModel::updateDeadline
                        )

                        RepeatRuleSection(
                            selected = taskUiState.repeatRule,
                            onSelect = taskViewModel::updateRepeatRule
                        )

                        HorizontalDivider()

                        RewardSection(
                            useCustomReward = taskUiState.useCustomReward,
                            baseRewardLevel = taskUiState.baseRewardLevel,
                            baseRewardAmount = taskUiState.baseRewardAmount,
                            hasDeadline = taskUiState.deadline != null,
                            useCustomEarlyReward = taskUiState.useCustomEarlyReward,
                            earlyRewardLevel = taskUiState.earlyRewardLevel,
                            earlyRewardAmount = taskUiState.earlyRewardAmount,
                            onToggleCustomReward = taskViewModel::toggleCustomReward,
                            onBaseRewardLevelChange = taskViewModel::updateBaseRewardLevel,
                            onBaseRewardAmountChange = taskViewModel::updateBaseRewardAmount,
                            onToggleCustomEarlyReward = taskViewModel::toggleCustomEarlyReward,
                            onEarlyRewardLevelChange = taskViewModel::updateEarlyRewardLevel,
                            onEarlyRewardAmountChange = taskViewModel::updateEarlyRewardAmount
                        )

                        HorizontalDivider()

                        OutlinedButton(
                            onClick = { taskViewModel.saveAsTemplate() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("另存为自定义模板")
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }

                1 -> {
                    // 里程碑表单
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Spacer(Modifier.height(4.dp))

                        OutlinedTextField(
                            value = milestoneUiState.name,
                            onValueChange = milestoneViewModel::updateName,
                            label = { Text("名称 *") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        // 类型下拉
                        var typeExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = typeExpanded,
                            onExpandedChange = { typeExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = milestoneTypeLabel(milestoneUiState.type),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("类型") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = typeExpanded,
                                onDismissRequest = { typeExpanded = false }
                            ) {
                                MilestoneType.values().forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(milestoneTypeLabel(type)) },
                                        onClick = {
                                            milestoneViewModel.updateType(type)
                                            typeExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 学科下拉
                        var subjectExpanded by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            expanded = subjectExpanded,
                            onExpandedChange = { subjectExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = milestoneSubjectLabel(milestoneUiState.subject),
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("学科") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded)
                                },
                                modifier = Modifier.fillMaxWidth().menuAnchor()
                            )
                            ExposedDropdownMenu(
                                expanded = subjectExpanded,
                                onDismissRequest = { subjectExpanded = false }
                            ) {
                                Subject.values().forEach { subject ->
                                    DropdownMenuItem(
                                        text = { Text(milestoneSubjectLabel(subject)) },
                                        onClick = {
                                            milestoneViewModel.updateSubject(subject)
                                            subjectExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // 分数段奖励配置
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "分数段奖励配置",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                    OutlinedButton(onClick = milestoneViewModel::applyDefaultRules) {
                                        Text(
                                            "应用默认规则",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                milestoneUiState.scoringRules.forEachIndexed { index, rule ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "${rule.minScore}-${rule.maxScore}分 → " +
                                                "${rule.rewardConfig.level.displayName} ×",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedTextField(
                                            value = milestoneUiState.ruleAmountTexts
                                                .getOrElse(index) {
                                                    rule.rewardConfig.amount.toString()
                                                },
                                            onValueChange = { v ->
                                                milestoneViewModel.updateRuleAmount(index, v)
                                            },
                                            modifier = Modifier
                                                .padding(start = 4.dp)
                                                .size(width = 64.dp, height = 52.dp),
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Number
                                            ),
                                            textStyle = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }
                            }
                        }

                        Button(
                            onClick = milestoneViewModel::save,
                            enabled = !milestoneUiState.isSaving,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                if (milestoneUiState.isSaving) "保存中…"
                                else "保存里程碑（需家长确认）"
                            )
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

private fun milestoneTypeLabel(type: MilestoneType) = when (type) {
    MilestoneType.MINI_TEST   -> "小测"
    MilestoneType.WEEKLY_TEST -> "周测"
    MilestoneType.SCHOOL_EXAM -> "校测"
    MilestoneType.MIDTERM     -> "期中"
    MilestoneType.FINAL       -> "期末"
}

private fun milestoneSubjectLabel(subject: Subject) = when (subject) {
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
