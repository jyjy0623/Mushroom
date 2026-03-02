package com.mushroom.feature.mushroom.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.AppealStatus
import com.mushroom.core.domain.entity.DeductionConfig
import com.mushroom.core.domain.entity.DeductionRecord
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
                        Text("扣除 ${config.mushroomLevel.displayName} × ${if (config.customAmount > 0) config.customAmount else config.defaultAmount}")
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
        topBar = { CenterAlignedTopAppBar(title = { Text("执行扣除") }) },
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
                    "${config.mushroomLevel.displayName} × $amount（每日上限 ${config.maxPerDay} 次）",
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
                        "${record.mushroomLevel.displayName} × ${record.amount}",
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeductionConfigScreen(
    viewModel: DeductionViewModel = hiltViewModel()
) {
    val configs by viewModel.configs.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is DeductionViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("扣除规则配置") }) },
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
            items(configs, key = { it.id }) { config ->
                ConfigCard(config = config, onToggle = { viewModel.toggleConfig(config) })
            }
        }
    }
}

@Composable
private fun ConfigCard(config: DeductionConfig, onToggle: () -> Unit) {
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
                Row {
                    Text(config.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                    if (config.isBuiltIn) {
                        Text(" [内置]", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    "${config.mushroomLevel.displayName} × $amount  每日上限 ${config.maxPerDay} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = config.isEnabled, onCheckedChange = { onToggle() })
        }
    }
}
