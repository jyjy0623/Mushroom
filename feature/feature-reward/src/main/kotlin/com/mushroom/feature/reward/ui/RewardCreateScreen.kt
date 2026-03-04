package com.mushroom.feature.reward.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.mushroom.core.domain.entity.PeriodType
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.feature.reward.viewmodel.RewardCreateViewEvent
import com.mushroom.feature.reward.viewmodel.RewardCreateViewModel
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardCreateScreen(
    onNavigateBack: () -> Unit,
    viewModel: RewardCreateViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) viewModel.updateImageUri(uri.toString())
    }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is RewardCreateViewEvent.SaveSuccess -> onNavigateBack()
                is RewardCreateViewEvent.ShowError -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("创建奖品") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { viewModel.save() },
                        enabled = !uiState.isSaving
                    ) {
                        if (uiState.isSaving) CircularProgressIndicator(Modifier.size(16.dp))
                        else Text("保存")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // 封面图选择
            CoverImagePicker(
                imageUri = uiState.imageUri,
                rewardType = uiState.type,
                onClick = { imagePicker.launch("image/*") }
            )

            // 奖品名称
            val nameError = uiState.validationErrors["name"]
            OutlinedTextField(
                value = uiState.name,
                onValueChange = viewModel::updateName,
                label = { Text("奖品名称 *") },
                modifier = Modifier.fillMaxWidth(),
                isError = nameError != null,
                supportingText = if (nameError != null) ({ Text(nameError) }) else null,
                singleLine = true
            )

            // 奖品类型
            RewardTypeSection(
                selected = uiState.type,
                onSelect = viewModel::updateType
            )

            HorizontalDivider()

            // 根据类型显示不同配置
            when (uiState.type) {
                RewardType.PHYSICAL -> PhysicalConfigSection(
                    puzzlePiecesText = uiState.puzzlePiecesText,
                    puzzlePiecesError = uiState.validationErrors["puzzlePieces"],
                    requiredPointsText = uiState.requiredPointsText,
                    requiredPointsError = uiState.validationErrors["requiredPoints"],
                    onPuzzlePiecesChange = viewModel::updatePuzzlePiecesText,
                    onRequiredPointsChange = viewModel::updateRequiredPointsText
                )
                RewardType.TIME_BASED -> TimeConfigSection(
                    unitMinutesText = uiState.unitMinutesText,
                    periodType = uiState.periodType,
                    maxMinutesPerPeriodText = uiState.maxMinutesPerPeriodText,
                    cooldownDaysText = uiState.cooldownDaysText,
                    unitMinutesError = uiState.validationErrors["unitMinutes"],
                    maxMinutesError = uiState.validationErrors["maxMinutesPerPeriod"],
                    onUnitMinutesChange = viewModel::updateUnitMinutesText,
                    onPeriodTypeChange = viewModel::updatePeriodType,
                    onMaxMinutesChange = viewModel::updateMaxMinutesPerPeriodText,
                    onCooldownDaysChange = viewModel::updateCooldownDaysText
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CoverImagePicker(
    imageUri: String,
    rewardType: RewardType,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (imageUri.isNotEmpty()) {
            AsyncImage(
                model = imageUri,
                contentDescription = "奖品封面",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
            // 半透明蒙层 + 提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.35f)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Text(
                    "点击更换图片",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (rewardType == RewardType.PHYSICAL) "点击添加奖品图片（可选）" else "点击添加封面图片（可选）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (rewardType == RewardType.PHYSICAL) "🎁" else "⏰",
                    fontSize = 32.sp
                )
            }
        }
    }
}

@Composable
private fun RewardTypeSection(selected: RewardType, onSelect: (RewardType) -> Unit) {
    Column {
        Text("奖品类型", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        listOf(
            RewardType.PHYSICAL to "实物奖品（拼图解锁）",
            RewardType.TIME_BASED to "时长奖品（游戏/视频时间）"
        ).forEach { (type, label) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = selected == type,
                    onClick = { onSelect(type) }
                )
                Text(label, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun PhysicalConfigSection(
    puzzlePiecesText: String,
    puzzlePiecesError: String?,
    requiredPointsText: String,
    requiredPointsError: String?,
    onPuzzlePiecesChange: (String) -> Unit,
    onRequiredPointsChange: (String) -> Unit
) {
    val pieces = puzzlePiecesText.toIntOrNull() ?: 0
    val points = requiredPointsText.toIntOrNull() ?: 0
    val perPiece = if (pieces > 0) maxOf(1, (points + pieces - 1) / pieces) else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "兑换条件（积分）",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = requiredPointsText,
                onValueChange = onRequiredPointsChange,
                label = { Text("所需总积分 *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = requiredPointsError != null,
                supportingText = if (requiredPointsError != null) ({ Text(requiredPointsError) })
                else ({ Text("兑换蘑菇时按等级折算积分，累计达到门槛即可解锁拼图") }),
                singleLine = true
            )
            OutlinedTextField(
                value = puzzlePiecesText,
                onValueChange = onPuzzlePiecesChange,
                label = { Text("拼图总块数 *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = puzzlePiecesError != null,
                supportingText = if (puzzlePiecesError != null) {
                    { Text(puzzlePiecesError) }
                } else if (perPiece > 0) {
                    { Text("每块需 $perPiece 积分，拼完即可领取奖品") }
                } else {
                    { Text("拼完所有拼图即可领取奖品") }
                },
                singleLine = true
            )
        }
    }
}

@Composable
private fun TimeConfigSection(
    unitMinutesText: String,
    periodType: PeriodType,
    maxMinutesPerPeriodText: String,
    cooldownDaysText: String,
    unitMinutesError: String?,
    maxMinutesError: String?,
    onUnitMinutesChange: (String) -> Unit,
    onPeriodTypeChange: (PeriodType) -> Unit,
    onMaxMinutesChange: (String) -> Unit,
    onCooldownDaysChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "时长设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // 每次获得时长
            OutlinedTextField(
                value = unitMinutesText,
                onValueChange = onUnitMinutesChange,
                label = { Text("每次兑换获得（分钟）*") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = unitMinutesError != null,
                supportingText = if (unitMinutesError != null) ({ Text(unitMinutesError) }) else null,
                singleLine = true
            )

            // 周期类型
            Text("周期类型", style = MaterialTheme.typography.bodyMedium)
            listOf(PeriodType.WEEKLY to "每周", PeriodType.MONTHLY to "每月").forEach { (pt, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = periodType == pt,
                        onClick = { onPeriodTypeChange(pt) }
                    )
                    Text(label, modifier = Modifier.padding(start = 4.dp))
                }
            }

            // 周期上限
            OutlinedTextField(
                value = maxMinutesPerPeriodText,
                onValueChange = onMaxMinutesChange,
                label = { Text("周期最多使用（分钟）*") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = maxMinutesError != null,
                supportingText = if (maxMinutesError != null) ({ Text(maxMinutesError) }) else null,
                singleLine = true
            )

            // 冷却天数
            OutlinedTextField(
                value = cooldownDaysText,
                onValueChange = onCooldownDaysChange,
                label = { Text("冷却天数（0 表示不限）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )
        }
    }
}
