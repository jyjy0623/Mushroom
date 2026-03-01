package com.mushroom.core.logging

import android.util.Log

/**
 * Debug 构建日志输出：输出全部级别到 Logcat，同时写入文件（I/W/E）。
 */
class DebugLogWriter(
    private val fileWriter: LogFileWriter
) : LogWriter {

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        when (level) {
            LogLevel.V -> Log.v(tag, message, throwable)
            LogLevel.D -> Log.d(tag, message, throwable)
            LogLevel.I -> {
                Log.i(tag, message, throwable)
                fileWriter.write(level, tag, message, throwable)
            }
            LogLevel.W -> {
                Log.w(tag, message, throwable)
                fileWriter.write(level, tag, message, throwable)
            }
            LogLevel.E -> {
                Log.e(tag, message, throwable)
                fileWriter.write(level, tag, message, throwable)
            }
        }
    }
}

/**
 * Release 构建日志输出：只输出 WARN 和 ERROR。
 * WARN → 只写文件；ERROR → Logcat + 文件。
 * INFO 及以下在 Release 不输出。
 */
class ReleaseLogWriter(
    private val fileWriter: LogFileWriter
) : LogWriter {

    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        if (level.value < LogLevel.W.value) return

        when (level) {
            LogLevel.W -> fileWriter.write(level, tag, message, throwable)
            LogLevel.E -> {
                Log.e(tag, message, throwable)
                fileWriter.write(level, tag, message, throwable)
            }
            else -> { /* V/D/I 不到这里 */ }
        }
    }
}
