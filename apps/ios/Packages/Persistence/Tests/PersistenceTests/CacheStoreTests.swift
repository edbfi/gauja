// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Model
import SwiftData
import Testing

@testable import Persistence

@Suite(.serialized)
struct CacheStoreTests {
    @Test func cachedUsersAndTitlesRemainIsolated() async throws {
        let container = try PersistenceContainer.make(inMemory: true)
        let users = UserCacheStore(modelContainer: container)
        let titles = TitleCacheStore(modelContainer: container)
        let firstProfile = ProfileID(rawValue: UUID())
        let secondProfile = ProfileID(rawValue: UUID())
        let date = Date(timeIntervalSince1970: 1234)
        let user = User(
            id: try #require(UserID(rawValue: 1)), displayName: "Reader", email: "reader@example.invalid",
            permissions: 32)
        try await users.save(Cached(user, fetchedAt: date), profileID: firstProfile)
        try await users.save(Cached(user, fetchedAt: date), profileID: secondProfile)
        let title = TitleSummary(
            id: try #require(TMDBID(rawValue: 42)), mediaType: .movie, title: "Synthetic title",
            year: 2026, posterPath: nil, rating: nil, status: .unrecognized(999))
        try await titles.save(Cached(title, fetchedAt: date), profileID: firstProfile)
        #expect(try await users.read(firstProfile)?.fetchedAt == date)
        #expect(
            try await titles.read(profileID: firstProfile, mediaType: "movie", tmdbID: 42)?.value.status
                == .unrecognized(999))
        try await users.clear(firstProfile)
        try await titles.clear(firstProfile)
        #expect(try await users.read(firstProfile) == nil)
        #expect(try await users.read(secondProfile)?.value == user)
        #expect(try await titles.read(profileID: firstProfile, mediaType: "movie", tmdbID: 42) == nil)
    }

    @Test func profileOrderAndCacheSurviveReopening() async throws {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }
        let url = directory.appendingPathComponent("cache.sqlite")
        let firstProfile = ProfileID(rawValue: UUID())
        let secondProfile = ProfileID(rawValue: UUID())
        try await seed(url: url, firstProfile: firstProfile, secondProfile: secondProfile)
        let container = try PersistenceContainer.make(url: url)
        let profiles = ServerProfileStore(modelContainer: container)
        #expect(try await profiles.profiles().map(\.id) == [secondProfile, firstProfile])
        #expect(try await profiles.profiles().first?.status?.fetchedAt == Date(timeIntervalSince1970: 1234))
    }

    private func seed(url: URL, firstProfile: ProfileID, secondProfile: ProfileID) async throws {
        let profiles = ServerProfileStore(modelContainer: try PersistenceContainer.make(url: url))
        let address = try #require(ServerAddress("https://example.invalid/seerr"))
        let status = Cached(
            ServerStatus(version: "3.4.1", commitTag: nil, updateAvailable: false, restartRequired: false),
            fetchedAt: Date(timeIntervalSince1970: 1234))
        try await profiles.save(
            try #require(ServerProfile(id: firstProfile, displayName: "A", address: address, status: status)))
        try await profiles.save(
            try #require(ServerProfile(id: secondProfile, displayName: "B", address: address, status: status)))
        try await profiles.reorder([secondProfile, firstProfile])
    }

    @Test func userObservationPublishesSavedValue() async throws {
        let users = UserCacheStore(modelContainer: try PersistenceContainer.make(inMemory: true))
        let id = ProfileID(rawValue: UUID())
        var iterator = await users.observe(id).makeAsyncIterator()
        let first = try await iterator.next()
        #expect(first != nil)
        #expect(first.flatMap { $0 } == nil)
        let user = User(
            id: try #require(UserID(rawValue: 2)), displayName: "Reader", email: "reader@example.invalid",
            permissions: 32)
        try await users.save(Cached(user, fetchedAt: Date(timeIntervalSince1970: 1)), profileID: id)
        let next = try await iterator.next()
        #expect(next.flatMap { $0 }?.value == user)
    }
}
