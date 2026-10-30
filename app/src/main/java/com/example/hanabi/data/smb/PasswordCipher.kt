package com.example.hanabi.data.smb

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * パスワードを AndroidKeyStore の AES-256/GCM 鍵で暗号化・復号する。
 * 保存形式は Base64(NO_WRAP) 文字列（中身は [packIvAndCipher] の形式）。
 */
class PasswordCipher {

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build()
        )
        return generator.generateKey()
    }

    /** 暗号化して保存用文字列にする。失敗時は例外（呼び出し側で扱う） */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(packIvAndCipher(cipher.iv, ct), Base64.NO_WRAP)
    }

    /** 復号する。鍵の消失・破損などで失敗したら空文字を返す */
    fun decrypt(stored: String): String = try {
        val (iv, ct) = unpackIvAndCipher(Base64.decode(stored, Base64.NO_WRAP))
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ct), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.w(TAG, "パスワードの復号に失敗しました", e)
        ""
    }

    private companion object {
        const val TAG = "PasswordCipher"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "hanabi_smb_password"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}

/** IV と暗号文を [IV長(1byte)][IV][暗号文] の形で連結する（Android API 非依存） */
internal fun packIvAndCipher(iv: ByteArray, cipherText: ByteArray): ByteArray {
    require(iv.size in 1..255) { "IV 長が不正です" }
    return byteArrayOf(iv.size.toByte()) + iv + cipherText
}

/** [packIvAndCipher] の逆変換。形式が不正なら IllegalArgumentException */
internal fun unpackIvAndCipher(packed: ByteArray): Pair<ByteArray, ByteArray> {
    require(packed.isNotEmpty()) { "データが空です" }
    val ivLen = packed[0].toInt() and 0xFF
    require(ivLen >= 1 && packed.size > 1 + ivLen) { "データ形式が不正です" }
    return packed.copyOfRange(1, 1 + ivLen) to packed.copyOfRange(1 + ivLen, packed.size)
}
