package com.mushroom.core.logging

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import android.content.Context

class LogFileWriterPurgeTest {

    @TempDir
    lateinit var tempDir: File

    private fun makeWriter(): LogFileWriter {
        val mockContext = mockk<Context>(relaxed = true)
        // 让 logDir 直接指向 tempDir（覆盖 open val）
        return object : LogFileWriter(mockContext) {
            override val logDir: File get() = tempDir
        }
    }

    private fun createFakeLogFile(date: LocalDate, sizeBytes: Int = 100): File {
        val fmt = DateTimeFormatter.ofPattern("yyyyMMdd")
        return File(tempDir, "mushroom_log_${date.format(fmt)}.txt")
            .also { it.writeText("A".repeat(sizeBytes)) }
    }

    @Test
    fun `when_file_older_than_retention_threshold_should_be_deleted`() {
        val today = LocalDate.now()
        val oldFile = createFakeLogFile(today.minusDays(3))
        val recentFile = createFakeLogFile(today.minusDays(1))

        makeWriter().purgeAndRotate()

        assertFalse(oldFile.exists(), "3天前的日志文件应被删除")
        assertTrue(recentFile.exists(), "1天前的日志文件应保留")
    }

    @Test
    fun `when_file_exactly_2_days_old_should_be_retained`() {
        val twoDaysAgo = createFakeLogFile(LocalDate.now().minusDays(2))

        makeWriter().purgeAndRotate()

        assertTrue(twoDaysAgo.exists(), "恰好2天前的日志文件（不早于阈值）应保留")
    }

    @Test
    fun `when_total_size_exceeds_512KB_should_delete_oldest_until_within_limit`() {
        val today = LocalDate.now()
        createFakeLogFile(today.minusDays(1), 200 * 1024)
        createFakeLogFile(today, 400 * 1024)  // 总 600KB > 512KB

        makeWriter().purgeAndRotate()

        val totalSize = tempDir.listFiles()
            ?.filter { it.name.startsWith("mushroom_log_") }
            ?.sumOf { it.length() } ?: 0L
        assertTrue(
            totalSize <= LogFileWriter.MAX_TOTAL_SIZE_KB * 1024,
            "清理后总大小应 <= 512KB，实际 $totalSize bytes"
        )
    }

    @Test
    fun `when_files_within_limit_should_not_delete_any`() {
        val today = LocalDate.now()
        val file1 = createFakeLogFile(today.minusDays(1), 100 * 1024)
        val file2 = createFakeLogFile(today, 100 * 1024)

        makeWriter().purgeAndRotate()

        assertTrue(file1.exists(), "未超限，文件1应保留")
        assertTrue(file2.exists(), "未超限，文件2应保留")
    }

    @Test
    fun `when_log_dir_is_empty_purge_should_not_throw`() {
        assertDoesNotThrow { makeWriter().purgeAndRotate() }
    }

    @Test
    fun `when_only_today_file_under_limit_should_keep_it`() {
        val todayFile = createFakeLogFile(LocalDate.now(), 50 * 1024)

        makeWriter().purgeAndRotate()

        assertTrue(todayFile.exists(), "当天文件应保留")
    }
}
