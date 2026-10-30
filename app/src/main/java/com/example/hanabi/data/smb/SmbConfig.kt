package com.example.hanabi.data.smb

import android.content.Context
import android.util.Log
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
        discardCachedContext()
    }

    /** キャッシュ済みコンテキストを破棄して close する */
    private fun discardCachedContext() {
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

    private val passwordCipher = PasswordCipher()

    /**
     * パスワードは AndroidKeyStore で暗号化して "password_enc" に保存する。
     * 旧版の平文 "password" が残っていれば、読み出し時に暗号化へ移行して平文を削除する。
     * 復号に失敗した場合は空文字を返す。
     */
    var password: String
        get() {
            val enc = prefs.getString("password_enc", null)
            if (enc != null) return passwordCipher.decrypt(enc)
            val legacy = prefs.getString("password", null) ?: return ""
            try {
                val encrypted = passwordCipher.encrypt(legacy)
                prefs.edit {
                    putString("password_enc", encrypted)
                    remove("password")
                }
            } catch (e: Exception) {
                // 暗号化できない場合は平文を残し、次回再試行する
                Log.w("SmbConfig", "パスワードの暗号化移行に失敗しました", e)
            }
            return legacy
        }
        set(value) {
            if (value == password) return
            val encrypted = try {
                passwordCipher.encrypt(value)
            } catch (e: Exception) {
                // KeyStore が使えない端末で保存操作を落とさないため、平文で保存する（次回読み出し時に移行を再試行）
                Log.w("SmbConfig", "パスワードの暗号化に失敗しました", e)
                null
            }
            prefs.edit {
                if (encrypted != null) {
                    putString("password_enc", encrypted)
                    remove("password")
                } else {
                    putString("password", value)
                    remove("password_enc")
                }
            }
            discardCachedContext()
        }

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

    private fun createCifsContext(): CIFSContext = createCifsContext(username, password)

    /** 指定した認証情報で、キャッシュしない一時的なコンテキストを作る（接続テスト用。呼び出し側が close する） */
    fun createTemporaryContext(username: String, password: String): CIFSContext =
        createCifsContext(username, password)

    private fun createCifsContext(username: String, password: String): CIFSContext {
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
