package com.example.hanabi.data.smb

/** NAS上のファイル/フォルダを表すデータクラス */
data class SmbEntry(
    val name: String,
    val path: String,        // smb://host/share/path 形式
    val isDirectory: Boolean,
    val size: Long = 0L,
    val lastModified: Long = 0L
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
