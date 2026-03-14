package com.mushroom.feature.task.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.foundation.clickable
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.RepeatRule
import com.mushroom.core.domain.entity.Subject
import com.mushroom.core.domain.entity.TaskTemplate
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.core.ui.R as CoreUiR
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import com.mushroom.feature.task.viewmodel.TaskEditViewEvent
import com.mushroom.feature.task.viewmodel.TaskEditViewModel
import com.mushroom.feature.task.viewmodel.TaskEditUiState
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditScreen(
    date: LocalDate = LocalDate.now(),
    onNavigateBack: () -> Unit,
    viewModel: TaskEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is TaskEditViewEvent.SaveSuccess -> onNavigateBack()
                is TaskEditViewEvent.SaveAsTemplateSuccess ->
                    snackbarHostState.showSnackbar("已保存为自定义模板")
                is TaskEditViewEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val taskId = uiState.taskId
    val isSaving = uiState.isSaving
    val isReadOnly = uiState.isReadOnly

    var selectedDate by remember { mutableStateOf(date) }
    var showDatePicker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val dateFmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            isReadOnly -> "查看任务（已完成）"
                            taskId == null -> "新建任务"
                            else -> "编辑任务"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {}
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            if (taskId == null) {
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

                val builtIn = uiState.builtInTemplates
                val custom = uiState.customTemplates
                if (builtIn.isNotEmpty() || custom.isNotEmpty()) {
                    TemplateDropdown(
                        builtInTemplates = builtIn,
                        customTemplates = custom,
                        onSelect = { template -> viewModel.applyTemplate(template, selectedDate) }
                    )
                }
            }

            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("任务名称 *") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadOnly,
                isError = uiState.validationErrors["title"] != null,
                supportingText = uiState.validationErrors["title"]?.let { { Text(it) } },
                singleLine = true
            )

            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("任务说明（选填）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadOnly,
                minLines = 2,
                maxLines = 4
            )

            SubjectDropdown(selected = uiState.subject, onSelect = viewModel::updateSubject, enabled = !isReadOnly)

            OutlinedTextField(
                value = uiState.estimatedMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateEstimatedMinutes) },
                label = { Text("预计时长（分钟）") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isReadOnly,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = uiState.validationErrors["estimatedMinutes"] != null,
                supportingText = uiState.validationErrors["estimatedMinutes"]?.let { { Text(it) } },
                singleLine = true
            )

            RepeatRuleSection(selected = uiState.repeatRule, onSelect = viewModel::updateRepeatRule, enabled = !isReadOnly)

            DeadlineSection(
                deadline = uiState.deadline,
                date = selectedDate,
                onDeadlineChange = viewModel::updateDeadline,
                enabled = !isReadOnly
            )

            HorizontalDivider()

            RewardSection(
                useCustomReward = uiState.useCustomReward,
                baseRewardLevel = uiState.baseRewardLevel,
                baseRewardAmount = uiState.baseRewardAmount,
                hasDeadline = uiState.deadline != null,
                useCustomEarlyReward = uiState.useCustomEarlyReward,
                earlyRewardLevel = uiState.earlyRewardLevel,
                earlyRewardAmount = uiState.earlyRewardAmount,
                enabled = !isReadOnly,
                onToggleCustomReward = viewModel::toggleCustomReward,
                onBaseRewardLevelChange = viewModel::updateBaseRewardLevel,
                onBaseRewardAmountChange = viewModel::updateBaseRewardAmount,
                onToggleCustomEarlyReward = viewModel::toggleCustomEarlyReward,
                onEarlyRewardLevelChange = viewModel::updateEarlyRewardLevel,
                onEarlyRewardAmountChange = viewModel::updateEarlyRewardAmount
            )

            if (!isReadOnly) {
                HorizontalDivider()
                Button(
                    onClick = { viewModel.save(selectedDate) },
                    enabled = !isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isSaving) CircularProgressIndicator(Modifier.size(16.dp))
                    else Text("保存")
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateDropdown(
    builtInTemplates: List<TaskTemplate>,
    customTemplates: List<TaskTemplate>,
    onSelect: (TaskTemplate) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    var selectedLabel by remember { mutableStateOf("（不使用模板）") }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("任务模板") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("（不使用模板）") },
                onClick = { selectedLabel = "（不使用模板）"; expanded = false }
            )
            if (builtInTemplates.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "── 系统内置 ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                builtInTemplates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.name) },
                        onClick = {
                            selectedLabel = template.name
                            expanded = false
                            onSelect(template)
                        }
                    )
                }
            }
            if (customTemplates.isNotEmpty()) {
                DropdownMenuItem(
                    text = {
                        Text(
                            "── 自定义 ──",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    onClick = {},
                    enabled = false
                )
                customTemplates.forEach { template ->
                    DropdownMenuItem(
                        text = { Text(template.name) },
                        onClick = {
                            selectedLabel = template.name
                            expanded = false
                            onSelect(template)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RewardSection(
    useCustomReward: Boolean,
    baseRewardLevel: MushroomLevel,
    baseRewardAmount: Int,
    hasDeadline: Boolean,
    useCustomEarlyReward: Boolean,
    earlyRewardLevel: MushroomLevel,
    earlyRewardAmount: Int,
    enabled: Boolean = true,
    onToggleCustomReward: (Boolean) -> Unit,
    onBaseRewardLevelChange: (MushroomLevel) -> Unit,
    onBaseRewardAmountChange: (Int) -> Unit,
    onToggleCustomEarlyReward: (Boolean) -> Unit,
    onEarlyRewardLevelChange: (MushroomLevel) -> Unit,
    onEarlyRewardAmountChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("奖励设置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自定义完成奖励", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = useCustomReward, onCheckedChange = onToggleCustomReward, enabled = enabled)
            }
            if (useCustomReward) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MushroomLevelDropdown(
                        selected = baseRewardLevel,
                        onSelect = onBaseRewardLevelChange,
                        modifier = Modifier.weight(1f),
                        enabled = enabled
                    )
                    OutlinedTextField(
                        value = baseRewardAmount.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onBaseRewardAmountChange) },
                        label = { Text("×数量") },
                        modifier = Modifier.weight(0.4f),
                        enabled = enabled,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            } else {
                Text(
                    stringResource(CoreUiR.string.task_reward_default_hint, stringResource(CoreUiR.string.level_small)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (hasDeadline) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自定义提前完成奖励", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useCustomEarlyReward, onCheckedChange = onToggleCustomEarlyReward, enabled = enabled)
                }
                if (useCustomEarlyReward) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MushroomLevelDropdown(
                            selected = earlyRewardLevel,
                            onSelect = onEarlyRewardLevelChange,
                            modifier = Modifier.weight(1f),
                            enabled = enabled
                        )
                        OutlinedTextField(
                            value = earlyRewardAmount.toString(),
                            onValueChange = { it.toIntOrNull()?.let(onEarlyRewardAmountChange) },
                            label = { Text("×数量") },
                            modifier = Modifier.weight(0.4f),
                            enabled = enabled,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                } else {
                    Text(
                        stringResource(CoreUiR.string.task_early_default_hint, stringResource(CoreUiR.string.level_small)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MushroomLevelDropdown(
    selected: MushroomLevel,
    onSelect: (MushroomLevel) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.themedDisplayName(),
            onValueChange = {},
            readOnly = true,
            label = { Text("奖励等级") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled,
            singleLine = true
        )
        if (enabled) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                listOf(MushroomLevel.SMALL, MushroomLevel.MEDIUM, MushroomLevel.LARGE, MushroomLevel.GOLD)
                    .forEach { level ->
                        DropdownMenuItem(
                            text = { Text(level.themedDisplayName()) },
                            onClick = { onSelect(level); expanded = false }
                        )
                    }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectDropdown(
    selected: Subject,
    onSelect: (Subject) -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val subjectNames = mapOf(
        Subject.MATH to "数学", Subject.CHINESE to "语文", Subject.ENGLISH to "英语",
        Subject.PHYSICS to "物理", Subject.CHEMISTRY to "化学", Subject.BIOLOGY to "生物",
        Subject.HISTORY to "历史", Subject.GEOGRAPHY to "地理", Subject.OTHER to "其他"
    )
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it }
    ) {
        OutlinedTextField(
            value = subjectNames[selected] ?: selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("学科") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded && enabled) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled
        )
        if (enabled) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                subjectNames.forEach { (s, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(s); expanded = false })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeadlineSection(
    deadline: LocalDateTime?,
    date: LocalDate,
    onDeadlineChange: (LocalDateTime?) -> Unit,
    enabled: Boolean = true
) {
    val hasDeadline = deadline != null
    var showTimePicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("截止时间", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterVertically))
            Switch(
                checked = hasDeadline,
                onCheckedChange = { checked ->
                    if (!checked) onDeadlineChange(null)
                    else onDeadlineChange(date.atTime(20, 0))
                },
                enabled = enabled
            )
        }
        if (hasDeadline && deadline != null) {
            val timeText = "${deadline.hour}:${deadline.minute.toString().padStart(2, '0')}"
            Text(
                "截止：$timeText",
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (enabled) Modifier.clickable { showTimePicker = true } else Modifier
            )
            Text(stringResource(CoreUiR.string.early_bonus_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showTimePicker && deadline != null && enabled) {
        val timePickerState = rememberTimePickerState(
            initialHour = deadline.hour,
            initialMinute = deadline.minute,
            is24Hour = true
        )
        BasicAlertDialog(onDismissRequest = { showTimePicker = false }) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("选择截止时间", style = MaterialTheme.typography.titleMedium)
                    TimePicker(state = timePickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showTimePicker = false }) { Text("取消") }
                        Button(onClick = {
                            onDeadlineChange(date.atTime(timePickerState.hour, timePickerState.minute))
                            showTimePicker = false
                        }) { Text("确定") }
                    }
                }
            }
        }
    }
}

@Composable
fun RepeatRuleSection(
    selected: RepeatRule,
    onSelect: (RepeatRule) -> Unit,
    enabled: Boolean = true
) {
    val dayLabels = mapOf(
        DayOfWeek.MONDAY to "周一", DayOfWeek.TUESDAY to "周二",
        DayOfWeek.WEDNESDAY to "周三", DayOfWeek.THURSDAY to "周四",
        DayOfWeek.FRIDAY to "周五", DayOfWeek.SATURDAY to "周六",
        DayOfWeek.SUNDAY to "周日"
    )
    val isCustom = selected is RepeatRule.Custom
    val selectedDays = (selected as? RepeatRule.Custom)?.daysOfWeek ?: emptySet()

    Column {
        Text("重复规则", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        listOf(
            RepeatRule.None to "不重复",
            RepeatRule.Daily to "每天",
            RepeatRule.Weekdays to "周一至周五",
            RepeatRule.Custom(emptySet()) to "自定义"
        ).forEach { (rule, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = when {
                        rule is RepeatRule.Custom -> isCustom
                        else -> selected::class == rule::class
                    },
                    onClick = {
                        when (rule) {
                            is RepeatRule.Custom -> onSelect(RepeatRule.Custom(selectedDays))
                            else -> onSelect(rule)
                        }
                    },
                    enabled = enabled
                )
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (isCustom) {
            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                dayLabels.forEach { (day, label) ->
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                        Checkbox(
                            checked = day in selectedDays,
                            onCheckedChange = { checked ->
                                val newDays = if (checked) selectedDays + day else selectedDays - day
                                onSelect(RepeatRule.Custom(newDays))
                            },
                            enabled = enabled
                        )
                    }
                }
            }
        }
    }
}
