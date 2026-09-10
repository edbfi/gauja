// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package app.gauja.core.datastore.crypto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.KSerializer
import kotlinx.serialization.protobuf.ProtoBuf

internal class EncryptedSerializer<T>(
    override val defaultValue: T,
    private val serializer: KSerializer<T>,
    private val cipher: StorageCipher,
    private val alias: String,
) : Serializer<T> {
    override suspend fun readFrom(input: InputStream): T {
        try {
            val plaintext = cipher.decrypt(alias, input.readBytes())
            return try {
                ProtoBuf.decodeFromByteArray(serializer, plaintext)
            } finally {
                plaintext.fill(0)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Never replace unreadable secrets with an empty store or expose crypto/decoder
            // details.
            throw CorruptionException("Secure storage could not be read")
        }
    }

    override suspend fun writeTo(t: T, output: OutputStream) {
        val plaintext = ProtoBuf.encodeToByteArray(serializer, t)
        try {
            output.write(cipher.encrypt(alias, plaintext))
        } finally {
            plaintext.fill(0)
        }
    }
}
