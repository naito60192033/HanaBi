package com.example.hanabi.data.smb

import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** フォルダのサムネとして使う画像ファイル名（拡張子を含む小文字） */
private val FOLDER_THUMBNAIL_NAMES = setOf(
    "thumbnail.jpg", "thumbnail.jpeg", "thumbnail.png", "thumbnail.webp"
)

/** カタカナをひらがなに正規化（U+30A1〜U+30F6 → U+3041〜U+3096）してかな混在ソートを実現 */
private fun Char.normalizeKana(): Char =
    if (this in 'ァ'..'ヶ') (this.code - 0x60).toChar() else this

/** 数字部分を数値として比較する自然順ソート用コンパレータ（ファイル名比較に使用） */
internal val naturalStringComparator: Comparator<String> = Comparator { a, b ->
    val aLower = a.lowercase()
    val bLower = b.lowercase()
    var i = 0
    var j = 0
    var result = 0
    while (result == 0 && i < aLower.length && j < bLower.length) {
        if (aLower[i].isDigit() && bLower[j].isDigit()) {
            var numA = 0L
            var numB = 0L
            while (i < aLower.length && aLower[i].isDigit()) numA = numA * 10 + (aLower[i++] - '0')
            while (j < bLower.length && bLower[j].isDigit()) numB = numB * 10 + (bLower[j++] - '0')
            result = numA.compareTo(numB)
        } else {
            result = aLower[i].normalizeKana().compareTo(bLower[j].normalizeKana())
            i++; j++
        }
    }
    if (result != 0) result else aLower.length - bLower.length
}

/** SMB経由でNASにアクセスするリポジトリ */
@Singleton
class SmbRepository @Inject constructor(
    private val config: SmbConfig
) {
    /** フォルダサムネ探索結果のキャッシュ値（ConcurrentHashMap は null 値不可のため包む。key=null は「なし」） */
    private class FolderThumb(val key: SmbThumbnailKey?)

    private val folderThumbCache = ConcurrentHashMap<String, FolderThumb>()
    private val folderThumbSemaphore = Semaphore(4)

    /**
     * 指定パスのエントリ一覧を取得する（フォルダサムネは含まない。[findFolderThumbnails] で別途取得）
     * @param path 空文字の場合は共有フォルダのルートを返す
     */
    suspend fun listEntries(path: String = ""): Result<List<SmbEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val cifsContext = buildContext()
            val url = config.buildUrl(path)
            val smbFile = SmbFile(url, cifsContext)

            val entries = smbFile.listFiles()
                ?.map { file ->
                    val fileName = file.name.trimEnd('/')
                    val filePath = file.canonicalPath
                    SmbEntry(
                        name = fileName,
                        path = filePath,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified(),
                        thumbnail = if (!file.isDirectory) SmbThumbnailKey(filePath, file.lastModified()) else null
                    )
                }
                ?.filter { entry ->
                    // 隠しファイル・システムフォルダは除外
                    !entry.name.startsWith(".") && entry.name != "@eaDir"
                }
                ?: return@runCatching emptyList()

            entries.sortedWith(
                // フォルダ優先、同種は自然順ソート（数字を数値として比較）
                compareByDescending<SmbEntry> { it.isDirectory }
                    .then(Comparator { a, b -> naturalStringComparator.compare(a.name, b.name) })
            )
        }
    }

    /**
     * フォルダ直下の thumbnail.{jpg,jpeg,png,webp} を探し、見つかったフォルダだけ path -> サムネ指定で返す。
     * 同時実行は 4 本まで。結果（見つからなかった場合も）はメモリにキャッシュし、再問い合わせしない。
     */
    suspend fun findFolderThumbnails(folders: List<SmbEntry>): Map<String, SmbThumbnailKey> =
        withContext(Dispatchers.IO) {
            val cifsContext = buildContext()
            coroutineScope {
                folders.filter { it.isDirectory }.map { folder ->
                    async {
                        val cached = folderThumbCache[folder.path]
                        if (cached != null) return@async folder.path to cached.key
                        folderThumbSemaphore.withPermit {
                            val found = findFolderThumbnail(folder.path, cifsContext)
                            // 接続失敗（例外）はキャッシュせず次回再試行する
                            if (found.succeeded) folderThumbCache[folder.path] = FolderThumb(found.key)
                            folder.path to found.key
                        }
                    }
                }.awaitAll()
            }.mapNotNull { (path, key) -> key?.let { path to it } }.toMap()
        }

    private class FolderSearchResult(val succeeded: Boolean, val key: SmbThumbnailKey?)

    private fun findFolderThumbnail(folderPath: String, cifsContext: jcifs.CIFSContext): FolderSearchResult {
        return try {
            val folder = SmbFile(if (folderPath.endsWith("/")) folderPath else "$folderPath/", cifsContext)
            val image = folder.listFiles()
                ?.firstOrNull { it.name.trimEnd('/').lowercase() in FOLDER_THUMBNAIL_NAMES }
            FolderSearchResult(true, image?.let { SmbThumbnailKey(it.canonicalPath, it.lastModified()) })
        } catch (_: Exception) {
            FolderSearchResult(false, null)
        }
    }

    /**
     * currentSmbUrl と同じディレクトリの次のビデオファイルを返す（なければ null）
     * @param currentSmbUrl 現在再生中のファイルの SMB URL（canonicalPath）
     */
    suspend fun findNextEpisode(currentSmbUrl: String): SmbEntry? = withContext(Dispatchers.IO) {
        runCatching {
            // SMB URL 例: smb://host/share/Series/S01/ep01.mp4 → "Series/S01/ep01.mp4"
            val relative = relativePathOf(currentSmbUrl)
            val dirPath = parentDirOf(relative)
            val currentFileName = fileNameOf(relative)

            val entries = listEntries(dirPath).getOrElse { return@runCatching null }
            val videoFiles = entries
                .filter { it.isVideo }
                .sortedWith(Comparator { a, b -> naturalStringComparator.compare(a.name, b.name) })

            val currentIndex = videoFiles.indexOfFirst { it.name == currentFileName }
            if (currentIndex >= 0 && currentIndex + 1 < videoFiles.size) {
                videoFiles[currentIndex + 1]
            } else {
                null
            }
        }.getOrNull()
    }

    private fun buildContext() = config.buildCifsContext()
}
