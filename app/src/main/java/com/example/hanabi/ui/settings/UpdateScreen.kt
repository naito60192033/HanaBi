package com.example.hanabi.ui.settings

import com.example.hanabi.ui.theme.HanaBiColors
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.res.stringResource
import com.example.hanabi.R
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
    val includeBeta by viewModel.includeBetaUpdates.collectAsState()
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
            .background(HanaBiColors.MenuBackground),
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
                text = stringResource(R.string.update_title),
                color = HanaBiColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = stringResource(R.string.update_current_version, BuildConfig.VERSION_NAME),
                color = HanaBiColors.TextSecondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when (val state = updateState) {
                is UpdateState.Idle -> {
                    Button(
                        onClick = { viewModel.setIncludeBetaUpdates(!includeBeta) },
                        colors = ButtonDefaults.colors(
                            containerColor = if (includeBeta) HanaBiColors.SuccessContainer else HanaBiColors.Surface,
                            focusedContainerColor = if (includeBeta) HanaBiColors.SuccessContainerFocused else HanaBiColors.SurfaceFocused,
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.update_include_beta, if (includeBeta) "ON" else "OFF"),
                            color = if (includeBeta) HanaBiColors.Success else HanaBiColors.TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = HanaBiColors.Surface,
                            focusedContainerColor = HanaBiColors.Accent,
                        )
                    ) {
                        Text(stringResource(R.string.update_check), color = HanaBiColors.TextPrimary, fontSize = 16.sp)
                    }
                }

                is UpdateState.Checking -> {
                    Text(
                        text = stringResource(R.string.update_checking),
                        color = HanaBiColors.TextPrimary,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.UpToDate -> {
                    Text(
                        text = stringResource(R.string.update_latest),
                        color = HanaBiColors.Success,
                        fontSize = 16.sp
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.resetUpdateState(); onBack() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = HanaBiColors.Surface,
                            focusedContainerColor = HanaBiColors.SurfaceFocused,
                        )
                    ) {
                        Text(stringResource(R.string.update_back), color = HanaBiColors.TextPrimary, fontSize = 16.sp)
                    }
                }

                is UpdateState.UpdateAvailable -> {
                    Text(
                        text = stringResource(R.string.update_available, state.version),
                        color = HanaBiColors.Warning,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.downloadAndInstall(state.downloadUrl) },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = HanaBiColors.Accent,
                            focusedContainerColor = HanaBiColors.AccentFocused,
                        )
                    ) {
                        Text(stringResource(R.string.update_download_install), color = HanaBiColors.TextPrimary, fontSize = 16.sp)
                    }
                }

                is UpdateState.Downloading -> {
                    Text(
                        text = stringResource(R.string.update_downloading),
                        color = HanaBiColors.TextPrimary,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.ReadyToInstall -> {
                    Text(
                        text = stringResource(R.string.update_launching),
                        color = HanaBiColors.TextPrimary,
                        fontSize = 16.sp
                    )
                }

                is UpdateState.Error -> {
                    Text(
                        text = stringResource(R.string.update_error, state.message),
                        color = HanaBiColors.Error,
                        fontSize = 14.sp
                    )
                    LaunchedEffect(Unit) { firstFocus.requestFocus() }
                    Button(
                        onClick = { viewModel.checkForUpdate() },
                        modifier = Modifier.focusRequester(firstFocus),
                        colors = ButtonDefaults.colors(
                            containerColor = HanaBiColors.Surface,
                            focusedContainerColor = HanaBiColors.Accent,
                        )
                    ) {
                        Text(stringResource(R.string.update_retry), color = HanaBiColors.TextPrimary, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}
