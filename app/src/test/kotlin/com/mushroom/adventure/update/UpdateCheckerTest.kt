package com.mushroom.adventure.update

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class UpdateCheckerTest {

    private val checker = UpdateChecker()

    // ------------------------------------------------------------------
    // isNewerVersion — pure logic, no network
    // ------------------------------------------------------------------

    @Test
    fun `newer major version is detected`() {
        assertTrue(checker.isNewerVersion("2.0.0", "1.9.9"))
    }

    @Test
    fun `newer minor version is detected`() {
        assertTrue(checker.isNewerVersion("1.3.0", "1.2.9"))
    }

    @Test
    fun `newer patch version is detected`() {
        assertTrue(checker.isNewerVersion("1.0.1", "1.0.0"))
    }

    @Test
    fun `same version is not considered newer`() {
        assertFalse(checker.isNewerVersion("1.0.0", "1.0.0"))
    }

    @Test
    fun `older major version is not considered newer`() {
        assertFalse(checker.isNewerVersion("0.9.9", "1.0.0"))
    }

    @Test
    fun `older minor version is not considered newer`() {
        assertFalse(checker.isNewerVersion("1.1.9", "1.2.0"))
    }

    @Test
    fun `older patch version is not considered newer`() {
        assertFalse(checker.isNewerVersion("1.0.0", "1.0.1"))
    }

    @Test
    fun `malformed remote version returns false`() {
        assertFalse(checker.isNewerVersion("1.0", "1.0.0"))
    }

    @Test
    fun `malformed current version returns false`() {
        assertFalse(checker.isNewerVersion("2.0.0", "bad"))
    }

    @Test
    fun `empty strings return false`() {
        assertFalse(checker.isNewerVersion("", ""))
    }

    @Test
    fun `multi-digit version segments are compared numerically not lexicographically`() {
        // "1.10.0" > "1.9.0" numerically — would fail with lexicographic comparison
        assertTrue(checker.isNewerVersion("1.10.0", "1.9.0"))
    }

    @Test
    fun `remote version with v prefix stripped before comparison`() {
        // This mimics what checkForUpdate does: strips "v" before calling isNewerVersion
        val remoteTag = "v1.5.0"
        val remote = remoteTag.removePrefix("v")
        assertTrue(checker.isNewerVersion(remote, "1.0.0"))
    }
}
