package com.example.hanabi.ui.browser

import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import coil.compose.AsyncImage
import android.view.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import com.example.hanabi.MainActivity
import com.example.hanabi.data.smb.SmbEntry
import com.example.hanabi.data.smb.SmbThumbnailKey
import com.example.hanabi.viewmodel.BrowserUiState
import com.example.hanabi.viewmodel.BrowserViewModel

/** ファイル/フォルダブラウザ画面 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun BrowserScreen(
    onNavigateToPlayer: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: BrowserViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    // グリッドの列数（レイアウト情報から動的に取得）
    val columnCount by remember {
        derivedStateOf {
            (gridState.layoutInfo.visibleItemsInfo.maxOfOrNull { it.column } ?: 0) + 1
        }
    }
    // 端でのフォーカス折り返し先インデックスとFocusRequester
    var wrapTargetIndex by remember { mutableStateOf<Int?>(null) }
    val wrapFocusRequester = remember { FocusRequester() }

    // 端キー折り返し: wrapTargetIndex が変わったらそのアイテムへスクロール＆フォーカス
    LaunchedEffect(wrapTargetIndex) {
        wrapTargetIndex?.let { idx ->
            if (gridState.layoutInfo.visibleItemsInfo.none { it.index == idx }) {
                gridState.scrollToItem(idx)
            }
            kotlinx.coroutines.delay(50)
            try { wrapFocusRequester.requestFocus() } catch (_: Exception) {}
            wrapTargetIndex = null
        }
    }

    // コンテンツ読み込み後に最終選択位置へスクロール＆フォーカス復元
    LaunchedEffect(uiState) {
        val idx = viewModel.lastSelectedIndex
        if (uiState is BrowserUiState.Success) {
            if (idx > 0) gridState.scrollToItem(idx)
            // グリッドアイテムの描画完了を待ってフォーカス要求
            kotlinx.coroutines.delay(50)
            try { focusRequester.requestFocus() } catch (_: Exception) {}
        }
    }

    // 設定画面から戻ったときに再読み込みする
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.loadCurrentPath()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // メニューボタンで設定画面へ遷移
    DisposableEffect(Unit) {
        MainActivity.menuKeyHandler = { event ->
            if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
                onNavigateToSettings()
                true
            } else false
        }
        onDispose { MainActivity.menuKeyHandler = null }
    }

    // Androidのバックキーでフォルダ階層を上に戻る
    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 48.dp, vertical = 27.dp)
    ) {
        // ヘッダー行（タイトル + 設定ボタン）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildHeaderTitle(viewModel.currentPath),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.focusRequester(settingsFocusRequester)
            ) {
                Icon(Icons.Default.Settings, contentDescription = "設定", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // メインコンテンツ
        when (val state = uiState) {
            is BrowserUiState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            is BrowserUiState.NotConfigured -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("NASの接続設定が必要です", color = Color.White)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToSettings) {
                            Text("設定を開く")
                        }
                    }
                }
            }

            is BrowserUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("接続エラー: ${state.message}", color = Color.Red)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCurrentPath() }) {
                            Text("再試行")
                        }
                    }
                }
            }

            is BrowserUiState.Success -> {
                // ファイル/フォルダのグリッド表示
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(state.entries, key = { _, e -> e.path }) { index, entry ->
                        EntryCard(
                            entry = entry,
                            index = index,
                            columnCount = columnCount,
                            totalCount = state.entries.size,
                            modifier = when {
                                index == viewModel.lastSelectedIndex -> Modifier.focusRequester(focusRequester)
                                index == wrapTargetIndex -> Modifier.focusRequester(wrapFocusRequester)
                                else -> Modifier
                            },
                            onClick = {
                                viewModel.setLastSelectedIndex(index)
                                if (entry.isDirectory) {
                                    viewModel.openDirectory(entry)
                                } else if (entry.isVideo) {
                                    onNavigateToPlayer(entry.path)
                                }
                            },
                            onWrapFocus = { targetIndex -> wrapTargetIndex = targetIndex },
                            onFocusSettings = { settingsFocusRequester.requestFocus() }
                        )
                    }
                }
            }
        }
    }
}

/** フォルダ/ファイルのカードコンポーネント */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun EntryCard(
    entry: SmbEntry,
    index: Int,
    columnCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onWrapFocus: (Int) -> Unit,
    onFocusSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFirstInRow = index % columnCount == 0
    val isLastInRow = (index % columnCount == columnCount - 1) || (index == totalCount - 1)
    val isInFirstRow = index < columnCount

    Card(
        onClick = onClick,
        modifier = modifier
            .width(200.dp)
            .height(140.dp)
            .onKeyEvent { event ->
                if (event.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) return@onKeyEvent false
                when (event.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> when {
                        !isLastInRow -> false                          // 行中: TVシステムに任せる
                        index + 1 < totalCount -> { onWrapFocus(index + 1); true } // 次行先頭へ
                        else -> true                                   // 最終アイテム: 何もしない
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> when {
                        !isFirstInRow -> false                         // 行中: TVシステムに任せる
                        index > 0 -> { onWrapFocus(index - 1); true } // 前行末尾へ
                        else -> true                                   // 先頭アイテム: 何もしない
                    }
                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (isInFirstRow) { onFocusSettings(); true } else false
                    }
                    else -> false
                }
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (entry.isDirectory) Color(0xFF1A237E) else Color(0xFF1B5E20)),
            contentAlignment = Alignment.Center
        ) {
            if (!entry.isDirectory && entry.thumbnailPath != null) {
                // 動画: サムネイル画像 + 下部に名前オーバーレイ
                AsyncImage(
                    model = entry.thumbnailPath?.let { SmbThumbnailKey(it) },
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(Icons.Default.PlayArrow)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = displayName(entry.name),
                        color = Color.White,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else {
                // フォルダ or サムネイルなし: アイコン表示
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = displayName(entry.name),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 現在のパスからヘッダータイトルを生成 */
private fun buildHeaderTitle(path: String): String {
    if (path.isBlank()) return "HanaBi"
    val parts = path.split("/").filter { it.isNotBlank() }
    val last = parts.lastOrNull() ?: return "HanaBi"
    // 末尾セグメントが S01 等の形式なら「親フォルダ名-Sxx」にする
    if (last.matches(Regex("S\\d+"))) {
        val parent = parts.getOrNull(parts.size - 2)
        if (parent != null) return "$parent-$last"
    }
    return last
}

/** ファイル名の表示用テキストを返す
 *  `タイトル_SxxExx_エピソード名.mp4` 形式の場合はエピソード名のみ抽出 */
private val episodeRegex = Regex("""^.+_S\d+E\d+_(.+)\.[^.]+$""")

private fun displayName(name: String): String {
    return episodeRegex.find(name)?.groupValues?.get(1) ?: name
}
