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
    val physics: GamePhysics = GamePhysics()
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

    // 障碍物生成参数
    private val obstacleSpeedBase = 0.0018f  // 每毫秒移动距离（归一化）
    private val groundY = 0.75f
    private val jumpVelocity = -0.0012f   // 跳跃初速度（归一化/ms，向上为负）
    private val gravity = 0.000004f        // 重力加速度（归一化/ms²）

    fun startGame() {
        if (_uiState.value.state == GameState.RUNNING) return
        _uiState.update {
            it.copy(
                state = GameState.RUNNING,
                score = 0,
                isNewRecord = false,
                physics = GamePhysics(
                    mushroomY = groundY,
                    velocityY = 0f,
                    isOnGround = true,
                    obstacles = emptyList()
                )
            )
        }
        startGameLoop()
    }

    fun jump() {
        val physics = _uiState.value.physics
        if (_uiState.value.state != GameState.RUNNING) return
        if (!physics.isOnGround) return
        _uiState.update { it.copy(physics = it.physics.copy(velocityY = jumpVelocity, isOnGround = false)) }
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

        // 障碍物更新
        val speed = obstacleSpeedBase * (1f + state.score / 500f)  // 随分数加速
        val newObstacles = physics.obstacles
            .map { it.copy(x = it.x - speed * dtMs) }
            .filter { it.x + it.width > -0.05f }  // 移出屏幕左边后删除
            .toMutableList()

        // 随机生成新障碍物
        val lastX = newObstacles.lastOrNull()?.x ?: 0f
        if (newObstacles.isEmpty() || lastX < 0.7f) {
            if (newObstacles.isEmpty() || Random.nextFloat() < 0.005f * dtMs) {
                newObstacles.add(
                    Obstacle(
                        x = 1.05f,
                        height = 0.1f + Random.nextFloat() * 0.12f
                    )
                )
            }
        }

        // 碰撞检测（蘑菇宽0.07，高0.13，居中于0.1位置）
        val mushroomLeft = 0.065f
        val mushroomRight = 0.135f
        val mushroomTop = newY - 0.13f
        val mushroomBottom = newY

        val collision = newObstacles.any { obs ->
            val obsTop = groundY - obs.height
            mushroomRight > obs.x && mushroomLeft < obs.x + obs.width &&
                mushroomBottom > obsTop && mushroomTop < groundY
        }

        if (collision) {
            endGame()
            return
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
