package com.mushroom.feature.reward.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.RewardStatus
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.feature.reward.viewmodel.RewardDetailViewEvent
import com.mushroom.feature.reward.viewmodel.RewardDetailViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RewardDetailScreen(
    rewardId: Long,
    onNavigateBack: () -> Unit = {},
    viewModel: RewardDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(rewardId) {
        viewModel.loadReward(rewardId)
    }

    LaunchedEffect(Unit) {
        viewModel.viewEvent.collectLatest { event ->
            when (event) {
                is RewardDetailViewEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message)
                RewardDetailViewEvent.ExchangeSuccess -> snackbarHostState.showSnackbar("兑换成功！")
                RewardDetailViewEvent.ClaimSuccess -> {
                    snackbarHostState.showSnackbar("恭喜领取奖品！")
                    onNavigateBack()
                }
            }
        }
    }

    // 庆祝效果
    if (uiState.celebrationTrigger) {
        LaunchedEffect(Unit) {
            snackbarHostState.showSnackbar("🎉 拼图完成！可以领取奖品了！")
            viewModel.dismissCelebration()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(uiState.reward?.name ?: "奖品详情") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        val reward = uiState.reward
        if (reward == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("加载中…")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 封面图占位
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(if (reward.type == RewardType.PHYSICAL) "🎁" else "⏱", fontSize = 48.sp)
            }

            when (reward.type) {
                RewardType.PHYSICAL -> PhysicalRewardContent(
                    uiState = uiState,
                    onExchange = { level, amount ->
                        viewModel.exchange(rewardId, level, amount)
                    },
                    onClaim = { viewModel.claimReward(rewardId) }
                )
                RewardType.TIME_BASED -> TimeRewardContent(
                    uiState = uiState,
                    onExchange = { level, amount ->
                        viewModel.exchange(rewardId, level, amount)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PhysicalRewardContent(
    uiState: com.mushroom.feature.reward.viewmodel.RewardDetailUiState,
    onExchange: (MushroomLevel, Int) -> Unit,
    onClaim: () -> Unit
) {
    val progress = uiState.puzzleProgress
    val reward = uiState.reward ?: return

    // 拼图块动画：记录"已渲染的解锁数"，每次新增时逐块 400ms 出现
    val totalPieces = progress?.totalPieces ?: 0
    val unlockedPieces = progress?.unlockedPieces ?: 0
    var animatedUnlocked by remember { mutableIntStateOf(unlockedPieces) }

    LaunchedEffect(unlockedPieces) {
        if (unlockedPieces > animatedUnlocked) {
            val start = animatedUnlocked
            for (i in start until unlockedPieces) {
                animatedUnlocked = i + 1
                delay(400)
            }
        } else {
            animatedUnlocked = unlockedPieces
        }
    }

    // 进度卡片
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("拼图进度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (progress != null) {
                Text(
                    "${progress.unlockedPieces} / ${progress.totalPieces} 块已解锁",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { progress.percentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                if (progress.isCompleted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "🎉 拼图已完成！",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
                // 拼图格子可视化
                if (totalPieces > 0) {
                    Spacer(Modifier.height(12.dp))
                    PuzzleGrid(totalPieces = totalPieces, animatedUnlocked = animatedUnlocked)
                }
            }
        }
    }

    // 领取按钮（拼图完成后显示）
    if (reward.status == RewardStatus.COMPLETED) {
        Button(
            onClick = onClaim,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("领取奖品（需家长确认）")
        }
    } else if (progress == null || !progress.isCompleted) {
        // 兑换区域
        ExchangeSection(
            isExchanging = uiState.isExchanging,
            requiredMushrooms = reward.requiredMushrooms,
            onExchange = onExchange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleGrid(totalPieces: Int, animatedUnlocked: Int) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (i in 0 until totalPieces) {
            val isUnlocked = i < animatedUnlocked
            val bgColor by animateColorAsState(
                targetValue = if (isUnlocked) MaterialTheme.colorScheme.primary
                              else MaterialTheme.colorScheme.surfaceVariant,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
                label = "piece_color_$i"
            )
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color = bgColor, shape = RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    Text("🍄", fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun TimeRewardContent(
    uiState: com.mushroom.feature.reward.viewmodel.RewardDetailUiState,
    onExchange: (MushroomLevel, Int) -> Unit
) {
    val balance = uiState.timeBalance
    val config = uiState.reward?.timeLimitConfig

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("本期额度", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            if (balance != null) {
                Text(
                    "${balance.usedMinutes} / ${balance.maxMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium
                )
                LinearProgressIndicator(
                    progress = { balance.usedMinutes.toFloat() / balance.maxMinutes.coerceAtLeast(1) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
                Text(
                    "剩余 ${balance.remainingMinutes} 分钟",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("本期额度充足", style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (config != null) {
        Text(
            "每次可获得 ${config.unitMinutes} 分钟",
            style = MaterialTheme.typography.bodyMedium
        )
        ExchangeSection(isExchanging = uiState.isExchanging, requiredMushrooms = uiState.reward?.requiredMushrooms ?: emptyMap(), onExchange = onExchange)
    }
}

@Composable
private fun ExchangeSection(
    isExchanging: Boolean,
    requiredMushrooms: Map<MushroomLevel, Int>,
    onExchange: (MushroomLevel, Int) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(MushroomLevel.SMALL) }
    var amount by remember { mutableStateOf(1) }

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("消耗蘑菇", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // 兑换条件说明
            if (requiredMushrooms.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                val conditionText = requiredMushrooms.entries.joinToString(" + ") { (level, count) ->
                    "${mushroomEmoji(level)} ${level.displayName} × $count"
                }
                Text(
                    "兑换条件：$conditionText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(Modifier.height(8.dp))

            // 等级选择：用 Box 替代 OutlinedButton 避免内部 padding 裁剪 emoji
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MushroomLevel.values().forEach { level ->
                    val isSelected = selectedLevel == level
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.surface,
                                shape = CircleShape
                            )
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { selectedLevel = level },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(mushroomEmoji(level), fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))

            // 数量选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (amount > 1) amount-- }) { Text("-") }
                Text("  $amount  ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { amount++ }) { Text("+") }
            }
            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { onExchange(selectedLevel, amount) },
                enabled = !isExchanging,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (isExchanging) "兑换中…" else "确认兑换")
            }
        }
    }
}

private fun mushroomEmoji(level: MushroomLevel) = when (level) {
    MushroomLevel.SMALL -> "🍄"
    MushroomLevel.MEDIUM -> "🍄‍🟫"
    MushroomLevel.LARGE -> "🌟"
    MushroomLevel.GOLD -> "✨"
    MushroomLevel.LEGEND -> "👑"
}
