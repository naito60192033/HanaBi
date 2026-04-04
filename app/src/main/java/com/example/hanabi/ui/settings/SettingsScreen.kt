package com.example.hanabi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
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
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
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
    var isEditing by remember { mutableStateOf(false) }
    val textFieldFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // 編集モードに入ったらTextFieldにフォーカスを移してキーボードを開く
    LaunchedEffect(isEditing) {
        if (isEditing) {
            textFieldFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.Gray, style = MaterialTheme.typography.bodySmall)

        // 非編集時はBoxがD-padフォーカスを受け取り、決定ボタンで入力モードへ移行する
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .focusable(!isEditing)
                .onKeyEvent { event ->
                    if (!isEditing &&
                        event.key == Key.DirectionCenter &&
                        event.type == KeyEventType.KeyDown
                    ) {
                        isEditing = true
                        true
                    } else false
                }
        ) {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = { Text(placeholder, color = Color.DarkGray) },
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(textFieldFocusRequester)
                    // 非編集時はD-padナビゲーションによるフォーカスを受け取らない
                    .focusProperties { canFocus = isEditing }
                    .onFocusChanged { state ->
                        // TextFieldからフォーカスが外れたら編集モードを終了
                        if (isEditing && !state.hasFocus) isEditing = false
                    }
            )
        }
    }
}
