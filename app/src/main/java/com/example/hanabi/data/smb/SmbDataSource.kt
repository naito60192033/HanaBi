@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.data.smb

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile

/** ExoPlayer が SMB ファイルを読み取るためのカスタム DataSource */
class SmbDataSource(
    private val config: SmbConfig,
    private val pathOverride: () -> String?
) : DataSource {

    private var randomAccessFile: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val cifsContext = config.buildCifsContext()
        // ファイル名に `#` 等が含まれると URI 経由では fragment 扱いで切り落とされるため、
        // PlayerViewModel が直接渡した生パスを優先して使用する。なければ URI からのデコードにフォールバック
        val rawPath = pathOverride() ?: Uri.decode(dataSpec.uri.toString())
        val smbFile = SmbFile(rawPath, cifsContext)
        val fileLength = smbFile.length()
        randomAccessFile = SmbRandomAccessFile(smbFile, "r")
        randomAccessFile!!.seek(dataSpec.position)
        bytesRemaining = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
            fileLength - dataSpec.position
        } else {
            dataSpec.length
        }
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val toRead = minOf(length.toLong(), bytesRemaining).toInt()
        val bytesRead = randomAccessFile!!.read(buffer, offset, toRead)
        if (bytesRead == -1) return C.RESULT_END_OF_INPUT
        bytesRemaining -= bytesRead
        return bytesRead
    }

    override fun getUri(): Uri? = uri

    override fun close() {
        try { randomAccessFile?.close() } catch (_: Exception) {}
        randomAccessFile = null
    }

    /**
     * Factory は currentSmbPath を保持し、PlayerViewModel.prepare() 時に更新される。
     * これにより ExoPlayer 側の URI 加工に依存せず、確実に生パスを jcifs に渡せる。
     */
    class Factory(private val config: SmbConfig) : DataSource.Factory {
        @Volatile
        var currentSmbPath: String? = null

        override fun createDataSource(): DataSource = SmbDataSource(config) { currentSmbPath }
    }
}
