package com.mushroom.core.domain.event

import app.cash.turbine.test
import com.mushroom.core.domain.entity.MushroomLevel
import com.mushroom.core.domain.entity.MushroomSource
import com.mushroom.core.domain.entity.MushroomTransaction
import com.mushroom.core.domain.entity.MushroomAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class AppEventBusImplTest {

    private lateinit var bus: AppEventBusImpl

    @BeforeEach
    fun setUp() {
        bus = AppEventBusImpl()
    }

    @Test
    fun `when_event_emitted_subscriber_should_receive_it`() = runTest {
        val event = AppEvent.TaskCheckedIn(
            taskId = 1L,
            checkInTime = LocalDateTime.of(2026, 3, 1, 20, 0),
            isEarly = false,
            earlyMinutes = 0
        )

        bus.events.test {
            bus.emit(event)
            val received = awaitItem()
            assertEquals(event, received)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when_multiple_events_emitted_should_receive_in_order`() = runTest {
        val event1 = AppEvent.TaskCheckedIn(1L, LocalDateTime.now(), false, 0)
        val event2 = AppEvent.TaskCheckedIn(2L, LocalDateTime.now(), true, 30)

        bus.events.test {
            bus.emit(event1)
            bus.emit(event2)
            assertEquals(event1, awaitItem())
            assertEquals(event2, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when_two_subscribers_exist_both_should_receive_same_event`() = runTest {
        val event = AppEvent.MilestoneScored(milestoneId = 10L, score = 95)

        val received1 = mutableListOf<AppEvent>()
        val received2 = mutableListOf<AppEvent>()

        val job1 = launch { bus.events.collect { received1.add(it) } }
        val job2 = launch { bus.events.collect { received2.add(it) } }

        bus.emit(event)

        // 给协程时间处理
        testScheduler.advanceUntilIdle()

        assertEquals(1, received1.size)
        assertEquals(1, received2.size)
        assertEquals(event, received1[0])
        assertEquals(event, received2[0])

        job1.cancel()
        job2.cancel()
    }

    @Test
    fun `when_MushroomEarned_event_emitted_should_preserve_transaction_list`() = runTest {
        val transactions = listOf(
            MushroomTransaction(
                id = 0,
                level = MushroomLevel.SMALL,
                action = MushroomAction.EARN,
                amount = 3,
                sourceType = MushroomSource.TASK,
                sourceId = 1L,
                note = null,
                createdAt = LocalDateTime.now()
            )
        )
        val event = AppEvent.MushroomEarned(transactions)

        bus.events.test {
            bus.emit(event)
            val received = awaitItem() as AppEvent.MushroomEarned
            assertEquals(1, received.transactions.size)
            assertEquals(MushroomLevel.SMALL, received.transactions[0].level)
            assertEquals(3, received.transactions[0].amount)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `when_no_event_emitted_subscriber_should_not_receive_anything`() = runTest {
        bus.events.test {
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
