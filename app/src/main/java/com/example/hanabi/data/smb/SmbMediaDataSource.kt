package com.example.hanabi.data.smb

import android.media.MediaDataSource
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile

/** MediaMetadataRetriever が SMB ファイルをランダムアクセスするためのデータソース */
class SmbMediaDataSource(smbFile: SmbFile) : MediaDataSource() {

    private val randomAccessFile = SmbRandomAccessFile(smbFile, "r")
    private val fileSize: Long = smbFile.length()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (position >= fileSize) return -1
        randomAccessFile.seek(position)
        return randomAccessFile.read(buffer, offset, size)
    }

    override fun getSize(): Long = fileSize

    override fun close() {
        try { randomAccessFile.close() } catch (_: Exception) {}
    }
}
