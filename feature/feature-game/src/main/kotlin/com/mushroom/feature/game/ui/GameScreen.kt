package com.mushroom.feature.game.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.feature.game.viewmodel.GamePhysics
import com.mushroom.feature.game.viewmodel.GameState
import com.mushroom.feature.game.viewmodel.GameViewModel
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.LaunchedEffect

@Composable
fun GameScreen(
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 进入游戏页时强制横屏，离开时恢复竖屏
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val originalOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (originalOrientation != null) {
                activity.requestedOrientation = originalOrientation
            }
        }
    }

    // 监听退出事件
    LaunchedEffect(Unit) {
        viewModel.exitEvent.collectLatest { onExit() }
    }

    // 每日首次进入发放奖励
    LaunchedEffect(Unit) {
        viewModel.awardDailyPlayReward()
    }

    // 游戏帧循环（仅 RUNNING 状态驱动）
    LaunchedEffect(uiState.state) {
        if (uiState.state == GameState.RUNNING) {
            var lastFrameMs = withFrameMillis { it }
            while (true) {
                val frameMs = withFrameMillis { it }
                val dt = (frameMs - lastFrameMs).coerceIn(1, 50)
                viewModel.tick(dt)
                lastFrameMs = frameMs
            }
        }
    }

    val bgBrush = if (uiState.score >= 700) {
        Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFFE0F4FF)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush)
            // RUNNING 时整个屏幕响应点击跳跃；其他状态不响应
            .then(
                if (uiState.state == GameState.RUNNING) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { viewModel.jump() }
                    }
                } else Modifier
            )
    ) {
        // Canvas 游戏场景
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawGameScene(uiState.physics, size.width, size.height, uiState.score)
        }

        // 右上角实时分数
        Text(
            text = "${uiState.score}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (uiState.score >= 700) Color.White else Color(0xFF333333),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        )

        // 各状态 Overlay
        when (uiState.state) {
            GameState.IDLE -> IdleOverlay(onStart = { viewModel.startGame() })
            GameState.GAME_OVER -> GameOverOverlay(
                score = uiState.score,
                isNewRecord = uiState.isNewRecord
            )
            GameState.RUNNING -> { /* 无遮罩，全屏游戏 */ }
        }
    }
}

// ── IDLE 开始界面 ──────────────────────────────────────────────
@Composable
private fun IdleOverlay(onStart: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "🍄 蘑菇大冒险 Run",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF222222)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "横屏游玩 · 点击屏幕跳跃 · 躲避仙人掌",
                fontSize = 14.sp,
                color = Color(0xFF555555)
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE53935),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .widthIn(min = 160.dp)
                    .height(52.dp)
            ) {
                Text("开始游戏", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── GAME OVER 结算界面 ─────────────────────────────────────────
@Composable
private fun GameOverOverlay(score: Int, isNewRecord: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("游戏结束", fontSize = 36.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(Modifier.height(8.dp))
            Text("得分：$score", fontSize = 26.sp, color = Color.White)
            if (isNewRecord) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "🏆 新纪录！",
                    fontSize = 22.sp,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("2 秒后自动返回...", fontSize = 14.sp, color = Color(0xFFCCCCCC))
        }
    }
}

// ── Canvas 绘制 ────────────────────────────────────────────────

private fun DrawScope.drawGameScene(physics: GamePhysics, w: Float, h: Float, score: Int) {
    val groundPx = physics.mushroomY * h

    // 地面
    drawRect(color = Color(0xFF5D4037), topLeft = Offset(0f, groundPx), size = Size(w, h - groundPx))
    drawRect(color = Color(0xFF388E3C), topLeft = Offset(0f, groundPx), size = Size(w, 8f))

    // 蘑菇
    drawMushroom(0.1f * w, physics.mushroomY * h, physics.frameIndex, score)

    // 障碍物
    physics.obstacles.forEach { obs ->
        val oh = obs.height * h
        drawCactus(obs.x * w, groundPx - oh, obs.width * w, oh)
    }
}

private fun DrawScope.drawMushroom(x: Float, groundY: Float, frame: Int, score: Int) {
    val bodyW = 32f; val bodyH = 28f; val headR = 22f
    val bodyTop = groundY - bodyH
    val headCY = bodyTop - headR * 0.5f

    val capColor = if (score >= 700) Color(0xFFFF6B6B) else Color(0xFFE53935)
    drawCircle(color = capColor, radius = headR, center = Offset(x, headCY))
    drawCircle(color = Color.White, radius = 5f, center = Offset(x - 7f, headCY - 5f))
    drawCircle(color = Color.White, radius = 3f, center = Offset(x + 8f, headCY - 2f))

    drawRect(Color(0xFFFFF9C4), Offset(x - bodyW / 2, bodyTop), Size(bodyW, bodyH))

    val legColor = Color(0xFF795548); val legH = 12f
    if (frame == 0) {
        drawRect(legColor, Offset(x - 10f, groundY - legH), Size(7f, legH))
        drawRect(legColor, Offset(x + 3f, groundY - legH * 0.5f), Size(7f, legH * 0.5f))
    } else {
        drawRect(legColor, Offset(x - 10f, groundY - legH * 0.5f), Size(7f, legH * 0.5f))
        drawRect(legColor, Offset(x + 3f, groundY - legH), Size(7f, legH))
    }
}

private fun DrawScope.drawCactus(x: Float, y: Float, w: Float, h: Float) {
    val green = Color(0xFF2E7D32)
    drawRect(green, Offset(x + w * 0.3f, y), Size(w * 0.4f, h))
    drawRect(green, Offset(x, y + h * 0.3f), Size(w * 0.35f, h * 0.2f))
    drawRect(green, Offset(x, y + h * 0.1f), Size(w * 0.12f, h * 0.22f))
    drawRect(green, Offset(x + w * 0.65f, y + h * 0.4f), Size(w * 0.35f, h * 0.2f))
    drawRect(green, Offset(x + w * 0.88f, y + h * 0.2f), Size(w * 0.12f, h * 0.22f))
}
