package com.mushroom.core.ui

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType

/**
 * 数字输入框组件，封装正确的"字符串状态 + 数字解析"模式。
 *
 * 解决了以下问题：
 * - 用户按 backspace 删除最后一位数字时，不应自动重置为 1
 * - 空字符串应允许存在，而不是被强制转换为默认值
 *
 * 使用方式：
 * - 状态类型为 String，直接对应 OutlinedTextField 的 value
 * - onValueChange 回调接收的也是 String，空字符串表示用户清空了输入
 * - 调用方负责解析和验证（如 toIntOrNull()）
 *
 * @param value 当前文本值（字符串）
 * @param onValueChange 文本变化回调，接收新文本字符串
 * @param label 标签文本
 * @param modifier 修饰符
 * @param isError 是否显示错误状态
 * @param supportingText 辅助文本（Composable lambda）
 * @param textStyle 文本样式
 * @param enabled 是否可用
 */
@Composable
fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    textStyle: TextStyle? = null,
    enabled: Boolean = true
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        isError = isError,
        supportingText = supportingText,
        textStyle = textStyle ?: TextStyle(),
        enabled = enabled
    )
}