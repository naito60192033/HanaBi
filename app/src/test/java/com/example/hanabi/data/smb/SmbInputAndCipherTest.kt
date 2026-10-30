package com.example.hanabi.data.smb

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SmbInputAndCipherTest {
    @Test fun normalizeHost_smbUrl() =
        assertEquals("192.168.1.10", normalizeHost(" smb://192.168.1.10/video/ "))

    @Test fun normalizeHost_unc() = assertEquals("nas", normalizeHost("\\\\nas\\video"))

    @Test fun normalizeHost_plain() = assertEquals("nas.local", normalizeHost("  nas.local "))

    @Test fun normalizeHost_trailingSlash() = assertEquals("nas", normalizeHost("nas/"))

    @Test fun normalizeHost_upperScheme() = assertEquals("nas", normalizeHost("SMB://nas/x"))

    @Test fun normalizeHost_empty() = assertEquals("", normalizeHost("  "))

    @Test fun normalizeShare_trims() = assertEquals("video", normalizeShare(" /video\\ "))

    @Test fun normalizeShare_keepsInner() = assertEquals("a/b", normalizeShare("/a/b/"))

    @Test fun packUnpack_roundTrip() {
        val iv = ByteArray(12) { it.toByte() }
        val ct = ByteArray(20) { (it + 100).toByte() }
        val (iv2, ct2) = unpackIvAndCipher(packIvAndCipher(iv, ct))
        assertArrayEquals(iv, iv2)
        assertArrayEquals(ct, ct2)
    }

    @Test fun unpack_invalid() {
        assertThrows(IllegalArgumentException::class.java) { unpackIvAndCipher(ByteArray(0)) }
        assertThrows(IllegalArgumentException::class.java) { unpackIvAndCipher(byteArrayOf(12, 1, 2)) }
    }
}
