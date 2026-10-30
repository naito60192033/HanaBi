package com.example.hanabi.ui

import android.view.KeyEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState

/**
 * Activity 最上位（dispatchKeyEvent）で呼ばれるキーハンドラーの登録口。
 *
 * 後から登録したハンドラーが先に呼ばれる。解除は register が返したトークン
 * （ハンドラー自身の identity）で行うため、自動次再生の popUpTo のように
 * 「新画面の登録 → 旧画面の解除」の順になっても新画面のハンドラーは消えない。
 * メインスレッド専用。
 */
class KeyHandlerRegistry {
    /** 登録順のリスト（末尾が最新） */
    private val handlers = mutableListOf<(KeyEvent) -> Boolean>()

    /** ハンドラーを登録する。戻り値のトークンを unregister に渡して解除する */
    fun register(handler: (KeyEvent) -> Boolean): (KeyEvent) -> Boolean {
        handlers.add(handler)
        return handler
    }

    /** register が返したトークンのハンドラーだけを解除する（未登録なら何もしない） */
    fun unregister(token: (KeyEvent) -> Boolean) {
        handlers.removeAll { it === token }
    }

    /** 新しい登録順に呼び、いずれかが処理したら true */
    fun dispatch(event: KeyEvent): Boolean {
        for (handler in handlers.toList().asReversed()) {
            if (handler(event)) return true
        }
        return false
    }
}

/** MainActivity が setContent 内で提供するレジストリ */
val LocalKeyHandlers = compositionLocalOf<KeyHandlerRegistry?> { null }

/**
 * コンポジションに入っている間だけ Activity 最上位のキーハンドラーを登録する。
 * onKey は rememberUpdatedState 経由で呼ぶので、常に最新のラムダ（最新の状態）が使われる。
 */
@Composable
fun ActivityKeyHandler(onKey: (KeyEvent) -> Boolean) {
    val registry = LocalKeyHandlers.current
    val currentOnKey by rememberUpdatedState(onKey)
    DisposableEffect(registry) {
        val token = registry?.register { event -> currentOnKey(event) }
        onDispose { if (token != null) registry.unregister(token) }
    }
}
