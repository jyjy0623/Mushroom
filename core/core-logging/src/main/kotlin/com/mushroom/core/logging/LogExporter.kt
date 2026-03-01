package com.mushroom.core.logging

import android.content.Context
import android.os.Build
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 日志导出器。
 * 将日志文件打包为 ZIP，包含 Claude 分析入口文档和错误索引。
 */
class LogExporter(
    private val context: Context,
    private val fileWriter: LogFileWriter
) {

    /**
     * 导出诊断包到缓存目录，返回 ZIP 文件路径。
     */
    fun export(): File {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val zipFile = File(context.cacheDir, "mushroom_diagnostics_$timestamp.zip")

        ZipOutputStream(zipFile.outputStream().buffered()).use { zos ->
            val logDir = File(context.filesDir, "logs")
            val logFiles = logDir.listFiles()
                ?.filter { it.name.endsWith(".txt") }
                ?.sortedBy { it.name }
                ?: emptyList()

            // 生成 error_index.txt 内容
            val errorLines = buildErrorIndex(logFiles)

            // 写入 error_index.txt
            zos.putNextEntry(ZipEntry("error_index.txt"))
            zos.write(errorLines.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 写入 diagnostic_summary.txt
            zos.putNextEntry(ZipEntry("diagnostic_summary.txt"))
            zos.write(buildDiagnosticSummary().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 写入 CLAUDE_ANALYSIS_BRIEF.md
            zos.putNextEntry(ZipEntry("CLAUDE_ANALYSIS_BRIEF.md"))
            zos.write(buildClaudeAnalysisBrief(logFiles, errorLines).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // 写入所有日志文件
            logFiles.forEach { logFile ->
                zos.putNextEntry(ZipEntry("logs/${logFile.name}"))
                logFile.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }

        return zipFile
    }

    private fun buildErrorIndex(logFiles: List<File>): String {
        val sb = StringBuilder()
        sb.appendLine("# Error Index")
        sb.appendLine("# 预提取的 ERROR / WARN 行（含文件名:行号）")
        sb.appendLine()

        logFiles.forEach { file ->
            file.bufferedReader().useLines { lines ->
                lines.forEachIndexed { lineNum, line ->
                    if (line.contains(" E/") || line.contains(" W/") ||
                        line.contains(" ERROR") || line.contains(" WARN")
                    ) {
                        sb.appendLine("${file.name}:${lineNum + 1}: $line")
                    }
                }
            }
        }

        return sb.toString()
    }

    private fun buildDiagnosticSummary(): String {
        return buildString {
            appendLine("=== 蘑菇大冒险 诊断摘要 ===")
            appendLine("导出时间: ${LocalDateTime.now()}")
            appendLine()
            appendLine("--- 设备信息 ---")
            appendLine("Android 版本: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("设备型号: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine()
            appendLine("--- 应用信息 ---")
            try {
                val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                appendLine("包名: ${pi.packageName}")
                appendLine("版本名: ${pi.versionName}")
                appendLine("版本号: ${pi.longVersionCode}")
            } catch (_: Exception) {
                appendLine("（应用信息获取失败）")
            }
        }
    }

    private fun buildClaudeAnalysisBrief(logFiles: List<File>, errorIndex: String): String {
        return buildString {
            appendLine("# CLAUDE_ANALYSIS_BRIEF.md")
            appendLine("# 蘑菇大冒险 - Claude 分析入口")
            appendLine()
            appendLine("## 如何开始分析")
            appendLine()
            appendLine("1. 首先阅读本文件了解日志结构")
            appendLine("2. 查看 `error_index.txt` 获取所有 ERROR/WARN 行汇总")
            appendLine("3. 按需读取 `logs/` 目录下的具体日志文件")
            appendLine("4. 日志以 `SESSION START` 标记分隔每次 App 启动")
            appendLine()
            appendLine("## 日志文件列表")
            appendLine()
            logFiles.forEach { appendLine("- logs/${it.name}") }
            appendLine()
            appendLine("## 日志级别说明")
            appendLine()
            appendLine("| 级别 | 含义 |")
            appendLine("|------|------|")
            appendLine("| I/INFO | 模块关键流程节点（启动、数据库初始化、打卡成功等）|")
            appendLine("| W/WARN | 非预期但可恢复的情况（重试、降级）|")
            appendLine("| E/ERROR | 异常和错误（含完整栈跟踪）|")
            appendLine()
            appendLine("## Tag 命名规范")
            appendLine()
            appendLine("| 模块 | Tag 前缀 | 示例 |")
            appendLine("|------|---------|------|")
            appendLine("| core-logging | LOG | LOG/FileWriter |")
            appendLine("| core-data | DB | DB/TaskDao |")
            appendLine("| feature-task | TASK | TASK/CreateUseCase |")
            appendLine("| feature-checkin | CHECKIN | CHECKIN/CheckInUseCase |")
            appendLine("| feature-mushroom | MUSHROOM | MUSHROOM/RewardEngine |")
            appendLine("| feature-reward | REWARD | REWARD/ExchangeUseCase |")
            appendLine("| feature-milestone | MILESTONE | MILESTONE/ScoreUseCase |")
            appendLine("| feature-statistics | STATS | STATS/StatisticsUseCase |")
            appendLine()
            appendLine("## 错误概览")
            appendLine()
            val errorCount = errorIndex.lines().count { it.isNotBlank() && !it.startsWith("#") }
            appendLine("error_index.txt 中共 $errorCount 行 ERROR/WARN 记录。")
        }
    }
}
