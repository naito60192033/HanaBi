package com.example.hanabi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.hanabi.ui.browser.BrowserScreen
import com.example.hanabi.ui.player.PlayerScreen
import com.example.hanabi.ui.settings.SettingsScreen
import com.example.hanabi.ui.theme.HanaBiTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HanaBiTheme {
                HanaBiNavHost()
            }
        }
    }
}

/** アプリ全体のナビゲーション定義 */
@Composable
fun HanaBiNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "browser",
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // フォルダ・ファイルブラウザ画面
        composable("browser") {
            BrowserScreen(
                onNavigateToPlayer = { smbPath ->
                    navController.navigate("player/${smbPath.encodeForNav()}")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        // 動画プレイヤー画面
        composable("player/{smbPath}") { backStackEntry ->
            val smbPath = backStackEntry.arguments?.getString("smbPath")?.decodeFromNav() ?: ""
            PlayerScreen(
                smbPath = smbPath,
                onBack = { navController.popBackStack() }
            )
        }

        // NAS接続設定画面
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** ナビゲーション引数でスラッシュが使えるようにエンコード */
private fun String.encodeForNav() = replace("/", "|")
private fun String.decodeFromNav() = replace("|", "/")
