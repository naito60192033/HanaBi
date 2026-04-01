@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.ui.player

import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.compose.material3.CircularProgressIndicator
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.*
import com.example.hanabi.MainActivity
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

private enum class SeekDirection { FORWARD, BACKWARD }

/**
 * シークイベント。
 * totalMs: 連続操作で加算された累積シーク量（表示用）
 * id: 同方向の連続押しでも LaunchedEffect を再起動させるための一意キー
 */
private data class SeekEvent(val direction: SeekDirection, val totalMs: Long, val id: Long)

/**
 * 連続スキップ時の累積量を管理するクラス。
 * Compose の状態ではなく通常クラスとして保持することで、
 * リコンポーズ前の複数回の急速押しでも正確に加算できる。
 */
private class SeekAccumulator {
    var totalMs: Long = 0L
    var direction: SeekDirection? = null

    /** 同方向なら加算、逆方向なら0からリセットして加算。累積量を返す。 */
    fun accumulate(dir: SeekDirection, amount: Long): Long {
        if (direction != dir) totalMs = 0L
        direction = dir
        totalMs += amount
        return totalMs
    }

    fun reset() {
        totalMs = 0L
        direction = null
    }
}

/** 動画プレイヤー画面 */
@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    smbPath: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val savedProgress by viewModel.savedProgress.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    var isReady by remember { mutableStateOf(false) }
    val seekEvent = remember { mutableStateOf<SeekEvent?>(null) }
    val accumulator = remember { SeekAccumulator() }
    // showController() 呼び出しのために PlayerView の参照を保持
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    // スキップ中だけ中央の再生/一時停止ボタンを隠すための参照
    val playPauseBtnRef = remember { mutableStateOf<View?>(null) }

    // ---------------------------------------------------------------
    // Activity 最上位でキーを捕捉（PlayerView / ExoPlayer より前に処理）
    // 左右もここで処理することで、showController() 後にコントローラーの
    // StyledPlayerControlView がフォーカスを奪ってもスキップが確実に届く。
    // ---------------------------------------------------------------
    DisposableEffect(Unit) {
        MainActivity.playerKeyHandler = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        viewModel.seekForward()
                        val total = accumulator.accumulate(SeekDirection.FORWARD, 30_000)
                        seekEvent.value = SeekEvent(
                            direction = SeekDirection.FORWARD,
                            totalMs = total,
                            id = System.currentTimeMillis()
                        )
                        // タイムバーを表示しつつ中央の再生/一時停止ボタンだけ隠す
                        playPauseBtnRef.value?.visibility = View.INVISIBLE
                        playerViewRef.value?.showController()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        viewModel.seekBackward()
                        val total = accumulator.accumulate(SeekDirection.BACKWARD, 10_000)
                        seekEvent.value = SeekEvent(
                            direction = SeekDirection.BACKWARD,
                            totalMs = total,
                            id = System.currentTimeMillis()
                        )
                        // タイムバーを表示しつつ中央の再生/一時停止ボタンだけ隠す
                        playPauseBtnRef.value?.visibility = View.INVISIBLE
                        playerViewRef.value?.showController()
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        val isNowPlaying = viewModel.togglePlayPause()
                        playPauseBtnRef.value?.visibility = View.VISIBLE
                        if (isNowPlaying) {
                            // 再生再開時はコントローラーをすぐ非表示にして映像を見せる
                            playerViewRef.value?.hideController()
                        } else {
                            // 一時停止時はコントローラーを表示
                            playerViewRef.value?.showController()
                        }
                        true
                    }
                    else -> false
                }
            } else false
        }
        onDispose {
            MainActivity.playerKeyHandler = null
        }
    }

    LaunchedEffect(smbPath) {
        viewModel.prepare(smbPath)
    }

    // 保存済み位置が確認できたら準備完了
    LaunchedEffect(savedProgress) {
        if (savedProgress != null || !isReady) {
            isReady = true
        }
    }

    BackHandler {
        viewModel.saveProgress()
        onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (!isReady) {
            // 読み込み中インジケーター
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else if (savedProgress != null && !savedProgress!!.isFinished && savedProgress!!.positionMs > 30_000) {
            // 続きから再生するか確認するダイアログ
            ResumeDialog(
                progress = savedProgress!!,
                onResume = { viewModel.resumePlayback() },
                onPlayFromBeginning = { viewModel.playFromBeginning() }
            )
        } else {
            // 再生開始
            LaunchedEffect(Unit) {
                viewModel.playFromBeginning()
            }
        }

        // ExoPlayer ビュー
        // キー処理はすべて Activity ハンドラーに委譲しているので、
        // ここでは標準の PlayerView をそのまま使う。
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = true
                    // スキップ時のバッファリングで自動表示されないよう false に設定。
                    // 一時停止・再生時は Activity ハンドラーから明示的に showController() を呼ぶ。
                    controllerAutoShow = false
                    controllerShowTimeoutMs = 3000
                    // 不要なコントローラーボタンを非表示
                    setShowRewindButton(false)
                    setShowFastForwardButton(false)
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                    setShowShuffleButton(false)
                }.also {
                    playerViewRef.value = it
                    // ExoPlayer コントローラー内の再生/一時停止ボタン参照を保持
                    playPauseBtnRef.value = it.findViewById(androidx.media3.ui.R.id.exo_play_pause)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // バッファリング中のスピナーオーバーレイ
        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // シークインジケーターオーバーレイ（左右に累積秒数を表示）
        val currentSeekEvent = seekEvent.value
        AnimatedVisibility(
            visible = currentSeekEvent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            currentSeekEvent?.let { event ->
                SeekIndicator(
                    seekEvent = event,
                    onDismiss = {
                        seekEvent.value = null
                        accumulator.reset()
                    }
                )
            }
        }
    }
}

/** シークインジケーターオーバーレイ（左右に表示、累積秒数を反映） */
@Composable
private fun SeekIndicator(seekEvent: SeekEvent, onDismiss: () -> Unit) {
    // id が変わるたびにタイマーをリセット（連続押しで延長される）
    LaunchedEffect(seekEvent.id) {
        delay(700)
        onDismiss()
    }

    val isForward = seekEvent.direction == SeekDirection.FORWARD
    val seconds = seekEvent.totalMs / 1000

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = if (isForward) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .width(130.dp)
                .fillMaxHeight(0.4f)
                .padding(
                    start = if (!isForward) 32.dp else 0.dp,
                    end = if (isForward) 32.dp else 0.dp
                )
                .background(
                    color = Color.Black.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(20.dp)
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isForward) "▶▶" else "◀◀",
                fontSize = 38.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (isForward) "+${seconds}秒" else "−${seconds}秒",
                fontSize = 18.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** 続きから再生するか確認するダイアログ */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ResumeDialog(
    progress: PlaybackProgress,
    onResume: () -> Unit,
    onPlayFromBeginning: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "前回の続きから再生しますか？",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Text(
                text = "再生位置: ${formatTime(progress.positionMs)} / ${formatTime(progress.durationMs)}",
                color = Color.Gray
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onResume) {
                    Text("続きから再生")
                }
                OutlinedButton(onClick = onPlayFromBeginning) {
                    Text("最初から再生")
                }
            }
        }
    }
}

/** ミリ秒を MM:SS 形式にフォーマット */
private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
