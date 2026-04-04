package com.example.hanabi.data.smb

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * ファイル名に `#` 等の URI 特殊文字が含まれる場合でも正しく扱えるよう、
 * Uri.parse() を通さず生パスを直接保持するラッパー
 */
data class SmbThumbnailKey(val path: String)

/** Coil用カスタムFetcher: smb://パスの動画からフレーム抽出、または画像を直接読み込む */
class SmbImageFetcher(
    private val rawPath: String,
    private val config: SmbConfig,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val cifsContext = config.buildCifsContext()
        // rawPath を URI 解析せず直接 SmbFile に渡す（# 等の特殊文字が壊れない）
        val smbFile = SmbFile(rawPath, cifsContext)

        val bytes = if (isVideoPath(rawPath)) {
            extractVideoFrame(smbFile)
        } else {
            smbFile.openInputStream().use { it.readBytes() }
        }

        SourceResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                context = options.context
            ),
            mimeType = "image/jpeg",
            dataSource = DataSource.NETWORK
        )
    }

    private fun extractVideoFrame(smbFile: SmbFile): ByteArray {
        val mediaSource = SmbMediaDataSource(smbFile)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mediaSource)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val positionUs = durationMs * 1000L * 15 / 100
            val bitmap = retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: error("フレーム抽出失敗: ${smbFile.name}")
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } finally {
            retriever.release()
            mediaSource.close()
        }
    }

    class Factory @Inject constructor(private val config: SmbConfig) : Fetcher.Factory<SmbThumbnailKey> {
        override fun create(data: SmbThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbImageFetcher(data.path, config, options)
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".mkv", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".wmv", ".flv"
        )

        fun isVideoPath(path: String?): Boolean =
            path != null && VIDEO_EXTENSIONS.any { path.lowercase().endsWith(it) }
    }
}
