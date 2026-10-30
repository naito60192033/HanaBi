package com.example.hanabi.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalStringComparatorTest {
    private fun lt(a: String, b: String) = assertTrue("$a < $b", naturalStringComparator.compare(a, b) < 0)

    @Test fun 数字は数値として比較する() {
        lt("ep2", "ep10")
        lt("ep9.mp4", "ep10.mp4")
    }

    @Test fun 大文字小文字を無視する() {
        assertEquals(0, naturalStringComparator.compare("ABC", "abc"))
        lt("a", "B")
    }

    @Test fun カタカナとひらがなを混在順にする() {
        assertEquals(0, naturalStringComparator.compare("あいう", "アイウ"))
        lt("あ", "キ")
        lt("ア", "い")
    }

    @Test fun 接頭辞は短い方が先() {
        lt("ep", "ep1")
        lt("abc", "abcd")
    }

    @Test fun 桁の大きい数も比較できる() {
        lt("file99999999", "file100000000")
        lt("x1234567890", "x12345678901")
    }
}
