@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class DelayAudioProcessorTest {

    private val bytes100ms = 48000 * 100 / 1000 * 4 // 19200

    private fun newProcessor(): DelayAudioProcessor {
        val p = DelayAudioProcessor()
        p.configure(AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        return p
    }

    /** 1 以上の値で埋めた入力（無音と区別するため） */
    private fun input(size: Int, fill: Byte = 1): ByteBuffer =
        ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder()).apply {
            repeat(size) { put(fill) }
            flip()
        }

    private fun AudioProcessor.process(buf: ByteBuffer): ByteArray {
        queueInput(buf)
        val out = getOutput()
        return ByteArray(out.remaining()).also { out.get(it) }
    }

    @Test
    fun 遅延0は素通し() {
        val p = newProcessor()
        val out = p.process(input(1000))
        assertArrayEquals(ByteArray(1000) { 1 }, out)
    }

    @Test
    fun 正の遅延は先頭に無音を前置() {
        val p = newProcessor()
        p.setDelay(100)
        val out = p.process(input(1000))
        assertEquals(bytes100ms + 1000, out.size)
        assertTrue(out.take(bytes100ms).all { it == 0.toByte() })
        assertTrue(out.drop(bytes100ms).all { it == 1.toByte() })
        // 2回目以降は無音を足さない
        assertEquals(1000, p.process(input(1000)).size)
    }

    @Test
    fun 遅延を変更しても差分だけ反映される() {
        val p = newProcessor()
        p.setDelay(100)
        assertEquals(bytes100ms + 1000, p.process(input(1000)).size)
        p.setDelay(200)
        val out = p.process(input(1000))
        assertEquals(bytes100ms + 1000, out.size)
        assertTrue(out.take(bytes100ms).all { it == 0.toByte() })
    }

    @Test
    fun 負の遅延は先頭の入力を捨てる() {
        val p = newProcessor()
        p.setDelay(-100)
        val out = p.process(input(bytes100ms + 800))
        assertEquals(800, out.size)
    }

    @Test
    fun 負の遅延は複数バッファにまたがって捨てる() {
        val p = newProcessor()
        p.setDelay(-100)
        assertEquals(0, p.process(input(bytes100ms - 400)).size)
        assertEquals(600, p.process(input(1000)).size)
    }

    @Test
    fun flush後に全量が再適用される() {
        val p = newProcessor()
        p.setDelay(100)
        assertEquals(bytes100ms + 500, p.process(input(500)).size)
        assertEquals(500, p.process(input(500)).size)
        p.flush()
        assertEquals(bytes100ms + 500, p.process(input(500)).size)
    }

    @Test
    fun フレーム境界を保つ() {
        val p = DelayAudioProcessor()
        p.configure(AudioFormat(44100, 2, C.ENCODING_PCM_16BIT))
        p.flush()
        p.setDelay(50)
        val out = p.process(input(400))
        assertEquals(0, out.size % 4)
        assertEquals(44100 * 50 / 1000 * 4 + 400, out.size)
    }

    @Test(expected = AudioProcessor.UnhandledAudioFormatException::class)
    fun PCM_FLOATは非対応() {
        DelayAudioProcessor().configure(AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT))
    }
}
