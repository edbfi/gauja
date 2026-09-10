// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.persistence

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.datastore.secrets.SecretStore
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class SecretStoreTest {
    @get:Rule val hilt = HiltAndroidRule(this)
    @Inject lateinit var store: SecretStore

    @Test
    fun keystoreBackedSecretsRemainEncryptedAndProfileIsolated() = runBlocking {
        hilt.inject()
        val first =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "first",
                requireNotNull(ServerAddress.parse("https://first.example")),
            )
        val second =
            ServerProfile(
                ProfileId(UUID.randomUUID()),
                "second",
                requireNotNull(ServerAddress.parse("https://second.example")),
            )
        try {
            for (kind in SecretKind.entries) {
                store.write(first, kind, Secret("synthetic-first-credential".toByteArray()))
                store.write(second, kind, Secret("synthetic-second-credential".toByteArray()))
            }
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val bytes = context.noBackupFilesDir.resolve("datastore/secrets.pb").readBytes()
            assertFalse(bytes.toString(Charsets.ISO_8859_1).contains("synthetic-"))
            store.clear(first.id)
            for (kind in SecretKind.entries) {
                assertNull(store.read(first, kind))
                assertEquals(
                    "synthetic-second-credential",
                    store.read(second, kind)?.useBytes { it.toString(Charsets.UTF_8) },
                )
            }
        } finally {
            store.clear(first.id)
            store.clear(second.id)
        }
    }
}
