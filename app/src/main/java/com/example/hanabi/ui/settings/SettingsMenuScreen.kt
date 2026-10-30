package com.example.hanabi.ui.settings

import com.example.hanabi.ui.theme.HanaBiColors
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.res.stringResource
import com.example.hanabi.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Text

/** 設定メニュー画面（メニューボタンから遷移） */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsMenuScreen(
    onNavigateToNasSettings: () -> Unit,
    onNavigateToAppSettings: () -> Unit,
    onNavigateToUpdate: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler { onBack() }

    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HanaBiColors.MenuBackground),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(360.dp)
        ) {
            Text(
                text = stringResource(R.string.menu_title),
                color = HanaBiColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            MenuButton(
                label = stringResource(R.string.menu_nas),
                description = stringResource(R.string.menu_nas_desc),
                onClick = onNavigateToNasSettings,
                focusRequester = firstFocus
            )

            MenuButton(
                label = stringResource(R.string.menu_playback),
                description = stringResource(R.string.menu_playback_desc),
                onClick = onNavigateToAppSettings
            )

            MenuButton(
                label = stringResource(R.string.menu_update),
                description = stringResource(R.string.menu_update_desc),
                onClick = onNavigateToUpdate
            )
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MenuButton(
    label: String,
    description: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
) {
    val modifier = if (focusRequester != null) {
        Modifier.fillMaxWidth().focusRequester(focusRequester)
    } else {
        Modifier.fillMaxWidth()
    }

    Button(
        onClick = onClick,
        modifier = modifier,
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.colors(
            containerColor = HanaBiColors.Surface,
            contentColor = HanaBiColors.TextPrimary,
            focusedContainerColor = HanaBiColors.Accent,
            focusedContentColor = HanaBiColors.TextPrimary,
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                // 青いフォーカス背景でも読めるよう、灰色ではなく文字色を半透明にする
                color = LocalContentColor.current.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}
