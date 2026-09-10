// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.common.Secret
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.AuthMethod
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.testing.FakeClock
import app.gauja.core.testing.MemorySecrets
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileTransportTest {
    @Test
    fun cookieReopensAndUnauthorizedClearsOnlyItsProfile() = runTest {
        MockWebServer().use { server ->
            server.start()
            val secrets = MemorySecrets()
            val first = profile(server)
            val second = profile(server)
            val factory = ProfileTransport(secrets, DeprecationStore(), FakeClock(), Dispatchers.IO)
            server.enqueue(
                MockResponse.Builder()
                    .addHeader("Set-Cookie", "connect.sid=first; Path=/; Max-Age=2592000")
                    .build()
            )
            factory.withProfile(first) {
                it.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            assertTrue(secrets.read(first, SecretKind.SESSION_COOKIE) != null)
            server.enqueue(MockResponse.Builder().build())
            factory.withProfile(second) {
                it.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            server.takeRequest()
            assertNull(server.takeRequest().headers["Cookie"])
            val reopened =
                ProfileTransport(secrets, DeprecationStore(), FakeClock(), Dispatchers.IO)
            server.enqueue(MockResponse.Builder().code(401).build())
            reopened.withProfile(first) {
                it.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            assertEquals("connect.sid=first", server.takeRequest().headers["Cookie"])
            assertNull(secrets.read(first, SecretKind.SESSION_COOKIE))
            factory.delete(first.id) {}
            factory.delete(second.id) {}
            reopened.delete(first.id) {}
        }
    }

    @Test
    fun operatorUsesOnlyApiKeyAndBasicAndRejectsRedirect() = runTest {
        MockWebServer().use { server ->
            server.start()
            val secrets = MemorySecrets()
            val operator =
                profile(server)
                    .copy(
                        authMethod = AuthMethod.API_KEY,
                        operatorAcknowledged = true,
                        basicAuthUsername = "operator",
                    )
            secrets.write(operator, SecretKind.API_KEY, Secret("synthetic-key".toByteArray()))
            secrets.write(
                operator,
                SecretKind.BASIC_AUTH_PASSWORD,
                Secret("synthetic-password".toByteArray()),
            )
            secrets.write(
                operator,
                SecretKind.SESSION_COOKIE,
                Secret("connect.sid=must-not-send; Path=/".toByteArray()),
            )
            val diagnostics = DeprecationStore()
            val factory = ProfileTransport(secrets, diagnostics, FakeClock(), Dispatchers.IO)
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "https://example.invalid")
                    .addHeader("Set-Cookie", "connect.sid=must-ignore; Path=/")
                    .addHeader("Deprecation", "@123")
                    .addHeader("Sunset", "Wed, 01 Jul 2026 00:00:00 GMT")
                    .build()
            )
            factory.withProfile(operator) { client ->
                client
                    .newCall(
                        Request.Builder()
                            .url(server.url("/api/v1/auth/me"))
                            .header("X-API-User", "5")
                            .build()
                    )
                    .execute()
                    .use { assertEquals(302, it.code) }
            }
            val request = server.takeRequest()
            assertNull(request.headers["Cookie"])
            assertNull(request.headers["X-API-User"])
            assertEquals("synthetic-key", request.headers["X-Api-Key"])
            assertTrue(request.headers["Authorization"]?.startsWith("Basic ") == true)
            assertEquals(1, diagnostics.read(operator.id).size)
            assertEquals(1, server.requestCount)
            server.enqueue(MockResponse.Builder().build())
            factory.withProfile(operator) { client ->
                client.newCall(Request.Builder().url(server.url("/")).build()).execute().close()
            }
            assertNull(server.takeRequest().headers["Cookie"])
            assertNull(secrets.read(operator, SecretKind.SESSION_COOKIE))
            factory.delete(operator.id) {}
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun deletionWaitsForCacheWritesAndRejectsStaleReaders() = runTest {
        val factory =
            ProfileTransport(
                MemorySecrets(),
                DeprecationStore(),
                FakeClock(),
                StandardTestDispatcher(testScheduler),
            )
        val id = ProfileId(UUID.randomUUID())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var cached = false
        val writer = async {
            factory.withCache(id) {
                entered.complete(Unit)
                release.await()
                cached = true
            }
        }
        entered.await()
        val deletion = async { factory.delete(id) { cached = false } }
        runCurrent()
        assertFalse(deletion.isCompleted)
        release.complete(Unit)
        writer.await()
        deletion.await()
        assertFalse(cached)
        val failure =
            try {
                factory.withCache(id) { error("A deleted profile must not be read") }
            } catch (failure: AppException) {
                failure
            }
        assertEquals(AppError.NOT_FOUND, failure.reason)
    }

    private fun profile(server: MockWebServer) =
        ServerProfile(
            ProfileId(UUID.randomUUID()),
            "Test",
            requireNotNull(ServerAddress.parse(server.url("/").toString())),
        )
}
