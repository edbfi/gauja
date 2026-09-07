// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.CertificateFingerprint
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.servers.TlsMode
import app.gauja.core.testing.FakeClock
import app.gauja.core.testing.MemorySecrets
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Test

class TransportTlsTest {
    @Test
    fun requiresConfirmedFingerprintAndMatchingHostname() = runTest {
        val base = System.getenv("GAUJA_TLS_SERVER")
        val raw = System.getenv("GAUJA_TLS_FINGERPRINT")
        assumeNotNull(base, raw)
        val address = requireNotNull(base)
        val fingerprint = requireNotNull(CertificateFingerprint.parse(requireNotNull(raw)))
        val wrong = requireNotNull(CertificateFingerprint.parse("0".repeat(64)))
        val factory =
            ProfileTransport(MemorySecrets(), DeprecationStore(), FakeClock(), Dispatchers.IO)
        val cases =
            listOf(
                TlsMode.System to address,
                TlsMode.Pinned(fingerprint) to address,
                TlsMode.Pinned(wrong) to address,
                TlsMode.Pinned(fingerprint) to address.replace("localhost", "127.0.0.1"),
            )
        for ((index, scenario) in cases.withIndex()) {
            val profile =
                ServerProfile(
                    ProfileId(UUID.randomUUID()),
                    "TLS test",
                    requireNotNull(ServerAddress.parse(scenario.second)),
                    tlsMode = scenario.first,
                )
            try {
                var rejected = false
                try {
                    factory.withProfile(profile) { client ->
                        client
                            .newCall(Request.Builder().url(scenario.second + "/tls").build())
                            .execute()
                            .use {
                                assertEquals(
                                    "Only the matching pin and hostname may reach HTTP",
                                    1,
                                    index,
                                )
                                assertEquals("trusted", it.body.string())
                            }
                    }
                } catch (_: SSLException) {
                    rejected = true
                }
                assertTrue("TLS trust scenario $index", rejected == (index != 1))
            } finally {
                factory.delete(profile.id) {}
            }
        }
    }
}
