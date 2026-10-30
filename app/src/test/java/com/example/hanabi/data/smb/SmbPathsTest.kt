package com.example.hanabi.data.smb

import org.junit.Assert.assertEquals
import org.junit.Test

class SmbPathsTest {

    @Test
    fun 通常のパス() {
        assertEquals("Series/S01/ep01.mp4", relativePathOf("smb://nas/video/Series/S01/ep01.mp4"))
    }

    @Test
    fun hostにshare名を含む場合() {
        assertEquals("Series/ep01.mp4", relativePathOf("smb://video-nas/video/Series/ep01.mp4"))
    }

    @Test
    fun share直下のファイル() {
        assertEquals("a.mp4", relativePathOf("smb://nas/video/a.mp4"))
        assertEquals("", parentDirOf("a.mp4"))
        assertEquals("a.mp4", fileNameOf("a.mp4"))
    }

    @Test
    fun 末尾スラッシュ付きフォルダ() {
        assertEquals("Series/S01", relativePathOf("smb://nas/video/Series/S01/"))
    }

    @Test
    fun share自体やshare以下が無い場合() {
        assertEquals("", relativePathOf("smb://nas/video/"))
        assertEquals("", relativePathOf("smb://nas/video"))
        assertEquals("", relativePathOf("smb://nas"))
    }

    @Test
    fun 記号空白日本語を含む名前() {
        val rel = relativePathOf("smb://nas/video/アニメ/第1話 #1 (前編).mkv")
        assertEquals("アニメ/第1話 #1 (前編).mkv", rel)
        assertEquals("アニメ", parentDirOf(rel))
        assertEquals("第1話 #1 (前編).mkv", fileNameOf(rel))
    }

    @Test
    fun smb以外() {
        assertEquals("", relativePathOf("http://nas/video/a.mp4"))
        assertEquals("", relativePathOf("/video/a.mp4"))
        assertEquals("", relativePathOf(""))
    }

    @Test
    fun ディレクトリとファイル名の分解() {
        assertEquals("Series/S01", parentDirOf("Series/S01/ep01.mp4"))
        assertEquals("ep01.mp4", fileNameOf("Series/S01/ep01.mp4"))
    }
}
