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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
                is TaskEditViewEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    // 提前读出各字段，避免 lambda 内智能转型失败
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

            Spacer(Modifier.height(16.dp))
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
