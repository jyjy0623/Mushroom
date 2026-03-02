package com.mushroom.feature.reward.puzzle

object PuzzleCutter {

    /**
     * 计算最接近正方形的网格尺寸（cols × rows = totalPieces）
     * 例：20块 → (4, 5)；25块 → (5, 5)；12块 → (3, 4)
     */
    fun calculateGrid(totalPieces: Int): Pair<Int, Int> {
        if (totalPieces <= 0) return 1 to 1
        var cols = 1
        var best = 1 to totalPieces
        var bestDiff = totalPieces - 1
        for (c in 1..totalPieces) {
            if (totalPieces % c == 0) {
                val r = totalPieces / c
                val diff = Math.abs(c - r)
                if (diff < bestDiff || (diff == bestDiff && c <= r)) {
                    bestDiff = diff
                    best = c to r
                }
            }
        }
        // Return cols <= rows (portrait orientation preferred)
        return if (best.first <= best.second) best else best.second to best.first
    }

    /**
     * 将图片URI按网格切割，返回各块的URI列表（占位实现，实际裁剪由UI层处理）
     * 在Android中真正切割图片需要Bitmap操作，此处返回描述性URI供UI渲染
     */
    fun cut(imageUri: String, totalPieces: Int): List<String> {
        val (cols, rows) = calculateGrid(totalPieces)
        val result = mutableListOf<String>()
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                result.add("$imageUri?piece=${row * cols + col}&cols=$cols&rows=$rows")
            }
        }
        return result
    }
}
