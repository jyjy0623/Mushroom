package com.mushroom.feature.milestone.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilestoneEditScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: MilestoneEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                MilestoneEditViewEvent.SaveSuccess -> onNavigateBack()
                is MilestoneEditViewEvent.ShowError ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("新建里程碑") },
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
                        OutlinedButton(
                            onClick = viewModel::applyDefaultRules,
                            modifier = Modifier
                        ) {
                            Text("应用默认规则", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    uiState.scoringRules.filter { it.rewardConfig.amount > 0 }.forEach { rule ->
                        Text(
                            "${rule.minScore}-${rule.maxScore}分 → " +
                                "${rule.rewardConfig.level.displayName}×${rule.rewardConfig.amount}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                    uiState.scoringRules.firstOrNull { it.rewardConfig.amount == 0 }?.let { rule ->
                        Text(
                            "${rule.minScore}-${rule.maxScore}分 → 无奖励",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = viewModel::save,
                enabled = !uiState.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (uiState.isSaving) "保存中…" else "保存里程碑（需家长确认）")
            }
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

private fun milestoneTypeLabel(type: MilestoneType) = when (type) {
    MilestoneType.MINI_TEST   -> "小测"
    MilestoneType.WEEKLY_TEST -> "周测"
    MilestoneType.SCHOOL_EXAM -> "校测"
    MilestoneType.MIDTERM     -> "期中"
    MilestoneType.FINAL       -> "期末"
}
