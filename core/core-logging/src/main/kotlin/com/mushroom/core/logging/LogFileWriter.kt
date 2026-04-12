package com.mushroom.core.logging

import android.content.Context
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 日志文件写入器。
 *
 * - 总大小上限：512 KB（MAX_TOTAL_SIZE_KB）
 * - 保留天数：最近 2 天（MAX_RETAIN_DAYS）
 * - 每次写入前检查并滚动/清理
 * - 文件名格式：app_log_yyyyMMdd.txt
 */
open class LogFileWriter(private val context: Context) {

    companion object {
        const val MAX_TOTAL_SIZE_KB = 512L
        const val MAX_RETAIN_DAYS = 2L
        internal val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    }

    protected open val logDir: File
        get() = File(context.filesDir, "logs").also { it.mkdirs() }

    private val todayFile: File
        get() = File(logDir, "app_log_${LocalDate.now().format(DATE_FORMAT)}.txt")

    /** 从 Context 获取当前 App 版本字符串 */
    protected open val appVersion: String
        get() = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (_: Exception) {
            "?"
        }

    /**
     * 写入一行日志到当日文件。
     * 每次写入后检查是否需要清理。
     */
    fun write(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        try {
            val timestamp = LocalDateTime.now().format(TIME_FORMAT)
            val sb = StringBuilder()
            sb.append("$timestamp [${appVersion}] ${level.name}/$tag: $message")
            throwable?.let {
                sb.append("\n")
                sb.append(it.stackTraceToString())
            }
            sb.append("\n")

            todayFile.appendText(sb.toString(), Charsets.UTF_8)
            purgeAndRotate()
        } catch (_: Exception) {
            // 日志写入失败不能向外抛异常
        }
    }

    /**
     * 写入 SESSION START 分隔标记（每次 App 启动调用一次）。
     */
    fun writeSessionStart() {
        try {
            val timestamp = LocalDateTime.now().format(TIME_FORMAT)
            val marker = "========== SESSION START @ $timestamp [${appVersion}] ==========\n"
            todayFile.appendText(marker, Charsets.UTF_8)
        } catch (_: Exception) {}
    }

    /**
     * 两步清理：
     * 1. 删除超过保留天数的日志文件
     * 2. 若总大小超过上限，删除最旧的文件，直到满足要求
     */
    fun purgeAndRotate() {
        try {
            val today = LocalDate.now()
            val files = logDir.listFiles()
                ?.filter { it.name.startsWith("app_log_") && it.name.endsWith(".txt") }
                ?.sortedBy { it.name }
                ?: return

            // Step 1: 删除超过保留天数的文件
            val retentionThreshold = today.minusDays(MAX_RETAIN_DAYS)
            files.filter { file ->
                try {
                    val dateStr = file.name.removePrefix("app_log_").removeSuffix(".txt")
                    val fileDate = LocalDate.parse(dateStr, DATE_FORMAT)
                    fileDate.isBefore(retentionThreshold)
                } catch (_: Exception) {
                    false
                }
            }.forEach { it.delete() }

            // Step 2: 总大小超限则删除最旧文件
            val remaining = logDir.listFiles()
                ?.filter { it.name.startsWith("app_log_") && it.name.endsWith(".txt") }
                ?.sortedBy { it.name }
                ?.toMutableList()
                ?: return

            while (remaining.isNotEmpty() &&
                remaining.sumOf { it.length() } > MAX_TOTAL_SIZE_KB * 1024
            ) {
                remaining.removeFirst().delete()
            }
        } catch (_: Exception) {}
    }
}
