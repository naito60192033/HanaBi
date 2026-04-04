package com.example.hanabi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hanabi.data.smb.SmbConfig
import com.example.hanabi.data.smb.SmbEntry
import com.example.hanabi.data.smb.SmbRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** ブラウザ画面の状態 */
sealed class BrowserUiState {
    data object Loading : BrowserUiState()
    data class Success(val entries: List<SmbEntry>) : BrowserUiState()
    data class Error(val message: String) : BrowserUiState()
    data object NotConfigured : BrowserUiState()
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: SmbRepository,
    private val config: SmbConfig
) : ViewModel() {

    private val _uiState = MutableStateFlow<BrowserUiState>(BrowserUiState.Loading)
    val uiState: StateFlow<BrowserUiState> = _uiState

    // 階層ナビゲーション用のパス履歴スタック
    private val pathStack = ArrayDeque<String>()

    // 階層ごとの選択インデックス履歴（goBack時に復元する）
    private val selectedIndexStack = ArrayDeque<Int>()

    val currentPath: String get() = pathStack.lastOrNull() ?: ""
    val canGoBack: Boolean get() = pathStack.isNotEmpty()

    /** カーソルを復元するための最終選択インデックス */
    var lastSelectedIndex: Int = 0
        private set

    fun setLastSelectedIndex(index: Int) {
        lastSelectedIndex = index
    }

    /** プレイヤーから戻る際に対象動画へフォーカスを当てるSMBパス */
    private var pendingFocusSmbPath: String? = null

    /** プレイヤーから戻る前に呼ぶ。loadCurrentPath 完了時にそのエントリへフォーカスを設定する */
    fun setFocusOnReturn(smbVideoPath: String) {
        pendingFocusSmbPath = smbVideoPath
    }

    init {
        loadCurrentPath()
    }

    /** 現在のパスのエントリ一覧を読み込む */
    fun loadCurrentPath() {
        if (!config.isConfigured) {
            _uiState.value = BrowserUiState.NotConfigured
            return
        }
        viewModelScope.launch {
            _uiState.value = BrowserUiState.Loading
            repository.listEntries(currentPath)
                .onSuccess { entries ->
                    // フォルダと動画ファイルのみ表示
                    val filtered = entries.filter { it.isDirectory || it.isVideo }
                    // プレイヤーから戻った際のフォーカス復元
                    pendingFocusSmbPath?.let { targetPath ->
                        val idx = filtered.indexOfFirst { it.path == targetPath }
                        if (idx >= 0) lastSelectedIndex = idx
                        pendingFocusSmbPath = null
                    }
                    _uiState.value = BrowserUiState.Success(filtered)
                }
                .onFailure { e ->
                    _uiState.value = BrowserUiState.Error(e.message ?: "不明なエラー")
                }
        }
    }

    /** フォルダを開く */
    fun openDirectory(entry: SmbEntry) {
        if (!entry.isDirectory) return
        // 現在の選択インデックスを保存してからフォルダに入る
        selectedIndexStack.addLast(lastSelectedIndex)
        lastSelectedIndex = 0
        val relativePath = extractRelativePath(entry.path)
        pathStack.addLast(relativePath)
        loadCurrentPath()
    }

    /** 1つ上の階層に戻る */
    fun goBack(): Boolean {
        if (pathStack.isEmpty()) return false
        pathStack.removeLast()
        // 直前の選択インデックスを復元する
        lastSelectedIndex = selectedIndexStack.removeLastOrNull() ?: 0
        loadCurrentPath()
        return true
    }

    /** SMB URLから共有フォルダ以下のパスを抽出 */
    private fun extractRelativePath(smbUrl: String): String {
        val share = config.share
        val idx = smbUrl.indexOf(share)
        if (idx == -1) return ""
        return smbUrl.substring(idx + share.length).trimStart('/')
    }
}
