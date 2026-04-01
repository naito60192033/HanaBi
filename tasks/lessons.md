# Lessons（修正から学んだルール）

## Android / Fire TV

- ビルドは Claude から実行できないため、コード変更後は必ずユーザーに Android Studio でのビルド・実機確認を依頼する
- 「一時停止時に画面が暗くなる」はスクリーンスリープではなく ExoPlayer の PlayerView コントローラーオーバーレイが原因。再生再開時に `hideController()` を呼ぶのが正解（`keepScreenOn` や Window フラグは無関係）

## 環境

- Android Studio は WSLパス（`\\wsl.localhost\...`）からプロジェクトを開けない。必ず Windows ネイティブパス（`I:\dev\HanaBi`）を使う
