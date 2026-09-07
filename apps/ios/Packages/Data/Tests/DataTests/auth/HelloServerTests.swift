// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import GaujaTesting
import Model
import Persistence
import Testing

@testable import Data

@Suite(.serialized)
struct HelloServerTests {
    @Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_AUTH_SERVER"] != nil))
    func liveInitializedSeerrHelloServer() async throws {
        let base = try #require(ProcessInfo.processInfo.environment["GAUJA_AUTH_SERVER"])
        let credentials = try #require(ProcessInfo.processInfo.environment["GAUJA_AUTH_CREDENTIALS"])
        let input = try #require(
            JSONSerialization.jsonObject(with: Foundation.Data(contentsOf: URL(fileURLWithPath: credentials)))
                as? [String: String])
        let email = try #require(input["email"])
        let password = Secret(Foundation.Data(try #require(input["password"]).utf8))
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let container = try PersistenceContainer.make(url: directory.appendingPathComponent("cache.store"))
        let profiles = ServerProfileStore(modelContainer: container)
        let users = UserCacheStore(modelContainer: container)
        let titles = TitleCacheStore(modelContainer: container)
        let secrets = MemorySecrets()
        let clock = FakeClock().clock
        let platform = CorePlatform(
            profiles: profiles, users: users, titles: titles, secrets: secrets,
            preferences: try PreferencesStore(suiteName: UUID().uuidString),
            imageDirectory: directory.appendingPathComponent("artwork"), clock: clock)
        let address = try #require(ServerAddress(base))
        let profile = try #require(
            ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "Local test", address: address))
        var configured = profile
        configured.publicSettings = Cached(
            PublicSettings(
                title: "Test", initialized: true, localLogin: true,
                mediaServerLogin: false, mediaServerType: .notConfigured, cacheImages: true), fetchedAt: clock.now())
        try await platform.servers.save(configured)
        try await platform.auth.signInLocal(profile.id, email: email, password: password)
        let user = try #require(try await users.read(profile.id))
        #expect(user.value.email == email)
        #expect(user.fetchedAt == clock.now())
        #expect(try await secrets.read(profileID: profile.id, kind: .sessionCookie) != nil)
        let titleID = try #require(TMDBID(rawValue: 42))
        let title = TitleSummary(
            id: titleID, mediaType: .movie, title: "Synthetic cached title", year: 2026,
            posterPath: nil, rating: 8.2, status: .unrecognized(999))
        try await titles.save(Cached(title, fetchedAt: clock.now()), profileID: profile.id)
        _ = try await platform.images.load(profile.id, source: "/gauja-test.png", size: .medium, offline: false)
        var offline = URLRequest(url: try #require(URL(string: base + "/__gauja_test/offline")))
        offline.httpMethod = "POST"
        let control = URLSession(configuration: .ephemeral)
        let (_, response) = try await control.data(for: offline)
        control.invalidateAndCancel()
        #expect((response as? HTTPURLResponse)?.statusCode == 204)
        // Framework startup is separate from read-through in an already interactive app.
        #expect(CachedTitleRenderer.render(title))
        let timer = ContinuousClock()
        let start = timer.now
        let poster = try await platform.images.load(profile.id, source: "/gauja-test.png", size: .medium, offline: true)
        let cached = try #require(try await titles.read(profileID: profile.id, mediaType: "movie", tmdbID: 42))
        #expect(CachedTitleRenderer.render(cached.value, poster: poster))
        let elapsed = start.duration(to: timer.now)
        print("cached-render-duration=\(elapsed)")
        #expect(elapsed <= .milliseconds(300))
        #expect(CachedTitleRenderer.render(cached.value, largeText: true))
        try await platform.servers.delete(profile.id)
        #expect(try await users.read(profile.id) == nil)
        #expect(try await secrets.read(profileID: profile.id, kind: .sessionCookie) == nil)
    }
}
