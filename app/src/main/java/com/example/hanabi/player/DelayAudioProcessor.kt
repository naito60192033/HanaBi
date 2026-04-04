@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.player

import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer

/**
 * 音声ディレイを実現する AudioProcessor。
 *
 * - 正値（+ms）: 音声を遅らせる → silence を先行して出力し、入力をリングバッファ経由で遅延出力
 * - 負値（-ms）: 音声を早める → 開始時に |delay| ms 分の入力バイトを破棄し、以降は直接出力
 * - 範囲: -500ms 〜 +500ms、50ms 刻みで設定
 */
class DelayAudioProcessor : BaseAudioProcessor() {

    private var delayMs: Long = 0L

    // 正ディレイ用リングバッファ
    private var ringBuffer: ByteArray = ByteArray(0)
    private var writePos: Int = 0
    private var readPos: Int = 0
    private var bufferedBytes: Int = 0
    private var delaySilenceRemaining: Int = 0  // 出力すべき silence バイト数

    // 負ディレイ用スキップ
    private var skipBytesRemaining: Int = 0

    fun setDelay(ms: Long) {
        val clamped = ms.coerceIn(-500L, 500L)
        if (clamped == delayMs) return
        delayMs = clamped
        // フォーマット確定済みなら即座にリセット
        if (isActive) flush()
    }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        // フォーマット変換なし（PCM_16BIT 前提）
        val bytesPerSample = 2 // PCM_16BIT
        // 最大 500ms 分のバッファを確保（正負両対応）
        val maxDelayBytes =
            (500L * inputAudioFormat.sampleRate / 1000).toInt() *
                    inputAudioFormat.channelCount * bytesPerSample
        ringBuffer = ByteArray(maxDelayBytes + 4096) // 余裕を持たせる
        return inputAudioFormat
    }

    override fun onFlush() {
        ringBuffer.fill(0)
        writePos = 0
        readPos = 0
        bufferedBytes = 0

        val fmt = inputAudioFormat
        val bytesPerSample = 2
        val bytesPerMs = fmt.sampleRate.toLong() * fmt.channelCount * bytesPerSample / 1000L

        if (delayMs > 0) {
            // 正ディレイ: 最初に silence を出力するバイト数をセット
            delaySilenceRemaining = (delayMs * bytesPerMs).toInt()
            skipBytesRemaining = 0
        } else if (delayMs < 0) {
            // 負ディレイ: 入力の最初の |delayMs| ms 分をスキップ
            skipBytesRemaining = (-delayMs * bytesPerMs).toInt()
            delaySilenceRemaining = 0
        } else {
            delaySilenceRemaining = 0
            skipBytesRemaining = 0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (delayMs == 0L) {
            // ディレイなし: ByteArray経由でコピー（output と inputBuffer が同一オブジェクトになるのを防ぐ）
            val size = inputBuffer.remaining()
            val bytes = ByteArray(size)
            inputBuffer.get(bytes)
            val output = replaceOutputBuffer(size)
            output.put(bytes)
            output.flip()
            return
        }

        if (delayMs > 0) {
            queueInputPositiveDelay(inputBuffer)
        } else {
            queueInputNegativeDelay(inputBuffer)
        }
    }

    /** 正ディレイ処理: silence → 遅延出力 */
    private fun queueInputPositiveDelay(inputBuffer: ByteBuffer) {
        val inputBytes = inputBuffer.remaining()
        // ByteArray に先読みしてから replaceOutputBuffer を呼ぶ（同一バッファ問題を回避）
        val inputData = ByteArray(inputBytes)
        inputBuffer.get(inputData)

        val totalOutputSize = delaySilenceRemaining + inputBytes
        if (totalOutputSize == 0) return
        val output = replaceOutputBuffer(totalOutputSize)

        // silence 出力
        if (delaySilenceRemaining > 0) {
            repeat(delaySilenceRemaining) { output.put(0) }
            delaySilenceRemaining = 0
        }

        output.put(inputData)
        output.flip()
    }

    /** 負ディレイ処理: 最初の |delay| ms 分を破棄し、以降は直接出力 */
    private fun queueInputNegativeDelay(inputBuffer: ByteBuffer) {
        // スキップすべきバイト数が残っている場合は破棄
        if (skipBytesRemaining > 0) {
            val toSkip = minOf(skipBytesRemaining, inputBuffer.remaining())
            inputBuffer.position(inputBuffer.position() + toSkip)
            skipBytesRemaining -= toSkip
        }

        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // ByteArray に先読みしてから replaceOutputBuffer を呼ぶ（同一バッファ問題を回避）
        val bytes = ByteArray(remaining)
        inputBuffer.get(bytes)
        val output = replaceOutputBuffer(remaining)
        output.put(bytes)
        output.flip()
    }
}
