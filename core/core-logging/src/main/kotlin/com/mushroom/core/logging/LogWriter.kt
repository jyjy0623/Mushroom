package com.mushroom.core.logging

/**
 * 日志输出后端接口。
 * Debug 构建注入 DebugLogWriter，Release 构建注入 ReleaseLogWriter。
 */
interface LogWriter {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}
