// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.AuthMethod
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.testing.FakeClock
import app.gauja.core.testing.MemorySecrets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OriginChangeTest {
    @Test
    fun changingOriginDoesNotReuseStoredCredentials() = runTest {
        MockWebServer().use { first ->
            MockWebServer().use { second ->
                first.start()
                second.start()
                val secrets = MemorySecrets()
                val original =
                    ServerProfile(
                        ProfileId(UUID.randomUUID()),
                        "Original",
                        requireNotNull(ServerAddress.parse(first.url("/").toString())),
                        authMethod = AuthMethod.API_KEY,
                        basicAuthUsername = "operator",
                        operatorAcknowledged = true,
                    )
                val changed =
                    original.copy(
                        address = requireNotNull(ServerAddress.parse(second.url("/").toString()))
                    )
                secrets.write(original, SecretKind.API_KEY, Secret("synthetic-key".toByteArray()))
                secrets.write(
                    original,
                    SecretKind.BASIC_AUTH_PASSWORD,
                    Secret("synthetic-password".toByteArray()),
                )
                val factory =
                    ProfileTransport(secrets, DeprecationStore(), FakeClock(), Dispatchers.IO)
                first.enqueue(MockResponse.Builder().build())
                factory.withProfile(original) {
                    it.newCall(Request.Builder().url(first.url("/")).build()).execute().close()
                }
                assertEquals("synthetic-key", first.takeRequest().headers["X-Api-Key"])
                second.enqueue(MockResponse.Builder().build())
                factory.withProfile(changed) {
                    it.newCall(Request.Builder().url(second.url("/")).build()).execute().close()
                }
                val received = second.takeRequest()
                val restarted =
                    ProfileTransport(secrets, DeprecationStore(), FakeClock(), Dispatchers.IO)
                second.enqueue(MockResponse.Builder().build())
                restarted.withProfile(changed) {
                    it.newCall(Request.Builder().url(second.url("/")).build()).execute().close()
                }
                val afterRestart = second.takeRequest()
                assertNull(afterRestart.headers["X-Api-Key"])
                assertNull(afterRestart.headers["Authorization"])
                assertNull(afterRestart.headers["Cookie"])
                restarted.delete(original.id) {}
                factory.delete(original.id) {}
                assertNull(received.headers["X-Api-Key"])
                assertNull(received.headers["Authorization"])
            }
        }
    }
}
