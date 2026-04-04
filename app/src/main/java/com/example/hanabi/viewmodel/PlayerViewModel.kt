@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.hanabi.data.PlaybackPreferences
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.db.PlaybackProgress
import com.example.hanabi.data.smb.SmbConfig
import com.example.hanabi.data.smb.SmbDataSource
import com.example.hanabi.data.smb.SmbEntry
import com.example.hanabi.data.smb.SmbRepository
import com.example.hanabi.player.DelayAudioProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 音声トラック・字幕トラックの情報 */
data class TrackInfo(val index: Int, val label: String, val language: String?)

/** プレイヤー画面のViewModel */
@HiltViewModel
class PlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playbackDao: PlaybackDao,
    private val smbConfig: SmbConfig,
    private val smbRepository: SmbRepository,
    private val playbackPrefs: PlaybackPreferences
) : ViewModel() {

    private val delayProcessor = DelayAudioProcessor()

    // ViewModel生成時に初期化（AndroidViewのfactoryより必ず先に存在する）
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            ProgressiveMediaSource.Factory(SmbDataSource.Factory(smbConfig))
        )
        .setRenderersFactory(object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                ctx: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(ctx)
                .setAudioProcessors(arrayOf(delayProcessor))
                .build()
        })
        // 再生中のスリープ防止（CPU Wake Lock + Wi-Fi Lock を自動管理）
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private val _savedProgress = MutableStateFlow<PlaybackProgress?>(null)
    val savedProgress: StateFlow<PlaybackProgress?> = _savedProgress

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _playbackSpeed = MutableStateFlow(playbackPrefs.playbackSpeed)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed

    private val _audioDelayMs = MutableStateFlow(0L)
    val audioDelayMs: StateFlow<Long> = _audioDelayMs

    private val _audioTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val audioTracks: StateFlow<List<TrackInfo>> = _audioTracks

    private val _subtitleTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val subtitleTracks: StateFlow<List<TrackInfo>> = _subtitleTracks

    private val _selectedAudioIndex = MutableStateFlow(-1)
    val selectedAudioIndex: StateFlow<Int> = _selectedAudioIndex

    private val _selectedSubtitleIndex = MutableStateFlow(-1)
    val selectedSubtitleIndex: StateFlow<Int> = _selectedSubtitleIndex

    private val _autoPlayNext = MutableStateFlow(playbackPrefs.autoPlayNext)
    val autoPlayNext: StateFlow<Boolean> = _autoPlayNext

    private val _nextEpisode = MutableStateFlow<SmbEntry?>(null)
    val nextEpisode: StateFlow<SmbEntry?> = _nextEpisode

    private val _showNextEpisodeBanner = MutableStateFlow(false)
    val showNextEpisodeBanner: StateFlow<Boolean> = _showNextEpisodeBanner

    /** 次エピソード自動遷移イベント（autoPlayNext オン時に emit） */
    private val _navigationEvent = MutableSharedFlow<String>(replay = 0)
    val navigationEvent: SharedFlow<String> = _navigationEvent

    private var currentSmbPath: String = ""

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                updateTrackLists(tracks)
            }
        })
        // 保存済み速度を適用
        player.setPlaybackSpeed(playbackPrefs.playbackSpeed)
        // 保存済み音声言語を適用
        applyPersistedTrackPreferences()
    }

    /** メディアをセットして準備する（再生はまだ開始しない） */
    fun prepare(smbPath: String) {
        currentSmbPath = smbPath
        // 新しい動画では音声ディレイをリセット
        _audioDelayMs.value = 0L
        delayProcessor.setDelay(0L)
        _isPrepared.value = false
        viewModelScope.launch {
            val progress = playbackDao.getProgress(smbPath)
            _savedProgress.value = progress
            player.setMediaItem(MediaItem.fromUri(smbPath))
            player.prepare()
            _isPrepared.value = true
        }
    }

    /** 最初から再生 */
    fun playFromBeginning() {
        _savedProgress.value = null
        player.seekTo(0)
        player.play()
        startProgressSaving()
    }

    /** 続きから再生 */
    fun resumePlayback() {
        val position = _savedProgress.value?.positionMs ?: 0L
        _savedProgress.value = null
        player.seekTo(position)
        player.play()
        startProgressSaving()
    }

    /** シークインジケーター表示用（現在の設定値） */
    val seekForwardMs: Long get() = playbackPrefs.seekForwardSec * 1000L
    val seekBackwardMs: Long get() = playbackPrefs.seekBackwardSec * 1000L

    /** 早送り */
    fun seekForward(ms: Long = playbackPrefs.seekForwardSec * 1000L) {
        val current = player.currentPosition
        val duration = player.duration
        val newPos = if (duration > 0) (current + ms).coerceAtMost(duration) else current + ms
        player.seekTo(newPos)
    }

    /** 巻き戻し */
    fun seekBackward(ms: Long = playbackPrefs.seekBackwardSec * 1000L) {
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

    /** 再生速度を設定・永続化 */
    fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        player.setPlaybackSpeed(speed)
        playbackPrefs.playbackSpeed = speed
    }

    /** 音声ディレイを設定（-500ms〜+500ms、永続化しない） */
    fun setAudioDelay(ms: Long) {
        val clamped = ms.coerceIn(-500L, 500L)
        _audioDelayMs.value = clamped
        delayProcessor.setDelay(clamped)
    }

    /** 音声トラックを選択・永続化 */
    fun selectAudioTrack(index: Int) {
        _selectedAudioIndex.value = index
        val tracks = _audioTracks.value
        if (index < 0 || index >= tracks.size) return
        val trackInfo = tracks[index]

        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        if (index < audioGroups.size) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setOverrideForType(
                    androidx.media3.common.TrackSelectionOverride(
                        audioGroups[index].mediaTrackGroup, listOf(0)
                    )
                )
                .build()
        }
        if (!trackInfo.language.isNullOrBlank()) {
            playbackPrefs.preferredAudioLanguage = trackInfo.language
        }
    }

    /** 字幕トラックを選択・永続化（index = -1 でオフ） */
    fun selectSubtitleTrack(index: Int) {
        _selectedSubtitleIndex.value = index
        if (index < 0) {
            // 字幕オフ
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            playbackPrefs.preferredSubtitleLanguage = ""
        } else {
            val subtitleGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
            if (index < subtitleGroups.size) {
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        androidx.media3.common.TrackSelectionOverride(
                            subtitleGroups[index].mediaTrackGroup, listOf(0)
                        )
                    )
                    .build()
            }
            val trackInfo = _subtitleTracks.value.getOrNull(index)
            if (!trackInfo?.language.isNullOrBlank()) {
                playbackPrefs.preferredSubtitleLanguage = trackInfo!!.language!!
            }
        }
    }

    /** 次エピソード自動再生の設定・永続化 */
    fun setAutoPlayNext(enabled: Boolean) {
        _autoPlayNext.value = enabled
        playbackPrefs.autoPlayNext = enabled
    }

    /** 次エピソードバナーを非表示 */
    fun dismissNextEpisodeBanner() {
        _showNextEpisodeBanner.value = false
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

    /** 再生位置を定期的に保存する（30秒ごと） */
    private fun startProgressSaving() {
        viewModelScope.launch {
            while (true) {
                delay(30_000)
                saveProgress()
            }
        }
    }

    /** 動画終了時の処理 */
    private fun onVideoEnded() {
        saveProgress()
        viewModelScope.launch {
            val next = smbRepository.findNextEpisode(currentSmbPath)
            _nextEpisode.value = next
            if (next != null) {
                if (_autoPlayNext.value) {
                    _navigationEvent.emit(next.path)
                } else {
                    _showNextEpisodeBanner.value = true
                }
            }
        }
    }

    /** トラック一覧を更新 */
    private fun updateTrackLists(tracks: Tracks) {
        val audioGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        val subtitleGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }

        _audioTracks.value = audioGroups.mapIndexed { index, group ->
            val format = group.getTrackFormat(0)
            val lang = format.language
            val label = buildString {
                if (!lang.isNullOrBlank() && lang != "und") append(lang.uppercase())
                else append("Track ${index + 1}")
                val label = format.label
                if (!label.isNullOrBlank()) append(" ($label)")
            }
            TrackInfo(index, label, lang?.takeIf { it != "und" })
        }

        _subtitleTracks.value = subtitleGroups.mapIndexed { index, group ->
            val format = group.getTrackFormat(0)
            val lang = format.language
            val label = buildString {
                if (!lang.isNullOrBlank() && lang != "und") append(lang.uppercase())
                else append("Sub ${index + 1}")
                val fLabel = format.label
                if (!fLabel.isNullOrBlank()) append(" ($fLabel)")
            }
            TrackInfo(index, label, lang?.takeIf { it != "und" })
        }

        // 現在選択中のトラックインデックスを更新
        _selectedAudioIndex.value = audioGroups.indexOfFirst { it.isSelected }
        _selectedSubtitleIndex.value = subtitleGroups.indexOfFirst { it.isSelected }.let {
            if (it < 0 || player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)) -1 else it
        }
    }

    /** 保存済みトラック設定を適用 */
    private fun applyPersistedTrackPreferences() {
        val audioLang = playbackPrefs.preferredAudioLanguage
        val subtitleLang = playbackPrefs.preferredSubtitleLanguage

        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().apply {
            if (audioLang.isNotBlank()) setPreferredAudioLanguage(audioLang)
            if (subtitleLang.isBlank()) {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                setPreferredTextLanguage(subtitleLang)
            }
        }.build()
    }

    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }
}
