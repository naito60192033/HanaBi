package com.example.hanabi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.example.hanabi.viewmodel.SettingsViewModel

/** NAS接続設定画面 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    var host by remember { mutableStateOf(viewModel.currentHost) }
    var share by remember { mutableStateOf(viewModel.currentShare) }
    var username by remember { mutableStateOf(viewModel.currentUsername) }
    var password by remember { mutableStateOf(viewModel.currentPassword) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 96.dp, vertical = 48.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text(
                "NAS接続設定",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )

            // ホスト名入力
            SettingsTextField(
                label = "ホスト名 / IPアドレス",
                value = host,
                onValueChange = { host = it },
                placeholder = "例: 192.168.1.100 または NAS名"
            )

            // 共有フォルダ名入力
            SettingsTextField(
                label = "共有フォルダ名",
                value = share,
                onValueChange = { share = it },
                placeholder = "例: video"
            )

            // ユーザー名入力
            SettingsTextField(
                label = "ユーザー名（省略可）",
                value = username,
                onValueChange = { username = it },
                placeholder = "例: admin"
            )

            // パスワード入力
            SettingsTextField(
                label = "パスワード（省略可）",
                value = password,
                onValueChange = { password = it },
                placeholder = "パスワード",
                isPassword = true
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        viewModel.save(host, share, username, password)
                        onBack()
                    }
                ) {
                    Text("保存して戻る")
                }
                OutlinedButton(onClick = onBack) {
                    Text("キャンセル")
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    isPassword: Boolean = false
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        // TODO: TV向けのテキスト入力は標準的なTextField＋フォーカス管理が必要
        // 現在はプレースホルダー実装 - 実装時にTVフレンドリーなInputを追加する
    }
}
