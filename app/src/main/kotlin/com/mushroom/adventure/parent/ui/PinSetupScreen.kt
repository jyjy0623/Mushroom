package com.mushroom.adventure.parent.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

/**
 * Full-screen page for first-time PIN setup or PIN change.
 * Requires the user to enter the new PIN twice for confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onNavigateBack: () -> Unit = {},
    viewModel: PinSetupViewModel = hiltViewModel()
) {
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.event.collect { event ->
            when (event) {
                PinSetupEvent.Success -> {
                    snackbarHostState.showSnackbar("PIN 设置成功")
                    onNavigateBack()
                }
                is PinSetupEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(if (viewModel.isPinAlreadySet) "修改家长 PIN" else "设置家长 PIN")
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "PIN 码用于家长权限验证（4-6位数字）",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 如果已有 PIN，需要先验证旧 PIN（通过导航到 PinVerify 完成，此处略）

            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) newPin = it },
                label = { Text("新 PIN 码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = confirmPin,
                onValueChange = { if (it.length <= 6 && it.all(Char::isDigit)) confirmPin = it },
                label = { Text("确认 PIN 码") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { viewModel.savePin(newPin, confirmPin) }
                ),
                isError = confirmPin.isNotEmpty() && newPin != confirmPin,
                supportingText = if (confirmPin.isNotEmpty() && newPin != confirmPin) {
                    { Text("两次输入不一致", color = MaterialTheme.colorScheme.error) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = { viewModel.savePin(newPin, confirmPin) },
                enabled = newPin.length >= 4 && newPin == confirmPin,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("保存 PIN 码", fontWeight = FontWeight.Bold)
            }
        }
    }
}
