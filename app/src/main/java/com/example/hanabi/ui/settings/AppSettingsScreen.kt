package com.example.hanabi.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.hanabi.viewmodel.SettingsViewModel

private val SPEED_OPTIONS = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val SEEK_FORWARD_OPTIONS = listOf(10, 15, 30, 60)
private val SEEK_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)

/** アプリ再生設定画面 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val defaultSpeed by viewModel.defaultPlaybackSpeed.collectAsState()
    val seekForwardSec by viewModel.seekForwardSec.collectAsState()
    val seekBackwardSec by viewModel.seekBackwardSec.collectAsState()
    val cacheSizeBytes by viewModel.thumbnailCacheSizeBytes.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    var cacheClearDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "再生設定",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 次エピソード自動再生
            SectionLabel("次エピソード自動再生")
            ToggleRow(
                options = listOf("OFF", "ON"),
                selectedIndex = if (autoPlayNext) 1 else 0,
                onSelect = { viewModel.setAutoPlayNext(it == 1) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // デフォルト再生速度
            SectionLabel("デフォルト再生速度")
            OptionRow(
                options = SPEED_OPTIONS.map { "${it}x".replace(".0x", "x") },
                selectedIndex = SPEED_OPTIONS.indexOfFirst { it == defaultSpeed }.coerceAtLeast(0),
                onSelect = { viewModel.setDefaultPlaybackSpeed(SPEED_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 早送り秒数
            SectionLabel("早送り秒数")
            OptionRow(
                options = SEEK_FORWARD_OPTIONS.map { "${it}秒" },
                selectedIndex = SEEK_FORWARD_OPTIONS.indexOfFirst { it == seekForwardSec }.coerceAtLeast(0),
                onSelect = { viewModel.setSeekForwardSec(SEEK_FORWARD_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 巻き戻し秒数
            SectionLabel("巻き戻し秒数")
            OptionRow(
                options = SEEK_BACKWARD_OPTIONS.map { "${it}秒" },
                selectedIndex = SEEK_BACKWARD_OPTIONS.indexOfFirst { it == seekBackwardSec }.coerceAtLeast(0),
                onSelect = { viewModel.setSeekBackwardSec(SEEK_BACKWARD_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // サムネイルキャッシュ
            SectionLabel("サムネイルキャッシュ")
            Text(
                text = "現在のサイズ: ${formatBytes(cacheSizeBytes)}",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (cacheClearDone) {
                Text(
                    text = "クリアしました",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Button(
                    onClick = {
                        viewModel.clearThumbnailCache()
                        cacheClearDone = true
                    },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFFB71C1C),
                    )
                ) {
                    Text("キャッシュをクリア", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 再生履歴リセット
            SectionLabel("再生履歴")
            if (resetDone) {
                Text(
                    text = "リセットしました",
                    color = Color(0xFF4CAF50),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFFB71C1C),
                    )
                ) {
                    Text("再生履歴をリセット", color = Color.White)
                }
            }
        }

        // リセット確認ダイアログ
        if (showResetConfirm) {
            ConfirmResetDialog(
                onConfirm = {
                    showResetConfirm = false
                    viewModel.resetPlaybackHistory {
                        resetDone = true
                    }
                },
                onDismiss = { showResetConfirm = false }
            )
        }
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "${bytes} B"
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.Gray,
        fontSize = 13.sp,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ToggleRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Button(
                onClick = { onSelect(index) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFF2A2A2A),
                    focusedContainerColor = if (isSelected) Color(0xFF1976D2) else Color(0xFF444444),
                )
            ) {
                Text(label, color = Color.White, fontSize = 16.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun OptionRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Button(
                onClick = { onSelect(index) },
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFF2A2A2A),
                    focusedContainerColor = if (isSelected) Color(0xFF1976D2) else Color(0xFF444444),
                )
            ) {
                Text(label, color = Color.White, fontSize = 14.sp)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun ConfirmResetDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
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
            modifier = Modifier
                .background(Color(0xFF1E1E1E), shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "再生履歴をリセットしますか？",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "「続きから再生」の記録がすべて削除されます。",
                color = Color.Gray,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF555555),
                    )
                ) {
                    Text("キャンセル", color = Color.White)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFFB71C1C),
                        focusedContainerColor = Color(0xFFC62828),
                    )
                ) {
                    Text("リセット", color = Color.White)
                }
            }
        }
    }
}
