package com.mushroom.adventure.core.network.repository

import com.mushroom.adventure.core.network.api.FriendApi
import com.mushroom.adventure.core.network.data.AddFriendRequest
import com.mushroom.adventure.core.network.data.AddFriendResponse
import com.mushroom.adventure.core.network.data.FriendInfo
import com.mushroom.adventure.core.network.data.FriendListResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FriendRepositoryTest {

    private lateinit var api: FriendApi
    private lateinit var repo: FriendRepository

    @BeforeEach
    fun setup() {
        api = mockk()
        repo = FriendRepository(api)
    }

    // -----------------------------------------------------------------------
    // addFriend
    // -----------------------------------------------------------------------

    @Test
    fun `when_addFriend_success_should_return_result_with_message`() = runTest {
        // Arrange
        val response = AddFriendResponse(success = true, message = "好友添加成功！")
        coEvery { api.addFriend(AddFriendRequest("13800000001")) } returns response

        // Act
        val result = repo.addFriend("13800000001")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("好友添加成功！", result.getOrNull()?.message)
        assertEquals(true, result.getOrNull()?.success)
        coVerify(exactly = 1) { api.addFriend(AddFriendRequest("13800000001")) }
    }

    @Test
    fun `when_addFriend_already_exists_should_return_success_false`() = runTest {
        // Arrange
        val response = AddFriendResponse(success = false, message = "已经是好友了")
        coEvery { api.addFriend(any()) } returns response

        // Act
        val result = repo.addFriend("13800000001")

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(false, result.getOrNull()?.success)
        assertEquals("已经是好友了", result.getOrNull()?.message)
    }

    @Test
    fun `when_addFriend_api_throws_should_return_failure`() = runTest {
        // Arrange
        coEvery { api.addFriend(any()) } throws RuntimeException("网络错误")

        // Act
        val result = repo.addFriend("13800000001")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("网络错误", result.exceptionOrNull()?.message)
    }

    // -----------------------------------------------------------------------
    // getFriendList
    // -----------------------------------------------------------------------

    @Test
    fun `when_getFriendList_success_should_return_friends`() = runTest {
        // Arrange
        val friends = listOf(
            FriendInfo(userId = 2, nickname = "小明", maskedPhone = "138****5678"),
            FriendInfo(userId = 3, nickname = "小红", maskedPhone = "139****1234")
        )
        coEvery { api.getFriendList() } returns FriendListResponse(friends)

        // Act
        val result = repo.getFriendList()

        // Assert
        assertTrue(result.isSuccess)
        assertEquals(2, result.getOrNull()?.friends?.size)
        assertEquals("小明", result.getOrNull()?.friends?.get(0)?.nickname)
        assertEquals("138****5678", result.getOrNull()?.friends?.get(0)?.maskedPhone)
    }

    @Test
    fun `when_getFriendList_empty_should_return_empty_list`() = runTest {
        // Arrange
        coEvery { api.getFriendList() } returns FriendListResponse(emptyList())

        // Act
        val result = repo.getFriendList()

        // Assert
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.friends?.isEmpty() == true)
    }

    @Test
    fun `when_getFriendList_api_throws_should_return_failure`() = runTest {
        // Arrange
        coEvery { api.getFriendList() } throws RuntimeException("服务器错误")

        // Act
        val result = repo.getFriendList()

        // Assert
        assertTrue(result.isFailure)
    }

    // -----------------------------------------------------------------------
    // removeFriend
    // -----------------------------------------------------------------------

    @Test
    fun `when_removeFriend_success_should_return_success`() = runTest {
        // Arrange
        coEvery { api.removeFriend(42) } returns Unit

        // Act
        val result = repo.removeFriend(42)

        // Assert
        assertTrue(result.isSuccess)
        coVerify(exactly = 1) { api.removeFriend(42) }
    }

    @Test
    fun `when_removeFriend_api_throws_should_return_failure`() = runTest {
        // Arrange
        coEvery { api.removeFriend(any()) } throws RuntimeException("删除失败")

        // Act
        val result = repo.removeFriend(42)

        // Assert
        assertTrue(result.isFailure)
    }
}
