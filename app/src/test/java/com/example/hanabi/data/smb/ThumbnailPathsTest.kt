package com.example.hanabi.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThumbnailPathsTest {

    @Test
    fun cacheKey_video_includesPathModifiedAndVersion() {
        assertEquals("smb://h/s/a.mp4#123#thumb_v3", thumbnailCacheKey("smb://h/s/a.mp4", 123L, true))
    }

    @Test
    fun cacheKey_image_hasNoVersionSuffix() {
        assertEquals("smb://h/s/d/thumbnail.jpg#456", thumbnailCacheKey("smb://h/s/d/thumbnail.jpg", 456L, false))
    }

    @Test
    fun cacheKey_changesWhenLastModifiedChanges() {
        assertNotEquals(
            thumbnailCacheKey("smb://h/s/a.mp4", 1L, true),
            thumbnailCacheKey("smb://h/s/a.mp4", 2L, true)
        )
    }

    @Test
    fun synologyCandidates_orderAndPath() {
        assertEquals(
            listOf(
                "smb://h/s/dir/@eaDir/a b.mp4/SYNOVIDEO_VIDEO_SCREENSHOT.jpg",
                "smb://h/s/dir/@eaDir/a b.mp4/SYNOPHOTO_THUMB_XL.jpg",
                "smb://h/s/dir/@eaDir/a b.mp4/SYNOPHOTO_THUMB_M.jpg"
            ),
            synologyThumbnailCandidates("smb://h/s/dir/a b.mp4")
        )
    }

    @Test
    fun synologyCandidates_invalidInputIsEmpty() {
        assertTrue(synologyThumbnailCandidates("noslash").isEmpty())
        assertTrue(synologyThumbnailCandidates("smb://h/s/dir/").isEmpty())
    }
}
