package com.example.hanabi.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseRangeTest {
    private val size = 2_000_000_000L  // 2GB 級の FLV を想定

    @Test fun ヘッダーなしはファイル全体() {
        assertEquals(0L to size - 1, parseRange(null, size))
    }

    @Test fun 先頭3MBを超える位置からの開始指定() {
        // 15% 地点へのシーク（従来は 3MB に切り詰めて長さが負になっていた）
        assertEquals(300_000_000L to size - 1, parseRange("bytes=300000000-", size))
    }

    @Test fun 終端指定はファイルサイズで切り詰める() {
        assertEquals(10L to 99L, parseRange("bytes=10-99", size))
        assertEquals(10L to 99L, parseRange("bytes=10-1000", 100L))
    }

    @Test fun 末尾からの指定() {
        assertEquals(900L to 999L, parseRange("bytes=-100", 1000L))
        assertEquals(0L to 999L, parseRange("bytes=-5000", 1000L))
    }

    @Test fun 範囲外や不正な指定はnull() {
        assertNull(parseRange("bytes=1000-", 1000L))
        assertNull(parseRange("bytes=50-10", 1000L))
        assertNull(parseRange("bytes=abc-", 1000L))
        assertNull(parseRange("items=0-1", 1000L))
        assertNull(parseRange(null, 0L))
    }
}
