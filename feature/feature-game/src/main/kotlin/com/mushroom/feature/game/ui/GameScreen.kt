package com.mushroom.feature.game.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.feature.game.viewmodel.GamePhysics
import com.mushroom.feature.game.viewmodel.GameState
import com.mushroom.feature.game.viewmodel.GameViewModel
import kotlinx.coroutines.flow.collectLatest

@Composable
fun GameScreen(
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Listen for exit event
    LaunchedEffect(Unit) {
        viewModel.exitEvent.collectLatest { onExit() }
    }

    // Award daily play reward when game starts (called once on enter)
    LaunchedEffect(Unit) {
        viewModel.awardDailyPlayReward()
    }

    // Game loop: drive physics ticks using frame timing
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

    val bgColor = if (uiState.score >= 700) {
        Brush.verticalGradient(listOf(Color(0xFF1A1A2E), Color(0xFF16213E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFF87CEEB), Color(0xFFE0F4FF)))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .pointerInput(uiState.state) {
                detectTapGestures {
                    when (uiState.state) {
                        GameState.IDLE -> viewModel.startGame()
                        GameState.RUNNING -> viewModel.jump()
                        GameState.GAME_OVER -> { /* wait for auto-exit */ }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            drawGameScene(uiState.physics, w, h, uiState.score)
        }

        // Score display
        Text(
            text = "${uiState.score}",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (uiState.score >= 700) Color.White else Color(0xFF333333),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(24.dp)
        )

        // Overlay messages
        when (uiState.state) {
            GameState.IDLE -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🍄 蘑菇大冒险 Run", fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333))
                    Spacer(Modifier.height(16.dp))
                    Text("点击屏幕开始", fontSize = 18.sp, color = Color(0xFF555555))
                }
            }
            GameState.GAME_OVER -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("游戏结束", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("得分：${uiState.score}", fontSize = 24.sp, color = Color.White)
                    if (uiState.isNewRecord) {
                        Spacer(Modifier.height(8.dp))
                        Text("🏆 新纪录！", fontSize = 20.sp, color = Color(0xFFFFD700),
                            fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("2秒后自动返回...", fontSize = 14.sp, color = Color(0xFFCCCCCC))
                }
            }
            GameState.RUNNING -> { /* no overlay */ }
        }
    }
}

private fun DrawScope.drawGameScene(physics: GamePhysics, w: Float, h: Float, score: Int) {
    val groundY = physics.mushroomY   // use actual ground level from physics (normalized)
    val groundPx = groundY * h

    // Ground line
    drawRect(
        color = Color(0xFF5D4037),
        topLeft = Offset(0f, groundPx),
        size = Size(w, h - groundPx)
    )
    drawRect(
        color = Color(0xFF388E3C),
        topLeft = Offset(0f, groundPx),
        size = Size(w, 8f)
    )

    // Mushroom
    val mX = 0.1f * w
    val mY = physics.mushroomY * h
    drawMushroom(mX, mY, physics.frameIndex, score)

    // Obstacles (cacti)
    physics.obstacles.forEach { obs ->
        val ox = obs.x * w
        val oh = obs.height * h
        val oy = groundPx - oh
        drawCactus(ox, oy, obs.width * w, oh)
    }
}

private fun DrawScope.drawMushroom(x: Float, groundY: Float, frame: Int, score: Int) {
    val bodyW = 32f
    val bodyH = 28f
    val headR = 22f
    val bodyTop = groundY - bodyH
    val headCY = bodyTop - headR * 0.5f

    // Cap (head)
    val capColor = if (score >= 700) Color(0xFFFF6B6B) else Color(0xFFE53935)
    drawCircle(color = capColor, radius = headR, center = Offset(x, headCY))
    // White dots on cap
    drawCircle(color = Color.White, radius = 5f, center = Offset(x - 7f, headCY - 5f))
    drawCircle(color = Color.White, radius = 3f, center = Offset(x + 8f, headCY - 2f))

    // Body
    drawRect(
        color = Color(0xFFFFF9C4),
        topLeft = Offset(x - bodyW / 2, bodyTop),
        size = Size(bodyW, bodyH)
    )

    // Legs (running animation)
    val legColor = Color(0xFF795548)
    val legH = 12f
    if (frame == 0) {
        // Left leg forward, right back
        drawRect(legColor, Offset(x - 10f, groundY - legH), Size(7f, legH))
        drawRect(legColor, Offset(x + 3f, groundY - legH * 0.5f), Size(7f, legH * 0.5f))
    } else {
        drawRect(legColor, Offset(x - 10f, groundY - legH * 0.5f), Size(7f, legH * 0.5f))
        drawRect(legColor, Offset(x + 3f, groundY - legH), Size(7f, legH))
    }
}

private fun DrawScope.drawCactus(x: Float, y: Float, w: Float, h: Float) {
    val green = Color(0xFF2E7D32)
    // Main stem
    drawRect(green, Offset(x + w * 0.3f, y), Size(w * 0.4f, h))
    // Left arm
    drawRect(green, Offset(x, y + h * 0.3f), Size(w * 0.35f, h * 0.2f))
    drawRect(green, Offset(x, y + h * 0.1f), Size(w * 0.12f, h * 0.22f))
    // Right arm
    drawRect(green, Offset(x + w * 0.65f, y + h * 0.4f), Size(w * 0.35f, h * 0.2f))
    drawRect(green, Offset(x + w * 0.88f, y + h * 0.2f), Size(w * 0.12f, h * 0.22f))
}
