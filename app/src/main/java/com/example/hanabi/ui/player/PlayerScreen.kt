@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.ui.player

import com.example.hanabi.ui.theme.HanaBiColors
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import com.example.hanabi.ui.ActivityKeyHandler
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.viewmodel.ChapterJumpInfo
import com.example.hanabi.viewmodel.PlayerViewModel
import kotlinx.coroutines.delay


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
    val playerError by viewModel.playerError.collectAsState()

    // バックグラウンド遷移（ON_STOP）で一時停止＋保存。ON_START で自動再開はしない
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackground()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

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
    val playPauseEvent = remember { mutableStateOf<PlayPauseEvent?>(null) }
    val playPauseVisible = remember { mutableStateOf(false) }

    // コントローラー表示管理
    val showController = remember { mutableStateOf(false) }
    val lastKeyPressTime = remember { mutableLongStateOf(0L) }

    // 再生開始済みフラグ
    var playbackInitiated by remember { mutableStateOf(false) }

    // 再開ダイアログ・エラーダイアログ・次エピソードバナーの表示有無（キー判定・BackHandler で参照）
    val shouldShowResumeDialog = isPrepared && savedProgress != null &&
        !savedProgress!!.isFinished && savedProgress!!.positionMs > 30_000
    val showErrorDialog = playerError != null

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

    // 再生/一時停止トグル時に中央オーバーレイを一瞬表示（YouTube 風）
    var lastPlayingTracked by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(isActuallyPlaying, playbackInitiated) {
        if (!playbackInitiated) return@LaunchedEffect
        val prev = lastPlayingTracked
        lastPlayingTracked = isActuallyPlaying
        if (prev == null || prev == isActuallyPlaying) return@LaunchedEffect
        if (shouldShowResumeDialog || showNextEpisodeBanner) return@LaunchedEffect
        playPauseEvent.value = PlayPauseEvent(isActuallyPlaying)
        playPauseVisible.value = true
    }

    // ---------------------------------------------------------------
    // Activity 最上位でキーを捕捉
    // ---------------------------------------------------------------
    // 左右キー・早送り/巻き戻しキー共通のシーク処理
    fun doSeek(direction: SeekDirection) {
        val forward = direction == SeekDirection.FORWARD
        if (forward) viewModel.seekForward() else viewModel.seekBackward()
        val total = accumulator.accumulate(
            direction,
            if (forward) viewModel.seekForwardMs else viewModel.seekBackwardMs
        )
        seekEvent.value = SeekEvent(
            direction = direction,
            totalMs = total,
            id = System.currentTimeMillis()
        )
    }
    fun doChapterJump(forward: Boolean) {
        val info: ChapterJumpInfo = viewModel.seekToChapterOrSkip(forward)
        chapterJumpEvent.value = ChapterJumpEvent(
            forward = forward,
            isChapter = info.isChapter,
            chapterName = info.chapterName,
            id = System.currentTimeMillis()
        )
    }
    // ActivityKeyHandler は常に最新のラムダを呼ぶので、ここから最新の状態を直接読める。
    // 自動次再生の popUpTo で新旧画面の登録・解除順が前後しても、レジストリが identity で解除する。
    ActivityKeyHandler { event ->
        if (event.action != KeyEvent.ACTION_DOWN) return@ActivityKeyHandler false
        val action = playerKeyAction(
            event.keyCode,
            PlayerKeyState(
                showResumeDialog = shouldShowResumeDialog,
                showErrorDialog = showErrorDialog,
                showNextEpisodeBanner = showNextEpisodeBanner,
                showSettingsOverlay = showSettingsOverlay.value
            )
        ) ?: return@ActivityKeyHandler false
        when (action) {
            PlayerKeyAction.SeekForward -> doSeek(SeekDirection.FORWARD)
            PlayerKeyAction.SeekBackward -> doSeek(SeekDirection.BACKWARD)
            PlayerKeyAction.Play -> viewModel.play()
            PlayerKeyAction.Pause -> viewModel.pause()
            PlayerKeyAction.TogglePlayPause -> viewModel.togglePlayPause()
            PlayerKeyAction.OpenSettings -> showSettingsOverlay.value = true
            PlayerKeyAction.CloseSettings -> showSettingsOverlay.value = false
            PlayerKeyAction.NextChapter -> doChapterJump(true)
            PlayerKeyAction.PreviousChapter -> doChapterJump(false)
        }
        // 自分が処理したキーのときだけコントローラーを表示する。
        // BACK 等のここで処理しないキーで showController を true にすると、
        // BackHandler 側の判定（コントローラー表示中なら閉じるだけ）と競合して
        // 「再生中の戻るボタンで前画面に戻れない」現象になる。
        // 設定オーバーレイを閉じるキー（CloseSettings）では従来どおり表示しない。
        if (action != PlayerKeyAction.CloseSettings) {
            lastKeyPressTime.longValue = System.currentTimeMillis()
            showController.value = true
        }
        true
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
        when {
            showErrorDialog -> {
                // エラー表示中は「戻る」と同じ動作
                onBack()
            }
            showSettingsOverlay.value -> showSettingsOverlay.value = false
            showNextEpisodeBanner -> viewModel.dismissNextEpisodeBanner()
            showController.value && isActuallyPlaying -> showController.value = false
            else -> {
                viewModel.saveProgress()
                onBack()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HanaBiColors.ScreenBackground)
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
        if (playerError == null && (!isPrepared || isBuffering)) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        // isPrepared になったら再生開始判断
        // エラー表示中は再開ダイアログを出さない（再試行で準備し直してから判断する）
        if (isPrepared && !playbackInitiated && playerError == null) {
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

        // 再生エラーダイアログ
        playerError?.let { message ->
            PlayerErrorDialog(
                message = message,
                // 再生開始前のエラーなら準備だけやり直し、続きから/最初からの判断に戻す
                onRetry = { viewModel.retryPlayback(resume = playbackInitiated) },
                onBack = onBack
            )
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

        // 再生/一時停止トグルインジケーター
        AnimatedVisibility(
            visible = playPauseVisible.value,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            playPauseEvent.value?.let { event ->
                PlayPauseIndicator(
                    event = event,
                    onDismiss = { playPauseVisible.value = false }
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
