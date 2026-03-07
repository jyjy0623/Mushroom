package com.mushroom.feature.milestone.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.mushroom.core.domain.entity.MilestoneType
import com.mushroom.core.domain.entity.ScoringRuleTemplate
import com.mushroom.core.domain.entity.Subject
import com.mushroom.feature.milestone.viewmodel.MilestoneEditViewEvent
import com.mushroom.feature.milestone.viewmodel.MilestoneEditViewModel
import com.mushroom.feature.milestone.viewmodel.ScoringRuleTemplateViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneEditScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MilestoneEditViewModel = hiltViewModel(),
    scoringRuleTemplateViewModel: ScoringRuleTemplateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scoringTemplates by scoringRuleTemplateViewModel.templates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTemplatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                MilestoneEditViewEvent.SaveSuccess -> onNavigateBack()
                is MilestoneEditViewEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
                is MilestoneEditViewEvent.ShowMessage ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val dateFmt = DateTimeFormatter.ofPattern("yyyy年MM月dd日 EEEE")
    var showDatePicker by remember { mutableStateOf(false) }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.scheduledDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val picked = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC).toLocalDate()
                        viewModel.updateScheduledDate(picked)
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (uiState.milestoneId != null) "编辑里程碑" else "新建里程碑") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 到期日期
            OutlinedTextField(
                value = uiState.scheduledDate.format(dateFmt),
                onValueChange = {},
                readOnly = true,
                label = { Text("到期日期") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("修改") }
                },
                singleLine = true,
                enabled = false
            )

            // 名称
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // 类型选择
            var typeExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = milestoneTypeLabel(uiState.type),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("类型") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                    MilestoneType.values().forEach { type ->
                        DropdownMenuItem(
                            text = { Text(milestoneTypeLabel(type)) },
                            onClick = {
                                viewModel.updateType(type)
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            // 学科选择
            var subjectExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = subjectExpanded,
                onExpandedChange = { subjectExpanded = it }
            ) {
                OutlinedTextField(
                    value = subjectLabel(uiState.subject),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("学科") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(subjectExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(expanded = subjectExpanded, onDismissRequest = { subjectExpanded = false }) {
                    Subject.values().forEach { subject ->
                        DropdownMenuItem(
                            text = { Text(subjectLabel(subject)) },
                            onClick = {
                                viewModel.updateSubject(subject)
                                subjectExpanded = false
                            }
                        )
                    }
                }
            }

            // 分数段规则卡片
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "分数段奖励配置",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Row {
                            if (scoringTemplates.isNotEmpty()) {
                                TextButton(onClick = { showTemplatePicker = true }) {
                                    Text("套用模板", style = MaterialTheme.typography.labelSmall)
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            OutlinedButton(
                                onClick = viewModel::applyDefaultRules,
                                modifier = Modifier
                            ) {
                                Text("应用默认规则", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    uiState.scoringRules.forEachIndexed { index, rule ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${rule.minScore}-${rule.maxScore}分 → ${rule.rewardConfig.level.displayName} ×",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(4.dp))
                            OutlinedTextField(
                                value = uiState.ruleAmountTexts.getOrElse(index) { rule.rewardConfig.amount.toString() },
                                onValueChange = { viewModel.updateRuleAmount(index, it) },
                                modifier = Modifier.width(64.dp),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSaving) "保存中…" else "保存")
            }
        }
    }

    // 套用模板弹窗
    if (showTemplatePicker) {
        TemplatePickerDialog(
            templates = scoringTemplates,
            onDismiss = { showTemplatePicker = false },
            onPick = { template ->
                viewModel.applyTemplateRules(template.rules, template.name)
                showTemplatePicker = false
            }
        )
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

private fun milestoneTypeLabel(type: MilestoneType) = when (type) {
    MilestoneType.MINI_TEST   -> "小测"
    MilestoneType.WEEKLY_TEST -> "周测"
    MilestoneType.SCHOOL_EXAM -> "校测"
    MilestoneType.MIDTERM     -> "期中"
    MilestoneType.FINAL       -> "期末"
}

@Composable
private fun TemplatePickerDialog(
    templates: List<ScoringRuleTemplate>,
    onDismiss: () -> Unit,
    onPick: (ScoringRuleTemplate) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择评分规则模板") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (templates.isEmpty()) {
                    Text("还没有评分规则模板", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                templates.forEach { template ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(template) }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(template.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            template.rules.forEach { rule ->
                                Text(
                                    "${rule.minScore}-${rule.maxScore}分：${rule.rewardConfig.level.displayName} × ${rule.rewardConfig.amount}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
