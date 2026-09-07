// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Testing

@testable import Model

@Test func linksKeepIdentityAndRejectUntrustedRoutes() throws {
    let address = try #require(ServerAddress("https://example.invalid/seerr"))
    let profile = try #require(
        ServerProfile(
            id: ProfileID(rawValue: UUID()), displayName: "Test",
            address: address))
    let target = LinkTarget.media(.movie, try #require(TMDBID(rawValue: 42)))
    #expect(parseDeepLink("gauja://server/\(profile.id.rawValue)/movie/42", profiles: [profile])?.target == target)
    #expect(parseDeepLink("https://example.invalid/seerr/movie/42", profiles: [profile])?.target == target)
    for url in [
        "https://foreign.invalid/seerr/movie/42", "https://example.invalid/seerr/movie/0",
        "https://example.invalid/seerr/movie/42/extra", "https://example.invalid/seerr/movie/%34%32",
        "https://example.invalid/seerr/../movie/42", "gauja://server/\(UUID())/requests",
    ] {
        #expect(parseDeepLink(url, profiles: [profile]) == nil)
    }
}
