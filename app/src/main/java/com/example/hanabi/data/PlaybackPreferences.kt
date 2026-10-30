package com.example.hanabi.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** 再生設定の永続化（SharedPreferences薄いラッパー） */
@Singleton
class PlaybackPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences("playback_prefs", Context.MODE_PRIVATE)

    var autoPlayNext: Boolean
        get() = prefs.getBoolean("autoPlayNext", false)
        set(value) = prefs.edit().putBoolean("autoPlayNext", value).apply()

    var playbackSpeed: Float
        get() = prefs.getFloat("playbackSpeed", 1.0f)
        set(value) = prefs.edit().putFloat("playbackSpeed", value).apply()

    /** 優先音声言語（言語タグがある場合のみ有効、空文字はデフォルト） */
    var preferredAudioLanguage: String
        get() = prefs.getString("preferredAudioLanguage", "") ?: ""
        set(value) = prefs.edit().putString("preferredAudioLanguage", value).apply()

    /** 優先字幕言語（空文字 = オフ） */
    var preferredSubtitleLanguage: String
        get() = prefs.getString("preferredSubtitleLanguage", "") ?: ""
        set(value) = prefs.edit().putString("preferredSubtitleLanguage", value).apply()

    var seekForwardSec: Int
        get() = prefs.getInt("seekForwardSec", 30)
        set(value) = prefs.edit().putInt("seekForwardSec", value).apply()

    var seekBackwardSec: Int
        get() = prefs.getInt("seekBackwardSec", 10)
        set(value) = prefs.edit().putInt("seekBackwardSec", value).apply()

    /** 一覧の列数（4 / 5 / 6）。既定の 4 列は 1 クール 12 話が 4×3 に収まる */
    var gridColumns: Int
        get() = prefs.getInt("gridColumns", 4)
        set(value) = prefs.edit().putInt("gridColumns", value).apply()

    /** ベータ版をアップデート対象に含めるか */
    var includeBetaUpdates: Boolean
        get() = prefs.getBoolean("includeBetaUpdates", false)
        set(value) = prefs.edit().putBoolean("includeBetaUpdates", value).apply()
}

/** 再生速度の選択肢（プレーヤーの設定パネルとアプリ設定で共通） */
val PLAYBACK_SPEED_OPTIONS = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
