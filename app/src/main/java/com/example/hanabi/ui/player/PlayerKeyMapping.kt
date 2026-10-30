package com.example.hanabi.ui.player

import android.view.KeyEvent

/** キー判定に必要な画面状態 */
internal data class PlayerKeyState(
    val showResumeDialog: Boolean,
    val showErrorDialog: Boolean,
    val showNextEpisodeBanner: Boolean,
    val showSettingsOverlay: Boolean
)

/** プレイヤー画面がキーに対して実行するアクション */
internal sealed interface PlayerKeyAction {
    data object SeekForward : PlayerKeyAction
    data object SeekBackward : PlayerKeyAction
    data object TogglePlayPause : PlayerKeyAction
    data object Play : PlayerKeyAction
    data object Pause : PlayerKeyAction
    data object OpenSettings : PlayerKeyAction
    data object CloseSettings : PlayerKeyAction
    data object NextChapter : PlayerKeyAction
    data object PreviousChapter : PlayerKeyAction
}

/**
 * ACTION_DOWN のキーコードをアクションに変換する純粋関数。
 * null は「処理しない」（Compose / システムにそのまま渡す）。
 * ACTION_DOWN 以外の判定は呼び出し側で行う。
 */
internal fun playerKeyAction(keyCode: Int, state: PlayerKeyState): PlayerKeyAction? = when {
    // ダイアログ・バナー表示中は全キーをそちらに渡す
    state.showResumeDialog -> null
    state.showErrorDialog -> null
    state.showNextEpisodeBanner -> null
    // 設定オーバーレイ表示中は MENU（閉じる）のみ処理
    state.showSettingsOverlay -> when (keyCode) {
        KeyEvent.KEYCODE_MENU -> PlayerKeyAction.CloseSettings
        else -> null
    }
    else -> when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT,
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> PlayerKeyAction.SeekForward
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_MEDIA_REWIND -> PlayerKeyAction.SeekBackward
        KeyEvent.KEYCODE_MEDIA_PLAY -> PlayerKeyAction.Play
        KeyEvent.KEYCODE_MEDIA_PAUSE -> PlayerKeyAction.Pause
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> PlayerKeyAction.TogglePlayPause
        KeyEvent.KEYCODE_MENU -> PlayerKeyAction.OpenSettings
        KeyEvent.KEYCODE_DPAD_UP -> PlayerKeyAction.NextChapter
        KeyEvent.KEYCODE_DPAD_DOWN -> PlayerKeyAction.PreviousChapter
        else -> null
    }
}
