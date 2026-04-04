@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.ui.player

import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
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
    onNavigateToPlayer: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val isPrepared by viewModel.isPrepared.collectAsState()
    val savedProgress by viewModel.savedProgress.collectAsState()
    val isBuffering by viewModel.isBuffering.collectAsState()
    val isActuallyPlaying by viewModel.isActuallyPlaying.collectAsState()
    val playbackSpeed by viewModel.playbackSpeed.collectAsState()
    val audioTracks by viewModel.audioTracks.collectAsState()
    val subtitleTracks by viewModel.subtitleTracks.collectAsState()
    val selectedAudioIndex by viewModel.selectedAudioIndex.collectAsState()
    val selectedSubtitleIndex by viewModel.selectedSubtitleIndex.collectAsState()
    val audioDelayMs by viewModel.audioDelayMs.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val nextEpisode by viewModel.nextEpisode.collectAsState()
    val showNextEpisodeBanner by viewModel.showNextEpisodeBanner.collectAsState()

    // 再生中のみ画面を常時点灯（スクリーンセーバー防止）
    val activity = LocalContext.current as? MainActivity
    LaunchedEffect(isActuallyPlaying) {
        val window = activity?.window ?: return@LaunchedEffect
        if (isActuallyPlaying) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // 画面離脱時に必ずフラグを解除する
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val seekEvent = remember { mutableStateOf<SeekEvent?>(null) }
    val accumulator = remember { SeekAccumulator() }
    val showSettingsOverlay = remember { mutableStateOf(false) }

    // 再生開始済みフラグ（ダイアログ選択後に LaunchedEffect(Unit) が再実行されるのを防ぐ）
    var playbackInitiated by remember { mutableStateOf(false) }

    // showController() 呼び出しのために PlayerView の参照を保持
    val playerViewRef = remember { mutableStateOf<PlayerView?>(null) }
    // スキップ中だけ中央の再生/一時停止ボタンを隠すための参照
    val playPauseBtnRef = remember { mutableStateOf<View?>(null) }

    // 再開ダイアログ表示中かを playerKeyHandler から参照するための状態
    val showResumeDialog = remember { mutableStateOf(false) }
    val shouldShowResumeDialog = isPrepared && savedProgress != null &&
        !savedProgress!!.isFinished && savedProgress!!.positionMs > 30_000
    SideEffect { showResumeDialog.value = shouldShowResumeDialog }

    // ---------------------------------------------------------------
    // Activity 最上位でキーを捕捉（PlayerView / ExoPlayer より前に処理）
    // 左右もここで処理することで、showController() 後にコントローラーの
    // StyledPlayerControlView がフォーカスを奪ってもスキップが確実に届く。
    // ---------------------------------------------------------------
    DisposableEffect(Unit) {
        MainActivity.playerKeyHandler = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                // 再開ダイアログ表示中は全キーをComposeに委譲（ボタンフォーカスで処理）
                showResumeDialog.value -> false
                // 設定オーバーレイ表示中は MENU/BACK のみ処理、他はオーバーレイに委譲
                showSettingsOverlay.value -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_MENU -> {
                            showSettingsOverlay.value = false
                            true
                        }
                        else -> false
                    }
                }
                else -> {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            viewModel.seekForward()
                            val total = accumulator.accumulate(
                                SeekDirection.FORWARD, viewModel.seekForwardMs
                            )
                            seekEvent.value = SeekEvent(
                                direction = SeekDirection.FORWARD,
                                totalMs = total,
                                id = System.currentTimeMillis()
                            )
                            playPauseBtnRef.value?.visibility = View.INVISIBLE
                            playerViewRef.value?.showController()
                            true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            viewModel.seekBackward()
                            val total = accumulator.accumulate(
                                SeekDirection.BACKWARD, viewModel.seekBackwardMs
                            )
                            seekEvent.value = SeekEvent(
                                direction = SeekDirection.BACKWARD,
                                totalMs = total,
                                id = System.currentTimeMillis()
                            )
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
                                playerViewRef.value?.hideController()
                            } else {
                                playerViewRef.value?.showController()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            showSettingsOverlay.value = true
                            true
                        }
                        else -> false
                    }
                }
                } // when
            } else false
        }
        onDispose {
            MainActivity.playerKeyHandler = null
        }
    }

    // 次エピソード自動遷移イベントを受信
    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect { nextPath ->
            onNavigateToPlayer(nextPath)
        }
    }

    LaunchedEffect(smbPath) {
        playbackInitiated = false
        viewModel.prepare(smbPath)
    }

    BackHandler {
        if (showSettingsOverlay.value) {
            showSettingsOverlay.value = false
        } else {
            viewModel.saveProgress()
            onBack()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // PlayerView は最初に配置（Boxの最背面）
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = true
                    controllerAutoShow = false
                    controllerShowTimeoutMs = 3000
                    setShowRewindButton(false)
                    setShowFastForwardButton(false)
                    setShowPreviousButton(false)
                    setShowNextButton(false)
                    setShowShuffleButton(false)
                }.also {
                    playerViewRef.value = it
                    playPauseBtnRef.value = it.findViewById(androidx.media3.ui.R.id.exo_play_pause)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 準備中 or バッファリング中のスピナー
        if (!isPrepared || isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // isPrepared になったら再生開始判断（playbackInitiated で二重実行を防ぐ）
        if (isPrepared && !playbackInitiated) {
            if (savedProgress != null && !savedProgress!!.isFinished && savedProgress!!.positionMs > 30_000) {
                ResumeDialog(
                    progress = savedProgress!!,
                    onResume = {
                        playbackInitiated = true
                        viewModel.resumePlayback()
                    },
                    onPlayFromBeginning = {
                        playbackInitiated = true
                        viewModel.playFromBeginning()
                    }
                )
            } else {
                LaunchedEffect(Unit) {
                    playbackInitiated = true
                    viewModel.playFromBeginning()
                }
            }
        }

        // シークインジケーターオーバーレイ
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

        // 再生設定オーバーレイ
        PlaybackSettingsOverlay(
            visible = showSettingsOverlay.value,
            playbackSpeed = playbackSpeed,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks,
            selectedAudioIndex = selectedAudioIndex,
            selectedSubtitleIndex = selectedSubtitleIndex,
            audioDelayMs = audioDelayMs,
            autoPlayNext = autoPlayNext,
            onSpeedChange = viewModel::setPlaybackSpeed,
            onAudioTrackSelect = viewModel::selectAudioTrack,
            onSubtitleTrackSelect = viewModel::selectSubtitleTrack,
            onAudioDelayChange = viewModel::setAudioDelay,
            onAutoPlayNextChange = viewModel::setAutoPlayNext,
            onClose = { showSettingsOverlay.value = false }
        )

        // 次エピソードバナー（autoPlayNext オフ時）
        if (showNextEpisodeBanner && nextEpisode != null) {
            NextEpisodeBanner(
                episodeName = nextEpisode!!.name,
                onPlay = { onNavigateToPlayer(nextEpisode!!.path) },
                onDismiss = viewModel::dismissNextEpisodeBanner
            )
        }
    }
}

/** シークインジケーターオーバーレイ（左右に表示、累積秒数を反映） */
@Composable
private fun SeekIndicator(seekEvent: SeekEvent, onDismiss: () -> Unit) {
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
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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
                Button(
                    onClick = onResume,
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
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
