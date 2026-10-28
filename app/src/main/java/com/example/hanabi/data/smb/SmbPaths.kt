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
