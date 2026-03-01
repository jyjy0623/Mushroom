package com.mushroom.core.logging

/**
 * 全局日志门面（object单例）。
 * 所有模块通过此对象记录日志，不直接调用 android.util.Log。
 *
 * 使用前需调用 init() 注入 LogWriter（由 Application.onCreate 完成）。
 */
object MushroomLogger {

    private var writer: LogWriter? = null

    fun init(writer: LogWriter) {
        this.writer = writer
    }

    fun v(tag: String, message: String) = log(LogLevel.V, tag, message)
    fun d(tag: String, message: String) = log(LogLevel.D, tag, message)
    fun i(tag: String, message: String) = log(LogLevel.I, tag, message)
    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.W, tag, message, throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log(LogLevel.E, tag, message, throwable)

    private fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null) {
        writer?.log(level, tag, message, throwable)
    }
}
