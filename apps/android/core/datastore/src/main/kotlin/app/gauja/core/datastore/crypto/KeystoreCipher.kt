// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

interface StorageCipher {
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray

    fun decrypt(alias: String, ciphertext: ByteArray): ByteArray
}

fun interface StorageKeys {
    fun key(alias: String, create: Boolean): SecretKey
}

@Singleton
class KeystoreKeys @Inject constructor() : StorageKeys {
    @Synchronized
    override fun key(alias: String, create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let {
            return it
        }
        check(create) { "Secure storage key unavailable" }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            .apply {
                init(
                    KeyGenParameterSpec.Builder(
                            alias,
                            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                        )
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build()
                )
            }
            .generateKey()
    }
}

@Singleton
class KeystoreCipher @Inject constructor(private val keys: StorageKeys) : StorageCipher {
    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keys.key(alias, true))
        cipher.updateAAD(alias.toByteArray())
        return byteArrayOf(1) + cipher.iv + cipher.doFinal(plaintext)
    }

    override fun decrypt(alias: String, ciphertext: ByteArray): ByteArray {
        require(ciphertext.size >= 29 && ciphertext[0] == 1.toByte())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            keys.key(alias, false),
            GCMParameterSpec(128, ciphertext.copyOfRange(1, 13)),
        )
        cipher.updateAAD(alias.toByteArray())
        return cipher.doFinal(ciphertext, 13, ciphertext.size - 13)
    }
}
