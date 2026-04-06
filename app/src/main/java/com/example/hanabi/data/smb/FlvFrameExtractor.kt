package com.example.hanabi.data.smb

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

private const val TAG = "FlvFrameExtractor"
private const val WIDTH = 640
private const val HEIGHT = 360
private const val TIMEOUT_MS = 20_000L

/**
 * ExoPlayer + EGL + OpenGL ES を使って FLV ファイルからフレームを抽出する。
 * Android の MediaMetadataRetriever が FLV 非対応のため、このアプローチを採用。
 *
 * 処理フロー:
 * 1. EGL コンテキスト + FBO をバックグラウンドスレッドで構築
 * 2. ExoPlayer が HTTP プロキシ経由で FLV を読み込み SurfaceTexture に描画
 * 3. onFrameAvailableListener で OES テクスチャ → FBO → glReadPixels → Bitmap
 */
object FlvFrameExtractor {

    private val VERT = """
        attribute vec2 aPos;
        attribute vec2 aUV;
        varying vec2 vUV;
        void main() { gl_Position = vec4(aPos, 0.0, 1.0); vUV = aUV; }
    """.trimIndent()

    // GL_OES_EGL_image_external: SurfaceTexture 由来の OES テクスチャをサンプリング
    private val FRAG = """
        #extension GL_OES_EGL_image_external : require
        precision mediump float;
        uniform samplerExternalOES uTex;
        varying vec2 vUV;
        void main() { gl_FragColor = texture2D(uTex, vUV); }
    """.trimIndent()

    suspend fun extract(context: Context, url: String): Bitmap? =
        withContext(Dispatchers.Main) {
            suspendCancellableCoroutine { cont ->
                val mainHandler = Handler(Looper.getMainLooper())
                val eglThread = HandlerThread("FlvEgl").also { it.start() }
                val eglHandler = Handler(eglThread.looper)
                val done = AtomicBoolean(false)

                var exoPlayer: ExoPlayer? = null
                var surface: Surface? = null
                var surfaceTex: SurfaceTexture? = null

                fun finish(bitmap: Bitmap?) {
                    if (!done.compareAndSet(false, true)) return
                    mainHandler.post {
                        exoPlayer?.release()
                        surface?.release()
                        surfaceTex?.release()
                        eglThread.quitSafely()
                        cont.resume(bitmap)
                    }
                }

                mainHandler.postDelayed({ finish(null) }, TIMEOUT_MS)

                eglHandler.post setup@{
                    // --- EGL セットアップ ---
                    val disp = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                    EGL14.eglInitialize(disp, null, 0, null, 0)

                    val cfgAttrs = intArrayOf(
                        EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8,
                        EGL14.EGL_BLUE_SIZE, 8, EGL14.EGL_ALPHA_SIZE, 8,
                        EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                        EGL14.EGL_NONE
                    )
                    val cfgs = arrayOfNulls<EGLConfig>(1)
                    EGL14.eglChooseConfig(disp, cfgAttrs, 0, cfgs, 0, 1, IntArray(1), 0)
                    val cfg = cfgs[0] ?: run {
                        Log.e(TAG, "EGL config not found"); finish(null); return@setup
                    }

                    val ctx = EGL14.eglCreateContext(
                        disp, cfg, EGL14.EGL_NO_CONTEXT,
                        intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
                    )
                    val pb = EGL14.eglCreatePbufferSurface(
                        disp, cfg,
                        intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE), 0
                    )
                    EGL14.eglMakeCurrent(disp, pb, pb, ctx)

                    // --- OES テクスチャ ---
                    val oesId = IntArray(1).also { GLES20.glGenTextures(1, it, 0) }[0]
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesId)
                    for ((p, v) in listOf(
                        GLES20.GL_TEXTURE_MIN_FILTER to GLES20.GL_NEAREST,
                        GLES20.GL_TEXTURE_MAG_FILTER to GLES20.GL_NEAREST,
                        GLES20.GL_TEXTURE_WRAP_S to GLES20.GL_CLAMP_TO_EDGE,
                        GLES20.GL_TEXTURE_WRAP_T to GLES20.GL_CLAMP_TO_EDGE,
                    )) GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, p, v)

                    val st = SurfaceTexture(oesId).apply { setDefaultBufferSize(WIDTH, HEIGHT) }
                    surfaceTex = st
                    surface = Surface(st)

                    // --- シェーダーコンパイル ---
                    val prog = buildProgram(VERT, FRAG)
                    val aPos = GLES20.glGetAttribLocation(prog, "aPos")
                    val aUV  = GLES20.glGetAttribLocation(prog, "aUV")
                    val uTex = GLES20.glGetUniformLocation(prog, "uTex")

                    // --- FBO ---
                    val fboTex = IntArray(1).also {
                        GLES20.glGenTextures(1, it, 0)
                        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, it[0])
                        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                            WIDTH, HEIGHT, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST)
                        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST)
                    }[0]
                    val fboId = IntArray(1).also {
                        GLES20.glGenFramebuffers(1, it, 0)
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, it[0])
                        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER,
                            GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, fboTex, 0)
                        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                    }[0]

                    // 頂点データ（フルスクリーンクワッド, TRIANGLE_STRIP）
                    // UV の V 軸を反転して OpenGL の底辺起点と画像の上辺起点を合わせる
                    val posBuf = floatBuf(floatArrayOf(-1f,-1f,  1f,-1f, -1f,1f,  1f,1f))
                    val uvBuf  = floatBuf(floatArrayOf( 0f, 1f,  1f, 1f,  0f,0f,  1f,0f))

                    // --- フレーム到着リスナー ---
                    st.setOnFrameAvailableListener({ _ ->
                        eglHandler.post {
                            if (done.get()) return@post
                            st.updateTexImage()

                            // OES テクスチャ → FBO に描画
                            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fboId)
                            GLES20.glViewport(0, 0, WIDTH, HEIGHT)
                            GLES20.glUseProgram(prog)
                            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesId)
                            GLES20.glUniform1i(uTex, 0)

                            posBuf.rewind()
                            GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, posBuf)
                            GLES20.glEnableVertexAttribArray(aPos)
                            uvBuf.rewind()
                            GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 0, uvBuf)
                            GLES20.glEnableVertexAttribArray(aUV)

                            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                            // ピクセル読み取り
                            val buf = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
                                .order(ByteOrder.nativeOrder())
                            GLES20.glReadPixels(0, 0, WIDTH, HEIGHT,
                                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf)
                            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)

                            // glReadPixels は下行から読むため上下反転して Bitmap に変換
                            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
                            val row = ByteArray(WIDTH * 4)
                            val flipped = ByteBuffer.allocateDirect(WIDTH * HEIGHT * 4)
                                .order(ByteOrder.nativeOrder())
                            for (y in 0 until HEIGHT) {
                                buf.position((HEIGHT - 1 - y) * WIDTH * 4)
                                buf.get(row)
                                flipped.put(row)
                            }
                            flipped.rewind()
                            bitmap.copyPixelsFromBuffer(flipped)

                            finish(bitmap)
                        }
                    }, eglHandler)

                    // --- ExoPlayer をメインスレッドで起動 ---
                    mainHandler.post {
                        val player = ExoPlayer.Builder(context).build()
                        exoPlayer = player
                        player.setVideoSurface(surface)
                        player.setMediaItem(MediaItem.fromUri(url))
                        player.addListener(object : Player.Listener {
                            override fun onPlaybackStateChanged(state: Int) {
                                when (state) {
                                    Player.STATE_READY -> {
                                        // 動画の15%地点へシーク後に再生
                                        val seekMs = (player.duration * 15L / 100L).coerceAtLeast(0L)
                                        player.seekTo(seekMs)
                                        player.play()
                                    }
                                    Player.STATE_ENDED -> finish(null)
                                }
                            }
                            override fun onPlayerError(error: PlaybackException) {
                                Log.e(TAG, "ExoPlayer error: ${error.message}")
                                finish(null)
                            }
                        })
                        player.prepare()
                    }
                }

                cont.invokeOnCancellation { finish(null) }
            }
        }

    private fun buildProgram(vertSrc: String, fragSrc: String): Int {
        fun compile(type: Int, src: String) = GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, src)
            GLES20.glCompileShader(it)
            val status = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, status, 0)
            if (status[0] == 0) Log.e(TAG, "Shader compile error: ${GLES20.glGetShaderInfoLog(it)}")
        }
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, compile(GLES20.GL_VERTEX_SHADER, vertSrc))
            GLES20.glAttachShader(it, compile(GLES20.GL_FRAGMENT_SHADER, fragSrc))
            GLES20.glLinkProgram(it)
        }
    }

    private fun floatBuf(arr: FloatArray) =
        ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
            .apply { put(arr); rewind() }
}
