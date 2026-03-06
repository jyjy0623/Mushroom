package com.mushroom.feature.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.feature.game.entity.GameScore
import com.mushroom.feature.game.repository.GameRepository
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.math.max
import kotlin.random.Random

private const val TAG = "GameViewModel"

enum class GameState { IDLE, RUNNING, GAME_OVER }

data class Obstacle(
    val x: Float,       // 0f..1f 归一化坐标
    val width: Float = 0.06f,
    val height: Float   // 0.1f..0.22f
)

data class Cloud(
    val x: Float,   // 归一化 0..1
    val y: Float,   // 归一化 0..1
    val scale: Float = 1f
)

data class GamePhysics(
    val mushroomY: Float = 0.7f,        // 归一化 Y，0=top 1=bottom
    val velocityY: Float = 0f,
    val isOnGround: Boolean = true,
    val obstacles: List<Obstacle> = emptyList(),
    val frameIndex: Int = 0,            // 0/1 交替，用于跑步动画
    val clouds: List<Cloud> = listOf(
        Cloud(0.3f, 0.15f, 1.0f),
        Cloud(0.65f, 0.25f, 0.7f),
        Cloud(0.9f, 0.1f, 0.85f)
    )
)

data class GameUiState(
    val state: GameState = GameState.IDLE,
    val score: Int = 0,
    val highScore: Int = 0,
    val isNewRecord: Boolean = false,
    val topScores: List<GameScore> = emptyList(),
    val physics: GamePhysics = GamePhysics(),
    val warmupMs: Long = 0L   // 游戏开始后的已运行时间，前2000ms不生成障碍物
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepo: GameRepository,
    private val mushroomRepo: MushroomRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            _uiState.update { it.copy(highScore = gameRepo.getHighScore()) }
        }
    }

    val topScores: StateFlow<List<GameScore>> = gameRepo.getTopScores(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exitEvent = MutableSharedFlow<Unit>()
    val exitEvent: SharedFlow<Unit> = _exitEvent.asSharedFlow()

    private var gameLoopJob: Job? = null
    private var scoreJob: Job? = null

    // ── 物理参数（精确对标 Chrome T-Rex 原版，全部做归一化换算）──────────────────
    //
    // 原版坐标系：canvas宽600px, FPS=60
    //   SPEED=6, MAX_SPEED=12, ACCELERATION=0.001 (px/frame 每帧加速)
    //   GRAVITY=0.6 (px/frame²), INITIAL_JUMP_VELOCITY=-10 (px/frame, Trex.config)
    //   GAP_COEFFICIENT=0.6
    //
    // 归一化换算（canvas宽600px为基准，1归一化单位=600px）：
    //   速度: 6px/frame ÷ 60fps = 0.1px/ms ÷ 600 = 0.000167/ms
    //   加速: 0.001px/frame ÷ 60fps ÷ 600 = 0.0000000278 归一化/(ms²)
    //        即每1000ms速度增量 ≈ 0.0000000278 * 1000 = 0.0000278/ms
    //        原版从6→12花费约6000帧/60fps=100秒（符合原版越来越快的感觉）
    //   重力: 0.6px/frame² ÷ 60² ÷ 600 = 0.000000278 归一化/ms²
    //        但我们用屏幕高h归一化，地面在0.75，跳跃高约0.25h
    //        调整：gravity = 0.000012f（顶点时间≈167ms，落地≈334ms，手感轻快）
    //   跳速: 10px/frame ÷ 60fps ÷ 600 = 0.000278/ms（画布宽归一化）
    //        改用画布高归一化（h≈w*0.43横屏）：0.000278/0.43 ≈ 0.000648/ms
    //        但跳跃高度需≤0.25h：v²/(2g) ≤ 0.25 → v ≤ sqrt(2*0.000012*0.25)=0.00245
    //        取 jumpVelocity = -0.0024f（顶点高≈0.24h，不出屏幕，手感自然）
    //
    // 速度加速公式（精确对标原版 speed += ACCELERATION 每帧累加）：
    //   用游戏时间 warmupMs 驱动：speed = speedBase + acceleration * runningTimeMs
    //   而非用 score（score是时间函数，但不如时间直接）

    private val speedBase        = 0.000167f   // 归一化初速度（原版SPEED=6 @600px/60fps）
    private val speedMax         = 0.000334f   // 归一化最高速（原版MAX_SPEED=12，初速2倍）
    private val speedAccel       = 0.0000000278f // 归一化加速度/ms²（原版ACCELERATION=0.001）
    private val groundY          = 0.75f
    private val jumpVelocity     = -0.0024f    // 跳跃初速（顶点高≈0.24h，落地约500ms）
    private val gravity          = 0.000012f   // 重力（顶点时间≈200ms）
    private val gapCoefficient   = 0.6f        // 障碍物间距系数（原版GAP_COEFFICIENT=0.6）

    fun startGame() {
        MushroomLogger.w(TAG, "startGame() called, current state=${_uiState.value.state}")
        if (_uiState.value.state == GameState.RUNNING) return
        _uiState.update {
            it.copy(
                state = GameState.RUNNING,
                score = 0,
                isNewRecord = false,
                warmupMs = 0L,
                physics = GamePhysics(
                    mushroomY = groundY,
                    velocityY = 0f,
                    isOnGround = true,
                    obstacles = emptyList()
                )
            )
        }
        MushroomLogger.w(TAG, "startGame() → state updated to RUNNING, mushroomY=$groundY")
        startGameLoop()
    }

    fun onTap() {
        val s = _uiState.value.state
        MushroomLogger.w(TAG, "onTap() state=$s")
        when (s) {
            GameState.IDLE     -> startGame()
            GameState.RUNNING  -> jump()
            GameState.GAME_OVER -> { }
        }
    }

    fun jump() {
        val physics = _uiState.value.physics
        val state = _uiState.value.state
        MushroomLogger.w(TAG, "jump() state=$state isOnGround=${physics.isOnGround} mushroomY=${physics.mushroomY} velocityY=${physics.velocityY}")
        if (state != GameState.RUNNING) return
        if (!physics.isOnGround) return
        MushroomLogger.w(TAG, "jump() → applying jumpVelocity=$jumpVelocity, setting isOnGround=false")
        _uiState.update { it.copy(physics = it.physics.copy(velocityY = jumpVelocity, isOnGround = false)) }
        MushroomLogger.w(TAG, "jump() → after update: velocityY=${_uiState.value.physics.velocityY} isOnGround=${_uiState.value.physics.isOnGround}")
    }

    fun tick(dtMs: Long) {
        if (_uiState.value.state != GameState.RUNNING) return
        val state = _uiState.value
        val physics = state.physics

        // 物理更新（先用旧速度更新位置，再更新速度，避免跳跃初帧被重力抵消）
        var newY = physics.mushroomY + physics.velocityY * dtMs
        var newVY = physics.velocityY + gravity * dtMs
        val onGround: Boolean
        if (newY >= groundY) {
            newY = groundY
            newVY = 0f
            onGround = true
        } else {
            onGround = false
        }

        // 障碍物更新（速度用运行时间驱动，精确对标原版 speed += ACCELERATION 每帧累加）
        val runningMs = state.warmupMs.toFloat()
        val speed = (speedBase + speedAccel * runningMs * runningMs / 2f)
            .coerceAtMost(speedMax)
        val newObstacles = physics.obstacles
            .map { it.copy(x = it.x - speed * dtMs) }
            .filter { it.x + it.width > -0.05f }
            .toMutableList()

        // 热身期（前3000ms）不生成障碍物；热身结束后第一个障碍物从 x=1.5 更远处生成
        val newWarmupMs = state.warmupMs + dtMs
        if (newWarmupMs >= 3000L) {
            val rightmostX = newObstacles.maxOfOrNull { it.x } ?: 0f
            // 最小间距：对标原版 minGap = obstacleWidth * currentSpeed * GAP_COEFFICIENT
            // 原版障碍物宽约 0.06（归一化），速度越快间距越大，保证可跳跃反应时间
            val minGap = 0.06f * speed / speedBase * gapCoefficient
            if (newObstacles.isEmpty() || rightmostX < minGap) {
                if (newObstacles.isEmpty() || Random.nextFloat() < 0.005f * dtMs) {
                    val spawnX = if (newObstacles.isEmpty()) 1.5f else 1.05f
                    newObstacles.add(
                        Obstacle(
                            x = spawnX,
                            height = 0.1f + Random.nextFloat() * 0.12f
                        )
                    )
                }
            }
        }

        // 碰撞检测（蘑菇 cx=0.1，u=h*0.0075，帽沿14u≈0.045w，总高12u≈0.09h）
        // 用帽沿宽度的70%作为碰撞宽度（去掉帽沿边缘）
        // 假设屏幕宽高比约2.3:1（横屏1080p），u/w ≈ 0.0075/2.3 ≈ 0.00326
        val halfBrimNorm = 7f * 0.00326f * 0.7f  // 帽沿半宽 * 0.7 ≈ 0.016
        val mushroomLeft  = 0.1f - halfBrimNorm   // ≈ 0.084
        val mushroomRight = 0.1f + halfBrimNorm   // ≈ 0.116
        val mushroomTop   = newY - 0.09f          // 总高约 9% 屏幕高
        val mushroomBottom = newY

        val collision = newObstacles.any { obs ->
            val obsTop = groundY - obs.height
            mushroomRight > obs.x && mushroomLeft < obs.x + obs.width &&
                mushroomBottom > obsTop && mushroomTop < groundY
        }

        if (collision) {
            val hit = newObstacles.first { obs ->
                val obsTop = groundY - obs.height
                mushroomRight > obs.x && mushroomLeft < obs.x + obs.width &&
                    mushroomBottom > obsTop && mushroomTop < groundY
            }
            MushroomLogger.w(TAG, "collision! mushroomY=$newY obs.x=${hit.x} obs.w=${hit.width} obs.h=${hit.height}")
            endGame()
            return
        }

        // 跳跃首帧：velocityY < 0 时记录物理状态（确认跳跃已生效）
        if (physics.velocityY < 0f) {
            MushroomLogger.w(TAG, "tick() jump in progress: mushroomY=$newY velocityY=$newVY onGround=$onGround dtMs=$dtMs")
        }

        val newFrame = if (dtMs > 0) (physics.frameIndex + 1) % 2 else physics.frameIndex

        // 云朵缓慢向左漂移，移出左边后从右侧重新进入
        val cloudSpeed = speed * 0.15f
        val newClouds = physics.clouds.map { cloud ->
            val nx = cloud.x - cloudSpeed * dtMs
            if (nx + 0.2f < 0f) cloud.copy(x = 1.1f, y = 0.05f + Random.nextFloat() * 0.3f)
            else cloud.copy(x = nx)
        }

        _uiState.update {
            it.copy(
                warmupMs = newWarmupMs,
                physics = GamePhysics(
                    mushroomY = newY,
                    velocityY = newVY,
                    isOnGround = onGround,
                    obstacles = newObstacles,
                    frameIndex = newFrame,
                    clouds = newClouds
                )
            )
        }
    }

    fun addScore(points: Int) {
        if (_uiState.value.state != GameState.RUNNING) return
        _uiState.update { it.copy(score = it.score + points) }
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        scoreJob?.cancel()

        scoreJob = viewModelScope.launch {
            while (_uiState.value.state == GameState.RUNNING) {
                delay(100)
                addScore(1)
            }
        }
    }

    private fun endGame() {
        MushroomLogger.w(TAG, "endGame() score=${_uiState.value.score}")
        gameLoopJob?.cancel()
        scoreJob?.cancel()

        val finalScore = _uiState.value.score
        _uiState.update { it.copy(state = GameState.GAME_OVER) }

        viewModelScope.launch {
            val highScore = gameRepo.getHighScore()
            val isNew = finalScore > highScore

            // 插入排行榜
            gameRepo.insertScore(GameScore(score = finalScore, playedAt = LocalDateTime.now()))

            _uiState.update {
                it.copy(
                    isNewRecord = isNew,
                    highScore = maxOf(finalScore, highScore)
                )
            }

            // 检查里程碑奖励
            checkMilestoneRewards(maxOf(finalScore, highScore))

            // 2秒后发出退出信号
            delay(2_000)
            _exitEvent.emit(Unit)
        }
    }

    private suspend fun checkMilestoneRewards(highScore: Int) {
        val milestones = listOf(
            500 to MushroomLevel.SMALL,
            1000 to MushroomLevel.MEDIUM,
            2000 to MushroomLevel.LARGE
        )
        milestones.forEach { (threshold, level) ->
            if (highScore >= threshold && !gameRepo.hasMilestoneRewarded(threshold)) {
                gameRepo.markMilestoneRewarded(threshold)
                mushroomRepo.recordTransaction(
                    MushroomTransaction(
                        level = level,
                        action = MushroomAction.EARN,
                        amount = 1,
                        sourceType = MushroomSource.GAME,
                        sourceId = null,
                        note = "跑酷游戏里程碑 ${threshold} 分",
                        createdAt = LocalDateTime.now()
                    )
                )
            }
        }
    }

    fun awardDailyPlayReward() {
        viewModelScope.launch {
            val today = LocalDate.now()
            if (!gameRepo.hasPlayedToday(today)) {
                gameRepo.markPlayedToday(today)
                mushroomRepo.recordTransaction(
                    MushroomTransaction(
                        level = MushroomLevel.SMALL,
                        action = MushroomAction.EARN,
                        amount = 1,
                        sourceType = MushroomSource.GAME,
                        sourceId = null,
                        note = "每日跑酷游戏奖励",
                        createdAt = LocalDateTime.now()
                    )
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        gameLoopJob?.cancel()
        scoreJob?.cancel()
    }
}
