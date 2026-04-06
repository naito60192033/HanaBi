package com.example.hanabi.data.smb

import fi.iki.elonen.NanoHTTPD
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream

/**
 * SMBファイルをローカルHTTP経由で公開するプロキシサーバー。
 * MediaMetadataRetriever がHTTP URLを受け付けるため、
 * SmbMediaDataSource が対応していないフォーマット（FLV等）の
 * サムネイル取得に使用する。
 * Rangeリクエストに対応しシーク可能。
 */
class SmbHttpProxyServer(private val smbFile: SmbFile) : NanoHTTPD("127.0.0.1", 0) {

    val url: String get() = "http://127.0.0.1:$listeningPort/file"

    override fun serve(session: IHTTPSession): Response {
        val fileSize = smbFile.length()
        // 先頭3MBに制限（FLVメタデータ・最初のキーフレームは先頭付近にある）
        val serveSize = minOf(fileSize, 3L * 1024 * 1024)
        val rangeHeader = session.headers["range"]

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val parts = rangeHeader.removePrefix("bytes=").split("-")
            val start = parts[0].toLongOrNull() ?: 0L
            val end = if (parts.size > 1 && parts[1].isNotEmpty()) {
                minOf(parts[1].toLongOrNull() ?: (serveSize - 1), serveSize - 1)
            } else {
                serveSize - 1
            }
            val length = end - start + 1

            val raf = SmbRandomAccessFile(smbFile, "r")
            raf.seek(start)
            val response = newFixedLengthResponse(
                Response.Status.PARTIAL_CONTENT, "video/x-flv", RangeInputStream(raf, length), length
            )
            response.addHeader("Content-Range", "bytes $start-$end/$serveSize")
            response.addHeader("Accept-Ranges", "bytes")
            return response
        }

        val raf = SmbRandomAccessFile(smbFile, "r")
        val response = newFixedLengthResponse(
            Response.Status.OK, "video/x-flv", RangeInputStream(raf, serveSize), serveSize
        )
        response.addHeader("Accept-Ranges", "bytes")
        return response
    }

    private class RangeInputStream(
        private val raf: SmbRandomAccessFile,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining <= 0) return -1
            remaining--
            return raf.read()
        }
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (remaining <= 0) return -1
            val toRead = minOf(len.toLong(), remaining).toInt()
            val n = raf.read(b, off, toRead)
            if (n > 0) remaining -= n
            return n
        }
        override fun close() = raf.close()
    }
}
