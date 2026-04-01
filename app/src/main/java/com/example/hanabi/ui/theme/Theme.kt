package com.example.hanabi.ui.theme

import androidx.compose.runtime.Composable
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/** HanaBi アプリのテーマ定義 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun HanaBiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(),
        content = content
    )
}
