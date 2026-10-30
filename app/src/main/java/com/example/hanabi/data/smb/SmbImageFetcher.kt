package com.example.hanabi.data.smb

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.disk.DiskCache
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * ファイル名に `#` 等の URI 特殊文字が含まれる場合でも正しく扱えるよう、
 * Uri.parse() を通さず生パスを直接保持するラッパー
 */
data class SmbThumbnailKey(val path: String, val lastModified: Long = 0L)

/** Coil用カスタムFetcher: smb://パスの動画からフレーム抽出、または画像を直接読み込む */
class SmbImageFetcher(
    private val rawPath: String,
    private val lastModified: Long,
    private val config: SmbConfig,
    private val options: Options,
    private val diskCache: DiskCache?
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        // path + 更新日時（動画は抽出ロジックの version 接尾辞つき）。Coil の Keyer と同じ規則
        val cacheKey = thumbnailCacheKey(rawPath, lastModified, isVideoPath(rawPath))

        // ディスクキャッシュにヒットすれば即返す
        diskCache?.openSnapshot(cacheKey)?.use { snapshot ->
            return@withContext SourceResult(
                source = ImageSource(
                    file = snapshot.data,
                    fileSystem = diskCache.fileSystem,
                    diskCacheKey = cacheKey
                ),
                mimeType = "image/jpeg",
                dataSource = DataSource.DISK
            )
        }

        // キャッシュミス: NAS から取得（同時実行数を制限）
        val bytes = generationSemaphore.withPermit {
            val cifsContext = config.buildCifsContext()
            val smbFile = SmbFile(rawPath, cifsContext)

            if (isVideoPath(rawPath)) {
                // Synology が生成済みのサムネがあればフレーム抽出より優先
                findSynologyThumbnail(cifsContext)
                    ?: if (rawPath.lowercase().endsWith(".flv")) {
                        extractFlvFrame(smbFile)
                    } else {
                        extractVideoFrame(smbFile)
                    }
            } else {
                smbFile.openInputStream().use { it.readBytes() }
            }
        }

        // ディスクキャッシュに書き込む
        diskCache?.openEditor(cacheKey)?.let { editor ->
            try {
                diskCache.fileSystem.write(editor.data) { write(bytes) }
                editor.commit()
            } catch (e: Exception) {
                editor.abort()
            }
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

    /** `@eaDir` 内の Synology 生成サムネを優先順に探し、最初に読めたものを返す（なければ null） */
    private fun findSynologyThumbnail(cifsContext: jcifs.CIFSContext): ByteArray? {
        for (candidate in synologyThumbnailCandidates(rawPath)) {
            try {
                val file = SmbFile(candidate, cifsContext)
                if (file.exists()) return file.openInputStream().use { it.readBytes() }
            } catch (_: Exception) {
                // 権限・不在などは次の候補へ
            }
        }
        return null
    }

    /** 長辺が THUMB_MAX_WIDTH に収まるよう縦横比を保って縮小（小さければそのまま） */
    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val longSide = maxOf(bitmap.width, bitmap.height)
        if (longSide <= THUMB_MAX_WIDTH) return bitmap
        val ratio = THUMB_MAX_WIDTH.toFloat() / longSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * ratio).toInt().coerceAtLeast(1),
            (bitmap.height * ratio).toInt().coerceAtLeast(1),
            true
        )
    }

    private fun extractVideoFrame(smbFile: SmbFile): ByteArray {
        val mediaSource = SmbMediaDataSource(smbFile)
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(mediaSource)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: 0L
            val positionUs = durationMs * 1000L * 15 / 100
            val bitmap = if (Build.VERSION.SDK_INT >= 27) {
                retriever.getScaledFrameAtTime(
                    positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, THUMB_MAX_WIDTH, THUMB_MAX_HEIGHT
                )
            } else {
                retriever.getFrameAtTime(positionUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?.let { scaleDown(it) }
            }
            bitmap ?: error("フレーム抽出失敗: ${smbFile.name}")
            return ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("SmbImageFetcher", "サムネイル取得失敗: ${smbFile.name}", e)
            throw e
        } finally {
            retriever.release()
            mediaSource.close()
        }
    }

    private suspend fun extractFlvFrame(smbFile: SmbFile): ByteArray {
        val proxy = SmbHttpProxyServer(smbFile)
        proxy.start()
        try {
            val bitmap = FlvFrameExtractor.extract(options.context, proxy.url)
                ?: error("FLVフレーム抽出失敗: ${smbFile.name}")
            val scaled = scaleDown(bitmap)
            return ByteArrayOutputStream().use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }
        } catch (e: Exception) {
            Log.e("SmbImageFetcher", "FLVサムネイル取得失敗: ${smbFile.name}", e)
            throw e
        } finally {
            proxy.stop()
        }
    }

    class Factory @Inject constructor(private val config: SmbConfig) : Fetcher.Factory<SmbThumbnailKey> {
        override fun create(data: SmbThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher =
            SmbImageFetcher(data.path, data.lastModified, config, options, imageLoader.diskCache)
    }

    companion object {
        /** NAS 読み込み・フレーム抽出の同時実行数上限 */
        private val generationSemaphore = Semaphore(3)

        private const val THUMB_MAX_WIDTH = 480
        private const val THUMB_MAX_HEIGHT = 270

        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".mkv", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".wmv", ".flv"
        )

        fun isVideoPath(path: String?): Boolean =
            path != null && VIDEO_EXTENSIONS.any { path.lowercase().endsWith(it) }
    }
}
