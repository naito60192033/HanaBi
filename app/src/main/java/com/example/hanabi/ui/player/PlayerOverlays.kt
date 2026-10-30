@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.*
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.player.Chapter
import kotlinx.coroutines.delay

internal enum class SeekDirection { FORWARD, BACKWARD }

/**
 * シークイベント。
 * totalMs: 連続操作で加算された累積シーク量（表示用）
 * id: 同方向の連続押しでも LaunchedEffect を再起動させるための一意キー
 */
internal data class SeekEvent(val direction: SeekDirection, val totalMs: Long, val id: Long)

/** チャプタージャンプ表示イベント */
internal data class ChapterJumpEvent(
    val forward: Boolean,
    val isChapter: Boolean,
    val chapterName: String?,
    val id: Long = System.currentTimeMillis()
)

/** 再生/一時停止トグル表示イベント */
internal data class PlayPauseEvent(
    val isPlaying: Boolean,
    val id: Long = System.currentTimeMillis()
)

/**
 * 連続スキップ時の累積量を管理するクラス。
 */
internal class SeekAccumulator {
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
/** YouTube風プレイヤーコントロールオーバーレイ */
@Composable
internal fun PlayerControlsOverlay(
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

/** 再生/一時停止トグルインジケーター（中央に円形オーバーレイ） */
@Composable
internal fun PlayPauseIndicator(event: PlayPauseEvent, onDismiss: () -> Unit) {
    LaunchedEffect(event.id) {
        delay(700)
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .background(Color.Black.copy(alpha = 0.55f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (event.isPlaying) "▶" else "❚❚",
                fontSize = 40.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/** チャプタージャンプインジケーター */
@Composable
internal fun ChapterJumpIndicator(event: ChapterJumpEvent, onDismiss: () -> Unit) {
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
internal fun SeekIndicator(seekEvent: SeekEvent, onDismiss: () -> Unit) {
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
internal fun ResumeDialog(
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

/** 再生エラーダイアログ（ResumeDialog と同じ見た目・フォーカス方式） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
internal fun PlayerErrorDialog(
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit
) {
    val retryFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { retryFocusRequester.requestFocus() }

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
                text = message,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocusRequester)
                ) {
                    Text("再試行")
                }
                OutlinedButton(onClick = onBack) {
                    Text("戻る")
                }
            }
        }
    }
}

/** ミリ秒を MM:SS 形式にフォーマット */
internal fun formatTime(ms: Long): String {
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
