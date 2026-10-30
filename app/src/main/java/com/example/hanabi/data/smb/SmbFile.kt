package com.example.hanabi.data.smb

/** NAS上のファイル/フォルダを表すデータクラス */
data class SmbEntry(
    val name: String,
    val path: String,        // smb://host/share/path 形式
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L,
    val thumbnail: SmbThumbnailKey? = null  // サムネイル画像の指定（動画は自身、フォルダは直下の画像。未取得なら null）
) {
    /** 動画ファイルかどうか（拡張子で判定） */
    val isVideo: Boolean
        get() = !isDirectory && name.lowercase().let { n ->
            VIDEO_EXTENSIONS.any { n.endsWith(it) }
        }

    companion object {
        private val VIDEO_EXTENSIONS = setOf(
            ".mp4", ".mkv", ".avi", ".mov", ".m4v", ".ts", ".m2ts", ".wmv", ".flv"
        )
    }
}
