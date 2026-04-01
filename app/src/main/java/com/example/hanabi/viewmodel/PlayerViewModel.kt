package com.example.hanabi.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.db.PlaybackProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** プレイヤー画面のViewModel */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackDao: PlaybackDao
) : ViewModel() {

    lateinit var player: ExoPlayer
        private set

    private val _savedProgress = MutableStateFlow<PlaybackProgress?>(null)
    val savedProgress: StateFlow<PlaybackProgress?> = _savedProgress

    private var currentSmbPath: String = ""

    /** 動画を準備する（再生はまだ開始しない） */
    fun prepare(smbPath: String) {
        currentSmbPath = smbPath

        // ExoPlayerを初期化
        player = ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }

        viewModelScope.launch {
            // 保存済みの再生位置を確認
            val progress = playbackDao.getProgress(smbPath)
            _savedProgress.value = progress

            // SMB URLをメディアアイテムとしてセット
            val mediaItem = MediaItem.fromUri(smbPath)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    /** 最初から再生 */
    fun playFromBeginning() {
        player.seekTo(0)
        player.play()
        startProgressSaving()
    }

    /** 保存位置から続きを再生 */
    fun resumePlayback() {
        val position = _savedProgress.value?.positionMs ?: 0L
        player.seekTo(position)
        player.play()
        startProgressSaving()
    }

    /** 再生位置を定期的に保存する（30秒ごと） */
    private fun startProgressSaving() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                saveProgress()
            }
        }
    }

    /** 現在の再生位置を保存 */
    fun saveProgress() {
        if (currentSmbPath.isBlank()) return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: return

        viewModelScope.launch {
            playbackDao.saveProgress(
                PlaybackProgress(
                    smbPath = currentSmbPath,
                    positionMs = position,
                    durationMs = duration
                )
            )
        }
    }

    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }
}
