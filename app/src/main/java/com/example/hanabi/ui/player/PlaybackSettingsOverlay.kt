package com.example.hanabi.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Text
import com.example.hanabi.viewmodel.TrackInfo

private val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
private val DELAY_STEP_MS = 50L

/** 再生設定サイドパネルオーバーレイ */
@Composable
fun PlaybackSettingsOverlay(
    visible: Boolean,
    playbackSpeed: Float,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    selectedAudioIndex: Int,
    selectedSubtitleIndex: Int,
    audioDelayMs: Long,
    autoPlayNext: Boolean,
    onSpeedChange: (Float) -> Unit,
    onAudioTrackSelect: (Int) -> Unit,
    onSubtitleTrackSelect: (Int) -> Unit,
    onAudioDelayChange: (Long) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally { it },
        exit = slideOutHorizontally { it },
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            // 背景タップ領域（パネル外を押したら閉じる）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
            )
            // 設定パネル本体
            SettingsPanel(
                playbackSpeed = playbackSpeed,
                audioTracks = audioTracks,
                subtitleTracks = subtitleTracks,
                selectedAudioIndex = selectedAudioIndex,
                selectedSubtitleIndex = selectedSubtitleIndex,
                audioDelayMs = audioDelayMs,
                autoPlayNext = autoPlayNext,
                onSpeedChange = onSpeedChange,
                onAudioTrackSelect = onAudioTrackSelect,
                onSubtitleTrackSelect = onSubtitleTrackSelect,
                onAudioDelayChange = onAudioDelayChange,
                onAutoPlayNextChange = onAutoPlayNextChange,
                onClose = onClose,
            )
        }
    }
}

@Composable
private fun SettingsPanel(
    playbackSpeed: Float,
    audioTracks: List<TrackInfo>,
    subtitleTracks: List<TrackInfo>,
    selectedAudioIndex: Int,
    selectedSubtitleIndex: Int,
    audioDelayMs: Long,
    autoPlayNext: Boolean,
    onSpeedChange: (Float) -> Unit,
    onAudioTrackSelect: (Int) -> Unit,
    onSubtitleTrackSelect: (Int) -> Unit,
    onAudioDelayChange: (Long) -> Unit,
    onAutoPlayNextChange: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val firstItemFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        firstItemFocus.requestFocus()
    }

    Column(
        modifier = Modifier
            .width(360.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp))
            .background(Color.Black.copy(alpha = 0.88f))
            .padding(top = 16.dp, bottom = 16.dp)
    ) {
        Text(
            text = "再生設定",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // 再生速度
            item {
                SectionHeader("再生速度")
                SpeedSection(
                    currentSpeed = playbackSpeed,
                    onSelect = onSpeedChange,
                    firstItemFocusRequester = firstItemFocus
                )
                SectionDivider()
            }

            // 音声トラック（2トラック以上ある場合のみ表示）
            if (audioTracks.size >= 2) {
                item {
                    SectionHeader("音声トラック")
                    TrackSection(
                        tracks = audioTracks.map { it.label },
                        selectedIndex = selectedAudioIndex,
                        onSelect = onAudioTrackSelect
                    )
                    SectionDivider()
                }
            }

            // 字幕
            item {
                SectionHeader("字幕")
                val allSubtitleLabels = listOf("オフ") + subtitleTracks.map { it.label }
                TrackSection(
                    tracks = allSubtitleLabels,
                    selectedIndex = selectedSubtitleIndex + 1, // -1=オフ → index 0
                    onSelect = { idx -> onSubtitleTrackSelect(idx - 1) } // 0=オフ → -1
                )
                SectionDivider()
            }

            // 音声ディレイ
            item {
                SectionHeader("音声ディレイ")
                AudioDelaySection(
                    delayMs = audioDelayMs,
                    onDelayChange = onAudioDelayChange
                )
                SectionDivider()
            }

            // 次エピソード自動再生
            item {
                SectionHeader("次エピソード自動再生")
                AutoPlayToggleSection(
                    enabled = autoPlayNext,
                    onToggle = onAutoPlayNextChange
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = Color.Gray,
        fontSize = 12.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
}

@Composable
private fun SectionDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .padding(horizontal = 12.dp)
            .background(Color.White.copy(alpha = 0.1f))
    )
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun SpeedSection(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    firstItemFocusRequester: FocusRequester
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(SPEED_OPTIONS.size) { idx ->
            val speed = SPEED_OPTIONS[idx]
            val isSelected = currentSpeed == speed
            val modifier = if (idx == 0) Modifier.focusRequester(firstItemFocusRequester) else Modifier
            Button(
                onClick = { onSelect(speed) },
                modifier = modifier,
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFF333333),
                    focusedContainerColor = if (isSelected) Color(0xFF1976D2) else Color(0xFF555555),
                )
            ) {
                Text(
                    text = "${speed}x".replace(".0x", "x"),
                    color = Color.White,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
private fun TrackSection(
    tracks: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tracks.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Button(
                onClick = { onSelect(index) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.colors(
                    containerColor = if (isSelected) Color(0xFF1565C0) else Color(0xFF333333),
                    focusedContainerColor = if (isSelected) Color(0xFF1976D2) else Color(0xFF555555),
                )
            ) {
                Text(
                    text = label,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AudioDelaySection(
    delayMs: Long,
    onDelayChange: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 減少ボタン
        Button(
            onClick = { onDelayChange(delayMs - DELAY_STEP_MS) },
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    if (event.key == Key.DirectionLeft && event.type == KeyEventType.KeyDown) {
                        onDelayChange(delayMs - DELAY_STEP_MS)
                        true
                    } else false
                },
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF555555),
            )
        ) {
            Text("−", color = Color.White, fontSize = 18.sp)
        }

        // 現在値表示
        Text(
            text = "${delayMs}ms",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        // 増加ボタン
        Button(
            onClick = { onDelayChange(delayMs + DELAY_STEP_MS) },
            modifier = Modifier
                .weight(1f)
                .onKeyEvent { event ->
                    if (event.key == Key.DirectionRight && event.type == KeyEventType.KeyDown) {
                        onDelayChange(delayMs + DELAY_STEP_MS)
                        true
                    } else false
                },
            colors = ButtonDefaults.colors(
                containerColor = Color(0xFF333333),
                focusedContainerColor = Color(0xFF555555),
            )
        ) {
            Text("+", color = Color.White, fontSize = 18.sp)
        }
    }
    Text(
        text = "-500ms 〜 +500ms（50ms 刻み）",
        color = Color.Gray,
        fontSize = 10.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
    )
}

@Composable
private fun AutoPlayToggleSection(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = { onToggle(false) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.colors(
                containerColor = if (!enabled) Color(0xFF1565C0) else Color(0xFF333333),
                focusedContainerColor = if (!enabled) Color(0xFF1976D2) else Color(0xFF555555),
            )
        ) {
            Text("OFF", color = Color.White, fontSize = 14.sp)
        }
        Button(
            onClick = { onToggle(true) },
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.colors(
                containerColor = if (enabled) Color(0xFF1565C0) else Color(0xFF333333),
                focusedContainerColor = if (enabled) Color(0xFF1976D2) else Color(0xFF555555),
            )
        ) {
            Text("ON", color = Color.White, fontSize = 14.sp)
        }
    }
}

/** 次エピソードバナー（autoPlayNext オフ時に表示） */
@Composable
fun NextEpisodeBanner(
    episodeName: String,
    onPlay: () -> Unit,
    onDismiss: () -> Unit,
) {
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { playFocus.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "次のエピソード",
                color = Color.Gray,
                fontSize = 12.sp
            )
            Text(
                text = episodeName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF333333),
                        focusedContainerColor = Color(0xFF555555),
                    )
                ) {
                    Text("閉じる", color = Color.White)
                }
                Button(
                    onClick = onPlay,
                    modifier = Modifier.focusRequester(playFocus),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF1565C0),
                        focusedContainerColor = Color(0xFF1976D2),
                    )
                ) {
                    Text("再生", color = Color.White)
                }
            }
        }
    }
}
