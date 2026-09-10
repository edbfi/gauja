// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Testing

@testable import Model

@Test(arguments: [
    "", "ftp://example.com", "https://user:pass@example.com", "https://example.com?q=x",
    "https://example.com#fragment", "https://", "https://example.com:0", "https://example.com/a/../b",
    "https://example.com/a/%2e%2e/b",
    "https://example.com/a%2fb", "https://example.com/a%5cb",
])
func invalidAddress(_ text: String) { #expect(ServerAddress(text) == nil) }

@Test func proxyPrefixSurvives() throws {
    let address = try #require(ServerAddress("  EXAMPLE.com:5055/seerr/  "))
    #expect(address.url.absoluteString == "https://example.com:5055/seerr")
    #expect(address.apiBase.absoluteString == "https://example.com:5055/seerr/api/v1")
    #expect(!address.isPlainHTTP)
    #expect(address.description == "[SERVER]")
}

@Test func credentialOriginsUseSchemeHostAndEffectivePort() throws {
    let base = try #require(ServerAddress("https://example.test"))
    #expect(base.origin == ServerAddress("https://EXAMPLE.test:443/path")?.origin)
    for input in ["http://example.test", "https://other.test", "https://example.test:444"] {
        #expect(base.origin != ServerAddress(input)?.origin)
    }
    #expect(ServerAddress("http://[::1]/one")?.origin == ServerAddress("http://[::1]:80/two")?.origin)
}
