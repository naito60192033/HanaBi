package com.example.hanabi.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.hanabi.data.PlaybackPreferences
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.smb.SmbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val config: SmbConfig,
    private val playbackPrefs: PlaybackPreferences,
    private val playbackDao: PlaybackDao
) : ViewModel() {

    // NAS 接続設定
    val currentHost get() = config.host
    val currentShare get() = config.share
    val currentUsername get() = config.username
    val currentPassword get() = config.password

    fun save(host: String, share: String, username: String, password: String) {
        config.host = host
        config.share = share
        config.username = username
        config.password = password
    }

    // 再生設定
    private val _autoPlayNext = MutableStateFlow(playbackPrefs.autoPlayNext)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext

    private val _defaultPlaybackSpeed = MutableStateFlow(playbackPrefs.playbackSpeed)
    val defaultPlaybackSpeed: StateFlow<Float> = _defaultPlaybackSpeed

    private val _seekForwardSec = MutableStateFlow(playbackPrefs.seekForwardSec)
    val seekForwardSec: StateFlow<Int> = _seekForwardSec

    private val _seekBackwardSec = MutableStateFlow(playbackPrefs.seekBackwardSec)
    val seekBackwardSec: StateFlow<Int> = _seekBackwardSec

    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        playbackPrefs.autoPlayNext = enabled
    }

    fun setDefaultPlaybackSpeed(speed: Float) {
        _defaultPlaybackSpeed.value = speed
        playbackPrefs.playbackSpeed = speed
    }

    fun setSeekForwardSec(sec: Int) {
        _seekForwardSec.value = sec
        playbackPrefs.seekForwardSec = sec
    }

    fun setSeekBackwardSec(sec: Int) {
        _seekBackwardSec.value = sec
        playbackPrefs.seekBackwardSec = sec
    }

    fun resetPlaybackHistory(onComplete: () -> Unit) {
        viewModelScope.launch {
            playbackDao.deleteAllProgress()
            onComplete()
        }
    }
}
