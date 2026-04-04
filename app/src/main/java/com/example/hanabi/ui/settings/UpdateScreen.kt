package com.example.hanabi.ui.settings

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Text
import com.example.hanabi.BuildConfig
import com.example.hanabi.viewmodel.SettingsViewModel
import com.example.hanabi.viewmodel.UpdateState

/** アップデート確認画面 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun UpdateScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    BackHandler { onBack() }

    val updateState by viewModel.updateState.collectAsState()
    val context = LocalContext.current
    val firstFocus = remember { FocusRequester() }

    // ダウンロード完了時にシステムインストーラーを起動
    LaunchedEffect(updateState) {
        if (updateState is UpdateState.ReadyToInstall) {
            val apkFile = (updateState as UpdateState.ReadyToInstall).apkFile
            val uri = FileProvider.getUriForFile(
                context,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            viewModel.resetUpdateState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .width(400.dp)
                .padding(32.dp)
        ) {
            Text(
                text = "アップデート確認",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = "現在のバージョン: v${BuildConfig.VERSION_NAME}",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (val state = updateState) {
                is UpdateState.Idle -> {
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2A2A),
                            focusedContainerColor = Color(0xFF1565C0),
                        )
                    ) {
                        Text("アップデートを確認", color = Color.White, fontSize = 16.sp)
                    }
                }

                is UpdateState.Checking -> {
                    Text(
                        text = "確認中...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.UpToDate -> {
                    Text(
                        text = "最新バージョンを使用しています",
                        color = Color(0xFF4CAF50),
                        fontSize = 16.sp
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.resetUpdateState(); onBack() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2A2A),
                            focusedContainerColor = Color(0xFF444444),
                        )
                    ) {
                        Text("戻る", color = Color.White, fontSize = 16.sp)
                    }
                }

                is UpdateState.UpdateAvailable -> {
                    Text(
                        text = "${state.version} が利用可能です",
                        color = Color(0xFFFFB300),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.downloadAndInstall(state.downloadUrl) },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF1565C0),
                            focusedContainerColor = Color(0xFF1976D2),
                        )
                    ) {
                        Text("ダウンロードしてインストール", color = Color.White, fontSize = 16.sp)
                    }
                }

                is UpdateState.Downloading -> {
                    Text(
                        text = "ダウンロード中...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.ReadyToInstall -> {
                    Text(
                        text = "インストーラーを起動しています...",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.Error -> {
                    Text(
                        text = "エラー: ${state.message}",
                        color = Color(0xFFEF5350),
                        fontSize = 14.sp
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = Color(0xFF2A2A2A),
                            focusedContainerColor = Color(0xFF1565C0),
                        )
                    ) {
                        Text("再試行", color = Color.White, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
