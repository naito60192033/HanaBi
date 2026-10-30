package com.example.hanabi.ui.player

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerKeyMappingTest {

    private val normal = PlayerKeyState(
        showResumeDialog = false,
        showErrorDialog = false,
        showNextEpisodeBanner = false,
        showSettingsOverlay = false
    )

    private val allKeys = listOf(
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_MEDIA_REWIND,
        KeyEvent.KEYCODE_MEDIA_PLAY, KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_BACK
    )

    @Test fun 右と早送りは前方シーク() {
        assertEquals(PlayerKeyAction.SeekForward, playerKeyAction(KeyEvent.KEYCODE_DPAD_RIGHT, normal))
        assertEquals(PlayerKeyAction.SeekForward, playerKeyAction(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, normal))
    }

    @Test fun 左と巻き戻しは後方シーク() {
        assertEquals(PlayerKeyAction.SeekBackward, playerKeyAction(KeyEvent.KEYCODE_DPAD_LEFT, normal))
        assertEquals(PlayerKeyAction.SeekBackward, playerKeyAction(KeyEvent.KEYCODE_MEDIA_REWIND, normal))
    }

    @Test fun 再生と一時停止() {
        assertEquals(PlayerKeyAction.Play, playerKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY, normal))
        assertEquals(PlayerKeyAction.Pause, playerKeyAction(KeyEvent.KEYCODE_MEDIA_PAUSE, normal))
    }

    @Test fun 中央とEnterと再生一時停止はトグル() {
        assertEquals(PlayerKeyAction.TogglePlayPause, playerKeyAction(KeyEvent.KEYCODE_DPAD_CENTER, normal))
        assertEquals(PlayerKeyAction.TogglePlayPause, playerKeyAction(KeyEvent.KEYCODE_ENTER, normal))
        assertEquals(PlayerKeyAction.TogglePlayPause, playerKeyAction(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, normal))
    }

    @Test fun メニューは設定を開く() {
        assertEquals(PlayerKeyAction.OpenSettings, playerKeyAction(KeyEvent.KEYCODE_MENU, normal))
    }

    @Test fun 上下はチャプター移動() {
        assertEquals(PlayerKeyAction.NextChapter, playerKeyAction(KeyEvent.KEYCODE_DPAD_UP, normal))
        assertEquals(PlayerKeyAction.PreviousChapter, playerKeyAction(KeyEvent.KEYCODE_DPAD_DOWN, normal))
    }

    @Test fun 未対応キーはnull() {
        assertNull(playerKeyAction(KeyEvent.KEYCODE_BACK, normal))
        assertNull(playerKeyAction(KeyEvent.KEYCODE_A, normal))
    }

    @Test fun 再開ダイアログ表示中は全キーnull() {
        val s = normal.copy(showResumeDialog = true)
        allKeys.forEach { assertNull(playerKeyAction(it, s)) }
    }

    @Test fun エラーダイアログ表示中は全キーnull() {
        val s = normal.copy(showErrorDialog = true)
        allKeys.forEach { assertNull(playerKeyAction(it, s)) }
    }

    @Test fun 次エピソードバナー表示中は全キーnull() {
        val s = normal.copy(showNextEpisodeBanner = true)
        allKeys.forEach { assertNull(playerKeyAction(it, s)) }
    }

    @Test fun 設定表示中はメニューのみ閉じる() {
        val s = normal.copy(showSettingsOverlay = true)
        assertEquals(PlayerKeyAction.CloseSettings, playerKeyAction(KeyEvent.KEYCODE_MENU, s))
        allKeys.filter { it != KeyEvent.KEYCODE_MENU }.forEach { assertNull(playerKeyAction(it, s)) }
    }

    @Test fun ダイアログが設定より優先される() {
        val s = normal.copy(showResumeDialog = true, showSettingsOverlay = true)
        assertNull(playerKeyAction(KeyEvent.KEYCODE_MENU, s))
    }
}
