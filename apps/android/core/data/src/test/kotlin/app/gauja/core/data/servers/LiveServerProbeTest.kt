// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.servers

import app.gauja.core.common.ProbeError
import app.gauja.core.common.ProbeException
import app.gauja.core.model.Compatibility
import app.gauja.core.model.ServerAddress
import app.gauja.core.model.status.MediaServerType
import app.gauja.core.network.ProbeTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeNotNull
import org.junit.Test

class LiveServerProbeTest {
    private val probe = LiveServerProbe(ProbeTransport(), Dispatchers.IO, probeJson())

    @Test
    fun mapsUnknownFieldsAndPreservesProxyPrefix() = runTest {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(
                MockResponse.Builder().body("""{"version":"3.4.1","future":123}""").build()
            )
            server.enqueue(
                MockResponse.Builder()
                    .body(
                        """{"initialized":false,"plexClientIdentifier":"00000000-0000-4000-8000-000000000000","applicationTitle":"Library","mediaServerType":999}"""
                    )
                    .build()
            )
            val snapshot =
                probe.check(requireNotNull(ServerAddress.parse(server.url("/seerr").toString())))
            assertEquals("Library", snapshot.title)
            assertEquals(MediaServerType.Unrecognized(999), snapshot.mediaServerType)
            assertEquals(
                "/seerr/api/v1/status?checkUpdateAvailable=false",
                server.takeRequest().target,
            )
            assertEquals("/seerr/api/v1/settings/public", server.takeRequest().target)
        }
    }

    @Test
    fun badResponsesBecomeSafeDomainErrors() = runTest {
        for ((code, body, reason) in
            listOf(
                Triple(401, "private proxy details", ProbeError.DENIED),
                Triple(302, "", ProbeError.REDIRECT),
                Triple(503, "private server details", ProbeError.SERVER),
                Triple(200, "not-json", ProbeError.RESPONSE),
            )) {
            MockWebServer().use { server ->
                server.start()
                server.enqueue(MockResponse.Builder().code(code).body(body).build())
                try {
                    probe.check(requireNotNull(ServerAddress.parse(server.url("/").toString())))
                    org.junit.Assert.fail("Expected safe error")
                } catch (error: ProbeException) {
                    assertEquals(reason, error.reason)
                    assertFalse(error.toString().contains("private"))
                }
            }
        }
    }

    @Test
    fun pinnedContainerContract() = runTest {
        val base = System.getenv("GAUJA_CONTRACT_SERVER")
        assumeNotNull(base)
        val snapshot = probe.check(requireNotNull(ServerAddress.parse(requireNotNull(base))))
        assertEquals("3.4.1", snapshot.version)
        assertEquals(Compatibility.TESTED, snapshot.compatibility)
        assertEquals("Seerr", snapshot.title)
        assertEquals(false, snapshot.initialized)
    }
}
