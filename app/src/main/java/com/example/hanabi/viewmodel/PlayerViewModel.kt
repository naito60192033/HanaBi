@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.data.smb.SmbConfig
import com.example.hanabi.data.smb.SmbDataSource
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
    private val playbackDao: PlaybackDao,
    private val smbConfig: SmbConfig
) : ViewModel() {

    // ViewModel生成時に初期化（AndroidViewのfactoryより必ず先に存在する）
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            ProgressiveMediaSource.Factory(SmbDataSource.Factory(smbConfig))
        )
        .build()

    private val _savedProgress = MutableStateFlow<PlaybackProgress?>(null)
    val savedProgress: StateFlow<PlaybackProgress?> = _savedProgress

    private var currentSmbPath: String = ""

    /** メディアをセットして準備する（再生はまだ開始しない） */
    fun prepare(smbPath: String) {
        currentSmbPath = smbPath
        viewModelScope.launch {
            val progress = playbackDao.getProgress(smbPath)
            _savedProgress.value = progress
            player.setMediaItem(MediaItem.fromUri(smbPath))
            player.prepare()
        }
    }

    /** 最初から再生 */
    fun playFromBeginning() {
        player.seekTo(0)
        player.play()
        startProgressSaving()
    }

    /** 30秒早送り */
    fun seekForward(ms: Long = 30_000L) {
        val duration = player.duration.takeIf { it > 0 } ?: return
        val newPos = (player.currentPosition + ms).coerceAtMost(duration)
        player.seekTo(newPos)
    }

    /** 10秒巻き戻し */
    fun seekBackward(ms: Long = 10_000L) {
        val newPos = (player.currentPosition - ms).coerceAtLeast(0L)
        player.seekTo(newPos)
    }

    /** 再生/一時停止トグル。現在の再生状態を返す（true=再生開始、false=一時停止） */
    fun togglePlayPause(): Boolean {
        return if (player.isPlaying) {
            player.pause()
            false
        } else {
            player.play()
            true
        }
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
