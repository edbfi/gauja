// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.links

import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DeepLinkTest {
    private val profile =
        ServerProfile(
            ProfileId(UUID.randomUUID()),
            "Test",
            requireNotNull(ServerAddress.parse("https://example.invalid/seerr")),
        )

    @Test
    fun canonicalAndWebLinksKeepProfileAndIdentity() {
        val target = LinkTarget.Media(LinkTarget.Kind.MOVIE, 42)
        assertEquals(
            target,
            parseDeepLink("gauja://server/${profile.id.value}/movie/42", listOf(profile))?.target,
        )
        assertEquals(
            target,
            parseDeepLink("https://example.invalid/seerr/movie/42", listOf(profile))?.target,
        )
        val other = profile.copy(id = ProfileId(UUID.randomUUID()))
        assertEquals(
            2,
            parseDeepLink("https://example.invalid/seerr/requests", listOf(profile, other))
                ?.profileIds
                ?.size,
        )
    }

    @Test
    fun foreignAndMalformedLinksFailClosed() {
        for (url in
            listOf(
                "https://foreign.invalid/seerr/movie/42",
                "https://example.invalid/seerr/movie/0",
                "https://example.invalid/seerr/movie/42/extra",
                "https://example.invalid/seerr/movie/%34%32",
                "https://example.invalid/seerr/../movie/42",
                "gauja://server/${UUID.randomUUID()}/requests",
            )) {
            assertNull(parseDeepLink(url, listOf(profile)))
        }
    }
}
