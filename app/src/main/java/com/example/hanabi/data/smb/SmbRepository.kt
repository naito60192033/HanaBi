package com.example.hanabi.data.smb

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Properties
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
                    SmbEntry(
                        name = file.name.trimEnd('/'),
                        path = file.canonicalPath,
                        isDirectory = file.isDirectory,
                        size = if (file.isDirectory) 0L else file.length(),
                        lastModified = file.lastModified()
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

    /** jcifs-ng の接続コンテキストを生成 */
    private fun buildContext(): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB2")
            setProperty("jcifs.smb.client.maxVersion", "SMB3")
            // タイムアウト設定（ミリ秒）
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "15000")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))

        return if (config.username.isNotBlank() && config.password.isNotBlank()) {
            baseContext.withCredentials(
                NtlmPasswordAuthenticator(config.username, config.password)
            )
        } else {
            // ゲストアクセス
            baseContext.withGuestCrendentials()
        }
    }
}
