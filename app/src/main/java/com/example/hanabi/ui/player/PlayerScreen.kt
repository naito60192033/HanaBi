@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.ui.player

import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.util.UnstableApi
import com.example.hanabi.player.Chapter
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.example.hanabi.MainActivity
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.viewmodel.ChapterJumpInfo
import com.example.hanabi.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay

private enum class SeekDirection { FORWARD, BACKWARD }

/**
 * シークイベント。
 * totalMs: 連続操作で加算された累積シーク量（表示用）
 * id: 同方向の連続押しでも LaunchedEffect を再起動させるための一意キー
 */
private data class SeekEvent(val direction: SeekDirection, val totalMs: Long, val id: Long)

/** チャプタージャンプ表示イベント */
private data class ChapterJumpEvent(
    val forward: Boolean,
    val isChapter: Boolean,
    val chapterName: String?,
    val id: Long = System.currentTimeMillis()
)

/**
 * 連続スキップ時の累積量を管理するクラス。
 */
private class SeekAccumulator {
    var totalMs: Long = 0L
    var direction: SeekDirection? = null

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
    val currentPositionMs by viewModel.currentPositionMs.collectAsState()
    val durationMs by viewModel.durationMs.collectAsState()
    val videoTitle by viewModel.videoTitle.collectAsState()
    val chapters by viewModel.chapters.collectAsState()

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
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val seekEvent = remember { mutableStateOf<SeekEvent?>(null) }
    val accumulator = remember { SeekAccumulator() }
    val showSettingsOverlay = remember { mutableStateOf(false) }
    val chapterJumpEvent = remember { mutableStateOf<ChapterJumpEvent?>(null) }

    // コントローラー表示管理
    val showController = remember { mutableStateOf(false) }
    val lastKeyPressTime = remember { mutableLongStateOf(0L) }

    // 再生開始済みフラグ
    var playbackInitiated by remember { mutableStateOf(false) }

    // 再開ダイアログ表示中かを playerKeyHandler から参照するための状態
    val showResumeDialog = remember { mutableStateOf(false) }
    val shouldShowResumeDialog = isPrepared && savedProgress != null &&
        !savedProgress!!.isFinished && savedProgress!!.positionMs > 30_000
    SideEffect { showResumeDialog.value = shouldShowResumeDialog }

    // コントローラー自動非表示（再生中は3秒後に隠す）
    LaunchedEffect(lastKeyPressTime.longValue) {
        if (lastKeyPressTime.longValue > 0L) {
            showController.value = true
            delay(3000)
            if (isActuallyPlaying) showController.value = false
        }
    }
    // 一時停止中はコントローラーを常時表示
    LaunchedEffect(isActuallyPlaying) {
        if (!isActuallyPlaying && isPrepared && playbackInitiated) {
            showController.value = true
        }
    }

    // ---------------------------------------------------------------
    // Activity 最上位でキーを捕捉
    // ---------------------------------------------------------------
    DisposableEffect(Unit) {
        MainActivity.playerKeyHandler = { event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when {
                    showResumeDialog.value -> false
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
                        // キー操作でコントローラーを表示
                        lastKeyPressTime.longValue = System.currentTimeMillis()
                        showController.value = true

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
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                viewModel.togglePlayPause()
                                true
                            }
                            KeyEvent.KEYCODE_MENU -> {
                                showSettingsOverlay.value = true
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_UP -> {
                                val info: ChapterJumpInfo = viewModel.seekToChapterOrSkip(true)
                                chapterJumpEvent.value = ChapterJumpEvent(
                                    forward = true,
                                    isChapter = info.isChapter,
                                    chapterName = info.chapterName,
                                    id = System.currentTimeMillis()
                                )
                                true
                            }
                            KeyEvent.KEYCODE_DPAD_DOWN -> {
                                val info: ChapterJumpInfo = viewModel.seekToChapterOrSkip(false)
                                chapterJumpEvent.value = ChapterJumpEvent(
                                    forward = false,
                                    isChapter = info.isChapter,
                                    chapterName = info.chapterName,
                                    id = System.currentTimeMillis()
                                )
                                true
                            }
                            else -> false
                        }
                    }
                }
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
        // PlayerView（コントローラーは使用しない）
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // 準備中 or バッファリング中のスピナー
        if (!isPrepared || isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // isPrepared になったら再生開始判断
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

        // カスタムプレイヤーコントロールオーバーレイ（YouTube風）
        if (playbackInitiated && !shouldShowResumeDialog && !showNextEpisodeBanner) {
            PlayerControlsOverlay(
                visible = showController.value && !showSettingsOverlay.value,
                title = videoTitle,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                chapters = chapters,
                playbackSpeed = playbackSpeed,
            )
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

        // チャプタージャンプインジケーター
        val currentChapterJumpEvent = chapterJumpEvent.value
        AnimatedVisibility(
            visible = currentChapterJumpEvent != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            currentChapterJumpEvent?.let { event ->
                ChapterJumpIndicator(
                    event = event,
                    onDismiss = { chapterJumpEvent.value = null }
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

/** YouTube風プレイヤーコントロールオーバーレイ */
@Composable
private fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    currentPositionMs: Long,
    durationMs: Long,
    chapters: List<Chapter>,
    playbackSpeed: Float,
) {
    val currentChapterName = remember(chapters, currentPositionMs) {
        chapters.lastOrNull { it.positionMs <= currentPositionMs }?.title
    }
    val progress = if (durationMs > 0) (currentPositionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(250)),
        exit = fadeOut(animationSpec = tween(300))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 上部: タイトル + チャプター名
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 32.dp, vertical = 24.dp)
            ) {
                Column {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentChapterName != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = currentChapterName,
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 下部: プログレスバー + 時間表示
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                        )
                    )
                    .padding(horizontal = 32.dp)
                    .padding(top = 48.dp, bottom = 28.dp)
            ) {
                // プログレスバー（Canvas）
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(12.dp)
                ) {
                    val barH = 4.dp.toPx()
                    val barTop = (size.height - barH) / 2

                    // トラック背景
                    drawRect(
                        color = Color.White.copy(alpha = 0.3f),
                        topLeft = Offset(0f, barTop),
                        size = size.copy(height = barH)
                    )
                    // 再生済み部分（YouTube風レッド）
                    if (progress > 0f) {
                        drawRect(
                            color = Color(0xFFFF0000),
                            topLeft = Offset(0f, barTop),
                            size = size.copy(width = size.width * progress, height = barH)
                        )
                    }
                    // チャプターマーカー（縦線）
                    if (durationMs > 0) {
                        val markerW = 2.dp.toPx()
                        for (chapter in chapters) {
                            if (chapter.positionMs <= 0 || chapter.positionMs >= durationMs) continue
                            val x = size.width * (chapter.positionMs.toFloat() / durationMs)
                            drawRect(
                                color = Color.Black.copy(alpha = 0.9f),
                                topLeft = Offset(x - markerW / 2, barTop),
                                size = size.copy(width = markerW, height = barH)
                            )
                        }
                    }
                    // サム（白丸）
                    if (progress > 0f && progress < 1f) {
                        drawCircle(
                            color = Color.White,
                            radius = 7.dp.toPx(),
                            center = Offset(size.width * progress, size.height / 2)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(currentPositionMs),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (playbackSpeed != 1.0f) {
                            Text(
                                text = "${playbackSpeed}x".replace(".0x", "x"),
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = formatTime(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/** チャプタージャンプインジケーター */
@Composable
private fun ChapterJumpIndicator(event: ChapterJumpEvent, onDismiss: () -> Unit) {
    LaunchedEffect(event.id) {
        delay(1200)
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(14.dp))
                .padding(horizontal = 28.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (event.forward) "▲" else "▼",
                fontSize = 26.sp,
                color = Color.White
            )
            Text(
                text = when {
                    event.isChapter && !event.chapterName.isNullOrBlank() -> event.chapterName
                    event.isChapter -> if (event.forward) "次のチャプター" else "前のチャプター"
                    else -> if (event.forward) "+10分" else "−10分"
                },
                fontSize = 16.sp,
                color = Color.White,
                fontWeight = FontWeight.Medium
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
    val resumeFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { resumeFocusRequester.requestFocus() }

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
                    modifier = Modifier.focusRequester(resumeFocusRequester)
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
