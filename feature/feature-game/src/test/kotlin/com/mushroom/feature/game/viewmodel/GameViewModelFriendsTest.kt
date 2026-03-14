package com.mushroom.feature.game.viewmodel

import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendInfo
import com.mushroom.adventure.core.network.data.FriendListResponse
import com.mushroom.adventure.core.network.data.LeaderboardEntry
import com.mushroom.adventure.core.network.data.LeaderboardResponse
import com.mushroom.adventure.core.network.repository.AuthRepository
import com.mushroom.adventure.core.network.repository.FriendRepository
import com.mushroom.adventure.core.network.repository.LeaderboardRepository
import com.mushroom.core.domain.repository.MushroomRepository
import com.mushroom.feature.game.repository.GameRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GameViewModelFriendsTest {

    private lateinit var viewModel: GameViewModel
    private lateinit var friendRepo: FriendRepository
    private lateinit var leaderboardRepo: LeaderboardRepository
    private val gameRepo: GameRepository = mockk(relaxed = true)
    private val mushroomRepo: MushroomRepository = mockk(relaxed = true)
    private val authRepo: AuthRepository = mockk(relaxed = true)
    private val testDispatcher = StandardTestDispatcher()

    @BeforeEach
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        friendRepo = mockk(relaxed = true)
        leaderboardRepo = mockk(relaxed = true)

        coEvery { gameRepo.getHighScore() } returns 0
        every { gameRepo.getTopScores(any()) } returns flowOf(emptyList())

        viewModel = GameViewModel(gameRepo, mushroomRepo, leaderboardRepo, authRepo, friendRepo)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -----------------------------------------------------------------------
    // loadFriends
    // -----------------------------------------------------------------------

    @Test
    fun `when_loadFriends_success_should_update_friends_state`() = runTest {
        // Arrange
        val friends = listOf(
            FriendInfo(userId = 2, nickname = "小明", maskedPhone = "138****5678"),
            FriendInfo(userId = 3, nickname = "小红", maskedPhone = "139****1234")
        )
        coEvery { friendRepo.getFriendList() } returns Result.success(FriendListResponse(friends))

        // Act
        viewModel.loadFriends()
        advanceUntilIdle()

        // Assert
        val state = viewModel.friendsState.value
        assertEquals(false, state.isLoading)
        assertEquals(2, state.friends.size)
        assertEquals("小明", state.friends[0].nickname)
        assertNull(state.error)
    }

    @Test
    fun `when_loadFriends_failure_should_set_error`() = runTest {
        // Arrange
        coEvery { friendRepo.getFriendList() } returns Result.failure(RuntimeException("网络错误"))

        // Act
        viewModel.loadFriends()
        advanceUntilIdle()

        // Assert
        val state = viewModel.friendsState.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error?.contains("网络错误") == true)
    }

    @Test
    fun `when_loadFriends_completes_should_not_be_loading`() = runTest {
        // Arrange
        coEvery { friendRepo.getFriendList() } returns Result.success(FriendListResponse(emptyList()))

        // Act
        viewModel.loadFriends()
        advanceUntilIdle()

        // Assert
        assertEquals(false, viewModel.friendsState.value.isLoading)
        assertTrue(viewModel.friendsState.value.friends.isEmpty())
    }

    // -----------------------------------------------------------------------
    // addFriend
    // -----------------------------------------------------------------------

    @Test
    fun `when_addFriend_success_should_set_addResult_and_reload`() = runTest {
        // Arrange
        coEvery { friendRepo.addFriend("13800000001") } returns Result.success(
            AddFriendResponse(success = true, message = "好友添加成功！")
        )
        coEvery { friendRepo.getFriendList() } returns Result.success(
            FriendListResponse(listOf(FriendInfo(2, "新好友", "138****0001")))
        )

        // Act
        viewModel.addFriend("13800000001")
        advanceUntilIdle()

        // Assert
        val state = viewModel.friendsState.value
        assertEquals("好友添加成功！", state.addResult)
        // 添加成功后应自动 reload
        coVerify(atLeast = 1) { friendRepo.getFriendList() }
    }

    @Test
    fun `when_addFriend_already_exists_should_show_message_without_reload`() = runTest {
        // Arrange
        coEvery { friendRepo.addFriend("13800000001") } returns Result.success(
            AddFriendResponse(success = false, message = "已经是好友了")
        )

        // Act
        viewModel.addFriend("13800000001")
        advanceUntilIdle()

        // Assert
        assertEquals("已经是好友了", viewModel.friendsState.value.addResult)
    }

    @Test
    fun `when_addFriend_failure_should_set_error_addResult`() = runTest {
        // Arrange
        coEvery { friendRepo.addFriend(any()) } returns Result.failure(RuntimeException("服务器错误"))

        // Act
        viewModel.addFriend("13800000001")
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.friendsState.value.addResult?.contains("服务器错误") == true)
    }

    // -----------------------------------------------------------------------
    // removeFriend
    // -----------------------------------------------------------------------

    @Test
    fun `when_removeFriend_success_should_reload_friends`() = runTest {
        // Arrange
        coEvery { friendRepo.removeFriend(42) } returns Result.success(Unit)
        coEvery { friendRepo.getFriendList() } returns Result.success(FriendListResponse(emptyList()))

        // Act
        viewModel.removeFriend(42)
        advanceUntilIdle()

        // Assert
        coVerify(exactly = 1) { friendRepo.removeFriend(42) }
        coVerify(atLeast = 1) { friendRepo.getFriendList() }
    }

    @Test
    fun `when_removeFriend_failure_should_set_error`() = runTest {
        // Arrange
        coEvery { friendRepo.removeFriend(any()) } returns Result.failure(RuntimeException("删除失败"))

        // Act
        viewModel.removeFriend(42)
        advanceUntilIdle()

        // Assert
        assertTrue(viewModel.friendsState.value.error?.contains("删除失败") == true)
    }

    // -----------------------------------------------------------------------
    // clearAddResult
    // -----------------------------------------------------------------------

    @Test
    fun `when_clearAddResult_should_set_addResult_to_null`() = runTest {
        // Arrange — first set a result
        coEvery { friendRepo.addFriend(any()) } returns Result.success(
            AddFriendResponse(success = true, message = "成功")
        )
        coEvery { friendRepo.getFriendList() } returns Result.success(FriendListResponse(emptyList()))
        viewModel.addFriend("13800000001")
        advanceUntilIdle()
        assertEquals("成功", viewModel.friendsState.value.addResult)

        // Act
        viewModel.clearAddResult()

        // Assert
        assertNull(viewModel.friendsState.value.addResult)
    }

    // -----------------------------------------------------------------------
    // loadFriendLeaderboard
    // -----------------------------------------------------------------------

    @Test
    fun `when_loadFriendLeaderboard_success_should_update_state`() = runTest {
        // Arrange
        val entries = listOf(
            LeaderboardEntry(rank = 1, userId = 2, nickname = "用户B", score = 200, createdAt = 0),
            LeaderboardEntry(rank = 2, userId = 1, nickname = "用户A", score = 100, createdAt = 0)
        )
        val myEntry = entries[1]
        coEvery { leaderboardRepo.getFriendLeaderboard("runner") } returns Result.success(
            LeaderboardResponse(entries = entries, myEntry = myEntry)
        )

        // Act
        viewModel.loadFriendLeaderboard()
        advanceUntilIdle()

        // Assert
        val state = viewModel.friendLeaderboard.value
        assertEquals(false, state.isLoading)
        assertEquals(2, state.entries.size)
        assertEquals("用户B", state.entries[0].nickname)
        assertEquals(1, state.myEntry?.userId)
        assertNull(state.error)
    }

    @Test
    fun `when_loadFriendLeaderboard_failure_should_set_error`() = runTest {
        // Arrange
        coEvery { leaderboardRepo.getFriendLeaderboard(any()) } returns Result.failure(RuntimeException("加载失败"))

        // Act
        viewModel.loadFriendLeaderboard()
        advanceUntilIdle()

        // Assert
        val state = viewModel.friendLeaderboard.value
        assertEquals(false, state.isLoading)
        assertTrue(state.error?.contains("加载失败") == true)
    }
}
