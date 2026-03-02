package com.mushroom.feature.reward.puzzle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PuzzleCutterTest {

    @Test
    fun `calculateGrid for 25 pieces should return 5x5`() {
        val (cols, rows) = PuzzleCutter.calculateGrid(25)
        assertEquals(5, cols)
        assertEquals(5, rows)
    }

    @Test
    fun `calculateGrid for 20 pieces should return near-square grid`() {
        val (cols, rows) = PuzzleCutter.calculateGrid(20)
        assertEquals(cols * rows, 20)
        // should be 4x5 or 5x4
        assertTrue(cols <= rows)
    }

    @Test
    fun `calculateGrid for 12 pieces should return 3x4`() {
        val (cols, rows) = PuzzleCutter.calculateGrid(12)
        assertEquals(3, cols)
        assertEquals(4, rows)
    }

    @Test
    fun `calculateGrid for 1 piece should return 1x1`() {
        val (cols, rows) = PuzzleCutter.calculateGrid(1)
        assertEquals(1, cols)
        assertEquals(1, rows)
    }

    @Test
    fun `cut returns correct number of piece URIs`() {
        val pieces = PuzzleCutter.cut("content://test/image.jpg", 20)
        assertEquals(20, pieces.size)
    }

    @Test
    fun `calculateGrid cols should always be less than or equal to rows`() {
        listOf(6, 8, 10, 15, 16, 24, 30).forEach { n ->
            val (cols, rows) = PuzzleCutter.calculateGrid(n)
            assertTrue(cols <= rows, "Expected cols <= rows for n=$n but got $cols x $rows")
        }
    }
}
