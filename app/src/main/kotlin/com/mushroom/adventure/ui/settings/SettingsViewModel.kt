package com.mushroom.adventure.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.core.network.config.ServerUrlManager
import com.mushroom.adventure.core.network.repository.ServerHealthRepository
import com.mushroom.core.data.backup.BackupService
import com.mushroom.core.logging.LogExporter
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

private const val TAG = "SETTINGS"

sealed class SettingsViewEvent {
    data class ShareFile(val intent: Intent) : SettingsViewEvent()
    data class ShowSnackbar(val message: String) : SettingsViewEvent()
}

data class ServerHealthState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val message: String = "未测试",
    val latency: Long = 0L
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupService: BackupService,
    private val logExporter: LogExporter,
    private val serverHealthRepository: ServerHealthRepository,
    val serverUrlManager: ServerUrlManager
) : ViewModel() {

    private val _viewEvent = MutableSharedFlow<SettingsViewEvent>()
    val viewEvent: SharedFlow<SettingsViewEvent> = _viewEvent.asSharedFlow()

    private val _serverHealthState = MutableStateFlow(ServerHealthState())
    val serverHealthState: StateFlow<ServerHealthState> = _serverHealthState.asStateFlow()

    val currentServerUrl: StateFlow<String> = serverUrlManager.currentUrl

    fun updateServerUrl(url: String) {
        serverUrlManager.updateUrl(url)
        // 重置连接状态，提示用户重新检测
        _serverHealthState.value = ServerHealthState(message = "地址已更新，点击检测")
    }

    fun resetServerUrl() {
        serverUrlManager.resetToDefault()
        _serverHealthState.value = ServerHealthState(message = "已恢复默认地址，点击检测")
    }

    fun exportBackup() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val file = backupService.export()
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "*/*"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _viewEvent.emit(SettingsViewEvent.ShareFile(Intent.createChooser(intent, "分享备份文件")))
            }.onFailure {
                MushroomLogger.e(TAG, "Export backup failed", it)
                _viewEvent.emit(SettingsViewEvent.ShowSnackbar("备份导出失败：${it.message}"))
            }
        }
    }

    fun exportDiagnostics() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cacheFile = logExporter.export()

                // 将 ZIP 复制到公共下载目录，解决鸿蒙/微信不支持 content:// URI 的问题
                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                downloadsDir.mkdirs()
                val publicFile = File(downloadsDir, cacheFile.name)
                cacheFile.copyTo(publicFile, overwrite = true)

                // 优先用公共文件路径分享（微信/鸿蒙兼容）；同时保留 FileProvider URI 授权
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    cacheFile
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/zip"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_TEXT, "文件已同步保存至：下载/${publicFile.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                _viewEvent.emit(
                    SettingsViewEvent.ShareFile(
                        Intent.createChooser(intent, "分享诊断日志")
                    )
                )
            }.onFailure {
                MushroomLogger.e(TAG, "Export diagnostics failed", it)
                _viewEvent.emit(SettingsViewEvent.ShowSnackbar("诊断日志导出失败：${it.message}"))
            }
        }
    }

    fun checkServerHealth() {
        viewModelScope.launch {
            _serverHealthState.value = _serverHealthState.value.copy(isLoading = true)
            val startTime = System.currentTimeMillis()

            serverHealthRepository.checkHealth()
                .onSuccess { response ->
                    val latency = System.currentTimeMillis() - startTime
                    _serverHealthState.value = ServerHealthState(
                        isLoading = false,
                        isConnected = true,
                        message = "连接成功 (${response.status})",
                        latency = latency
                    )
                    MushroomLogger.d(TAG, "Server health check passed: ${response.status} (${latency}ms)")
                }
                .onFailure { error ->
                    val latency = System.currentTimeMillis() - startTime
                    _serverHealthState.value = ServerHealthState(
                        isLoading = false,
                        isConnected = false,
                        message = "连接失败: ${error.message}",
                        latency = latency
                    )
                    MushroomLogger.e(TAG, "Server health check failed", error)
                }
        }
    }
}
