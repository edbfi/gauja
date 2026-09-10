// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.images

import app.gauja.core.model.ServerAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImageUrlTest {
    private val address = requireNotNull(ServerAddress.parse("https://example.invalid/seerr"))

    @Test
    fun proxyPrefixAndLayoutSizeSurvive() {
        assertEquals(
            "https://example.invalid/seerr/imageproxy/tmdb/t/p/w342/poster.jpg",
            imageUrl("/poster.jpg", address, true, PosterSize.MEDIUM),
        )
        assertEquals(
            "https://image.tmdb.org/t/p/w185/poster.jpg",
            imageUrl(
                "https://image.tmdb.org/t/p/original/poster.jpg",
                address,
                false,
                PosterSize.SMALL,
            ),
        )
        assertEquals(
            "https://example.invalid/seerr/imageproxy/tvdb/banners/poster.jpg",
            imageUrl(
                "https://artworks.thetvdb.com/banners/poster.jpg",
                address,
                true,
                PosterSize.LARGE,
            ),
        )
    }

    @Test
    fun arbitraryHostsAndTraversalCannotReachLoader() {
        for (source in
            listOf(
                "https://evil.invalid/a.jpg",
                "https://image.tmdb.org.evil.invalid/a.jpg",
                "/../private",
                "/%2e%2e/private",
                "https://user:pass@image.tmdb.org/t/p/w185/a.jpg",
                "https://image.tmdb.org/t/p/w185/a.jpg?secret=private",
            )) {
            assertNull(imageUrl(source, address, true, PosterSize.SMALL))
        }
    }
}
