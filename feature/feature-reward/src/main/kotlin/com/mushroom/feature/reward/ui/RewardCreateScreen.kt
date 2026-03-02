package com.mushroom.feature.reward.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.MushroomLevel
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

            // 兑换条件（蘑菇类型 + 数量）
            ExchangeRequirementSection(
                level = uiState.requiredLevel,
                amount = uiState.requiredAmount,
                amountError = uiState.validationErrors["requiredAmount"],
                onLevelChange = viewModel::updateRequiredLevel,
                onAmountChange = viewModel::updateRequiredAmount
            )

            HorizontalDivider()

            // 根据类型显示不同配置
            when (uiState.type) {
                RewardType.PHYSICAL -> PhysicalConfigSection(
                    puzzlePieces = uiState.puzzlePieces,
                    puzzlePiecesError = uiState.validationErrors["puzzlePieces"],
                    onPuzzlePiecesChange = viewModel::updatePuzzlePieces
                )
                RewardType.TIME_BASED -> TimeConfigSection(
                    unitMinutes = uiState.unitMinutes,
                    periodType = uiState.periodType,
                    maxMinutesPerPeriod = uiState.maxMinutesPerPeriod,
                    cooldownDays = uiState.cooldownDays,
                    requireParentConfirm = uiState.requireParentConfirm,
                    unitMinutesError = uiState.validationErrors["unitMinutes"],
                    maxMinutesError = uiState.validationErrors["maxMinutesPerPeriod"],
                    onUnitMinutesChange = viewModel::updateUnitMinutes,
                    onPeriodTypeChange = viewModel::updatePeriodType,
                    onMaxMinutesChange = viewModel::updateMaxMinutesPerPeriod,
                    onCooldownDaysChange = viewModel::updateCooldownDays,
                    onRequireParentConfirmChange = viewModel::updateRequireParentConfirm
                )
            }

            Spacer(Modifier.height(16.dp))
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
private fun ExchangeRequirementSection(
    level: MushroomLevel,
    amount: Int,
    amountError: String?,
    onLevelChange: (MushroomLevel) -> Unit,
    onAmountChange: (Int) -> Unit
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
                "兑换所需蘑菇",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RewardMushroomLevelDropdown(
                    selected = level,
                    onSelect = onLevelChange,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = amount.toString(),
                    onValueChange = { it.toIntOrNull()?.let(onAmountChange) },
                    label = { Text("×数量") },
                    modifier = Modifier.weight(0.4f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = amountError != null,
                    supportingText = if (amountError != null) ({ Text(amountError) }) else null,
                    singleLine = true
                )
            }
        }
    }
}

@Composable
private fun PhysicalConfigSection(
    puzzlePieces: Int,
    puzzlePiecesError: String?,
    onPuzzlePiecesChange: (Int) -> Unit
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
                "拼图设置",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            OutlinedTextField(
                value = puzzlePieces.toString(),
                onValueChange = { it.toIntOrNull()?.let(onPuzzlePiecesChange) },
                label = { Text("拼图总块数 *") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = puzzlePiecesError != null,
                supportingText = if (puzzlePiecesError != null) {
                    { Text(puzzlePiecesError) }
                } else {
                    { Text("每兑换一次蘑菇解锁一块拼图，拼完即可领取奖品", style = MaterialTheme.typography.bodySmall) }
                },
                singleLine = true
            )
        }
    }
}

@Composable
private fun TimeConfigSection(
    unitMinutes: Int,
    periodType: PeriodType,
    maxMinutesPerPeriod: Int,
    cooldownDays: Int,
    requireParentConfirm: Boolean,
    unitMinutesError: String?,
    maxMinutesError: String?,
    onUnitMinutesChange: (Int) -> Unit,
    onPeriodTypeChange: (PeriodType) -> Unit,
    onMaxMinutesChange: (Int) -> Unit,
    onCooldownDaysChange: (Int) -> Unit,
    onRequireParentConfirmChange: (Boolean) -> Unit
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
                value = unitMinutes.toString(),
                onValueChange = { it.toIntOrNull()?.let(onUnitMinutesChange) },
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
                value = maxMinutesPerPeriod.toString(),
                onValueChange = { it.toIntOrNull()?.let(onMaxMinutesChange) },
                label = { Text("周期最多使用（分钟）*") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = maxMinutesError != null,
                supportingText = if (maxMinutesError != null) ({ Text(maxMinutesError) }) else null,
                singleLine = true
            )

            // 冷却天数
            OutlinedTextField(
                value = cooldownDays.toString(),
                onValueChange = { it.toIntOrNull()?.let(onCooldownDaysChange) },
                label = { Text("冷却天数（0 表示不限）") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true
            )

            // 需要家长确认
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("使用时需家长确认", style = MaterialTheme.typography.bodyMedium)
                Switch(
                    checked = requireParentConfirm,
                    onCheckedChange = onRequireParentConfirmChange
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RewardMushroomLevelDropdown(
    selected: MushroomLevel,
    onSelect: (MushroomLevel) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.displayName,
            onValueChange = {},
            readOnly = true,
            label = { Text("蘑菇类型") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            singleLine = true
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf(
                MushroomLevel.SMALL,
                MushroomLevel.MEDIUM,
                MushroomLevel.LARGE,
                MushroomLevel.GOLD
            ).forEach { level ->
                DropdownMenuItem(
                    text = { Text(level.displayName) },
                    onClick = { onSelect(level); expanded = false }
                )
            }
        }
    }
}
