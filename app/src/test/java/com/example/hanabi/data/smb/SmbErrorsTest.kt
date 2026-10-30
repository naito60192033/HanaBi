package com.example.hanabi.data.smb

import jcifs.smb.NtStatus
import jcifs.smb.newSmbAuthException
import jcifs.smb.SmbException
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SmbErrorsTest {
    private fun smb(status: Int) = SmbException(status, false)

    @Test fun auth() = assertEquals(
        "ユーザー名またはパスワードが違います",
        smbErrorMessage(newSmbAuthException("denied"))
    )

    @Test fun badNetworkName() = assertEquals(
        "共有フォルダが見つかりません", smbErrorMessage(smb(NtStatus.NT_STATUS_BAD_NETWORK_NAME))
    )

    @Test fun objectNameNotFound() = assertEquals(
        "フォルダが見つかりません", smbErrorMessage(smb(NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND))
    )

    @Test fun objectPathNotFound() = assertEquals(
        "フォルダが見つかりません", smbErrorMessage(smb(NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND))
    )

    @Test fun accessDenied() = assertEquals(
        "アクセス権がありません", smbErrorMessage(smb(NtStatus.NT_STATUS_ACCESS_DENIED))
    )

    @Test fun unknownHost() = assertEquals(
        "ホストが見つかりません（ホスト名・IPアドレスを確認してください）",
        smbErrorMessage(UnknownHostException("x"))
    )

    @Test fun networkErrors() {
        val msg = "NASに接続できません（電源・ネットワークを確認してください）"
        assertEquals(msg, smbErrorMessage(ConnectException("x")))
        assertEquals(msg, smbErrorMessage(SocketTimeoutException("x")))
        assertEquals(msg, smbErrorMessage(NoRouteToHostException("x")))
    }

    @Test fun causeChain() = assertEquals(
        "ホストが見つかりません（ホスト名・IPアドレスを確認してください）",
        smbErrorMessage(SmbException("wrap", RuntimeException("mid", UnknownHostException("h"))))
    )

    @Test fun unknown_doesNotLeakMessage() = assertEquals(
        "接続エラーが発生しました", smbErrorMessage(IllegalStateException("secret detail"))
    )
}
