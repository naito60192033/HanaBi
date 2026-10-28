package com.example.hanabi.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparisonTest {
    // 呼び出し元は tag_name.trimStart('v') した値を渡す
    private fun tag(t: String) = t.trimStart('v')

    @Test fun 正式版の新旧() {
        assertTrue(isNewerVersion("0.3.0", "0.2.3"))
        assertFalse(isNewerVersion("0.2.3", "0.3.0"))
    }

    @Test fun 正式版はプレリリースより新しい() {
        assertTrue(isNewerVersion("0.3.0", "0.3.0-beta.6"))
        assertFalse(isNewerVersion("0.3.0-beta.6", "0.3.0"))
    }

    @Test fun プレリリース同士は番号で比較() {
        assertTrue(isNewerVersion("0.3.0-beta.6", "0.3.0-beta.5"))
        assertFalse(isNewerVersion("0.3.0-beta.5", "0.3.0-beta.6"))
        assertTrue(isNewerVersion("0.3.0-beta.10", "0.3.0-beta.9"))
    }

    @Test fun 同じ版は新しくない() {
        assertFalse(isNewerVersion("0.3.0", "0.3.0"))
        assertFalse(isNewerVersion("0.3.0-beta.6", "0.3.0-beta.6"))
    }

    @Test fun v接頭辞付きタグは除去後に比較される() {
        assertTrue(isNewerVersion(tag("v0.3.0"), "0.3.0-beta.6"))
        assertFalse(isNewerVersion(tag("v0.3.0"), "0.3.0"))
    }

    @Test fun splitPreReleaseの分解() {
        assertEquals(listOf(0, 3, 0) to "beta.1", splitPreRelease("0.3.0-beta.1"))
        val (base, pre) = splitPreRelease("0.3.0")
        assertEquals(listOf(0, 3, 0), base)
        assertNull(pre)
    }
}
