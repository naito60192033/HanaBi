package com.example.hanabi

import android.os.Bundle
import android.view.KeyEvent
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
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.hanabi.ui.browser.BrowserScreen
import com.example.hanabi.ui.player.PlayerScreen
import com.example.hanabi.ui.settings.AppSettingsScreen
import com.example.hanabi.ui.settings.SettingsMenuScreen
import com.example.hanabi.ui.settings.SettingsScreen
import com.example.hanabi.ui.settings.UpdateScreen
import com.example.hanabi.ui.theme.HanaBiTheme
import com.example.hanabi.viewmodel.BrowserViewModel
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

    /**
     * Activity最上位でキーイベントを捕捉する。
     * PlayerScreenが登録したハンドラーを優先処理することで、
     * ExoPlayer内部やMediaSessionに横取りされる前に中央ボタン等を処理できる。
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (playerKeyHandler?.invoke(event) == true) return true
        if (menuKeyHandler?.invoke(event) == true) return true
        return super.dispatchKeyEvent(event)
    }

    companion object {
        /** PlayerScreen がアクティブな間だけ登録されるキーハンドラー */
        var playerKeyHandler: ((KeyEvent) -> Boolean)? = null
        /** BrowserScreen がアクティブな間だけ登録されるメニューキーハンドラー */
        var menuKeyHandler: ((KeyEvent) -> Boolean)? = null
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
                    navController.navigate("settings_menu")
                }
            )
        }

        // 動画プレイヤー画面
        composable("player/{smbPath}") { backStackEntry ->
            val smbPath = backStackEntry.arguments?.getString("smbPath")?.decodeFromNav() ?: ""
            val browserEntry = remember(navController) { navController.getBackStackEntry("browser") }
            val browserVm: BrowserViewModel = hiltViewModel(browserEntry)
            PlayerScreen(
                smbPath = smbPath,
                onBack = {
                    // 現在再生中の動画にフォーカスを当ててからブラウザへ戻る
                    browserVm.setFocusOnReturn(smbPath)
                    navController.popBackStack("browser", inclusive = false)
                },
                onNavigateToPlayer = { nextPath ->
                    // 現在のプレイヤーをスタックせず置き換える（戻るボタンでブラウザに戻れるように）
                    navController.navigate("player/${nextPath.encodeForNav()}") {
                        popUpTo("player/{smbPath}") { inclusive = true }
                    }
                }
            )
        }

        // 設定メニュー画面
        composable("settings_menu") {
            SettingsMenuScreen(
                onNavigateToNasSettings = { navController.navigate("settings") },
                onNavigateToAppSettings = { navController.navigate("app_settings") },
                onNavigateToUpdate = { navController.navigate("update_check") },
                onBack = { navController.popBackStack() }
            )
        }

        // NAS接続設定画面
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // アプリ再生設定画面
        composable("app_settings") {
            AppSettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }

        // アップデート確認画面
        composable("update_check") {
            UpdateScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/** ナビゲーション引数でスラッシュが使えるようにエンコード */
private fun String.encodeForNav() = replace("/", "|")
private fun String.decodeFromNav() = replace("|", "/")
