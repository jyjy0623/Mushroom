package com.mushroom.adventure.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.mushroom.adventure.parent.AuthReason

/**
 * Modal dialog shown whenever a parent PIN is required.
 * The caller passes [reason] so a context-sensitive title is shown.
 */
@Composable
fun PinVerifyDialog(
    reason: AuthReason,
    errorMessage: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "家长验证",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = reasonDescription(reason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) pin = it },
                    label = { Text("PIN") },
                    placeholder = { Text("请输入家长 PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { if (pin.isNotEmpty()) onConfirm(pin) }
                    ),
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(pin) },
                enabled = pin.isNotEmpty()
            ) {
                Text("确认")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

private fun reasonDescription(reason: AuthReason) = when (reason) {
    AuthReason.EXCHANGE_APPROVAL    -> "兑换奖品需要家长授权，请输入家长 PIN 码。"
    AuthReason.TIME_REWARD_CONFIRM  -> "开始使用时长奖品需要家长确认，请输入家长 PIN 码。"
    AuthReason.SCORE_ENTRY          -> "录入成绩需要家长授权，请输入家长 PIN 码。"
    AuthReason.CREATE_MILESTONE     -> "创建里程碑需要家长授权，请输入家长 PIN 码。"
    AuthReason.SETTINGS             -> "修改家长设置需要验证，请输入家长 PIN 码。"
}
