package com.example.hanabi.data.smb

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
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

/** Coil用カスタムFetcher: smb://URIの動画からフレーム抽出、または画像を直接読み込む */
class SmbImageFetcher(
    private val data: Uri,
    private val config: SmbConfig,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val cifsContext = config.buildCifsContext()
        val smbFile = SmbFile(data.toString(), cifsContext)

        val bytes = if (isVideoPath(data.path)) {
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
            val bitmap = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
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

    class Factory @Inject constructor(private val config: SmbConfig) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "smb") SmbImageFetcher(data, config, options) else null
    }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".mkv", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".wmv", ".flv"
        )

        fun isVideoPath(path: String?): Boolean =
            path != null && VIDEO_EXTENSIONS.any { path.lowercase().endsWith(it) }
    }
}
