package com.example.hanabi.data.smb

import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

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

            smbFile.listFiles()
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
                ?.sortedWith(
                    // フォルダ優先、同種はアルファベット順
                    compareByDescending<SmbEntry> { it.isDirectory }
                        .thenBy { it.name.lowercase() }
                )
                ?: emptyList()
        }
    }

    private fun buildContext() = config.buildCifsContext()
}
