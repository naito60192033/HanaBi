package com.example.hanabi.player

import com.example.hanabi.data.smb.SmbMediaDataSource

/** チャプター情報 */
data class Chapter(val positionMs: Long, val title: String?)

/**
 * MKV (Matroska) ファイルの EBML 構造からチャプター情報を抽出するパーサー。
 *
 * 処理手順:
 * 1. ファイル先頭 128KB を読み込み、EBML ヘッダー確認と Segment 開始位置を特定
 * 2. SeekHead を解析して Chapters エレメントの絶対ファイル位置を取得
 * 3. Chapters 位置が判明した場合はそこから 64KB を読み込んでパース
 * 4. SeekHead がない場合は先頭バッファのリニアスキャンにフォールバック
 *
 * 注意: MKV の Chapters は通常ファイル末尾付近に配置されるため
 * 先頭固定読み込みだけでは取得できないケースがほとんど。
 */
object MkvChapterExtractor {

    private const val INITIAL_READ = 128 * 1024  // 128KB
    private const val CHAPTER_READ =  64 * 1024  //  64KB

    // EBML element IDs（先頭のクラスビットを含む生バイト値）
    private const val ID_SEGMENT        = 0x18538067L
    private const val ID_SEEKHEAD       = 0x114D9B74L
    private const val ID_SEEK           = 0x4DBBL
    private const val ID_SEEK_ID        = 0x53ABL
    private const val ID_SEEK_POS       = 0x53ACL
    private const val ID_CHAPTERS       = 0x1043A770L
    private const val ID_EDITION_ENTRY  = 0x45B9L
    private const val ID_CHAPTER_ATOM   = 0xB6L
    private const val ID_TIME_START     = 0x91L   // nanoseconds
    private const val ID_DISPLAY        = 0x80L
    private const val ID_CHAP_STRING    = 0x85L
    private const val ID_FLAG_HIDDEN    = 0x98L
    private const val ID_FLAG_ENABLED   = 0x4598L

    /**
     * SmbMediaDataSource からチャプターリストを抽出する。
     * 失敗した場合は空リストを返す（例外を外に出さない）。
     */
    fun extract(source: SmbMediaDataSource): List<Chapter> {
        return try {
            // Step 1: 先頭バッファ読み込み
            val initBuf = ByteArray(INITIAL_READ)
            val initRead = readBuf(source, 0L, initBuf, INITIAL_READ)
            if (initRead < 4) return emptyList()
            if (!isMkv(initBuf)) return emptyList()

            // Step 2: Segment データ開始位置を特定
            val segmentDataStart = findSegmentDataStart(initBuf, initRead)
                ?: return emptyList()

            // Step 3: SeekHead から Chapters の絶対位置を取得
            val chaptersPos = findChaptersPosition(initBuf, initRead, segmentDataStart)

            if (chaptersPos != null) {
                // Chapters エレメントを直接読み込んでパース
                val chapBuf = ByteArray(CHAPTER_READ)
                val chapRead = readBuf(source, chaptersPos, chapBuf, CHAPTER_READ)
                if (chapRead > 8) findAndParseChapters(chapBuf, chapRead) else emptyList()
            } else {
                // フォールバック: 先頭バッファのリニアスキャン
                findAndParseChapters(initBuf, initRead)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    // ──────────────────────────────────────────
    // SeekHead 解析
    // ──────────────────────────────────────────

    private fun isMkv(buf: ByteArray): Boolean =
        buf[0] == 0x1A.toByte() && buf[1] == 0x45.toByte() &&
        buf[2] == 0xDF.toByte() && buf[3] == 0xA3.toByte()

    /** Segment エレメントのデータ開始位置（絶対バイトオフセット）を返す */
    private fun findSegmentDataStart(buf: ByteArray, limit: Int): Long? {
        var pos = 0
        while (pos < limit - 12) {
            if (readId(buf, pos) == ID_SEGMENT) {
                val idLen = ebmlIdLen(buf, pos)
                val sizePos = pos + idLen
                val result = readVint(buf, sizePos, limit) ?: return null
                val (_, sizeLen) = result
                return (sizePos + sizeLen).toLong()
            }
            pos++
        }
        return null
    }

    /**
     * SeekHead を解析して Chapters エレメントの絶対ファイルオフセットを返す。
     * SeekHead が見つからない、または Chapters エントリがない場合は null。
     *
     * SeekPosition は Segment データ先頭からの相対オフセットなので
     * segmentDataStart を加算して絶対位置を計算する。
     */
    private fun findChaptersPosition(buf: ByteArray, limit: Int, segmentDataStart: Long): Long? {
        val segStart = segmentDataStart.toInt().coerceAtMost(limit - 1)
        var result: Long? = null

        forEachElement(buf, segStart, limit) { id, s, e ->
            if (id == ID_SEEKHEAD && result == null) {
                forEachElement(buf, s, e) { seekId, ss, se ->
                    if (seekId == ID_SEEK && result == null) {
                        var elementId: Long? = null
                        var seekPos: Long? = null
                        forEachElement(buf, ss, se) { innerId, sss, sse ->
                            when (innerId) {
                                ID_SEEK_ID  -> elementId = readUInt(buf, sss, sse - sss)
                                ID_SEEK_POS -> seekPos   = readUInt(buf, sss, sse - sss)
                            }
                        }
                        if (elementId == ID_CHAPTERS && seekPos != null) {
                            result = segmentDataStart + seekPos!!
                        }
                    }
                }
            }
        }
        return result
    }

    // ──────────────────────────────────────────
    // Chapters パース（既存ロジック）
    // ──────────────────────────────────────────

    /** バッファ内で Chapters 要素を探してパース */
    private fun findAndParseChapters(buf: ByteArray, limit: Int): List<Chapter> {
        var pos = 0
        while (pos <= limit - 12) {
            if (readId(buf, pos) == ID_CHAPTERS) {
                val idLen = ebmlIdLen(buf, pos)
                val sizePos = pos + idLen
                val (size, sizeLen) = readVint(buf, sizePos, limit) ?: return emptyList()
                val dataStart = sizePos + sizeLen
                val dataEnd = (dataStart.toLong() + size).coerceAtMost(limit.toLong()).toInt()
                if (dataEnd > dataStart) {
                    return parseChapters(buf, dataStart, dataEnd)
                }
                return emptyList()
            }
            pos++
        }
        return emptyList()
    }

    private fun parseChapters(buf: ByteArray, start: Int, end: Int): List<Chapter> {
        val result = mutableListOf<Chapter>()
        forEachElement(buf, start, end) { id, s, e ->
            if (id == ID_EDITION_ENTRY) parseEditionEntry(buf, s, e, result)
        }
        return result.sortedBy { it.positionMs }
    }

    private fun parseEditionEntry(buf: ByteArray, start: Int, end: Int, out: MutableList<Chapter>) {
        forEachElement(buf, start, end) { id, s, e ->
            if (id == ID_CHAPTER_ATOM) parseChapterAtom(buf, s, e)?.let { out.add(it) }
        }
    }

    private fun parseChapterAtom(buf: ByteArray, start: Int, end: Int): Chapter? {
        var timeNs: Long? = null
        var title: String? = null
        var hidden = false
        var enabled = true

        forEachElement(buf, start, end) { id, s, e ->
            when (id) {
                ID_TIME_START   -> timeNs = readUInt(buf, s, e - s)
                ID_DISPLAY      -> if (title == null) title = parseDisplay(buf, s, e)
                ID_FLAG_HIDDEN  -> if (e > s) hidden = buf[s] != 0.toByte()
                ID_FLAG_ENABLED -> if (e > s) enabled = buf[s] != 0.toByte()
            }
        }

        if (timeNs == null || hidden || !enabled) return null
        return Chapter(timeNs!! / 1_000_000L, title)
    }

    private fun parseDisplay(buf: ByteArray, start: Int, end: Int): String? {
        var result: String? = null
        forEachElement(buf, start, end) { id, s, e ->
            if (id == ID_CHAP_STRING && e > s) {
                result = String(buf, s, e - s, Charsets.UTF_8)
            }
        }
        return result
    }

    // ──────────────────────────────────────────
    // I/O
    // ──────────────────────────────────────────

    /** startPos から最大 size バイト読み込む */
    private fun readBuf(source: SmbMediaDataSource, startPos: Long, buf: ByteArray, size: Int): Int {
        var total = 0
        while (total < size) {
            val n = source.readAt(startPos + total, buf, total, size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    // ──────────────────────────────────────────
    // EBML プリミティブ
    // ──────────────────────────────────────────

    /** 指定範囲の要素を順番にコールバック処理 */
    private inline fun forEachElement(
        buf: ByteArray, start: Int, end: Int,
        action: (id: Long, dataStart: Int, dataEnd: Int) -> Unit
    ) {
        var pos = start
        while (pos < end - 1) {
            val idLen = ebmlIdLen(buf, pos).takeIf { it > 0 } ?: break
            val id = readId(buf, pos)
            pos += idLen
            if (pos >= end) break
            val (size, sizeLen) = readVint(buf, pos, end) ?: break
            pos += sizeLen
            val elemEnd = (pos.toLong() + size).coerceAtMost(end.toLong()).toInt()
            action(id, pos, elemEnd)
            pos = elemEnd
        }
    }

    /** 先頭バイトから EBML ID の長さ（バイト数）を返す */
    private fun ebmlIdLen(buf: ByteArray, pos: Int): Int {
        if (pos >= buf.size) return 0
        val first = buf[pos].toInt() and 0xFF
        return when {
            first and 0x80 != 0 -> 1
            first and 0x40 != 0 -> 2
            first and 0x20 != 0 -> 3
            first and 0x10 != 0 -> 4
            else -> 0
        }
    }

    /**
     * EBML ID を Long として読む（先頭のクラスビットを含む生バイト値）。
     * ID はクラスビットを保持したまま使うため VINT と異なる。
     */
    private fun readId(buf: ByteArray, pos: Int): Long {
        val len = ebmlIdLen(buf, pos)
        var id = 0L
        for (i in 0 until len) id = (id shl 8) or (buf[pos + i].toLong() and 0xFF)
        return id
    }

    /**
     * EBML VINT（サイズ用）を読む。先頭のマーカービットを除いた値を返す。
     * 不明サイズ（全ビット1）の場合は残りバッファ全体を示す大きな値を返す。
     */
    private fun readVint(buf: ByteArray, pos: Int, end: Int): Pair<Long, Int>? {
        if (pos >= end) return null
        val first = buf[pos].toInt() and 0xFF
        val len = when {
            first and 0x80 != 0 -> 1
            first and 0x40 != 0 -> 2
            first and 0x20 != 0 -> 3
            first and 0x10 != 0 -> 4
            first and 0x08 != 0 -> 5
            first and 0x04 != 0 -> 6
            first and 0x02 != 0 -> 7
            first and 0x01 != 0 -> 8
            else -> return null
        }
        if (pos + len > end) return null
        val masks = intArrayOf(0x7F, 0x3F, 0x1F, 0x0F, 0x07, 0x03, 0x01, 0x00)
        var size = buf[pos].toLong() and masks[len - 1].toLong()
        for (i in 1 until len) size = (size shl 8) or (buf[pos + i].toLong() and 0xFF)
        val unknownSize = (size == (1L shl (7 * len)) - 1L)
        return Pair(if (unknownSize) (end - pos).toLong() else size, len)
    }

    /** バイト列を符号なし整数として読む（最大8バイト） */
    private fun readUInt(buf: ByteArray, pos: Int, size: Int): Long {
        var result = 0L
        for (i in 0 until minOf(size, 8)) {
            result = (result shl 8) or (buf[pos + i].toLong() and 0xFF)
        }
        return result
    }
}
