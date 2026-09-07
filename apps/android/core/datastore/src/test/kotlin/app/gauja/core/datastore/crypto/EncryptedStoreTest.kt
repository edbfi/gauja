// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.crypto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.EncryptedSecretStore
import app.gauja.core.datastore.secrets.SecretDocument
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.model.servers.ProfileId
import java.util.UUID
import javax.crypto.KeyGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class EncryptedStoreTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun encryptedSecretsSurviveReopenAndWipeOnlyOneProfile() = runTest {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = KeystoreCipher { _, _ -> key }
        val serializer =
            EncryptedSerializer(SecretDocument(), SecretDocument.serializer(), cipher, "test")
        val file = folder.root.resolve("secrets.pb")
        val firstJob = SupervisorJob()
        val first =
            EncryptedSecretStore(
                DataStoreFactory.create(
                    serializer,
                    scope = CoroutineScope(firstJob + StandardTestDispatcher(testScheduler)),
                    produceFile = { file },
                )
            )
        val a = ProfileId(UUID.randomUUID())
        val b = ProfileId(UUID.randomUUID())
        first.write(a, SecretKind.SESSION_COOKIE, Secret("first-session".toByteArray()))
        first.write(b, SecretKind.API_KEY, Secret("second-operator".toByteArray()))
        assertFalse(file.readText().contains("first-session"))
        assertFalse(file.readText().contains("second-operator"))
        firstJob.cancel()
        firstJob.join()

        val secondJob = SupervisorJob()
        val second =
            EncryptedSecretStore(
                DataStoreFactory.create(
                    serializer,
                    scope = CoroutineScope(secondJob + StandardTestDispatcher(testScheduler)),
                    produceFile = { file },
                )
            )
        try {
            assertEquals(
                "first-session",
                second.read(a, SecretKind.SESSION_COOKIE)?.useBytes { it.toString(Charsets.UTF_8) },
            )
            second.clear(a)
            assertNull(second.read(a, SecretKind.SESSION_COOKIE))
            assertEquals(
                "second-operator",
                second.read(b, SecretKind.API_KEY)?.useBytes { it.toString(Charsets.UTF_8) },
            )
        } finally {
            secondJob.cancel()
            secondJob.join()
        }
    }

    @Test
    fun corruptCiphertextCannotBecomeAnEmptySecretStore() = runTest {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val cipher = KeystoreCipher { _, _ -> key }
        val serializer =
            EncryptedSerializer(SecretDocument(), SecretDocument.serializer(), cipher, "test")
        val bytes = cipher.encrypt("test", byteArrayOf(0))
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        try {
            serializer.readFrom(bytes.inputStream())
            fail("Corrupted storage was accepted")
        } catch (error: CorruptionException) {
            assertEquals("Secure storage could not be read", error.message)
        }
    }
}
