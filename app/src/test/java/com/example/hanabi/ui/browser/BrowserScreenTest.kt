package com.example.hanabi.ui.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserScreenTest {
    @Test fun 空パスはアプリ名() {
        assertEquals("HanaBi", buildHeaderTitle(""))
        assertEquals("HanaBi", buildHeaderTitle("  "))
    }

    @Test fun シーズンフォルダは親フォルダ名付き() {
        assertEquals("Series-S01", buildHeaderTitle("Series/S01"))
        assertEquals("B-S02", buildHeaderTitle("A/B/S02"))
    }

    @Test fun 通常は末尾セグメント() {
        assertEquals("Series", buildHeaderTitle("Series"))
        assertEquals("Other", buildHeaderTitle("A/Other/"))
    }

    @Test fun エピソード名を抽出する() {
        assertEquals("エピソード名", displayName("タイトル_S01E02_エピソード名.mp4"))
    }

    @Test fun 該当しない名前はそのまま() {
        assertEquals("movie.mp4", displayName("movie.mp4"))
        assertEquals("タイトル_S01E02", displayName("タイトル_S01E02"))
    }
}
