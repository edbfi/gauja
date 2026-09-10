// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import GaujaTesting
import Model
import Network
import Persistence
import Testing

@testable import Data

@Test func originEditsWaitForRequestsAndWipeOnlyTheEditedServer() async throws {
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: directory) }
    let container = try PersistenceContainer.make(inMemory: true)
    let profiles = ServerProfileStore(modelContainer: container)
    let users = UserCacheStore(modelContainer: container)
    let titles = TitleCacheStore(modelContainer: container)
    let secrets = MemorySecrets()
    let transport = ProfileTransport(secrets: secrets, clock: .system, diagnostics: DeprecationStore())
    let sessions = APISession(profiles: profiles, transport: transport)
    let repository = LiveServersRepository(
        sessions: sessions, secrets: secrets,
        preferences: try PreferencesStore(suiteName: UUID().uuidString), users: users, titles: titles,
        clock: .system, images: ProfileImages(directory: directory, clock: .system))
    let address = try #require(ServerAddress("https://first.example"))
    let status = Cached(
        ServerStatus(version: "3.4.1", commitTag: nil, updateAvailable: false, restartRequired: false),
        fetchedAt: Date())
    let original = try #require(
        ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "First", address: address, status: status))
    let other = try #require(ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "Other", address: address))
    let user = try Cached(
        User(
            id: #require(UserID(rawValue: 1)), displayName: "Reader", email: "reader@example.invalid", permissions: 32),
        fetchedAt: Date())
    let title = try Cached(
        TitleSummary(
            id: #require(TMDBID(rawValue: 42)), mediaType: .movie, title: "Synthetic", year: nil, posterPath: nil,
            rating: nil, status: .available), fetchedAt: Date())
    for profile in [original, other] {
        try await repository.save(profile)
        try await users.save(user, profileID: profile.id)
        try await titles.save(title, profileID: profile.id)
        for kind in SecretKind.allCases {
            await secrets.write(profile: profile, kind: kind, value: Secret(Foundation.Data("synthetic".utf8)))
        }
    }
    let renamedAddress = try #require(ServerAddress("https://FIRST.example:443/path"))
    let renamed = try #require(
        ServerProfile(id: original.id, displayName: "Renamed", address: renamedAddress, status: status))
    try await repository.save(renamed)
    #expect(try await users.read(original.id) == user)
    #expect(await secrets.read(profile: renamed, kind: .basicAuthPassword) != nil)
    let started = AsyncStream<Void>.makeStream()
    let release = AsyncStream<Void>.makeStream()
    let request = Task {
        try await sessions.use(original.id) { profile, _, _ in
            started.continuation.yield(())
            for await _ in release.stream { break }
            // A delayed response persists the profile it originally resolved.
            try await profiles.save(profile)
        }
    }
    for await _ in started.stream { break }
    let changedAddress = try #require(ServerAddress("https://second.example"))
    let changed = try #require(
        ServerProfile(id: original.id, displayName: "Changed", address: changedAddress, status: status))
    let edit = Task { try await repository.save(changed) }
    // Give the edit an opportunity to run while the request owns the gate.
    try await Task.sleep(for: .milliseconds(50))
    #expect(try await profiles.profiles().first { $0.id == original.id }?.address == renamed.address)
    release.continuation.yield(())
    try await request.value
    try await edit.value
    let saved = try #require(try await profiles.profiles().first { $0.id == original.id })
    #expect(saved.address == changed.address)
    #expect(saved.status == nil)
    #expect(saved.publicSettings == nil)
    #expect(try await users.read(original.id) == nil)
    #expect(try await titles.read(profileID: original.id, mediaType: "movie", tmdbID: 42) == nil)
    #expect(try await users.read(other.id) == user)
    #expect(try await titles.read(profileID: other.id, mediaType: "movie", tmdbID: 42) == title)
    for kind in SecretKind.allCases {
        #expect(await secrets.read(profile: original, kind: kind) == nil)
        #expect(await secrets.read(profile: changed, kind: kind) == nil)
        #expect(await secrets.read(profile: other, kind: kind) != nil)
    }
}
