package com.example.hanabi.data.smb

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import java.util.Properties
import javax.inject.Inject
import javax.inject.Singleton

/** NAS接続設定の保存・読み込み */
@Singleton
class SmbConfig @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("smb_config", Context.MODE_PRIVATE)

    /** キャッシュ中の接続コンテキスト（設定変更で破棄する）。_cifsLock で保護 */
    private val _cifsLock = Any()
    private var cachedContext: CIFSContext? = null

    /** 値が変わる場合のみ保存し、キャッシュ済みコンテキストを破棄して close する */
    private fun updateSetting(key: String, value: String, current: String) {
        if (value == current) return
        prefs.edit { putString(key, value) }
        val old = synchronized(_cifsLock) {
            cachedContext.also { cachedContext = null }
        }
        // 使用中の接続が切れる可能性はあるが、設定変更時のみなので許容。close の失敗は無視
        try { old?.close() } catch (_: Exception) {}
    }

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = updateSetting("host", value, host)

    var share: String
        get() = prefs.getString("share", "") ?: ""
        set(value) = updateSetting("share", value, share)

    var username: String
        get() = prefs.getString("username", "guest") ?: "guest"
        set(value) = updateSetting("username", value, username)

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = updateSetting("password", value, password)

    /** 設定が完了しているか確認 */
    val isConfigured: Boolean
        get() = host.isNotBlank() && share.isNotBlank()

    /** SMB URLの生成: smb://host/share/path/ */
    fun buildUrl(path: String = ""): String {
        val base = "smb://$host/$share"
        return if (path.isBlank()) "$base/" else "$base/$path/"
    }

    /**
     * jcifs-ng の接続コンテキストを返す（キャッシュ済みならそれを再利用し、接続を使い回す）。
     * host / share / username / password のいずれかが変更されるとキャッシュは破棄される。
     */
    fun buildCifsContext(): CIFSContext = synchronized(_cifsLock) {
        cachedContext ?: createCifsContext().also { cachedContext = it }
    }

    private fun createCifsContext(): CIFSContext {
        val props = Properties().apply {
            setProperty("jcifs.smb.client.minVersion", "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            setProperty("jcifs.smb.client.connTimeout", "10000")
            setProperty("jcifs.smb.client.soTimeout", "15000")
            setProperty("jcifs.resolveOrder", "DNS,BCAST")
            setProperty("jcifs.smb.client.dfs.disabled", "true")
            setProperty("jcifs.smb.client.ipcSigningEnforced", "false")
        }
        val baseContext = BaseContext(PropertyConfiguration(props))
        return if (username.isNotBlank() && password.isNotBlank()) {
            baseContext.withCredentials(NtlmPasswordAuthenticator(username, password))
        } else {
            baseContext.withGuestCrendentials()
        }
    }
}
