@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * 音声ディレイを実現する AudioProcessor（PCM_16BIT のみ対応）。
 *
 * - setDelay は目標値を書き換えるだけ（UI スレッドから呼ばれる。flush はしない）
 * - オーディオスレッドの queueInput 先頭で「目標値 - 適用済みの値」の差分だけを反映する
 *   - 差分 > 0: 差分 ms 分の無音を出力の先頭に前置
 *   - 差分 < 0: |差分| ms 分の入力を破棄（複数バッファにまたがってよい）
 * - onFlush（シーク等）では適用済みの値を 0 に戻し、次の queueInput で目標値の全量を再適用する
 * - 範囲: -500ms 〜 +500ms
 */
class DelayAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var targetDelayMs: Long = 0L

    // 以下はオーディオスレッドからのみ触る
    private var appliedDelayMs: Long = 0L
    private var pendingSilenceBytes: Int = 0
    private var pendingSkipBytes: Int = 0

    fun setDelay(ms: Long) {
        targetDelayMs = ms.coerceIn(-500L, 500L)
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        appliedDelayMs = 0L
        pendingSilenceBytes = 0
        pendingSkipBytes = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        applyDelta()

        // 破棄すべき入力があれば先に捨てる
        if (pendingSkipBytes > 0) {
            val toSkip = minOf(pendingSkipBytes, inputBuffer.remaining())
            inputBuffer.position(inputBuffer.position() + toSkip)
            pendingSkipBytes -= toSkip
        }

        val inputBytes = inputBuffer.remaining()
        val total = pendingSilenceBytes + inputBytes
        if (total == 0) return

        val output = replaceOutputBuffer(total)
        if (pendingSilenceBytes > 0) {
            // 出力バッファは再利用される可能性があるため、無音は明示的に 0 を書く
            output.put(ByteArray(pendingSilenceBytes))
            pendingSilenceBytes = 0
        }
        output.put(inputBuffer)
        output.flip()
    }

    /** 目標値と適用済みの値の差分を、無音挿入／入力破棄の保留量に反映する */
    private fun applyDelta() {
        val delta = targetDelayMs - appliedDelayMs
        if (delta == 0L) return
        val bytes = msToBytes(kotlin.math.abs(delta))
        if (delta > 0) {
            // 未消化の破棄分があれば相殺してから無音を足す
            val cancel = minOf(pendingSkipBytes, bytes)
            pendingSkipBytes -= cancel
            pendingSilenceBytes += bytes - cancel
        } else {
            val cancel = minOf(pendingSilenceBytes, bytes)
            pendingSilenceBytes -= cancel
            pendingSkipBytes += bytes - cancel
        }
        appliedDelayMs = targetDelayMs
    }

    /** ms をフレーム単位でバイト数に換算する（フレーム境界を保つ） */
    private fun msToBytes(ms: Long): Int {
        val fmt = inputAudioFormat
        val frames = fmt.sampleRate.toLong() * ms / 1000L
        return (frames * fmt.bytesPerFrame).toInt()
    }
}
