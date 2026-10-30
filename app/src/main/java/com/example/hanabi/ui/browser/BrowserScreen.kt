package com.example.hanabi.ui.browser

import com.example.hanabi.ui.theme.HanaBiColors
import androidx.activity.compose.BackHandler
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.res.stringResource
import com.example.hanabi.R
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import coil.compose.AsyncImage
import android.view.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import com.example.hanabi.ui.ActivityKeyHandler
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
    val progressMap by viewModel.progressMap.collectAsState()
    val gridState = rememberLazyGridState()
    val focusRequester = remember { FocusRequester() }
    val settingsFocusRequester = remember { FocusRequester() }

    // グリッドの列数（設定値。画面の密度によらず固定の列数にする）
    val columnCount by viewModel.gridColumns.collectAsState()
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
    // フォルダサムネ取得でエントリが差し替わっても再実行されないよう、キーは一覧のパス列にする
    val listKey = (uiState as? BrowserUiState.Success)?.entries?.map { it.path }
    LaunchedEffect(listKey) {
        val idx = viewModel.lastSelectedIndex
        if (listKey != null) {
            if (idx > 0) gridState.scrollToItem(idx)
            // グリッドアイテムの描画完了を待ってフォーカス要求
            kotlinx.coroutines.delay(50)
            // 空フォルダのときはグリッドにフォーカス先が無いので設定ボタンに当てる
            val target = if (listKey.isEmpty()) settingsFocusRequester else focusRequester
            try { target.requestFocus() } catch (_: Exception) {}
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
    ActivityKeyHandler { event ->
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_MENU) {
            onNavigateToSettings()
            true
        } else false
    }

    // Androidのバックキーでフォルダ階層を上に戻る
    BackHandler(enabled = viewModel.canGoBack) {
        viewModel.goBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HanaBiColors.ScreenBackground)
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
                color = HanaBiColors.TextPrimary
            )
            IconButton(
                onClick = onNavigateToSettings,
                modifier = Modifier.focusRequester(settingsFocusRequester)
            ) {
                // tint を指定しない: フォーカス時は背景が白になるので、IconButton が状態に応じた文字色を渡す
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.browser_settings))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // メインコンテンツ
        when (val state = uiState) {
            is BrowserUiState.Loading -> {
                LoadingSkeleton(columnCount = columnCount)
            }

            is BrowserUiState.NotConfigured -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.browser_not_configured), color = HanaBiColors.TextPrimary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateToSettings) {
                            Text(stringResource(R.string.browser_open_settings))
                        }
                    }
                }
            }

            is BrowserUiState.Error -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.message, color = HanaBiColors.Error)
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.loadCurrentPath() }) {
                            Text(stringResource(R.string.browser_retry))
                        }
                    }
                }
            }

            is BrowserUiState.Success -> if (state.entries.isEmpty()) {
                EmptyFolder()
            } else {
                // ファイル/フォルダのグリッド表示
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(columnCount),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(state.entries, key = { _, e -> e.path }) { index, entry ->
                        EntryCard(
                            entry = entry,
                            index = index,
                            columnCount = columnCount,
                            totalCount = state.entries.size,
                            progress = progressMap[entry.path] ?: 0f,
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

/** 読み込み中のスケルトン表示（本番と同じ列数・カード形状のプレースホルダーを 3 行分並べる） */
@Composable
private fun LoadingSkeleton(columnCount: Int) {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "skeletonAlpha"
    )
    LazyVerticalGrid(
        columns = GridCells.Fixed(columnCount),
        userScrollEnabled = false,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(columnCount * 3) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 2f)
                    .alpha(alpha)
                    .background(HanaBiColors.Surface, RoundedCornerShape(8.dp))
            )
        }
    }
}

/** 空フォルダの表示 */
@Composable
private fun EmptyFolder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = HanaBiColors.TextSecondary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.browser_empty_title),
                style = MaterialTheme.typography.titleMedium,
                color = HanaBiColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.browser_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = HanaBiColors.TextSecondary
            )
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
    progress: Float,
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
            // 幅は列に合わせて伸縮し、高さは 3:2 で決める
            .fillMaxWidth()
            .aspectRatio(3f / 2f)
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
                .background(if (entry.isDirectory) HanaBiColors.FolderCard else HanaBiColors.VideoCardBase),
            contentAlignment = Alignment.Center
        ) {
            if (entry.thumbnail != null) {
                // 動画 or サムネ付きフォルダ: サムネイル画像 + 下部に名前オーバーレイ + 視聴進捗バー（動画のみ）
                AsyncImage(
                    model = entry.thumbnail,
                    contentDescription = null,
                    contentScale = if (entry.isDirectory) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    error = rememberVectorPainter(
                        if (entry.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow
                    )
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomStart)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (entry.isDirectory) HanaBiColors.FolderCard.copy(alpha = 0.85f)
                                else HanaBiColors.Scrim.copy(alpha = 0.55f)
                            )
                    ) {
                        Text(
                            text = displayName(entry.name),
                            color = HanaBiColors.TextPrimary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    if (!entry.isDirectory && progress > 0f) {
                        // YouTube風 視聴進捗バー（カード最下端）
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(HanaBiColors.Scrim.copy(alpha = 0.5f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress.coerceIn(0.01f, 1f))
                                    .fillMaxHeight()
                                    .background(HanaBiColors.ProgressBar)
                            )
                        }
                    }
                }
            } else {
                // フォルダ or サムネイルなし: アイコン表示
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(6.dp)
                ) {
                    Icon(
                        imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = HanaBiColors.TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayName(entry.name),
                        color = HanaBiColors.TextPrimary,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** 現在のパスからヘッダータイトルを生成 */
internal fun buildHeaderTitle(path: String): String {
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

internal fun displayName(name: String): String {
    return episodeRegex.find(name)?.groupValues?.get(1) ?: name
}
