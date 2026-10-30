package com.example.hanabi.ui.theme

import androidx.compose.ui.graphics.Color

/** HanaBi の色定義（役割名で参照する） */
object HanaBiColors {
    // 背景
    /** 画面背景（ブラウザ・プレーヤー・接続設定） */
    val ScreenBackground = Color.Black
    /** 画面背景（設定メニュー系の画面） */
    val MenuBackground = Color(0xFF121212)
    /** ダイアログ等のパネル背景 */
    val DialogBackground = Color(0xFF1E1E1E)
    /** 半透明オーバーレイ・スクリムの基準色（alpha は用途側で指定） */
    val Scrim = Color.Black

    // ボタン・チップ
    /** チップ/ボタンの非選択背景・パネル背景 */
    val Surface = Color(0xFF333333)
    /** チップ/ボタンのフォーカス時背景 */
    val SurfaceFocused = Color(0xFF555555)
    /** 選択中（アクセント）。フォーカスのハイライトにも使う */
    val Accent = Color(0xFF1565C0)
    /** 選択中 + フォーカス */
    val AccentFocused = Color(0xFF1976D2)
    /** スイッチのオン色 */
    val SwitchOn = Color(0xFF60A5FA)

    // カード
    /** フォルダカードの背景 */
    val FolderCard = Color(0xFF1A237E)
    /** 動画カードの下地（サムネ読み込み前） */
    val VideoCardBase = Color(0xFF1B5E20)
    /** 視聴進捗バー */
    val ProgressBar = Color(0xFFFF0000)

    // 状態
    /** 成功 */
    val Success = Color(0xFF4CAF50)
    /** 有効状態のトグル背景 */
    val SuccessContainer = Color(0xFF1B5E20)
    /** 有効状態のトグル背景（フォーカス時） */
    val SuccessContainerFocused = Color(0xFF2E7D32)
    /** 警告 */
    val Warning = Color(0xFFFFB300)
    /** エラー */
    val Error = Color(0xFFEF5350)
    /** 破壊的操作（リセット等）の背景 */
    val Destructive = Color(0xFFB71C1C)
    /** 破壊的操作のフォーカス時背景 */
    val DestructiveFocused = Color(0xFFC62828)

    // テキスト
    val TextPrimary = Color.White
    /** 補助テキスト */
    val TextSecondary = Color.Gray
    /** プレースホルダー等 */
    val TextHint = Color.DarkGray
}
