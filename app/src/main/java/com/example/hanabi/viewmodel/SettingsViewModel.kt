package com.example.hanabi.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.ImageLoader
import com.example.hanabi.BuildConfig
import com.example.hanabi.data.PlaybackPreferences
import com.example.hanabi.data.db.PlaybackDao
import com.example.hanabi.data.smb.SmbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class UpdateAvailable(val version: String, val downloadUrl: String) : UpdateState()
    object Downloading : UpdateState()
    data class ReadyToInstall(val apkFile: File) : UpdateState()
    data class Error(val message: String) : UpdateState()
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SmbConfig,
    private val playbackPrefs: PlaybackPreferences,
    private val playbackDao: PlaybackDao,
    private val imageLoader: ImageLoader
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

    // サムネイルキャッシュ
    private val _thumbnailCacheSizeBytes = MutableStateFlow(0L)
    val thumbnailCacheSizeBytes: StateFlow<Long> = _thumbnailCacheSizeBytes

    fun refreshCacheSize() {
        val cacheDir = imageLoader.diskCache?.directory?.toFile()
        _thumbnailCacheSizeBytes.value = cacheDir
            ?.walkTopDown()
            ?.filter { it.isFile }
            ?.sumOf { it.length() }
            ?: 0L
    }

    fun clearThumbnailCache() {
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
        _thumbnailCacheSizeBytes.value = 0L
    }

    // アップデート確認
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState

    fun checkForUpdate() {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Checking
            try {
                val url = URL("https://api.github.com/repos/naito60192033/HanaBi/releases/latest")
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    setRequestProperty("Accept", "application/vnd.github.v3+json")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val response = connection.inputStream.bufferedReader().readText()
                connection.disconnect()

                val json = JSONObject(response)
                val tagName = json.getString("tag_name") // "v0.1.4"
                val latestVersion = tagName.trimStart('v')

                val assets = json.getJSONArray("assets")
                var downloadUrl: String? = null
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        downloadUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                if (isNewerVersion(latestVersion, BuildConfig.VERSION_NAME) && downloadUrl != null) {
                    _updateState.value = UpdateState.UpdateAvailable(tagName, downloadUrl)
                } else {
                    _updateState.value = UpdateState.UpToDate
                }
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "不明なエラー")
            }
        }
    }

    fun downloadAndInstall(downloadUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _updateState.value = UpdateState.Downloading
            try {
                val url = URL(downloadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.connectTimeout = 30_000
                connection.readTimeout = 60_000

                val apkDir = File(context.cacheDir, "apk")
                apkDir.mkdirs()
                val apkFile = File(apkDir, "update.apk")

                connection.inputStream.use { input ->
                    apkFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                connection.disconnect()

                _updateState.value = UpdateState.ReadyToInstall(apkFile)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error(e.message ?: "ダウンロード失敗")
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = UpdateState.Idle
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        val l = latest.split(".").mapNotNull { it.toIntOrNull() }
        val c = current.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv > cv) return true
            if (lv < cv) return false
        }
        return false
    }
}
