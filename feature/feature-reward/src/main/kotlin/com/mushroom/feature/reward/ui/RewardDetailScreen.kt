package com.mushroom.feature.reward.ui

import android.net.Uri
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.RewardType
import com.mushroom.core.ui.themedDisplayName
import com.mushroom.core.ui.themedEmoji
import com.mushroom.core.ui.R as CoreUiR
import androidx.compose.ui.res.stringResource
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
            // 封面图
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (reward.imageUri.isNotEmpty()) {
                    val imageModel: Any = if (reward.imageUri.startsWith("/")) File(reward.imageUri) else Uri.parse(reward.imageUri)
                    AsyncImage(
                        model = imageModel,
                        contentDescription = "奖品封面",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(if (reward.type == RewardType.PHYSICAL) "🎁" else "⏰", fontSize = 48.sp)
                }
            }

            when (reward.type) {
                RewardType.PHYSICAL -> PhysicalRewardContent(
                    uiState = uiState,
                    onExchange = { level, amount ->
                        viewModel.exchange(rewardId, level, amount)
                    }
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
    onExchange: (MushroomLevel, Int) -> Unit
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
                    PuzzleGrid(
                        totalPieces = totalPieces,
                        animatedUnlocked = animatedUnlocked,
                        pieceEmojis = progress.pieceEmojis
                    )
                }
            }
        }
    }

    // 兑换区域（拼图未完成时显示）
    if (progress == null || !progress.isCompleted) {
        ExchangeSection(
            isExchanging = uiState.isExchanging,
            pointsPerPiece = reward.pointsPerPiece,
            remainingPieces = (reward.puzzlePieces) - (progress?.unlockedPieces ?: 0),
            onExchange = onExchange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PuzzleGrid(
    totalPieces: Int,
    animatedUnlocked: Int,
    pieceEmojis: List<MushroomLevel>
) {
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
                    .background(color = bgColor, shape = RoundedCornerShape(4.dp))
                    .then(
                        if (!isUnlocked) Modifier.border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp)
                        ) else Modifier
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isUnlocked) {
                    val level = pieceEmojis.getOrNull(i) ?: MushroomLevel.SMALL
                    Text(level.themedEmoji(), fontSize = 14.sp)
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
    val config = uiState.reward?.timeLimitConfig ?: return

    // 次数余额卡片（有周期限制才显示）
    val periodType = config.periodType
    if (periodType != null) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                val periodLabel = if (periodType.name == "WEEKLY") "本周" else "本月"
                Text("$periodLabel 兑换次数", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                val usedTimes = balance?.usedTimes ?: 0
                val maxTimes = balance?.maxTimes ?: config.maxTimesPerPeriod
                if (maxTimes != null) {
                    Text(
                        "$usedTimes / $maxTimes 次",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    LinearProgressIndicator(
                        progress = { usedTimes.toFloat() / maxTimes.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    val remaining = maxTimes - usedTimes
                    Text(
                        if (remaining > 0) "剩余 $remaining 次" else "本期已用完",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remaining > 0) MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("已兑换 $usedTimes 次", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    // 一键兑换卡片
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("兑换时长", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "消耗：${config.costMushroomLevel.themedEmoji()} ${config.costMushroomLevel.themedDisplayName()} × ${config.costMushroomCount}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "获得：⏱ ${config.unitMinutes} 分钟",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            val canExchange = run {
                val pt = config.periodType
                val mt = config.maxTimesPerPeriod
                if (pt == null || mt == null) return@run true
                val usedTimes = balance?.usedTimes ?: 0
                usedTimes < mt
            }

            Button(
                onClick = { onExchange(config.costMushroomLevel, config.costMushroomCount) },
                enabled = !uiState.isExchanging && canExchange,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    when {
                        uiState.isExchanging -> "兑换中…"
                        !canExchange -> "本期已达上限"
                        else -> "确认兑换"
                    }
                )
            }
        }
    }
}

@Composable
private fun ExchangeSection(
    isExchanging: Boolean,
    pointsPerPiece: Int,
    remainingPieces: Int,
    onExchange: (MushroomLevel, Int) -> Unit
) {
    var selectedLevel by remember { mutableStateOf(MushroomLevel.SMALL) }
    var amount by remember { mutableStateOf(1) }

    // 实时计算：贡献积分 → 可解锁块数
    val contributedPoints = amount * selectedLevel.exchangePoints
    val piecesToUnlock = if (pointsPerPiece > 0)
        minOf(contributedPoints / pointsPerPiece, remainingPieces)
    else 0

    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(CoreUiR.string.consume_currency), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

            // 兑换条件说明
            if (pointsPerPiece > 0) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "每块拼图需 $pointsPerPiece 积分",
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
                        Text(level.themedEmoji(), fontSize = 20.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // 已选等级文字提示
            Text(
                "已选：${selectedLevel.themedEmoji()} ${selectedLevel.themedDisplayName()}（${selectedLevel.exchangePoints}分/个）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))

            // 数量选择
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { if (amount > 1) amount-- }) { Text("-") }
                Text("  $amount  ", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = { amount++ }) { Text("+") }
            }

            // 实时预览
            if (pointsPerPiece > 0) {
                Spacer(Modifier.height(8.dp))
                val previewColor = if (piecesToUnlock > 0)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
                Text(
                    "贡献积分：$contributedPoints 分 → 可解锁：$piecesToUnlock 块",
                    style = MaterialTheme.typography.bodyMedium,
                    color = previewColor,
                    fontWeight = FontWeight.Medium
                )
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
