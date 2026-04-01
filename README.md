# HanaBi 🎆

Fire TV向け NAS動画プレイヤーアプリ。Synology NAS（SMB共有）に保存したMP4動画を、快適なTV UIで視聴できます。

## 特徴

- **Fire TVリモコンに最適化された操作感** — D-padによる直感的なシーク・再生操作
- **SMB対応** — Synology NAS等のSMB共有に直接接続
- **フォルダ階層ブラウジング** — NAS内のフォルダ構造をそのまま閲覧
- **続きから再生** — 視聴位置を自動保存し、次回起動時に続きから再生

## 動作環境

- **デバイス**: Amazon Fire TV Stick 4K / Fire TV Stick 4K Max
- **NAS**: Synology（SMB共有）

## 技術スタック

| 役割 | 技術 |
|------|------|
| 言語 | Kotlin |
| UI | Jetpack Compose for TV (TV Material3) |
| 動画再生 | Media3 (ExoPlayer) |
| NASアクセス | jcifs-ng (SMB2/3) |
| 画像読み込み | Coil |
| DI | Hilt |
| ローカルDB | Room（再生位置保存） |
| アーキテクチャ | MVVM + Clean Architecture |

## セットアップ

### 必要なもの

- Android Studio（最新版推奨）
- JDK 17以上
- Fire TV（開発者モード有効化済み）
- SMB共有が有効なNAS

### ビルド手順

1. リポジトリをクローン
   ```bash
   git clone https://github.com/naito60192033/HanaBi.git
   ```

2. Android Studioでプロジェクトを開く

3. Gradle同期完了後、Fire TVをADB接続してRunを実行

### アプリ内設定

初回起動時に設定画面でNASの接続情報を入力してください：

| 項目 | 説明 | 例 |
|------|------|----|
| ホスト名 / IPアドレス | NASのIPまたはホスト名 | `192.168.1.100` |
| 共有フォルダ名 | SMB共有名 | `video` |
| ユーザー名 | SMBユーザー名（省略可） | `admin` |
| パスワード | SMBパスワード（省略可） | |

## リリース

[Releases](https://github.com/naito60192033/HanaBi/releases) から最新の `.apk` ファイルをダウンロードし、Fire TVにサイドロードしてください。

## ライセンス

MIT
