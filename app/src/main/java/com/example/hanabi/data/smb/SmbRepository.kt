package com.example.hanabi.data.smb

import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
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
    /**
     * 指定パスのエントリ一覧を取得する
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
                        thumbnailPath = if (!file.isDirectory) filePath else null
                    )
                }
                ?.filter { entry ->
                    // 隠しファイル・システムフォルダは除外
                    !entry.name.startsWith(".") && entry.name != "@eaDir"
                }
                ?: return@runCatching emptyList()

            // フォルダ直下の thumbnail.{jpg,png,...} を並列に検索し、見つかればサムネに設定
            val withFolderThumbs = coroutineScope {
                entries.map { entry ->
                    async {
                        if (entry.isDirectory) {
                            val thumbPath = findFolderThumbnail(entry.path, cifsContext)
                            if (thumbPath != null) entry.copy(thumbnailPath = thumbPath) else entry
                        } else entry
                    }
                }.awaitAll()
            }

            withFolderThumbs.sortedWith(
                // フォルダ優先、同種は自然順ソート（数字を数値として比較）
                compareByDescending<SmbEntry> { it.isDirectory }
                    .then(Comparator { a, b -> naturalStringComparator.compare(a.name, b.name) })
            )
        }
    }

    /** フォルダ直下に thumbnail.{jpg,jpeg,png,webp} があればその SMB URL を返す */
    private fun findFolderThumbnail(folderPath: String, cifsContext: jcifs.CIFSContext): String? {
        return try {
            val folder = SmbFile(if (folderPath.endsWith("/")) folderPath else "$folderPath/", cifsContext)
            folder.listFiles()
                ?.firstOrNull { it.name.trimEnd('/').lowercase() in FOLDER_THUMBNAIL_NAMES }
                ?.canonicalPath
        } catch (_: Exception) {
            null
        }
    }

    /**
     * currentSmbUrl と同じディレクトリの次のビデオファイルを返す（なければ null）
     * @param currentSmbUrl 現在再生中のファイルの SMB URL（canonicalPath）
     */
    suspend fun findNextEpisode(currentSmbUrl: String): SmbEntry? = withContext(Dispatchers.IO) {
        runCatching {
            // SMB URL 例: smb://host/share/Series/S01/ep01.mp4
            val share = config.share
            // share 以降のパスを抽出: "Series/S01/ep01.mp4"
            val afterShare = currentSmbUrl.substringAfter("/$share/", "")
            val dirPath = afterShare.substringBeforeLast("/", "")
            val currentFileName = afterShare.substringAfterLast("/")

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
