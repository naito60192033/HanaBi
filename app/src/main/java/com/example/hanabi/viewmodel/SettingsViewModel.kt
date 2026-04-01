package com.example.hanabi.viewmodel

import androidx.lifecycle.ViewModel
import com.example.hanabi.data.smb.SmbConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val config: SmbConfig
) : ViewModel() {

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
}
