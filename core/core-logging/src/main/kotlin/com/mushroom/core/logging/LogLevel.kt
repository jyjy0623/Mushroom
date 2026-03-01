package com.mushroom.core.logging

/**
 * 日志级别枚举，数值越大级别越高。
 */
enum class LogLevel(val value: Int) {
    V(1),  // VERBOSE
    D(2),  // DEBUG
    I(3),  // INFO
    W(4),  // WARN
    E(5)   // ERROR
}
