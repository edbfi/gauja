// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Testing

@testable import Model

@Test func imageRewritingKeepsPrefixSizeAndApprovedHosts() throws {
    let address = try #require(ServerAddress("https://example.invalid/seerr"))
    #expect(
        imageURL(source: "/poster.jpg", address: address, cacheImages: true, size: .medium)?.absoluteString
            == "https://example.invalid/seerr/imageproxy/tmdb/t/p/w342/poster.jpg")
    #expect(
        imageURL(
            source: "https://image.tmdb.org/t/p/original/poster.jpg", address: address, cacheImages: false, size: .small
        )?.absoluteString == "https://image.tmdb.org/t/p/w185/poster.jpg")
    #expect(
        imageURL(
            source: "https://artworks.thetvdb.com/banners/poster.jpg", address: address, cacheImages: true, size: .large
        )?.absoluteString == "https://example.invalid/seerr/imageproxy/tvdb/banners/poster.jpg")
    for source in [
        "https://evil.invalid/a.jpg", "https://image.tmdb.org.evil.invalid/a.jpg", "/../private", "/%2e%2e/private",
        "https://user:pass@image.tmdb.org/t/p/w185/a.jpg", "https://image.tmdb.org/t/p/w185/a.jpg?secret=private",
    ] {
        #expect(imageURL(source: source, address: address, cacheImages: true, size: .small) == nil)
    }
}
