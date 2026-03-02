package com.mushroom.feature.task.ui

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.mushroom.feature.task.viewmodel.TaskEditViewEvent
import com.mushroom.feature.task.viewmodel.TaskEditViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.LocalDate
import java.time.LocalDateTime

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
    val titleError = uiState.validationErrors["title"]
    val minutesError = uiState.validationErrors["estimatedMinutes"]
    val title = uiState.title
    val subject = uiState.subject
    val estimatedMinutes = uiState.estimatedMinutes
    val deadline = uiState.deadline
    val repeatRule = uiState.repeatRule

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (taskId == null) "新建任务" else "编辑任务") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save(date) },
                        enabled = !isSaving
                    ) {
                        if (isSaving) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = title,
                onValueChange = viewModel::updateTitle,
                label = { Text("任务名称 *") },
                modifier = Modifier.fillMaxWidth(),
                isError = titleError != null,
                supportingText = if (titleError != null) ({ Text(titleError) }) else null,
                singleLine = true
            )

            SubjectDropdown(selected = subject, onSelect = viewModel::updateSubject)

            OutlinedTextField(
                value = estimatedMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(viewModel::updateEstimatedMinutes) },
                label = { Text("预计时长（分钟）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = minutesError != null,
                supportingText = if (minutesError != null) ({ Text(minutesError) }) else null,
                singleLine = true
            )

            DeadlineSection(
                deadline = deadline,
                date = date,
                onDeadlineChange = viewModel::updateDeadline
            )

            RepeatRuleSection(selected = repeatRule, onSelect = viewModel::updateRepeatRule)

            HorizontalDivider()

            // 奖励设置区块
            RewardSection(
                useCustomReward = uiState.useCustomReward,
                baseRewardLevel = uiState.baseRewardLevel,
                baseRewardAmount = uiState.baseRewardAmount,
                hasDeadline = deadline != null,
                useCustomEarlyReward = uiState.useCustomEarlyReward,
                earlyRewardLevel = uiState.earlyRewardLevel,
                earlyRewardAmount = uiState.earlyRewardAmount,
                onToggleCustomReward = viewModel::toggleCustomReward,
                onBaseRewardLevelChange = viewModel::updateBaseRewardLevel,
                onBaseRewardAmountChange = viewModel::updateBaseRewardAmount,
                onToggleCustomEarlyReward = viewModel::toggleCustomEarlyReward,
                onEarlyRewardLevelChange = viewModel::updateEarlyRewardLevel,
                onEarlyRewardAmountChange = viewModel::updateEarlyRewardAmount
            )

            HorizontalDivider()

            // 另存为模板按钮
            OutlinedButton(
                onClick = { viewModel.saveAsTemplate() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("另存为自定义模板")
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun RewardSection(
    useCustomReward: Boolean,
    baseRewardLevel: MushroomLevel,
    baseRewardAmount: Int,
    hasDeadline: Boolean,
    useCustomEarlyReward: Boolean,
    earlyRewardLevel: MushroomLevel,
    earlyRewardAmount: Int,
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
            Text(
                "奖励设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 完成奖励
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("自定义完成奖励", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = useCustomReward, onCheckedChange = onToggleCustomReward)
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
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = baseRewardAmount.toString(),
                        onValueChange = { it.toIntOrNull()?.let(onBaseRewardAmountChange) },
                        label = { Text("×数量") },
                        modifier = Modifier.weight(0.4f),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            } else {
                Text(
                    "默认：由规则引擎自动计算（小蘑菇×1）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 提前完成奖励（仅设置了截止时间才显示）
            if (hasDeadline) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("自定义提前完成奖励", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = useCustomEarlyReward, onCheckedChange = onToggleCustomEarlyReward)
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
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = earlyRewardAmount.toString(),
                            onValueChange = { it.toIntOrNull()?.let(onEarlyRewardAmountChange) },
                            label = { Text("×数量") },
                            modifier = Modifier.weight(0.4f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                } else {
                    Text(
                        "默认：按提前分钟数自动计算",
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
private fun MushroomLevelDropdown(
    selected: MushroomLevel,
    onSelect: (MushroomLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it },
        modifier = modifier) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("蘑菇类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                MushroomLevel.SMALL,
                MushroomLevel.MEDIUM,
                MushroomLevel.LARGE,
                MushroomLevel.GOLD
            ).forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.displayName) },
                    onClick = { onSelect(level); expanded = false }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubjectDropdown(selected: Subject, onSelect: (Subject) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val subjectNames = mapOf(
        Subject.MATH to "数学", Subject.CHINESE to "语文", Subject.ENGLISH to "英语",
        Subject.PHYSICS to "物理", Subject.CHEMISTRY to "化学", Subject.BIOLOGY to "生物",
        Subject.HISTORY to "历史", Subject.GEOGRAPHY to "地理", Subject.OTHER to "其他"
    )
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = subjectNames[selected] ?: selected.name,
            onValueChange = {},
            readOnly = true,
            label = { Text("学科") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            subjectNames.forEach { (s, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelect(s); expanded = false })
            }
        }
    }
}

@Composable
private fun DeadlineSection(
    deadline: LocalDateTime?,
    date: LocalDate,
    onDeadlineChange: (LocalDateTime?) -> Unit
) {
    var enabled by remember { mutableStateOf(deadline != null) }
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("截止时间", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterVertically))
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    if (!checked) onDeadlineChange(null)
                    else onDeadlineChange(date.atTime(20, 0))
                }
            )
        }
        if (enabled) {
            val timeText = deadline?.let { "${it.hour}:${it.minute.toString().padStart(2, '0')}" } ?: ""
            Text("截止：$timeText", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary)
            Text("设置截止时间后，提前完成可获得额外蘑菇奖励 ⚡",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RepeatRuleSection(selected: RepeatRule, onSelect: (RepeatRule) -> Unit) {
    Column {
        Text("重复规则", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        listOf(
            RepeatRule.None to "不重复",
            RepeatRule.Daily to "每天",
            RepeatRule.Weekdays to "周一至周五"
        ).forEach { (rule, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected::class == rule::class,
                    onClick = { onSelect(rule) }
                )
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}
