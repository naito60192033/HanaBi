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

    var host: String
        get() = prefs.getString("host", "") ?: ""
        set(value) = prefs.edit { putString("host", value) }

    var share: String
        get() = prefs.getString("share", "") ?: ""
        set(value) = prefs.edit { putString("share", value) }

    var username: String
        get() = prefs.getString("username", "guest") ?: "guest"
        set(value) = prefs.edit { putString("username", value) }

    var password: String
        get() = prefs.getString("password", "") ?: ""
        set(value) = prefs.edit { putString("password", value) }

    /** 設定が完了しているか確認 */
    val isConfigured: Boolean
        get() = host.isNotBlank() && share.isNotBlank()

    /** SMB URLの生成: smb://host/share/path/ */
    fun buildUrl(path: String = ""): String {
        val base = "smb://$host/$share"
        return if (path.isBlank()) "$base/" else "$base/$path/"
    }

    /** jcifs-ng の接続コンテキストを生成 */
    fun buildCifsContext(): CIFSContext {
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
