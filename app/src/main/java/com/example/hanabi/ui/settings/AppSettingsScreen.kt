package com.example.hanabi.ui.settings

import com.example.hanabi.data.PLAYBACK_SPEED_OPTIONS
import com.example.hanabi.ui.theme.HanaBiColors
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
import androidx.compose.ui.res.stringResource
import com.example.hanabi.R
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

private val SEEK_FORWARD_OPTIONS = listOf(10, 15, 30, 60)
private val SEEK_BACKWARD_OPTIONS = listOf(5, 10, 15, 30)
private val GRID_COLUMN_OPTIONS = listOf(4, 5, 6)

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
    val gridColumns by viewModel.gridColumns.collectAsState()
    val seekBackwardSec by viewModel.seekBackwardSec.collectAsState()
    val cacheSizeBytes by viewModel.thumbnailCacheSizeBytes.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }
    var resetDone by remember { mutableStateOf(false) }
    var cacheClearDone by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refreshCacheSize() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HanaBiColors.MenuBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.app_title),
                color = HanaBiColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // 次エピソード自動再生
            SectionLabel(stringResource(R.string.app_auto_next))
            ToggleRow(
                options = listOf("OFF", "ON"),
                selectedIndex = if (autoPlayNext) 1 else 0,
                onSelect = { viewModel.setAutoPlayNext(it == 1) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // デフォルト再生速度
            SectionLabel(stringResource(R.string.app_default_speed))
            OptionRow(
                options = PLAYBACK_SPEED_OPTIONS.map { "${it}x".replace(".0x", "x") },
                selectedIndex = PLAYBACK_SPEED_OPTIONS.indexOfFirst { it == defaultSpeed }.coerceAtLeast(0),
                onSelect = { viewModel.setDefaultPlaybackSpeed(PLAYBACK_SPEED_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 早送り秒数
            SectionLabel(stringResource(R.string.app_seek_forward))
            OptionRow(
                options = SEEK_FORWARD_OPTIONS.map { stringResource(R.string.app_seconds_option, it) },
                selectedIndex = SEEK_FORWARD_OPTIONS.indexOfFirst { it == seekForwardSec }.coerceAtLeast(0),
                onSelect = { viewModel.setSeekForwardSec(SEEK_FORWARD_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 巻き戻し秒数
            SectionLabel(stringResource(R.string.app_seek_backward))
            OptionRow(
                options = SEEK_BACKWARD_OPTIONS.map { stringResource(R.string.app_seconds_option, it) },
                selectedIndex = SEEK_BACKWARD_OPTIONS.indexOfFirst { it == seekBackwardSec }.coerceAtLeast(0),
                onSelect = { viewModel.setSeekBackwardSec(SEEK_BACKWARD_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // 一覧の列数
            SectionLabel(stringResource(R.string.app_grid_columns))
            OptionRow(
                options = GRID_COLUMN_OPTIONS.map { stringResource(R.string.app_columns_option, it) },
                selectedIndex = GRID_COLUMN_OPTIONS.indexOfFirst { it == gridColumns }.coerceAtLeast(0),
                onSelect = { viewModel.setGridColumns(GRID_COLUMN_OPTIONS[it]) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // サムネイルキャッシュ
            SectionLabel(stringResource(R.string.app_thumb_cache))
            Text(
                text = stringResource(R.string.app_cache_size, formatBytes(cacheSizeBytes)),
                color = HanaBiColors.TextPrimary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            if (cacheClearDone) {
                Text(
                    text = stringResource(R.string.app_cache_cleared),
                    color = HanaBiColors.Success,
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
                        containerColor = HanaBiColors.Surface,
                        focusedContainerColor = HanaBiColors.Destructive,
                    )
                ) {
                    Text(stringResource(R.string.app_clear_cache), color = HanaBiColors.TextPrimary)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 再生履歴リセット
            SectionLabel(stringResource(R.string.app_history))
            if (resetDone) {
                Text(
                    text = stringResource(R.string.app_history_reset_done),
                    color = HanaBiColors.Success,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                Button(
                    onClick = { showResetConfirm = true },
                    colors = ButtonDefaults.colors(
                        containerColor = HanaBiColors.Surface,
                        focusedContainerColor = HanaBiColors.Destructive,
                    )
                ) {
                    Text(stringResource(R.string.app_reset_history), color = HanaBiColors.TextPrimary)
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
        color = HanaBiColors.TextSecondary,
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
                    containerColor = if (isSelected) HanaBiColors.Accent else HanaBiColors.Surface,
                    focusedContainerColor = if (isSelected) HanaBiColors.AccentFocused else HanaBiColors.SurfaceFocused,
                )
            ) {
                Text(label, color = HanaBiColors.TextPrimary, fontSize = 16.sp)
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
                    containerColor = if (isSelected) HanaBiColors.Accent else HanaBiColors.Surface,
                    focusedContainerColor = if (isSelected) HanaBiColors.AccentFocused else HanaBiColors.SurfaceFocused,
                )
            ) {
                Text(label, color = HanaBiColors.TextPrimary, fontSize = 14.sp)
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
            .background(HanaBiColors.Scrim.copy(alpha = 0.7f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .background(HanaBiColors.DialogBackground, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.app_reset_confirm_title),
                color = HanaBiColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.app_reset_confirm_message),
                color = HanaBiColors.TextSecondary,
                fontSize = 14.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.focusRequester(focusRequester),
                    colors = ButtonDefaults.colors(
                        containerColor = HanaBiColors.Surface,
                        focusedContainerColor = HanaBiColors.SurfaceFocused,
                    )
                ) {
                    Text(stringResource(R.string.app_cancel), color = HanaBiColors.TextPrimary)
                }
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.colors(
                        containerColor = HanaBiColors.Destructive,
                        focusedContainerColor = HanaBiColors.DestructiveFocused,
                    )
                ) {
                    Text(stringResource(R.string.app_reset), color = HanaBiColors.TextPrimary)
                }
            }
        }
    }
}
