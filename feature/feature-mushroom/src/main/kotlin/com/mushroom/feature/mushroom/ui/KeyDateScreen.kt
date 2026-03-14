package com.mushroom.feature.mushroom.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.KeyDate
import com.mushroom.core.domain.entity.KeyDateCondition
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomRewardConfig
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.feature.mushroom.viewmodel.KeyDateViewModel
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.LaunchedEffect

private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

// ---------------------------------------------------------------------------
// KeyDateListScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDateListScreen(
    onNavigateBack: () -> Unit = {},
    onNavigateToEdit: (Long) -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    viewModel: KeyDateViewModel = hiltViewModel()
) {
    val keyDates by viewModel.keyDates.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var deleteTarget by remember { mutableStateOf<KeyDate?>(null) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    deleteTarget?.let { kd ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除「${kd.name}」") },
            text = { Text("确定要删除这个关键时间配置吗？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(kd.id)
                    deleteTarget = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("关键奖励时间") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCreate) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (keyDates.isEmpty()) {
                item {
                    Text(
                        "暂无关键时间，点击 + 添加",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                }
            } else {
                items(keyDates, key = { it.id }) { kd ->
                    KeyDateCard(
                        keyDate = kd,
                        onClick = { onNavigateToEdit(kd.id) },
                        onDelete = { deleteTarget = kd }
                    )
                }
            }
        }
    }
}

@Composable
private fun KeyDateCard(keyDate: KeyDate, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(keyDate.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(keyDate.date.format(DATE_FMT), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                val conditionLabel = when (val c = keyDate.condition) {
                    is KeyDateCondition.ConsecutiveCheckinDays -> "连续打卡 ${c.days} 天"
                    is KeyDateCondition.MilestoneScore -> "里程碑 ≥ ${c.minScore} 分"
                    is KeyDateCondition.ManualTrigger -> "手动触发"
                }
                Text(
                    "条件：$conditionLabel · 奖励：${keyDate.rewardConfig.level.themedDisplayName()}×${keyDate.rewardConfig.amount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// KeyDateEditScreen
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyDateEditScreen(
    keyDateId: Long = -1L,
    onNavigateBack: () -> Unit = {},
    viewModel: KeyDateViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val keyDates by viewModel.keyDates.collectAsStateWithLifecycle()
    val existing = remember(keyDateId, keyDates) {
        if (keyDateId > 0) keyDates.firstOrNull { it.id == keyDateId } else null
    }

    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var selectedDate by remember(existing) { mutableStateOf(existing?.date ?: LocalDate.now().plusDays(1)) }
    var showDatePicker by remember { mutableStateOf(false) }
    var conditionType by remember(existing) {
        mutableStateOf(when (existing?.condition) {
            is KeyDateCondition.ConsecutiveCheckinDays -> "连续打卡天数"
            is KeyDateCondition.MilestoneScore -> "里程碑得分"
            else -> "手动触发"
        })
    }
    var conditionDays by remember(existing) {
        mutableStateOf(
            ((existing?.condition as? KeyDateCondition.ConsecutiveCheckinDays)?.days ?: 30).toString()
        )
    }
    var rewardLevel by remember(existing) { mutableStateOf(existing?.rewardConfig?.level ?: MushroomLevel.SMALL) }
    var rewardAmount by remember(existing) {
        mutableStateOf((existing?.rewardConfig?.amount ?: 1).toString())
    }
    var nameError by remember { mutableStateOf(false) }
    var conditionExpanded by remember { mutableStateOf(false) }
    var levelExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { msg ->
            if (msg == "__saved__") {
                onNavigateBack()
            } else {
                snackbarHostState.showSnackbar(msg)
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atTime(12, 0)
                .toInstant(java.time.ZoneOffset.UTC).toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        val picked = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                        selectedDate = picked
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
                title = { Text(if (keyDateId > 0) "编辑关键时间" else "新建关键时间") },
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                label = { Text("名称 *") },
                modifier = Modifier.fillMaxWidth(),
                isError = nameError,
                supportingText = if (nameError) ({ Text("名称不能为空") }) else null
            )

            // 日期选择
            OutlinedTextField(
                value = selectedDate.format(DATE_FMT),
                onValueChange = {},
                readOnly = true,
                label = { Text("日期") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    TextButton(onClick = { showDatePicker = true }) { Text("修改") }
                },
                enabled = false
            )

            // 条件类型
            ExposedDropdownMenuBox(
                expanded = conditionExpanded,
                onExpandedChange = { conditionExpanded = it }
            ) {
                OutlinedTextField(
                    value = conditionType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("触发条件") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = conditionExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = conditionExpanded, onDismissRequest = { conditionExpanded = false }) {
                    listOf("连续打卡天数", "手动触发").forEach { opt ->
                        DropdownMenuItem(
                            text = { Text(opt) },
                            onClick = { conditionType = opt; conditionExpanded = false }
                        )
                    }
                }
            }

            if (conditionType == "连续打卡天数") {
                OutlinedTextField(
                    value = conditionDays,
                    onValueChange = { conditionDays = it },
                    label = { Text("连续天数") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 奖励等级
            ExposedDropdownMenuBox(
                expanded = levelExpanded,
                onExpandedChange = { levelExpanded = it }
            ) {
                OutlinedTextField(
                    value = rewardLevel.themedDisplayName(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("奖励等级") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = levelExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                ExposedDropdownMenu(expanded = levelExpanded, onDismissRequest = { levelExpanded = false }) {
                    MushroomLevel.values().forEach { lvl ->
                        DropdownMenuItem(
                            text = { Text(lvl.themedDisplayName()) },
                            onClick = { rewardLevel = lvl; levelExpanded = false }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = rewardAmount,
                onValueChange = { rewardAmount = it },
                label = { Text("奖励数量") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    val condition = when (conditionType) {
                        "连续打卡天数" -> KeyDateCondition.ConsecutiveCheckinDays(
                            conditionDays.toIntOrNull() ?: 30
                        )
                        else -> KeyDateCondition.ManualTrigger
                    }
                    val keyDate = KeyDate(
                        id = if (keyDateId > 0) keyDateId else 0,
                        name = name.trim(),
                        date = selectedDate,
                        condition = condition,
                        rewardConfig = MushroomRewardConfig(
                            level = rewardLevel,
                            amount = rewardAmount.toIntOrNull() ?: 1
                        )
                    )
                    viewModel.save(keyDate)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("保存") }
        }
    }
}
