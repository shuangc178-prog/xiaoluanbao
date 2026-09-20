package com.example.campuslife.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** WebDAV 应用密码只以 AES-GCM 密文落盘；密钥由 Android Keystore 持有、不可导出。 */
internal class CredentialCipher {
    fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey())
            updateAAD(ASSOCIATED_DATA)
        }
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val encoder = Base64.getUrlEncoder().withoutPadding()
        return listOf(
            CIPHER_VERSION,
            encoder.encodeToString(cipher.iv),
            encoder.encodeToString(encrypted),
        ).joinToString(":")
    }

    fun decrypt(stored: String): String {
        val parts = stored.split(':')
        if (parts.size != 3 || parts[0] != CIPHER_VERSION) {
            throw IllegalArgumentException("无法识别的密码密文")
        }
        val decoder = Base64.getUrlDecoder()
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(GCM_TAG_BITS, decoder.decode(parts[1])),
            )
            updateAAD(ASSOCIATED_DATA)
        }
        return String(cipher.doFinal(decoder.decode(parts[2])), Charsets.UTF_8)
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEY_STORE).run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private companion object {
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val KEY_ALIAS = "xiaoluanbao_nutstore_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val CIPHER_VERSION = "1"
        const val GCM_TAG_BITS = 128
        val ASSOCIATED_DATA = "xiaoluanbao-webdav-password".toByteArray(Charsets.UTF_8)
    }
}
