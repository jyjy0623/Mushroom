package com.mushroom.adventure.ui.settings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.data.backup.BackupService
import com.mushroom.core.logging.LogExporter
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "SETTINGS"

sealed class SettingsViewEvent {
    data class ShareFile(val intent: Intent) : SettingsViewEvent()
    data class ShowSnackbar(val message: String) : SettingsViewEvent()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupService: BackupService,
    private val logExporter: LogExporter
) : ViewModel() {

    private val _viewEvent = MutableSharedFlow<SettingsViewEvent>()
    val viewEvent: SharedFlow<SettingsViewEvent> = _viewEvent.asSharedFlow()

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
                val file = logExporter.export()
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
                _viewEvent.emit(SettingsViewEvent.ShareFile(Intent.createChooser(intent, "分享诊断日志")))
            }.onFailure {
                MushroomLogger.e(TAG, "Export diagnostics failed", it)
                _viewEvent.emit(SettingsViewEvent.ShowSnackbar("诊断日志导出失败：${it.message}"))
            }
        }
    }
}
