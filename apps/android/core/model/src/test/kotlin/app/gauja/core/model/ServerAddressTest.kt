// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun invalidAddressesDoNotBecomeURLs() {
        listOf(
                "",
                "ftp://example.com",
                "https://user:pass@example.com",
                "https://example.com?q=x",
                "https://example.com#fragment",
                "https://",
                "https://example.com:0",
                "https://example.com/a/../b",
                "https://example.com/a/%2e%2e/b",
                "https://example.com/a%2fb",
                "https://example.com/a%5cb",
            )
            .forEach { assertNull(it, ServerAddress.parse(it)) }
    }

    @Test
    fun preservesProxyPrefixAndRedactsDescription() {
        val address = requireNotNull(ServerAddress.parse("  EXAMPLE.com:5055/seerr/  "))
        assertEquals("https://example.com:5055/seerr", address.value)
        assertEquals("https://example.com:5055/seerr/api/v1/", address.apiBase)
        assertFalse(address.isPlainHttp)
        assertEquals("[SERVER]", address.toString())
    }

    @Test
    fun credentialOriginsUseSchemeHostAndEffectivePort() {
        val base = requireNotNull(ServerAddress.parse("https://example.test"))
        assertEquals(base.origin, ServerAddress.parse("https://EXAMPLE.test:443/path")?.origin)
        for (input in
            listOf("http://example.test", "https://other.test", "https://example.test:444")) {
            org.junit.Assert.assertNotEquals(base.origin, ServerAddress.parse(input)?.origin)
        }
        assertEquals(
            ServerAddress.parse("http://[::1]/one")?.origin,
            ServerAddress.parse("http://[::1]:80/two")?.origin,
        )
    }
}
