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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.*
import com.example.hanabi.data.smb.SmbEntry
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

    // コンテンツ読み込み後に最終選択位置へスクロール＆フォーカス復元
    LaunchedEffect(uiState) {
        val idx = viewModel.lastSelectedIndex
        if (uiState is BrowserUiState.Success && idx > 0) {
            gridState.scrollToItem(idx)
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
            IconButton(onClick = onNavigateToSettings) {
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
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    itemsIndexed(state.entries, key = { _, e -> e.path }) { index, entry ->
                        EntryCard(
                            entry = entry,
                            modifier = if (index == viewModel.lastSelectedIndex)
                                Modifier.focusRequester(focusRequester)
                            else Modifier,
                            onClick = {
                                viewModel.setLastSelectedIndex(index)
                                if (entry.isDirectory) {
                                    viewModel.openDirectory(entry)
                                } else if (entry.isVideo) {
                                    onNavigateToPlayer(entry.path)
                                }
                            }
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .width(200.dp)
            .height(140.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(if (entry.isDirectory) Color(0xFF1A237E) else Color(0xFF1B5E20)),
            contentAlignment = Alignment.Center
        ) {
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
                    text = entry.name,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/** 現在のパスからヘッダータイトルを生成 */
private fun buildHeaderTitle(path: String): String {
    if (path.isBlank()) return "HanaBi"
    val parts = path.split("/").filter { it.isNotBlank() }
    return parts.lastOrNull() ?: "HanaBi"
}
