package com.mushroom.core.logging

import io.mockk.mockk
import io.mockk.verify
import io.mockk.justRun
import io.mockk.slot
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ReleaseLogWriterTest {

    private lateinit var mockFileWriter: LogFileWriter
    private lateinit var writer: ReleaseLogWriter

    @BeforeEach
    fun setUp() {
        mockFileWriter = mockk(relaxed = true)
        writer = ReleaseLogWriter(mockFileWriter)
    }

    @Test
    fun `when_log_level_VERBOSE_should_not_write_to_file`() {
        writer.log(LogLevel.V, "TAG", "verbose message")
        verify(exactly = 0) { mockFileWriter.write(any(), any(), any(), any()) }
    }

    @Test
    fun `when_log_level_DEBUG_should_not_write_to_file`() {
        writer.log(LogLevel.D, "TAG", "debug message")
        verify(exactly = 0) { mockFileWriter.write(any(), any(), any(), any()) }
    }

    @Test
    fun `when_log_level_INFO_should_not_write_to_file`() {
        writer.log(LogLevel.I, "TAG", "info message")
        verify(exactly = 0) { mockFileWriter.write(any(), any(), any(), any()) }
    }

    @Test
    fun `when_log_level_WARN_should_write_to_file_exactly_once`() {
        writer.log(LogLevel.W, "TAG", "warn message")
        verify(exactly = 1) { mockFileWriter.write(LogLevel.W, "TAG", "warn message", null) }
    }

    @Test
    fun `when_log_level_ERROR_should_write_to_file_exactly_once`() {
        writer.log(LogLevel.E, "TAG", "error message")
        verify(exactly = 1) { mockFileWriter.write(LogLevel.E, "TAG", "error message", null) }
    }

    @Test
    fun `when_log_level_ERROR_with_throwable_should_pass_throwable_to_file_writer`() {
        val ex = RuntimeException("test exception")
        writer.log(LogLevel.E, "TAG", "error", ex)
        verify(exactly = 1) { mockFileWriter.write(LogLevel.E, "TAG", "error", ex) }
    }

    @Test
    fun `when_log_level_WARN_with_throwable_should_pass_throwable_to_file_writer`() {
        val ex = IllegalStateException("warn cause")
        writer.log(LogLevel.W, "TAG", "warn", ex)
        verify(exactly = 1) { mockFileWriter.write(LogLevel.W, "TAG", "warn", ex) }
    }
}
