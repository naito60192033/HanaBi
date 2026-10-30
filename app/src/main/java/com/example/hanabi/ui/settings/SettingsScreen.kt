package com.example.hanabi.ui.settings

import com.example.hanabi.ui.theme.HanaBiColors
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.res.stringResource
import com.example.hanabi.R
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.*
import com.example.hanabi.viewmodel.ConnectionTestState
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
    var passwordVisible by remember { mutableStateOf(false) }
    val testState by viewModel.connectionTest.collectAsState()
    val passwordVisibilityFocusRequester = remember { FocusRequester() }
    val saveButtonFocusRequester = remember { FocusRequester() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HanaBiColors.ScreenBackground)
            .padding(horizontal = 96.dp, vertical = 48.dp)
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Text(
                stringResource(R.string.settings_nas_title),
                style = MaterialTheme.typography.headlineMedium,
                color = HanaBiColors.TextPrimary
            )

            // ホスト名入力
            SettingsTextField(
                label = stringResource(R.string.settings_host_label),
                value = host,
                onValueChange = { host = it },
                placeholder = stringResource(R.string.settings_host_placeholder)
            )

            // 共有フォルダ名入力
            SettingsTextField(
                label = stringResource(R.string.settings_share_label),
                value = share,
                onValueChange = { share = it },
                placeholder = stringResource(R.string.settings_share_placeholder)
            )

            // ユーザー名入力
            SettingsTextField(
                label = stringResource(R.string.settings_user_label),
                value = username,
                onValueChange = { username = it },
                placeholder = stringResource(R.string.settings_user_placeholder)
            )

            // パスワード入力
            SettingsTextField(
                label = stringResource(R.string.settings_password_label),
                value = password,
                onValueChange = { password = it },
                placeholder = stringResource(R.string.settings_password_placeholder),
                isPassword = true,
                maskValue = !passwordVisible,
                nextFocusDown = passwordVisibilityFocusRequester
            )

            // パスワード表示トグル
            PasswordVisibilityToggle(
                visible = passwordVisible,
                onToggle = { passwordVisible = !passwordVisible },
                focusRequester = passwordVisibilityFocusRequester,
                nextFocusDown = saveButtonFocusRequester
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = {
                        viewModel.save(host, share, username, password)
                        onBack()
                    },
                    modifier = Modifier.focusRequester(saveButtonFocusRequester)
                ) {
                    Text(stringResource(R.string.settings_save_and_back))
                }
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.settings_cancel))
                }
                OutlinedButton(
                    onClick = { viewModel.testConnection(host, share, username, password) }
                ) {
                    Text(stringResource(R.string.settings_connection_test))
                }
            }

            // 接続テスト結果
            when (val st = testState) {
                is ConnectionTestState.Idle -> {}
                is ConnectionTestState.Testing ->
                    Text(stringResource(R.string.settings_connecting), color = HanaBiColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
                is ConnectionTestState.Success ->
                    Text(stringResource(R.string.settings_connected, st.count), color = HanaBiColors.Success, style = MaterialTheme.typography.bodyMedium)
                is ConnectionTestState.Failure ->
                    Text(st.message, color = HanaBiColors.Error, style = MaterialTheme.typography.bodyMedium)
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
    isPassword: Boolean = false,
    maskValue: Boolean = isPassword,
    nextFocusDown: FocusRequester? = null
) {
    var isEditing by remember { mutableStateOf(false) }
    var isFocused by remember { mutableStateOf(false) }
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
        Text(label, color = HanaBiColors.TextSecondary, style = MaterialTheme.typography.bodySmall)

        // 非編集時はBoxがD-padフォーカスを受け取り、決定ボタンで入力モードへ移行する
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isFocused && !isEditing)
                        Modifier.border(2.dp, HanaBiColors.TextPrimary, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    else
                        Modifier
                )
                .onFocusChanged { state -> isFocused = state.hasFocus }
                .focusProperties { if (nextFocusDown != null) down = nextFocusDown }
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
                placeholder = { Text(placeholder, color = HanaBiColors.TextHint) },
                visualTransformation = if (maskValue) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = HanaBiColors.TextPrimary,
                    unfocusedTextColor = HanaBiColors.TextPrimary,
                    focusedBorderColor = HanaBiColors.TextPrimary,
                    unfocusedBorderColor = HanaBiColors.TextSecondary,
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

/**
 * パスワード表示/非表示を D-pad の決定キーで切り替える Material 3 風トグル。
 * 左に Visibility/VisibilityOff アイコン + ラベル、右に pill 型スイッチを配置。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PasswordVisibilityToggle(
    visible: Boolean,
    onToggle: () -> Unit,
    focusRequester: FocusRequester,
    nextFocusDown: FocusRequester? = null
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )
    val containerColor by animateColorAsState(
        targetValue = when {
            isFocused -> HanaBiColors.TextPrimary.copy(alpha = 0.16f)
            else      -> HanaBiColors.TextPrimary.copy(alpha = 0.05f)
        },
        animationSpec = tween(150),
        label = "container"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) HanaBiColors.TextPrimary else HanaBiColors.TextPrimary.copy(alpha = 0.18f),
        animationSpec = tween(150),
        label = "border"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusProperties { if (nextFocusDown != null) down = nextFocusDown }
            .onFocusChanged { state -> isFocused = state.hasFocus }
            .focusable()
            .onKeyEvent { event ->
                if (event.key == Key.DirectionCenter && event.type == KeyEventType.KeyDown) {
                    onToggle(); true
                } else false
            }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = HanaBiColors.TextPrimary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = stringResource(R.string.settings_show_password),
                color = HanaBiColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        PillSwitch(checked = visible)
    }
}

/** OS 設定アプリでよく見る pill 型スイッチ。視覚的フィードバック専用（フォーカスは親 Row が受ける） */
@Composable
private fun PillSwitch(checked: Boolean) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) HanaBiColors.SwitchOn else HanaBiColors.TextPrimary.copy(alpha = 0.28f),
        animationSpec = tween(180),
        label = "track"
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(180),
        label = "thumb"
    )
    Box(
        modifier = Modifier
            .size(width = 46.dp, height = 24.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(trackColor)
    ) {
        Box(
            modifier = Modifier
                .padding(3.dp)
                .offset(x = thumbOffset)
                .size(18.dp)
                .clip(CircleShape)
                .background(HanaBiColors.TextPrimary)
        )
    }
}
