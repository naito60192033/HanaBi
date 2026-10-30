@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.hanabi.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.SeekParameters
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
import com.example.hanabi.data.smb.SmbMediaDataSource
import com.example.hanabi.player.Chapter
import com.example.hanabi.player.DelayAudioProcessor
import com.example.hanabi.player.MkvChapterExtractor
import com.example.hanabi.player.Mp4ChapterExtractor
import jcifs.smb.SmbFile
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 音声トラック・字幕トラックの情報 */
data class TrackInfo(val index: Int, val label: String, val language: String?)

/** チャプタージャンプの結果情報 */
data class ChapterJumpInfo(
    val forward: Boolean,
    val isChapter: Boolean,
    val chapterName: String?
)

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

    // SmbDataSource に直接生パスを渡すための Factory（PlayerViewModel から再生対象パスをセット）
    private val smbDataSourceFactory = SmbDataSource.Factory(smbConfig)

    // ViewModel生成時に初期化（AndroidViewのfactoryより必ず先に存在する）
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            ProgressiveMediaSource.Factory(smbDataSourceFactory)
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
        // NAS 越しの再生を安定させるため先読みを長めに取る（バイト上限は既定のままメモリ保護）
        .setLoadControl(
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs = */ 30_000,
                    /* maxBufferMs = */ 120_000,
                    /* bufferForPlaybackMs = */ 2_500,
                    /* bufferForPlaybackAfterRebufferMs = */ 5_000
                )
                .build()
        )
        .build()
        // ネットワーク越しのシークを速くするためキーフレームに合わせる
        .also { it.setSeekParameters(SeekParameters.CLOSEST_SYNC) }

    private val _savedProgress = MutableStateFlow<PlaybackProgress?>(null)
    val savedProgress: StateFlow<PlaybackProgress?> = _savedProgress

    private val _isPrepared = MutableStateFlow(false)
    val isPrepared: StateFlow<Boolean> = _isPrepared

    private val _isBuffering = MutableStateFlow(false)
    val isBuffering: StateFlow<Boolean> = _isBuffering

    private val _isActuallyPlaying = MutableStateFlow(false)
    val isActuallyPlaying: StateFlow<Boolean> = _isActuallyPlaying

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

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs

    private val _videoTitle = MutableStateFlow("")
    val videoTitle: StateFlow<String> = _videoTitle

    private val _chapters = MutableStateFlow<List<Chapter>>(emptyList())
    val chapters: StateFlow<List<Chapter>> = _chapters

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

    /** 再生エラー時の利用者向けメッセージ（null=エラーなし） */
    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError

    /** 直前の有効な再生位置（エラー後の再試行用。エラー時に 0 等で上書きしない） */
    private var lastValidPositionMs: Long = 0L

    private var positionJob: Job? = null
    private var progressSaveJob: Job? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                _isBuffering.value = playbackState == Player.STATE_BUFFERING
                _isActuallyPlaying.value = player.isPlaying
                val dur = player.duration
                if (dur > 0) _durationMs.value = dur
                if (playbackState == Player.STATE_ENDED) {
                    onVideoEnded()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isActuallyPlaying.value = isPlaying
                if (isPlaying) {
                    startPositionPolling()
                } else {
                    positionJob?.cancel()
                    positionJob = null
                    // 停止した時点の位置を一度反映する
                    updatePosition()
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                // 停止中のシークでも表示を更新する
                updatePosition()
            }

            override fun onPlayerError(error: PlaybackException) {
                // 位置が 0 等になっている可能性があるため、有効な値のときだけ更新し saveProgress は呼ばない
                val pos = player.currentPosition
                if (pos > 0) lastValidPositionMs = pos
                _playerError.value = errorMessageOf(error)
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

    /** 再生位置を反映する */
    private fun updatePosition() {
        // シーク保留中は表示を目標位置のままにする（古い位置で上書きしない）
        if (pendingSeekMs != null) return
        val pos = player.currentPosition.coerceAtLeast(0L)
        _currentPositionMs.value = pos
        if (_playerError.value == null && pos > 0) lastValidPositionMs = pos
        val dur = player.duration
        if (dur > 0) _durationMs.value = dur
    }

    /** 再生中のみ位置を定期ポーリング（500ms間隔） */
    private fun startPositionPolling() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                updatePosition()
                delay(500)
            }
        }
    }

    /** PlaybackException を利用者向けメッセージに分類する */
    private fun errorMessageOf(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE ->
            "NASとの接続が切れました"
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED ->
            "この動画形式は再生できません"
        else -> "再生中にエラーが発生しました (${error.errorCodeName})"
    }

    /**
     * エラー後の再試行：直前の位置から prepare し直す。
     * @param resume true なら再生を再開する（再生開始前のエラーでは false にして開始判断を UI に戻す）
     */
    fun retryPlayback(resume: Boolean) {
        _playerError.value = null
        cancelPendingSeek()
        player.seekTo(lastValidPositionMs)
        player.prepare()
        if (resume) player.play()
    }

    /** バックグラウンド遷移時：一時停止して位置を保存（復帰時の自動再開はしない） */
    fun onAppBackground() {
        player.pause()
        saveProgress()
    }

    /** 再生（一時停止中のみ） */
    fun play() {
        if (!player.isPlaying) player.play()
    }

    /** 一時停止（再生中のみ） */
    fun pause() {
        if (player.isPlaying) player.pause()
    }

    /** メディアをセットして準備する（再生はまだ開始しない） */
    fun prepare(smbPath: String) {
        cancelPendingSeek()
        currentSmbPath = smbPath
        _videoTitle.value = smbPath.substringAfterLast('/').let { filename ->
            if ('.' in filename) filename.substringBeforeLast('.') else filename
        }
        _chapters.value = emptyList()
        _currentPositionMs.value = 0L
        _durationMs.value = 0L
        // 新しい動画では音声ディレイをリセット
        _audioDelayMs.value = 0L
        delayProcessor.setDelay(0L)
        _isPrepared.value = false
        _playerError.value = null
        lastValidPositionMs = 0L
        // SmbDataSource に渡す生パスを登録（# 等を含むファイル名で URI を介さず読み込めるようにする）
        val mediaUri = encodeSmbUri(smbPath)
        smbDataSourceFactory.registerRawPath(mediaUri.toString(), smbPath)
        viewModelScope.launch {
            val progress = playbackDao.getProgress(smbPath)
            _savedProgress.value = progress
            // URI は ExoPlayer 内部の識別子用途。実ファイル読込みは smbDataSourceFactory 側で生パスを使う。
            // それでも MediaItem 側で fragment 切り落としによる識別子衝突を避けるため path 部を percent-encode
            player.setMediaItem(MediaItem.fromUri(mediaUri))
            player.prepare()
            _isPrepared.value = true
        }
        // チャプター抽出（MKV/MP4/M4V、非同期でバックグラウンド実行）
        val ext = smbPath.substringAfterLast('.').lowercase()
        if (ext in setOf("mkv", "mp4", "m4v")) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val cifsContext = smbConfig.buildCifsContext()
                    val smbFile = SmbFile(smbPath, cifsContext)
                    val source = SmbMediaDataSource(smbFile)
                    try {
                        _chapters.value = when (ext) {
                            "mkv" -> MkvChapterExtractor.extract(source)
                            else  -> Mp4ChapterExtractor.extract(source)
                        }
                    } finally {
                        source.close()
                    }
                } catch (_: Exception) {
                    // チャプター抽出失敗は無視（±10分スキップにフォールバック）
                }
            }
        }
    }

    /** 最初から再生 */
    fun playFromBeginning() {
        _savedProgress.value = null
        cancelPendingSeek()
        player.seekTo(0)
        player.play()
        startProgressSaving()
    }

    /** 続きから再生 */
    fun resumePlayback() {
        val position = _savedProgress.value?.positionMs ?: 0L
        _savedProgress.value = null
        cancelPendingSeek()
        player.seekTo(position)
        player.play()
        startProgressSaving()
    }

    /** シークインジケーター表示用（現在の設定値） */
    val seekForwardMs: Long get() = playbackPrefs.seekForwardSec * 1000L
    val seekBackwardMs: Long get() = playbackPrefs.seekBackwardSec * 1000L

    /** デバウンス中の目標位置（null=保留なし） */
    private var pendingSeekMs: Long? = null
    private var seekJob: Job? = null

    /** 保留中の目標位置があればそれ、なければ現在位置 */
    private fun basePositionMs(): Long = pendingSeekMs ?: player.currentPosition

    /** 目標位置を即表示に反映し、実際の seekTo は最後の操作から 300ms 後に 1 回だけ行う */
    private fun requestSeek(targetMs: Long) {
        val duration = player.duration
        val clamped = (if (duration > 0) targetMs.coerceAtMost(duration) else targetMs).coerceAtLeast(0L)
        pendingSeekMs = clamped
        _currentPositionMs.value = clamped
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(SEEK_DEBOUNCE_MS)
            pendingSeekMs = null
            player.seekTo(clamped)
        }
    }

    /** 早送り */
    fun seekForward(ms: Long = playbackPrefs.seekForwardSec * 1000L) {
        requestSeek(basePositionMs() + ms)
    }

    /** 巻き戻し */
    fun seekBackward(ms: Long = playbackPrefs.seekBackwardSec * 1000L) {
        requestSeek(basePositionMs() - ms)
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

    /**
     * 上ボタン（forward=true）: 次チャプターへジャンプ、チャプターなしは+10分スキップ
     * 下ボタン（forward=false）: 前チャプターへジャンプ、チャプターなしは-10分スキップ
     */
    fun seekToChapterOrSkip(forward: Boolean): ChapterJumpInfo {
        val chapterList = _chapters.value
        val currentPos = basePositionMs()
        if (chapterList.isNotEmpty()) {
            if (forward) {
                val next = chapterList.firstOrNull { it.positionMs > currentPos + 1000 }
                if (next != null) {
                    cancelPendingSeek()
                    player.seekTo(next.positionMs)
                    return ChapterJumpInfo(true, true, next.title?.toString())
                }
            } else {
                val prev = chapterList.lastOrNull { it.positionMs < currentPos - 1000 }
                if (prev != null) {
                    cancelPendingSeek()
                    player.seekTo(prev.positionMs)
                    return ChapterJumpInfo(false, true, prev.title?.toString())
                }
            }
        }
        if (forward) seekForward(10 * 60 * 1000L) else seekBackward(10 * 60 * 1000L)
        return ChapterJumpInfo(forward, false, null)
    }

    /** 保留中のデバウンスシークを破棄する（別の seekTo を直接行う前に呼ぶ） */
    private fun cancelPendingSeek() {
        seekJob?.cancel()
        seekJob = null
        pendingSeekMs = null
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
        // エラー中は位置が 0 等で上書きされうるため保存しない
        if (_playerError.value != null) return
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
        progressSaveJob?.cancel()
        progressSaveJob = viewModelScope.launch {
            while (true) {
                delay(30_000)
                if (player.isPlaying) saveProgress()
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

    companion object {
        /** 連打シークのデバウンス時間 */
        private const val SEEK_DEBOUNCE_MS = 300L
    }

    override fun onCleared() {
        saveProgress()
        player.release()
        super.onCleared()
    }

    /** smb:// URL の path 部のみを percent-encode する（`/` は保持） */
    private fun encodeSmbUri(smbPath: String): Uri {
        val prefix = "smb://"
        if (!smbPath.startsWith(prefix)) return Uri.parse(smbPath)
        val rest = smbPath.substring(prefix.length)
        val slash = rest.indexOf('/')
        if (slash < 0) return Uri.parse(smbPath)
        val authority = rest.substring(0, slash)
        val path = rest.substring(slash)
        return Uri.parse("$prefix$authority${Uri.encode(path, "/")}")
    }
}
