// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.crypto

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStoreFactory
import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.EncryptedSecretStore
import app.gauja.core.datastore.secrets.SecretDocument
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.datastore.secrets.SecretRecord
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
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
import org.junit.Assert.assertTrue
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
        val a =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "a",
                requireNotNull(ServerAddress.parse("https://a.example")),
            )
        val b =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "b",
                requireNotNull(ServerAddress.parse("https://b.example")),
            )
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
            val changed =
                a.copy(address = requireNotNull(ServerAddress.parse("https://changed.example")))
            val renamed =
                a.copy(
                    displayName = "Renamed",
                    address = requireNotNull(ServerAddress.parse("https://A.example:443/path")),
                )
            assertNull(second.read(changed, SecretKind.SESSION_COOKIE))
            assertEquals(
                "first-session",
                second.read(renamed, SecretKind.SESSION_COOKIE)?.useBytes {
                    it.toString(Charsets.UTF_8)
                },
            )
            second.clear(a.id)
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
    fun legacyUnboundSecretsAreNeverAdopted() = runTest {
        val key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val profile =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "Test",
                requireNotNull(ServerAddress.parse("https://example.test")),
            )
        val legacy =
            SecretDocument(
                SecretKind.entries.map {
                    SecretRecord(
                        profile.id.value.toString(),
                        it.name,
                        "synthetic-legacy".toByteArray(),
                    )
                }
            )
        val serializer =
            EncryptedSerializer(
                legacy,
                SecretDocument.serializer(),
                KeystoreCipher { _, _ -> key },
                "test",
            )
        val job = SupervisorJob()
        val data =
            DataStoreFactory.create(
                serializer,
                scope = CoroutineScope(job + StandardTestDispatcher(testScheduler)),
                produceFile = { folder.root.resolve("legacy.pb") },
            )
        try {
            val store = EncryptedSecretStore(data)
            for (kind in SecretKind.entries) assertNull(store.read(profile, kind))
            store.clear(profile.id)
            assertTrue(data.data.first().records.isEmpty())
        } finally {
            job.cancel()
            job.join()
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
