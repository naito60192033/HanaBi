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
class SmbDataSource(private val config: SmbConfig) : DataSource {

    private var randomAccessFile: SmbRandomAccessFile? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = 0L

    override fun addTransferListener(transferListener: TransferListener) {}

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val cifsContext = config.buildCifsContext()
        val smbFile = SmbFile(dataSpec.uri.toString(), cifsContext)
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

    class Factory(private val config: SmbConfig) : DataSource.Factory {
        override fun createDataSource(): DataSource = SmbDataSource(config)
    }
}
