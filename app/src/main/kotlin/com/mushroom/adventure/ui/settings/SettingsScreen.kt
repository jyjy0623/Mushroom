package com.mushroom.adventure.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.mushroom.adventure.BuildConfig
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCheckUpdate: () -> Unit = {},
    onNavigateToDeductionConfig: () -> Unit = {},
    onNavigateToDeductionRecord: () -> Unit = {},
    onNavigateToKeyDateList: () -> Unit = {},
    onNavigateToGame: () -> Unit = {},
    onNavigateToTemplateManage: () -> Unit = {},
    onNavigateToLogin: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }

    // 系统分享 sheet
    val shareLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { /* no result needed */ }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is SettingsViewEvent.ShareFile -> shareLauncher.launch(event.intent)
                is SettingsViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("设置") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // -------------------------------------------------------
            // 账号
            // -------------------------------------------------------
            Text(
                "账号",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                AccountItem(
                    viewModel = viewModel,
                    onNavigateToLogin = onNavigateToLogin,
                    onNavigateToProfile = onNavigateToProfile
                )
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------------
            // 家长管理
            // -------------------------------------------------------
            Text(
                "家长管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Delete,
                    title = "扣除规则配置",
                    subtitle = "启用或关闭扣分规则，设置每日上限",
                    onClick = onNavigateToDeductionConfig
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Star,
                    title = "执行扣除",
                    subtitle = "对已启用的扣分规则执行扣分操作",
                    onClick = onNavigateToDeductionRecord
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.DateRange,
                    title = "关键奖励时间",
                    subtitle = "配置特殊日期里程碑奖励（如生日、假期结束）",
                    onClick = onNavigateToKeyDateList
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Edit,
                    title = "模板配置管理",
                    subtitle = "编辑任务模板奖励，管理里程碑评分规则套餐",
                    onClick = onNavigateToTemplateManage
                )
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------------
            // 数据管理
            // -------------------------------------------------------
            Text(
                "数据管理",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                SettingsItem(
                    icon = Icons.Default.Share,
                    title = "导出备份",
                    subtitle = "将所有任务、打卡、蘑菇记录导出为 JSON 文件",
                    onClick = { viewModel.exportBackup() }
                )
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Build,
                    title = "导出诊断日志",
                    subtitle = "生成包含错误日志的诊断包，用于排查问题",
                    onClick = { viewModel.exportDiagnostics() }
                )
            }

            Spacer(Modifier.height(24.dp))

            // -------------------------------------------------------
            // 网络功能
            // -------------------------------------------------------
            Text(
                "网络功能",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                ServerConnectionItem(viewModel = viewModel)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ServerUrlItem(viewModel = viewModel)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                CloudBackupItem(viewModel = viewModel, onNavigateToLogin = onNavigateToLogin)
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                CloudRestoreItem(viewModel = viewModel, onNavigateToLogin = onNavigateToLogin)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "关于",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // 连续点击3次"蘑菇大冒险"触发游戏调试入口
                    var tapCount by remember { mutableIntStateOf(0) }
                    var lastTapMs by remember { mutableLongStateOf(0L) }
                    Text(
                        "蘑菇大冒险",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            val now = System.currentTimeMillis()
                            if (now - lastTapMs < 1000) {
                                tapCount++
                            } else {
                                tapCount = 1
                            }
                            lastTapMs = now
                            if (tapCount >= 3) {
                                tapCount = 0
                                onNavigateToGame()
                            }
                        }
                    )
                    Text("版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                SettingsItem(
                    icon = Icons.Default.Refresh,
                    title = "检查更新",
                    subtitle = "当前版本 ${BuildConfig.VERSION_NAME}，点击检测是否有新版本",
                    onClick = onCheckUpdate
                )
            }
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun ServerConnectionItem(viewModel: SettingsViewModel) {
    val healthState by viewModel.serverHealthState.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (!healthState.isLoading) {
                    viewModel.checkServerHealth()
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (healthState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                imageVector = if (healthState.isConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (healthState.isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "服务器连接",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${healthState.message} ${if (healthState.latency > 0) "(${healthState.latency}ms)" else ""}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "检测",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun ServerUrlItem(viewModel: SettingsViewModel) {
    val currentUrl by viewModel.currentServerUrl.collectAsStateWithLifecycle()
    var showEditDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showEditDialog = true }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "服务器地址",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                currentUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showEditDialog) {
        ServerUrlEditDialog(
            currentUrl = currentUrl,
            onDismiss = { showEditDialog = false },
            onConfirm = { newUrl ->
                viewModel.updateServerUrl(newUrl)
                showEditDialog = false
            },
            onReset = {
                viewModel.resetServerUrl()
                showEditDialog = false
            }
        )
    }
}

@Composable
private fun ServerUrlEditDialog(
    currentUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onReset: () -> Unit
) {
    var urlText by remember { mutableStateOf(currentUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改服务器地址") },
        text = {
            Column {
                Text(
                    "输入服务器地址（如 http://192.168.1.100:8080）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("http://host:port") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(urlText) },
                enabled = urlText.isNotBlank()
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) {
                    Text("恢复默认")
                }
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        }
    )
}

@Composable
private fun CloudBackupItem(viewModel: SettingsViewModel, onNavigateToLogin: () -> Unit) {
    val state by viewModel.cloudBackupState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !state.isUploading) {
                if (isLoggedIn) viewModel.uploadCloudBackup() else onNavigateToLogin()
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isUploading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Default.Share,
                contentDescription = null,
                tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "云端备份",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isLoggedIn) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (!isLoggedIn) "请先登录"
                else if (state.isUploading) "正在上传..."
                else if (state.lastUploadTime != null) "上次备份：${state.lastUploadTime}"
                else "将数据备份到云端服务器",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudRestoreItem(viewModel: SettingsViewModel, onNavigateToLogin: () -> Unit) {
    val state by viewModel.cloudBackupState.collectAsStateWithLifecycle()
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    var showRestoreDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isLoggedIn) {
                    viewModel.loadCloudBackupList()
                    showRestoreDialog = true
                } else {
                    onNavigateToLogin()
                }
            }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.isDownloading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "云端恢复",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isLoggedIn) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                if (!isLoggedIn) "请先登录"
                else if (state.isDownloading) "正在恢复..."
                else "从云端备份恢复数据",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }

    if (showRestoreDialog) {
        CloudRestoreDialog(
            state = state,
            onDismiss = { showRestoreDialog = false },
            onRestore = { backupId ->
                viewModel.restoreCloudBackup(backupId)
                showRestoreDialog = false
            },
            onDelete = { backupId -> viewModel.deleteCloudBackup(backupId) }
        )
    }
}

@Composable
private fun CloudRestoreDialog(
    state: CloudBackupState,
    onDismiss: () -> Unit,
    onRestore: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    var confirmRestoreId by remember { mutableStateOf<Int?>(null) }
    var confirmDeleteId by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择云端备份") },
        text = {
            if (state.isLoadingList) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("加载中...", style = MaterialTheme.typography.bodySmall)
                }
            } else if (state.error != null) {
                Text("加载失败：${state.error}", color = MaterialTheme.colorScheme.error)
            } else if (state.backupList.isEmpty()) {
                Text("暂无云端备份", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    state.backupList.forEach { backup ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "备份时间：${backup.exportedAt}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    "任务数：${backup.taskCount}，大小：${backup.sizeBytes / 1024}KB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row {
                                    TextButton(onClick = { confirmRestoreId = backup.id }) {
                                        Text("恢复")
                                    }
                                    TextButton(onClick = { confirmDeleteId = backup.id }) {
                                        Text("删除", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        },
        dismissButton = {}
    )

    confirmRestoreId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmRestoreId = null },
            title = { Text("确认恢复") },
            text = { Text("恢复将替换当前所有数据，此操作不可撤销。确定继续吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestoreId = null
                    onRestore(id)
                }) { Text("确定恢复") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestoreId = null }) { Text("取消") }
            }
        )
    }

    confirmDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            title = { Text("确认删除") },
            text = { Text("删除后无法恢复，确定要删除这条云端备份吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteId = null
                    onDelete(id)
                }) { Text("确定删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun AccountItem(
    viewModel: SettingsViewModel,
    onNavigateToLogin: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val isLoggedIn by viewModel.isLoggedIn.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (isLoggedIn) onNavigateToProfile() else onNavigateToLogin() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Star,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isLoggedIn) currentUser?.nickname ?: "已登录" else "点击登录",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (isLoggedIn) "管理账号信息" else "登录后可使用云备份和排行榜",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
    }
}
