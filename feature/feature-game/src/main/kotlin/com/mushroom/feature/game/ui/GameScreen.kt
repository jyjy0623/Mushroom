package com.mushroom.feature.game.ui

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.mushroom.core.logging.MushroomLogger
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

private const val TAG = "GameScreen"

@Composable
fun GameScreen(
    onExit: () -> Unit,
    viewModel: GameViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 版本 + 进入日志（用于确认测试版本）
    LaunchedEffect(Unit) {
        val pm = context.packageManager
        val pi = pm.getPackageInfo(context.packageName, 0)
        MushroomLogger.w(TAG, "GameScreen entered. pkg=${context.packageName} versionName=${pi.versionName} versionCode=${pi.longVersionCode}")
    }

    // 进入横屏并锁定方向，离开时恢复原方向
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val orig = activity?.requestedOrientation
        MushroomLogger.w(TAG, "orientation lock: orig=$orig → SENSOR_LANDSCAPE, activity=${activity?.javaClass?.simpleName}")
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            MushroomLogger.w(TAG, "GameScreen disposed, restoring orientation to $orig")
            if (orig != null) activity.requestedOrientation = orig
        }
    }

    LaunchedEffect(Unit) { viewModel.exitEvent.collectLatest { onExit() } }
    LaunchedEffect(Unit) { viewModel.awardDailyPlayReward() }

    // 帧循环状态追踪
    LaunchedEffect(uiState.state) {
        MushroomLogger.w(TAG, "LaunchedEffect(state): state changed to ${uiState.state}")
        if (uiState.state == GameState.RUNNING) {
            MushroomLogger.w(TAG, "frame loop starting")
            var last = withFrameMillis { it }
            var frameCount = 0
            while (true) {
                val now = withFrameMillis { it }
                val dt = (now - last).coerceIn(1, 50)
                viewModel.tick(dt)
                last = now
                frameCount++
                // 每100帧记录一次确认帧循环在运行
                if (frameCount == 100) {
                    MushroomLogger.w(TAG, "frame loop alive: 100 frames processed, score=${uiState.score}")
                }
            }
        }
    }

    val isNight = uiState.score >= 700
    val bg   = if (isNight) DinoColors.bgNight  else DinoColors.bgDay
    val fg   = if (isNight) DinoColors.fgNight  else DinoColors.fgDay
    val text = if (isNight) DinoColors.textNight else DinoColors.textDay

    // pointerInput 用固定 key=Unit，始终挂载；按下即响应（awaitFirstDown），无需等待抬起
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    viewModel.onTap()
                }
            }
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
            GameState.IDLE     -> IdleOverlay(fg)
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
private fun IdleOverlay(fg: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "MUSHROOM  ADVENTURE",
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
            Spacer(Modifier.height(16.dp))
            Text(
                "[ tap anywhere to start ]",
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = fg.copy(alpha = 0.75f)
            )
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
    val groundPx = 0.75f * h  // 固定地面高度，与 GameViewModel.groundY 保持一致

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

    // 蘑菇（像素风）—— 使用 physics.mushroomY * h 渲染实际位置
    drawMushroomPixel(0.1f * w, physics.mushroomY * h, physics.frameIndex, physics.isOnGround, fg)

    // 仙人掌
    physics.obstacles.forEach { obs ->
        val oh = obs.height * h
        drawDinoCactus(obs.x * w, groundPx, obs.width * w, oh, fg)
    }
}

// ── 蘑菇绘制（像素风，原点 (cx, baseY) 为脚底中心）──────────────
// 参考马里奥像素蘑菇比例：大帽子（拱形）+ 细茎 + 短腿
private fun DrawScope.drawMushroomPixel(cx: Float, baseY: Float, frame: Int, onGround: Boolean, fg: Color) {
    val u = 5f  // 1 pixel unit
    val spotColor = if (fg == DinoColors.fgDay) DinoColors.bgDay else DinoColors.bgNight

    // ── 腿（跑步动画，高 2u）──────────────────────────────────
    val legH = 2 * u
    val legTop = baseY - legH
    when {
        !onGround -> {
            // 空中：两腿并拢收起
            drawRect(fg, Offset(cx - 2 * u, legTop), Size(u, legH))
            drawRect(fg, Offset(cx + u,     legTop), Size(u, legH))
        }
        frame == 0 -> {
            // 跑步帧A：左腿前伸、右腿后蹬
            drawRect(fg, Offset(cx - 3 * u, legTop),         Size(u, legH))
            drawRect(fg, Offset(cx + u,     legTop + u),     Size(u, u))
        }
        else -> {
            // 跑步帧B：右腿前伸、左腿后蹬
            drawRect(fg, Offset(cx - 3 * u, legTop + u),     Size(u, u))
            drawRect(fg, Offset(cx + u,     legTop),         Size(u, legH))
        }
    }

    // ── 茎（细矩形，帽子宽度约40%）────────────────────────────
    val stemW = 4 * u
    val stemH = 3 * u
    val stemTop = legTop - stemH
    drawRect(fg, Offset(cx - stemW / 2f, stemTop), Size(stemW, stemH))

    // ── 帽沿（比茎宽很多，视觉上是伞边）──────────────────────
    val brimW = 14 * u
    val brimH = 2 * u
    val brimTop = stemTop - brimH
    drawRect(fg, Offset(cx - brimW / 2f, brimTop), Size(brimW, brimH))

    // ── 帽顶（像素拱形：从宽到窄再到窄，模拟圆弧）────────────
    // 行宽序列（从下到上）：12u 12u 10u 8u 5u
    // 整体呈半圆形，最宽行和帽沿等宽或略窄
    val capRows = listOf(12 * u, 12 * u, 10 * u, 8 * u, 5 * u)
    var rowTop = brimTop - capRows.size * u
    for (rowW in capRows) {
        drawRect(fg, Offset(cx - rowW / 2f, rowTop), Size(rowW, u))
        rowTop += u
    }

    // ── 帽子白色斑点（画在帽上半部，左右各一）────────────────
    // 斑点位于帽顶第2、3行高度，左右对称
    val spotY = brimTop - 4 * u
    drawRect(spotColor, Offset(cx - 5 * u, spotY), Size(2 * u, 2 * u))
    drawRect(spotColor, Offset(cx + 3 * u, spotY), Size(2 * u, 2 * u))

    // ── 眼睛（茎右侧小白点）──────────────────────────────────
    drawRect(spotColor, Offset(cx + stemW / 2f - u, stemTop + u), Size(u, u))
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
