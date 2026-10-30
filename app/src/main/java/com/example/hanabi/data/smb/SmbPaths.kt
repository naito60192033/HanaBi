package com.example.hanabi.data.smb

/**
 * SMB URL のパス分解（Android API に依存しない純粋関数）。
 * 形式: smb://<authority>/<share>/<rest>
 */

private const val SMB_PREFIX = "smb://"

/**
 * `smb://<authority>/<share>/<rest>` の `<rest>` を前後の `/` を除いて返す。
 * host・share 名の文字列一致には頼らず、セグメント位置（1つ目=authority、2つ目=share）で切る。
 * `smb://` で始まらない・share 以下が無い場合は ""。
 */
internal fun relativePathOf(smbUrl: String): String {
    if (!smbUrl.startsWith(SMB_PREFIX)) return ""
    val afterScheme = smbUrl.substring(SMB_PREFIX.length)
    val authorityEnd = afterScheme.indexOf('/')
    if (authorityEnd < 0) return ""
    val afterAuthority = afterScheme.substring(authorityEnd + 1)
    val shareEnd = afterAuthority.indexOf('/')
    if (shareEnd < 0) return ""
    return afterAuthority.substring(shareEnd + 1).trim('/')
}

/** 相対パスのディレクトリ部（親フォルダ）。無ければ "" */
internal fun parentDirOf(relativePath: String): String =
    relativePath.substringBeforeLast('/', "")

/** 相対パスのファイル名（最後のセグメント） */
internal fun fileNameOf(relativePath: String): String =
    relativePath.substringAfterLast('/')

/** 動画サムネ抽出ロジックのバージョン接尾辞（抽出方法を変えたらキャッシュ破棄のため上げる） */
private const val VIDEO_THUMB_VERSION = "thumb_v3"

/**
 * サムネイルのディスクキャッシュキー（Coil の Keyer と Fetcher で共通利用）。
 * 更新日時を含めることでファイル更新時に古いサムネを使わない。動画はバージョン接尾辞を付ける。
 */
internal fun thumbnailCacheKey(path: String, lastModified: Long, isVideo: Boolean): String =
    if (isVideo) "$path#$lastModified#$VIDEO_THUMB_VERSION" else "$path#$lastModified"

/** Synology が生成するサムネイル画像ファイル名（優先順） */
private val SYNOLOGY_THUMB_NAMES = listOf(
    "SYNOVIDEO_VIDEO_SCREENSHOT.jpg",
    "SYNOPHOTO_THUMB_XL.jpg",
    "SYNOPHOTO_THUMB_M.jpg"
)

/**
 * 動画の SMB URL から Synology の `@eaDir` 内サムネイル候補パスを優先順に返す。
 * 例: smb://h/s/dir/a.mp4 → smb://h/s/dir/@eaDir/a.mp4/SYNOVIDEO_VIDEO_SCREENSHOT.jpg ...
 */
internal fun synologyThumbnailCandidates(videoPath: String): List<String> {
    val slash = videoPath.lastIndexOf('/')
    if (slash < 0) return emptyList()
    val dir = videoPath.substring(0, slash)
    val fileName = videoPath.substring(slash + 1)
    if (fileName.isEmpty()) return emptyList()
    return SYNOLOGY_THUMB_NAMES.map { "$dir/@eaDir/$fileName/$it" }
}

/**
 * ホスト名入力の正規化。前後空白・先頭の `smb://` / `\\` を除き、最初の `/` または `\` 以降を捨てる。
 * 例: " smb://192.168.1.10/video/ " → "192.168.1.10"
 */
internal fun normalizeHost(input: String): String {
    var s = input.trim()
    if (s.startsWith(SMB_PREFIX, ignoreCase = true)) s = s.substring(SMB_PREFIX.length)
    s = s.trimStart('\\', '/')
    val end = s.indexOfFirst { it == '/' || it == '\\' }
    if (end >= 0) s = s.substring(0, end)
    return s.trim()
}

/** 共有名入力の正規化。前後空白と前後の `/`・`\` を除く */
internal fun normalizeShare(input: String): String =
    input.trim().trim('/', '\\').trim()
