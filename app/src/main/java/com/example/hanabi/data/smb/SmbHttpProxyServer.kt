package com.example.hanabi.data.smb

import fi.iki.elonen.NanoHTTPD
import jcifs.smb.SmbFile
import jcifs.smb.SmbRandomAccessFile
import java.io.InputStream

/**
 * SMBファイルをローカルHTTP経由で公開するプロキシサーバー。
 * FLV のサムネイル取得（[FlvFrameExtractor] の ExoPlayer）に使用する。
 * Rangeリクエストに対応しシーク可能。
 */
class SmbHttpProxyServer(private val smbFile: SmbFile) : NanoHTTPD("127.0.0.1", 0) {

    val url: String get() = "http://127.0.0.1:$listeningPort/file"

    override fun serve(session: IHTTPSession): Response {
        // ファイル全体を公開する（シーク先が先頭付近とは限らないため。実際に読むのは要求された範囲だけ）
        val fileSize = smbFile.length()
        val range = parseRange(session.headers["range"], fileSize)
            ?: return newFixedLengthResponse(
                Response.Status.RANGE_NOT_SATISFIABLE, "text/plain", ""
            ).apply { addHeader("Content-Range", "bytes */$fileSize") }

        val (start, end) = range
        val length = end - start + 1
        val raf = SmbRandomAccessFile(smbFile, "r")
        raf.seek(start)
        val partial = session.headers["range"] != null
        val response = newFixedLengthResponse(
            if (partial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            "video/x-flv",
            RangeInputStream(raf, length),
            length
        )
        if (partial) response.addHeader("Content-Range", "bytes $start-$end/$fileSize")
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

/**
 * HTTP Range ヘッダー（`bytes=start-end` / `bytes=start-` / `bytes=-suffix`）を解釈し、
 * ファイル内の [start, end]（両端含む）を返す。ヘッダーが無ければファイル全体。
 * 範囲外・不正なら null（416 を返すべき）。
 */
internal fun parseRange(header: String?, fileSize: Long): Pair<Long, Long>? {
    if (fileSize <= 0) return null
    if (header == null) return 0L to fileSize - 1
    if (!header.startsWith("bytes=")) return null
    val spec = header.removePrefix("bytes=").substringBefore(',').trim()
    val dash = spec.indexOf('-')
    if (dash < 0) return null
    val first = spec.substring(0, dash).trim()
    val last = spec.substring(dash + 1).trim()
    if (first.isEmpty()) {
        // 末尾から suffix バイト
        val suffix = last.toLongOrNull() ?: return null
        if (suffix <= 0) return null
        return maxOf(0L, fileSize - suffix) to fileSize - 1
    }
    val start = first.toLongOrNull() ?: return null
    if (start < 0 || start >= fileSize) return null
    val end = if (last.isEmpty()) fileSize - 1 else (last.toLongOrNull() ?: return null).coerceAtMost(fileSize - 1)
    if (end < start) return null
    return start to end
}
