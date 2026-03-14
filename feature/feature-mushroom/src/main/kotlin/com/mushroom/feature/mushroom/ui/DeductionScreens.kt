package com.mushroom.feature.mushroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.DeductionConfig
import com.mushroom.core.domain.entity.DeductionRecord
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.feature.mushroom.viewmodel.DeductionViewEvent
import com.mushroom.feature.mushroom.viewmodel.DeductionViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import java.time.format.DateTimeFormatter

private val DT_FMT = DateTimeFormatter.ofPattern("MM-dd HH:mm")

// ---------------------------------------------------------------------------
// DeductionRecordScreen — 执行扣除操作
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeductionRecordScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DeductionViewModel = hiltViewModel()
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDialog by remember { mutableStateOf(false) }
    var selectedConfig by remember { mutableStateOf<DeductionConfig?>(null) }
    var reasonText by remember { mutableStateOf("") }

    // 扣分红色闪烁：扣成功时背景闪一下红色，600ms 渐回透明
    var flashRed by remember { mutableStateOf(false) }
    val flashColor by animateColorAsState(
        targetValue = if (flashRed) MaterialTheme.colorScheme.error.copy(alpha = 0.25f) else Color.Transparent,
        animationSpec = tween(durationMillis = 600),
        label = "deduct_flash"
    )

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DeductionViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                DeductionViewEvent.DeductSuccess -> {
                    snackbarHostState.showSnackbar("扣除成功")
                    flashRed = true
                    delay(200)
                    flashRed = false
                }
                else -> Unit
            }
        }
    }

    if (showDialog) {
        val config = selectedConfig
        if (config != null) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text("确认扣除：${config.name}") },
                text = {
                    Column {
                        Text("扣除 ${config.mushroomLevel.themedDisplayName()} × ${if (config.customAmount > 0) config.customAmount else config.defaultAmount}")
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = reasonText,
                            onValueChange = { reasonText = it },
                            label = { Text("原因（可选）") },
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    Button(onClick = {
                        viewModel.deduct(config, reasonText.ifBlank { config.name })
                        showDialog = false
                        reasonText = ""
                    }) { Text("确认") }
                },
                dismissButton = {
                    TextButton(onClick = { showDialog = false }) { Text("取消") }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("执行扣除") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(flashColor)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            val enabled = configs.filter { it.isEnabled }
            if (enabled.isEmpty()) {
                item {
                    Text("没有启用的扣除规则，请先在配置页面启用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(enabled, key = { it.id }) { config ->
                    DeductionConfigCard(
                        config = config,
                        onDeduct = {
                            selectedConfig = config
                            showDialog = true
                        }
                    )
                }
            }
        }
        } // Box
    }
}

@Composable
private fun DeductionConfigCard(config: DeductionConfig, onDeduct: () -> Unit) {
    val amount = if (config.customAmount > 0) config.customAmount else config.defaultAmount
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(config.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    "${config.mushroomLevel.themedDisplayName()} × $amount（每日上限 ${config.maxPerDay} 次）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(onClick = onDeduct) { Text("扣除") }
        }
    }
}

// ---------------------------------------------------------------------------
// DeductionHistoryScreen — 历史记录 + 申诉
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeductionHistoryScreen(
    viewModel: DeductionViewModel = hiltViewModel()
) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var appealRecordId by remember { mutableStateOf<Long?>(null) }
    var appealText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DeductionViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                DeductionViewEvent.AppealSuccess -> snackbarHostState.showSnackbar("申诉已提交")
                else -> Unit
            }
        }
    }

    if (appealRecordId != null) {
        AlertDialog(
            onDismissRequest = { appealRecordId = null },
            title = { Text("提交申诉") },
            text = {
                OutlinedTextField(
                    value = appealText,
                    onValueChange = { appealText = it },
                    label = { Text("申诉理由") },
                    singleLine = false,
                    minLines = 2
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.appeal(appealRecordId!!, appealText)
                    appealRecordId = null
                    appealText = ""
                }) { Text("提交") }
            },
            dismissButton = {
                TextButton(onClick = { appealRecordId = null }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("扣除历史") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            if (records.isEmpty()) {
                item {
                    Text("暂无扣除记录",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(records, key = { it.id }) { record ->
                    DeductionRecordCard(
                        record = record,
                        onAppeal = { appealRecordId = record.id }
                    )
                }
            }
        }
    }
}

@Composable
private fun DeductionRecordCard(record: DeductionRecord, onAppeal: () -> Unit) {
    val canAppeal = record.appealStatus == AppealStatus.NONE
    val statusLabel = when (record.appealStatus) {
        AppealStatus.NONE -> ""
        AppealStatus.PENDING -> "申诉中"
        AppealStatus.APPROVED -> "申诉通过"
        AppealStatus.REJECTED -> "申诉驳回"
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(record.reason, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Text(
                        "${record.mushroomLevel.themedDisplayName()} × ${record.amount}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Text(
                        record.recordedAt.format(DT_FMT),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (statusLabel.isNotEmpty()) {
                    Text(statusLabel, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary)
                }
            }
            if (canAppeal) {
                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onAppeal) { Text("申诉") }
            }
            record.appealNote?.let { note ->
                Text("申诉说明：$note", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// DeductionConfigScreen — 配置扣除规则
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DeductionConfigScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: DeductionViewModel = hiltViewModel()
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 新建/编辑对话框状态
    var showEditDialog by remember { mutableStateOf(false) }
    var editingConfig by remember { mutableStateOf<DeductionConfig?>(null) }  // null = 新建
    // 删除确认对话框
    var pendingDeleteConfig by remember { mutableStateOf<DeductionConfig?>(null) }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DeductionViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    // 删除确认对话框
    pendingDeleteConfig?.let { config ->
        AlertDialog(
            onDismissRequest = { pendingDeleteConfig = null },
            title = { Text("删除规则") },
            text = { Text("确定删除自定义规则「${config.name}」？") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteConfig(config)
                    pendingDeleteConfig = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteConfig = null }) { Text("取消") }
            }
        )
    }

    // 新建/编辑对话框
    if (showEditDialog) {
        ConfigEditDialog(
            initial = editingConfig,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, level, amount, maxPerDay ->
                val existing = editingConfig
                if (existing == null) {
                    viewModel.createConfig(name, level, amount, maxPerDay)
                } else {
                    viewModel.updateCustomConfig(
                        existing.copy(
                            name = name,
                            mushroomLevel = level,
                            defaultAmount = amount,
                            maxPerDay = maxPerDay
                        )
                    )
                }
                showEditDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("扣除规则配置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                editingConfig = null
                showEditDialog = true
            }) {
                Icon(Icons.Default.Add, contentDescription = "新建规则")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val builtIn = configs.filter { it.isBuiltIn }
        val custom = configs.filter { !it.isBuiltIn }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // 内置规则分组
            item {
                Text("内置规则", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            items(builtIn, key = { it.id }) { config ->
                ConfigCard(
                    config = config,
                    onToggle = { viewModel.toggleConfig(config) },
                    onEdit = null,
                    onDelete = null
                )
            }

            // 自定义规则分组
            item {
                Spacer(Modifier.height(4.dp))
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))
                Text("自定义规则（长按编辑，双击删除）",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            if (custom.isEmpty()) {
                item {
                    Text("暂无自定义规则，点击右下角 + 新建",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp))
                }
            } else {
                items(custom, key = { it.id }) { config ->
                    ConfigCard(
                        config = config,
                        onToggle = { viewModel.toggleConfig(config) },
                        onEdit = {
                            editingConfig = config
                            showEditDialog = true
                        },
                        onDelete = { pendingDeleteConfig = config }
                    )
                }
            }

            item { Spacer(Modifier.height(72.dp)) }  // FAB 留白
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConfigCard(
    config: DeductionConfig,
    onToggle: () -> Unit,
    onEdit: (() -> Unit)?,
    onDelete: (() -> Unit)?
) {
    val amount = if (config.customAmount > 0) config.customAmount else config.defaultAmount
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onEdit != null && onDelete != null)
                    Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = onEdit,
                        onDoubleClick = onDelete
                    )
                else Modifier
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(config.name, style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium)
                    if (!config.isBuiltIn) {
                        Spacer(Modifier.width(4.dp))
                        Text("[自定义]", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary)
                    }
                }
                Text(
                    "${config.mushroomLevel.themedDisplayName()} × $amount  每日上限 ${config.maxPerDay} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = config.isEnabled, onCheckedChange = { onToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfigEditDialog(
    initial: DeductionConfig?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, level: MushroomLevel, amount: Int, maxPerDay: Int) -> Unit
) {
    val isEdit = initial != null
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var level by remember { mutableStateOf(initial?.mushroomLevel ?: MushroomLevel.SMALL) }
    var amountText by remember { mutableStateOf((if (initial != null && initial.defaultAmount > 0) initial.defaultAmount else 1).toString()) }
    var maxPerDayText by remember { mutableStateOf((initial?.maxPerDay ?: 1).toString()) }
    var levelExpanded by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    val levels = MushroomLevel.values().map { it to it.themedDisplayName() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEdit) "编辑规则" else "新建规则") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // 规则名称
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; nameError = false },
                    label = { Text("规则名称 *") },
                    singleLine = true,
                    isError = nameError,
                    supportingText = if (nameError) ({ Text("名称不能为空") }) else null,
                    modifier = Modifier.fillMaxWidth()
                )
                // 蘑菇等级
                ExposedDropdownMenuBox(
                    expanded = levelExpanded,
                    onExpandedChange = { levelExpanded = it }
                ) {
                    OutlinedTextField(
                        value = levels.first { it.first == level }.second,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("奖励等级") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(levelExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                        singleLine = true
                    )
                    ExposedDropdownMenu(
                        expanded = levelExpanded,
                        onDismissRequest = { levelExpanded = false }
                    ) {
                        levels.forEach { (l, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { level = l; levelExpanded = false }
                            )
                        }
                    }
                }
                // 扣除数量 & 每日上限
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("扣除数量") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = maxPerDayText,
                        onValueChange = { maxPerDayText = it },
                        label = { Text("每日上限") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (name.isBlank()) { nameError = true; return@Button }
                val amount = amountText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                val maxPerDay = maxPerDayText.toIntOrNull()?.coerceIn(1, 10) ?: 1
                onConfirm(name.trim(), level, amount, maxPerDay)
            }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
