package com.mushroom.feature.game.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mushroom.adventure.core.network.data.LeaderboardEntry
import com.mushroom.adventure.core.network.data.LeaderboardResponse
import com.mushroom.adventure.core.network.data.FriendInfo
import com.mushroom.adventure.core.network.data.FriendStatsResponse
import com.mushroom.adventure.core.network.repository.AuthRepository
import com.mushroom.adventure.core.network.repository.FriendRepository
import com.mushroom.adventure.core.network.repository.LeaderboardRepository
import com.mushroom.core.domain.entity.MushroomAction
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.feature.game.entity.GameScore
import com.mushroom.feature.game.repository.GameRepository
import com.mushroom.core.logging.MushroomLogger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
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

data class GlobalLeaderboardState(
    val isLoading: Boolean = false,
    val entries: List<LeaderboardEntry> = emptyList(),
    val myEntry: LeaderboardEntry? = null,
    val error: String? = null
)

data class FriendsState(
    val isLoading: Boolean = false,
    val friends: List<FriendInfo> = emptyList(),
    val addResult: String? = null,
    val error: String? = null
)

data class FriendStatsState(
    val isLoading: Boolean = false,
    val stats: FriendStatsResponse? = null,
    val error: String? = null
)

@HiltViewModel
class GameViewModel @Inject constructor(
    private val gameRepo: GameRepository,
    private val mushroomRepo: MushroomRepository,
    private val leaderboardRepo: LeaderboardRepository,
    private val authRepo: AuthRepository,
    private val friendRepo: FriendRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val _globalLeaderboard = MutableStateFlow(GlobalLeaderboardState())
    val globalLeaderboard: StateFlow<GlobalLeaderboardState> = _globalLeaderboard.asStateFlow()

    private val _friendLeaderboard = MutableStateFlow(GlobalLeaderboardState())
    val friendLeaderboard: StateFlow<GlobalLeaderboardState> = _friendLeaderboard.asStateFlow()

    private val _friendsState = MutableStateFlow(FriendsState())
    val friendsState: StateFlow<FriendsState> = _friendsState.asStateFlow()

    private val _friendStats = MutableStateFlow(FriendStatsState())
    val friendStats: StateFlow<FriendStatsState> = _friendStats.asStateFlow()

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

    // ── 物理参数（对标 Chrome T-Rex 原版，并针对手机触屏调整反应时间）────────────
    private val speedBase      = 0.0004f
    private val speedMax       = 0.0009f
    private val speedAccel     = 0.000005f
    private val groundY        = 0.75f
    private val jumpVelocity   = -0.002f
    private val gravity        = 0.000008f
    private val gapCoefficient = 0.6f

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

        // 物理更新
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
        val runningMs = state.warmupMs.toFloat()
        val speed = (speedBase + speedAccel * runningMs).coerceAtMost(speedMax)
        val newObstacles = physics.obstacles
            .map { it.copy(x = it.x - speed * dtMs) }
            .filter { it.x + it.width > -0.05f }
            .toMutableList()

        val newWarmupMs = state.warmupMs + dtMs
        if (newWarmupMs >= 3000L) {
            val rightmostX = newObstacles.maxOfOrNull { it.x } ?: 0f
            val minGap = 0.06f * speed / speedBase * gapCoefficient
            if (newObstacles.isEmpty() || rightmostX < minGap) {
                if (newObstacles.isEmpty() || Random.nextFloat() < 0.005f * dtMs) {
                    val spawnX = if (newObstacles.isEmpty()) 1.4f else 1.05f
                    newObstacles.add(
                        Obstacle(
                            x = spawnX,
                            height = 0.1f + Random.nextFloat() * 0.12f
                        )
                    )
                }
            }
        }

        // 碰撞检测
        val halfBrimNorm = 7f * 0.00326f * 0.7f
        val mushroomLeft  = 0.1f - halfBrimNorm
        val mushroomRight = 0.1f + halfBrimNorm
        val mushroomTop   = newY - 0.09f
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

        if (physics.velocityY < 0f) {
            MushroomLogger.w(TAG, "tick() jump in progress: mushroomY=$newY velocityY=$newVY onGround=$onGround dtMs=$dtMs")
        }

        val newFrame = if (dtMs > 0) (physics.frameIndex + 1) % 2 else physics.frameIndex

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

            // 插入本地排行榜
            gameRepo.insertScore(GameScore(score = finalScore, playedAt = LocalDateTime.now()))

            _uiState.update {
                it.copy(
                    isNewRecord = isNew,
                    highScore = maxOf(finalScore, highScore)
                )
            }

            // 云端提交分数（仅已登录用户，静默失败）
            if (authRepo.isLoggedIn.value) {
                launch(Dispatchers.IO) {
                    leaderboardRepo.submitScore("runner", finalScore).onFailure { e ->
                        MushroomLogger.e(TAG, "Cloud score submit failed", e)
                    }
                }
            }

            // 检查里程碑奖励
            checkMilestoneRewards(maxOf(finalScore, highScore))

            // 2秒后发出退出信号
            delay(2_000)
            _exitEvent.emit(Unit)
        }
    }

    fun loadGlobalLeaderboard() {
        viewModelScope.launch {
            _globalLeaderboard.update { it.copy(isLoading = true, error = null) }
            leaderboardRepo.getLeaderboard("runner", 100)
                .onSuccess { response ->
                    _globalLeaderboard.update {
                        it.copy(
                            isLoading = false,
                            entries = response.entries,
                            myEntry = response.myEntry,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _globalLeaderboard.update {
                        it.copy(isLoading = false, error = "加载失败: ${e.message}")
                    }
                }
        }
    }

    fun loadFriendLeaderboard() {
        viewModelScope.launch {
            _friendLeaderboard.update { it.copy(isLoading = true, error = null) }
            leaderboardRepo.getFriendLeaderboard("runner")
                .onSuccess { response ->
                    _friendLeaderboard.update {
                        it.copy(
                            isLoading = false,
                            entries = response.entries,
                            myEntry = response.myEntry,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _friendLeaderboard.update {
                        it.copy(isLoading = false, error = "加载失败: ${e.message}")
                    }
                }
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            _friendsState.update { it.copy(isLoading = true, error = null) }
            friendRepo.getFriendList()
                .onSuccess { list ->
                    _friendsState.update {
                        it.copy(
                            isLoading = false,
                            friends = list.friends,
                            error = null
                        )
                    }
                }
                .onFailure { e ->
                    _friendsState.update {
                        it.copy(isLoading = false, error = "加载失败: ${e.message}")
                    }
                }
        }
    }

    fun addFriend(phone: String, message: String = "") {
        viewModelScope.launch {
            _friendsState.update { it.copy(addResult = null) }
            friendRepo.addFriend(phone, message)
                .onSuccess { response ->
                    _friendsState.update { it.copy(addResult = response.message) }
                    if (response.success) loadFriends()
                }
                .onFailure { e ->
                    _friendsState.update { it.copy(addResult = "操作失败: ${e.message}") }
                }
        }
    }

    fun removeFriend(userId: Int) {
        viewModelScope.launch {
            friendRepo.removeFriend(userId)
                .onSuccess { loadFriends() }
                .onFailure { e ->
                    _friendsState.update { it.copy(error = "删除失败: ${e.message}") }
                }
        }
    }

    fun clearAddResult() {
        _friendsState.update { it.copy(addResult = null) }
    }

    fun loadFriendStats(userId: Int) {
        viewModelScope.launch {
            _friendStats.update { FriendStatsState(isLoading = true) }
            friendRepo.getFriendStats(userId)
                .onSuccess { stats ->
                    _friendStats.update { FriendStatsState(stats = stats) }
                }
                .onFailure { e ->
                    _friendStats.update { FriendStatsState(error = "加载失败: ${e.message}") }
                }
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
