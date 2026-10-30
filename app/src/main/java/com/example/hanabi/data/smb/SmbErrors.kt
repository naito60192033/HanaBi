package com.example.hanabi.data.smb

import jcifs.smb.NtStatus
import jcifs.smb.SmbAuthException
import jcifs.smb.SmbException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 例外を利用者向けの短い日本語メッセージに分類する（Android API 非依存の純粋関数）。
 * 原因チェーン（cause）を根元までたどり、最初に該当した種別を採用する。
 * 生の例外メッセージは返さない。
 */
internal fun smbErrorMessage(e: Throwable): String {
    var t: Throwable? = e
    var depth = 0
    while (t != null && depth < 16) {
        classify(t)?.let { return it }
        t = t.cause?.takeIf { it !== t }
        depth++
    }
    return "接続エラーが発生しました"
}

private fun classify(t: Throwable): String? = when (t) {
    is SmbAuthException -> "ユーザー名またはパスワードが違います"
    is SmbException -> when (t.ntStatus) {
        NtStatus.NT_STATUS_BAD_NETWORK_NAME -> "共有フォルダが見つかりません"
        NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND,
        NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND -> "フォルダが見つかりません"
        NtStatus.NT_STATUS_ACCESS_DENIED -> "アクセス権がありません"
        else -> null
    }
    is UnknownHostException -> "ホストが見つかりません（ホスト名・IPアドレスを確認してください）"
    is ConnectException, is SocketTimeoutException, is NoRouteToHostException ->
        "NASに接続できません（電源・ネットワークを確認してください）"
    else -> null
}
