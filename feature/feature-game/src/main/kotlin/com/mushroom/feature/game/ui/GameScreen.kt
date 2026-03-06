package com.mushroom.feature.game.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mushroom.feature.game.viewmodel.Cloud
import com.mushroom.feature.game.viewmodel.GamePhysics
import com.mushroom.feature.game.viewmodel.GameState
import com.mushroom.feature.game.viewmodel.GameViewModel
import kotlinx.coroutines.flow.collectLatest

// ── 昼夜配色 ────────────────────────────────────────────────────
private object DinoColors {
    // 白天
    val bgDay       = Color(0xFFFFFFFF)
    val fgDay       = Color(0xFF535353)   // 恐龙、仙人掌、地面线
    val textDay     = Color(0xFF535353)
    val cloudDay    = Color(0xFFCCCCCC)
    // 夜晚（700分后反色）
    val bgNight     = Color(0xFF1A1A1A)
    val fgNight     = Color(0xFFD4D4D4)
    val textNight   = Color(0xFFD4D4D4)
    val cloudNight  = Color(0xFF444444)
}

@Composable
fun GameScreen(
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 进入横屏，离开时恢复原方向
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val orig = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { if (orig != null) activity.requestedOrientation = orig }
    }

    LaunchedEffect(Unit) { viewModel.exitEvent.collectLatest { onExit() } }
    LaunchedEffect(Unit) { viewModel.awardDailyPlayReward() }

    // 游戏帧循环（仅 RUNNING）
    LaunchedEffect(uiState.state) {
        if (uiState.state == GameState.RUNNING) {
            var last = withFrameMillis { it }
            while (true) {
                val now = withFrameMillis { it }
                viewModel.tick((now - last).coerceIn(1, 50))
                last = now
            }
        }
    }

    val isNight = uiState.score >= 700
    val bg   = if (isNight) DinoColors.bgNight  else DinoColors.bgDay
    val fg   = if (isNight) DinoColors.fgNight  else DinoColors.fgDay
    val text = if (isNight) DinoColors.textNight else DinoColors.textDay

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .then(
                if (uiState.state == GameState.RUNNING)
                    Modifier.pointerInput(Unit) { detectTapGestures { viewModel.jump() } }
                else Modifier
            )
    ) {
        // ── Canvas 游戏场景 ────────────────────────────────────
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawDinoScene(uiState.physics, size.width, size.height, fg)
        }

        // ── 分数 / HI ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.highScore > 0) {
                Text(
                    text = "HI ${uiState.highScore.toString().padStart(5, '0')}",
                    color = text.copy(alpha = 0.5f),
                    fontSize = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(16.dp))
            }
            Text(
                text = uiState.score.toString().padStart(5, '0'),
                color = text,
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }

        // ── 状态 Overlay ───────────────────────────────────────
        when (uiState.state) {
            GameState.IDLE     -> IdleOverlay(fg, bg, onStart = { viewModel.startGame() })
            GameState.GAME_OVER -> GameOverOverlay(
                score = uiState.score,
                isNewRecord = uiState.isNewRecord,
                fg = fg, bg = bg
            )
            GameState.RUNNING  -> { /* 无遮罩 */ }
        }
    }
}

// ── IDLE 开始界面 ──────────────────────────────────────────────
@Composable
private fun IdleOverlay(fg: Color, bg: Color, onStart: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "DINO  RUN",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = fg
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "tap screen to jump  ·  avoid cacti",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = fg.copy(alpha = 0.55f)
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onStart,
                shape = RoundedCornerShape(4.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = fg,
                    contentColor = bg
                ),
                modifier = Modifier
                    .widthIn(min = 140.dp)
                    .height(46.dp)
            ) {
                Text(
                    "START",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// ── GAME OVER 结算界面 ─────────────────────────────────────────
@Composable
private fun GameOverOverlay(score: Int, isNewRecord: Boolean, fg: Color, bg: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "G A M E  O V E R",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = fg
            )
            if (isNewRecord) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "NEW RECORD !",
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "returning in 2s...",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = fg.copy(alpha = 0.5f)
            )
        }
    }
}

// ── Canvas 绘制 ────────────────────────────────────────────────

private fun DrawScope.drawDinoScene(physics: GamePhysics, w: Float, h: Float, fg: Color) {
    val groundPx = physics.mushroomY * h

    // 云朵（浅灰，昼夜由调用方颜色区分）
    val cloudColor = fg.copy(alpha = 0.25f)
    physics.clouds.forEach { cloud ->
        drawCloud(cloud.x * w, cloud.y * h, cloud.scale, cloudColor)
    }

    // 地面线（一条细线 + 点状纹理）
    drawLine(color = fg, start = Offset(0f, groundPx), end = Offset(w, groundPx), strokeWidth = 2.5f)
    // 地面碎石点
    var gx = 20f
    while (gx < w) {
        val dotW = 6f + ((gx * 7 + 13) % 18)
        drawRect(fg.copy(alpha = 0.18f), Offset(gx, groundPx + 4f), Size(dotW, 2f))
        gx += dotW + 8f + ((gx.toInt() * 3) % 20)
    }

    // 恐龙（Dino 像素风）
    drawDino(0.1f * w, groundPx, physics.frameIndex, physics.isOnGround, fg)

    // 仙人掌
    physics.obstacles.forEach { obs ->
        val oh = obs.height * h
        drawDinoCactus(obs.x * w, groundPx, obs.width * w, oh, fg)
    }
}

// ── 恐龙绘制 ──────────────────────────────────────────────────
// 全部用矩形拼出像素风身体，原点 (cx, groundY)
private fun DrawScope.drawDino(cx: Float, groundY: Float, frame: Int, onGround: Boolean, fg: Color) {
    // 尺寸基准
    val u = 6f  // 1 pixel unit

    // 身体（躯干）
    val bodyW = 8 * u; val bodyH = 5 * u
    val bodyLeft = cx - bodyW * 0.4f
    val bodyTop  = groundY - bodyH - 2 * u  // 腿高 = 2u
    drawRect(fg, Offset(bodyLeft, bodyTop), Size(bodyW, bodyH))

    // 尾巴（身体左下方斜块，用两个矩形）
    drawRect(fg, Offset(bodyLeft - 2 * u, bodyTop + bodyH - 2 * u), Size(2 * u, 2 * u))
    drawRect(fg, Offset(bodyLeft - 3 * u, bodyTop + bodyH - u),     Size(u, u))

    // 脖子 + 头
    val neckLeft = bodyLeft + bodyW - 2 * u
    val neckTop  = bodyTop - 3 * u
    drawRect(fg, Offset(neckLeft, neckTop), Size(3 * u, 3 * u))   // 脖子

    val headW = 7 * u; val headH = 4 * u
    val headLeft = neckLeft - u
    val headTop  = neckTop - headH
    drawRect(fg, Offset(headLeft, headTop), Size(headW, headH))   // 头

    // 嘴（开口，挖掉右下角 1u×1u 白块）
    drawRect(fg.copy(alpha = 0f), Offset(headLeft + headW - u, headTop + headH - u), Size(u, u))

    // 眼睛（白色小圆孔）
    drawRect(
        color = if (fg == DinoColors.fgDay) DinoColors.bgDay else DinoColors.bgNight,
        topLeft = Offset(headLeft + headW - 3 * u, headTop + u),
        size = Size(u, u)
    )

    // 腿（地面上方 2u 高，IDLE/空中固定，奔跑时交替）
    val legTop = groundY - 2 * u
    when {
        !onGround -> {
            // 空中：双腿平行伸出
            drawRect(fg, Offset(cx - 2 * u, legTop), Size(u + 1, 2 * u))
            drawRect(fg, Offset(cx + u,     legTop), Size(u + 1, 2 * u))
        }
        frame == 0 -> {
            // 左腿前伸
            drawRect(fg, Offset(cx - 3 * u, legTop), Size(u + 1, 2 * u))
            drawRect(fg, Offset(cx + u,     legTop + u), Size(u + 1, u))
        }
        else -> {
            // 右腿前伸
            drawRect(fg, Offset(cx - 3 * u, legTop + u), Size(u + 1, u))
            drawRect(fg, Offset(cx + u,     legTop), Size(u + 1, 2 * u))
        }
    }
}

// ── 仙人掌绘制（Dino 风格：深灰矩形组合）──────────────────────
private fun DrawScope.drawDinoCactus(x: Float, groundY: Float, w: Float, h: Float, fg: Color) {
    // 主干
    val stemW = w * 0.28f
    val stemX = x + w * 0.36f
    drawRect(fg, Offset(stemX, groundY - h), Size(stemW, h))

    // 左臂
    drawRect(fg, Offset(x, groundY - h * 0.62f), Size(w * 0.38f, h * 0.17f))
    drawRect(fg, Offset(x, groundY - h * 0.80f), Size(w * 0.14f, h * 0.20f))

    // 右臂
    drawRect(fg, Offset(x + w * 0.62f, groundY - h * 0.52f), Size(w * 0.38f, h * 0.17f))
    drawRect(fg, Offset(x + w * 0.86f, groundY - h * 0.70f), Size(w * 0.14f, h * 0.20f))
}

// ── 云朵绘制（三个圆角矩形叠加）──────────────────────────────
private fun DrawScope.drawCloud(cx: Float, cy: Float, scale: Float, color: Color) {
    val bw = 70f * scale; val bh = 16f * scale
    drawRoundRect(color, Offset(cx - bw / 2, cy - bh / 2), Size(bw, bh), CornerRadius(bh / 2))
    drawRoundRect(color, Offset(cx - bw * 0.25f, cy - bh * 1.4f), Size(bw * 0.45f, bh * 1.1f), CornerRadius(bh / 2))
    drawRoundRect(color, Offset(cx + bw * 0.05f, cy - bh),        Size(bw * 0.30f, bh * 0.9f), CornerRadius(bh / 2))
}
