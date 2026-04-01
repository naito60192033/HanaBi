package com.example.hanabi.ui.player

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView
import androidx.tv.material3.*
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.viewmodel.PlayerViewModel

/** 動画プレイヤー画面 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    smbPath: String,
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val savedProgress by viewModel.savedProgress.collectAsState()
    var isReady by remember { mutableStateOf(false) }

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

        // ExoPlayer ビュー（Fire TVリモコンに対応したPlayerView）
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.player
                    // Fire TVリモコンのD-padに合わせたコントローラー設定
                    useController = true
                    controllerAutoShow = true
                    controllerShowTimeoutMs = 3000
                }
            },
            modifier = Modifier.fillMaxSize()
        )
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
