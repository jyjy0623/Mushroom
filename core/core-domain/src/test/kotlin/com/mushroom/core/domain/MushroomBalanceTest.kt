package com.mushroom.core.domain.entity

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MushroomBalanceTest {

    @Test
    fun `when_all_levels_zero_totalPoints_should_be_zero`() {
        val balance = MushroomBalance.empty()
        assertEquals(0, balance.totalPoints())
    }

    @Test
    fun `when_single_small_mushroom_totalPoints_should_be_1`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.SMALL to 1))
        assertEquals(1, balance.totalPoints())
    }

    @Test
    fun `when_single_medium_mushroom_totalPoints_should_be_5`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.MEDIUM to 1))
        assertEquals(5, balance.totalPoints())
    }

    @Test
    fun `when_single_large_mushroom_totalPoints_should_be_25`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.LARGE to 1))
        assertEquals(25, balance.totalPoints())
    }

    @Test
    fun `when_single_gold_mushroom_totalPoints_should_be_100`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.GOLD to 1))
        assertEquals(100, balance.totalPoints())
    }

    @Test
    fun `when_single_legend_mushroom_totalPoints_should_be_500`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.LEGEND to 1))
        assertEquals(500, balance.totalPoints())
    }

    @Test
    fun `when_mixed_levels_totalPoints_should_sum_correctly`() {
        // 3 小蘑菇(3×1) + 2 中蘑菇(2×5) + 1 大蘑菇(1×25) = 38
        val balance = MushroomBalance(
            mapOf(
                MushroomLevel.SMALL to 3,
                MushroomLevel.MEDIUM to 2,
                MushroomLevel.LARGE to 1
            )
        )
        assertEquals(38, balance.totalPoints())
    }

    @Test
    fun `when_get_existing_level_should_return_count`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.GOLD to 5))
        assertEquals(5, balance.get(MushroomLevel.GOLD))
    }

    @Test
    fun `when_get_missing_level_should_return_zero`() {
        val balance = MushroomBalance(emptyMap())
        assertEquals(0, balance.get(MushroomLevel.LEGEND))
    }

    @Test
    fun `when_empty_factory_should_contain_all_levels_as_zero`() {
        val balance = MushroomBalance.empty()
        MushroomLevel.values().forEach { level ->
            assertEquals(0, balance.get(level), "Level $level should be 0")
        }
    }

    @Test
    fun `when_multiple_legend_mushrooms_totalPoints_should_multiply_correctly`() {
        val balance = MushroomBalance(mapOf(MushroomLevel.LEGEND to 3))
        assertEquals(1500, balance.totalPoints())
    }
}
