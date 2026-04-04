package com.example.hanabi.player

import com.example.hanabi.data.smb.SmbMediaDataSource

/**
 * MP4/M4V ファイルのボックス構造からチャプター情報を抽出するパーサー。
 *
 * 対応形式:
 * 1. Nero chapters (moov/udta/chpl) — ffmpeg 等で生成
 * 2. QuickTime text track — HandBrake, MakeMKV 等で生成
 *
 * 処理手順:
 * 1. ファイル先頭からボックスヘッダーを辿って moov の位置を特定
 * 2. moov の中身を読み込み（最大16MB）
 * 3. udta/chpl (Nero) → mdia/minf/stbl のテキストトラックの順で検索
 */
object Mp4ChapterExtractor {

    private const val MOOV_BUF_MAX = 16 * 1024 * 1024  // 16MB

    // Box types (big-endian 4-byte ASCII as Int)
    private val TYPE_MOOV = bt("moov")
    private val TYPE_UDTA = bt("udta")
    private val TYPE_CHPL = bt("chpl")   // Nero chapter list
    private val TYPE_TRAK = bt("trak")
    private val TYPE_MDIA = bt("mdia")
    private val TYPE_MDHD = bt("mdhd")
    private val TYPE_HDLR = bt("hdlr")
    private val TYPE_MINF = bt("minf")
    private val TYPE_STBL = bt("stbl")
    private val TYPE_STTS = bt("stts")
    private val TYPE_STCO = bt("stco")
    private val TYPE_CO64 = bt("co64")
    private val TYPE_STSC = bt("stsc")
    private val TYPE_STSZ = bt("stsz")

    private val HANDLER_TEXT = bt("text")

    private fun bt(s: String): Int =
        (s[0].code shl 24) or (s[1].code shl 16) or (s[2].code shl 8) or s[3].code

    fun extract(source: SmbMediaDataSource): List<Chapter> {
        return try { extractInternal(source) } catch (_: Exception) { emptyList() }
    }

    private fun extractInternal(source: SmbMediaDataSource): List<Chapter> {
        val fileSize = source.size

        // moov ボックスの位置を特定（先頭から順次スキャン）
        val moovPos = findMoovPos(source, fileSize) ?: return emptyList()

        // moov ヘッダー読み込み（size=4, type=4, [largesize=8]）
        val hdr = ByteArray(16)
        if (readBuf(source, moovPos, hdr, 8) < 8) return emptyList()
        val sizeField = readU32(hdr, 0)
        val headerSize: Long
        val moovTotalSize: Long
        when {
            sizeField == 1L -> {
                readBuf(source, moovPos + 8, hdr, 8)
                headerSize = 16L
                moovTotalSize = readU64(hdr, 0)
            }
            sizeField == 0L -> {
                headerSize = 8L
                moovTotalSize = fileSize - moovPos
            }
            else -> {
                headerSize = 8L
                moovTotalSize = sizeField
            }
        }

        // moov データを読み込む
        val moovDataLen = (moovTotalSize - headerSize).toInt().coerceIn(0, MOOV_BUF_MAX)
        val moovBuf = ByteArray(moovDataLen)
        val moovRead = readBuf(source, moovPos + headerSize, moovBuf, moovDataLen)

        // 1. Nero chapters (udta/chpl)
        val nero = findNeroChapters(moovBuf, moovRead)
        if (nero.isNotEmpty()) return nero

        // 2. QuickTime テキストトラック
        return findQTChapters(source, moovBuf, moovRead)
    }

    /** ファイル先頭からボックスヘッダーを辿って moov の開始オフセットを返す */
    private fun findMoovPos(source: SmbMediaDataSource, fileSize: Long): Long? {
        var pos = 0L
        val hdr = ByteArray(16)
        while (pos + 8 <= fileSize) {
            if (readBuf(source, pos, hdr, 8) < 8) break
            val sizeField = readU32(hdr, 0)
            val type = readI32(hdr, 4)
            if (type == TYPE_MOOV) return pos
            val boxSize = when {
                sizeField == 1L -> {
                    if (readBuf(source, pos + 8, hdr, 8) < 8) return null
                    readU64(hdr, 0)
                }
                sizeField == 0L -> return null  // EOF まで続くボックス、moov は見つからない
                else -> sizeField
            }
            if (boxSize < 8) break
            pos += boxSize
        }
        return null
    }

    // ── Nero chapters (udta/chpl) ──────────────────────────────────────────────

    private fun findNeroChapters(buf: ByteArray, limit: Int): List<Chapter> {
        val udta = findBox(buf, 0, limit, TYPE_UDTA) ?: return emptyList()
        val chpl = findBox(buf, udta.first, udta.second, TYPE_CHPL) ?: return emptyList()
        return parseChpl(buf, chpl.first, chpl.second)
    }

    /**
     * chpl ボックスのデータ部分をパース（ffmpeg 形式）
     * FullBox: version(1) + flags(3) = 4bytes
     * reserved(4) — ffmpeg は version によらず常に書き込む
     * count(1) + [timestamp(8, 100ns) + titleLen(1) + title(UTF-8)] × count
     */
    private fun parseChpl(buf: ByteArray, start: Int, end: Int): List<Chapter> {
        if (end - start < 9) return emptyList()
        var pos = start + 8  // skip version+flags(4) + reserved(4)
        if (pos >= end) return emptyList()
        val count = buf[pos].toInt() and 0xFF; pos++

        val chapters = mutableListOf<Chapter>()
        repeat(count) {
            if (pos + 9 > end) return@repeat
            val timeHns = readU64(buf, pos); pos += 8   // 100-nanosecond units
            val titleLen = buf[pos].toInt() and 0xFF; pos++
            if (pos + titleLen > end) return@repeat
            val title = String(buf, pos, titleLen, Charsets.UTF_8); pos += titleLen
            chapters.add(Chapter(timeHns / 10_000L, title.ifBlank { null }))
        }
        return chapters.sortedBy { it.positionMs }
    }

    // ── QuickTime text track ───────────────────────────────────────────────────

    private fun findQTChapters(source: SmbMediaDataSource, moovBuf: ByteArray, moovRead: Int): List<Chapter> {
        val chapters = mutableListOf<Chapter>()
        forEachBox(moovBuf, 0, moovRead) { type, s, e ->
            if (type != TYPE_TRAK) return@forEachBox
            if (getHandlerType(moovBuf, s, e) != HANDLER_TEXT) return@forEachBox

            val timescale = getTimescale(moovBuf, s, e)
            if (timescale <= 0) return@forEachBox

            val stbl = findBoxNested(moovBuf, s, e, TYPE_MDIA, TYPE_MINF, TYPE_STBL)
                ?: return@forEachBox

            val times   = parseSampleTimes(moovBuf, stbl.first, stbl.second, timescale)
            val offsets = parseSampleOffsets(moovBuf, stbl.first, stbl.second)
            val sizes   = parseSampleSizes(moovBuf, stbl.first, stbl.second)
            if (times.isEmpty() || offsets.isEmpty() || sizes.isEmpty()) return@forEachBox

            val n = minOf(times.size, offsets.size, sizes.size)
            for (i in 0 until n) {
                val sz = sizes[i].coerceAtMost(512)
                if (sz < 2) continue
                val sampleBuf = ByteArray(sz)
                if (readBuf(source, offsets[i], sampleBuf, sz) < 2) continue
                // QuickTime text format: 2-byte length + UTF-8
                val textLen = ((sampleBuf[0].toInt() and 0xFF) shl 8) or
                              (sampleBuf[1].toInt() and 0xFF)
                val title = if (textLen > 0 && textLen + 2 <= sz) {
                    String(sampleBuf, 2, textLen, Charsets.UTF_8)
                } else ""
                chapters.add(Chapter(times[i], title.ifBlank { null }))
            }
        }
        return chapters.sortedBy { it.positionMs }
    }

    private fun getHandlerType(buf: ByteArray, trakS: Int, trakE: Int): Int {
        // mdia/hdlr: FullBox(4) + pre_defined(4) + handler_type(4)
        val r = findBoxNested(buf, trakS, trakE, TYPE_MDIA, TYPE_HDLR) ?: return 0
        if (r.first + 12 > r.second) return 0
        return readI32(buf, r.first + 8)
    }

    private fun getTimescale(buf: ByteArray, trakS: Int, trakE: Int): Int {
        val r = findBoxNested(buf, trakS, trakE, TYPE_MDIA, TYPE_MDHD) ?: return 0
        if (r.first + 4 > r.second) return 0
        val version = buf[r.first].toInt() and 0xFF
        // v0: FullBox(4) + creation(4) + modification(4) + timescale(4) → offset 12
        // v1: FullBox(4) + creation(8) + modification(8) + timescale(4) → offset 20
        val tsPos = r.first + if (version == 1) 20 else 12
        if (tsPos + 4 > r.second) return 0
        return readI32(buf, tsPos)
    }

    /** stts から各サンプルの開始時刻（ms）リストを構築 */
    private fun parseSampleTimes(buf: ByteArray, stblS: Int, stblE: Int, timescale: Int): List<Long> {
        val r = findBox(buf, stblS, stblE, TYPE_STTS) ?: return emptyList()
        if (r.first + 8 > r.second) return emptyList()
        // FullBox(4) + entry_count(4)
        val entryCount = readI32(buf, r.first + 4)
        val times = mutableListOf<Long>()
        var t = 0L
        var pos = r.first + 8
        repeat(entryCount) {
            if (pos + 8 > r.second) return@repeat
            val cnt = readI32(buf, pos)
            val dur = readI32(buf, pos + 4)
            repeat(cnt) { times.add(t * 1000L / timescale); t += dur }
            pos += 8
        }
        return times
    }

    /** stco/co64 + stsc から各サンプルのファイル内絶対オフセットリストを構築 */
    private fun parseSampleOffsets(buf: ByteArray, stblS: Int, stblE: Int): List<Long> {
        val co64r = findBox(buf, stblS, stblE, TYPE_CO64)
        val stcor = findBox(buf, stblS, stblE, TYPE_STCO)
        val (r, is64) = when {
            co64r != null -> Pair(co64r, true)
            stcor != null -> Pair(stcor, false)
            else -> return emptyList()
        }
        if (r.first + 8 > r.second) return emptyList()
        // FullBox(4) + entry_count(4)
        val count = readI32(buf, r.first + 4)
        val chunkOffsets = mutableListOf<Long>()
        var pos = r.first + 8
        repeat(count) {
            if (is64) {
                if (pos + 8 <= r.second) { chunkOffsets.add(readU64(buf, pos)); pos += 8 }
            } else {
                if (pos + 4 <= r.second) { chunkOffsets.add(readU32(buf, pos)); pos += 4 }
            }
        }
        return expandToSampleOffsets(buf, stblS, stblE, chunkOffsets)
    }

    /** stsc を使ってチャンクオフセットをサンプルオフセットに展開 */
    private fun expandToSampleOffsets(
        buf: ByteArray, stblS: Int, stblE: Int, chunkOffsets: List<Long>
    ): List<Long> {
        val r = findBox(buf, stblS, stblE, TYPE_STSC) ?: return chunkOffsets
        if (r.first + 8 > r.second) return chunkOffsets
        val entryCount = readI32(buf, r.first + 4)
        data class SE(val firstChunk: Int, val spc: Int)
        val entries = mutableListOf<SE>()
        var ep = r.first + 8
        repeat(entryCount) {
            if (ep + 12 <= r.second) {
                entries.add(SE(readI32(buf, ep), readI32(buf, ep + 4))); ep += 12
            }
        }
        // 1サンプル/チャンクが大半（チャプタートラックはこのケース）
        if (entries.isEmpty() || entries.all { it.spc == 1 }) return chunkOffsets

        // 汎用展開
        val sizes = parseSampleSizes(buf, stblS, stblE)
        val result = mutableListOf<Long>()
        var si = 0
        for (ci in chunkOffsets.indices) {
            val chunkNum = ci + 1
            val spc = entries.lastOrNull { it.firstChunk <= chunkNum }?.spc ?: 1
            var off = chunkOffsets[ci]
            repeat(spc) {
                result.add(off)
                off += if (si < sizes.size) sizes[si].toLong() else 0L
                si++
            }
        }
        return result
    }

    /** stsz から各サンプルのサイズリストを返す */
    private fun parseSampleSizes(buf: ByteArray, stblS: Int, stblE: Int): List<Int> {
        val r = findBox(buf, stblS, stblE, TYPE_STSZ) ?: return emptyList()
        if (r.first + 12 > r.second) return emptyList()
        // FullBox(4) + sample_size(4) + sample_count(4)
        val defSize = readI32(buf, r.first + 4)
        val count = readI32(buf, r.first + 8)
        if (defSize != 0) return List(count) { defSize }
        val sizes = mutableListOf<Int>()
        var pos = r.first + 12
        repeat(count) {
            if (pos + 4 <= r.second) { sizes.add(readI32(buf, pos)); pos += 4 }
        }
        return sizes
    }

    // ── Box utilities ──────────────────────────────────────────────────────────

    private fun findBox(buf: ByteArray, start: Int, end: Int, type: Int): Pair<Int, Int>? {
        var pos = start
        while (pos + 8 <= end) {
            val size = readI32(buf, pos)
            val t = readI32(buf, pos + 4)
            val de = (pos + size).coerceAtMost(end)
            if (size >= 8 && t == type) return Pair(pos + 8, de)
            if (size < 8) break
            pos += size
        }
        return null
    }

    private fun findBoxNested(buf: ByteArray, start: Int, end: Int, vararg types: Int): Pair<Int, Int>? {
        var r = Pair(start, end)
        for (t in types) r = findBox(buf, r.first, r.second, t) ?: return null
        return r
    }

    private inline fun forEachBox(
        buf: ByteArray, start: Int, end: Int,
        action: (type: Int, dataStart: Int, dataEnd: Int) -> Unit
    ) {
        var pos = start
        while (pos + 8 <= end) {
            val size = readI32(buf, pos)
            val type = readI32(buf, pos + 4)
            if (size < 8) break
            action(type, pos + 8, (pos + size).coerceAtMost(end))
            pos += size
        }
    }

    // ── I/O ───────────────────────────────────────────────────────────────────

    private fun readBuf(source: SmbMediaDataSource, startPos: Long, buf: ByteArray, size: Int): Int {
        var total = 0
        while (total < size) {
            val n = source.readAt(startPos + total, buf, total, size - total)
            if (n <= 0) break
            total += n
        }
        return total
    }

    // ── Big-endian primitives ──────────────────────────────────────────────────

    private fun readI32(buf: ByteArray, pos: Int): Int =
        ((buf[pos].toInt()   and 0xFF) shl 24) or
        ((buf[pos+1].toInt() and 0xFF) shl 16) or
        ((buf[pos+2].toInt() and 0xFF) shl 8)  or
         (buf[pos+3].toInt() and 0xFF)

    private fun readU32(buf: ByteArray, pos: Int): Long =
        readI32(buf, pos).toLong() and 0xFFFFFFFFL

    private fun readU64(buf: ByteArray, pos: Int): Long =
        ((buf[pos].toLong()   and 0xFF) shl 56) or
        ((buf[pos+1].toLong() and 0xFF) shl 48) or
        ((buf[pos+2].toLong() and 0xFF) shl 40) or
        ((buf[pos+3].toLong() and 0xFF) shl 32) or
        ((buf[pos+4].toLong() and 0xFF) shl 24) or
        ((buf[pos+5].toLong() and 0xFF) shl 16) or
        ((buf[pos+6].toLong() and 0xFF) shl 8)  or
         (buf[pos+7].toLong() and 0xFF)
}
