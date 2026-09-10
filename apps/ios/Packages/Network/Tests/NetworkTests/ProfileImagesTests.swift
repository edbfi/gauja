// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Testing

@testable import Network

@Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"] != nil))
func artworkReopensOfflineAndWipePreservesTheOtherProfile() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"])
    let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
    defer { try? FileManager.default.removeItem(at: directory) }
    let address = try #require(ServerAddress(base))
    let settings = Cached(
        PublicSettings(
            title: "Test", initialized: true, localLogin: true, mediaServerLogin: false,
            mediaServerType: .notConfigured, cacheImages: true), fetchedAt: Date())
    let first = try #require(
        ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "First", address: address, publicSettings: settings)
    )
    let second = try #require(
        ServerProfile(
            id: ProfileID(rawValue: UUID()), displayName: "Second", address: address, publicSettings: settings))
    let cache = ProfileImages(directory: directory, clock: .system)
    _ = try await cache.load(first, source: "/gauja-test.png", size: .medium, offline: false)
    _ = try await cache.load(second, source: "/gauja-test.png", size: .medium, offline: false)
    await cache.close()
    let reopened = ProfileImages(directory: directory, clock: .system)
    #expect(try await reopened.load(first, source: "/gauja-test.png", size: .medium, offline: true).width > 0)
    #expect(try await reopened.load(second, source: "/gauja-test.png", size: .medium, offline: true).width > 0)
    try await reopened.clear(first.id)
    await #expect(throws: AppError.offline) {
        _ = try await reopened.load(first, source: "/gauja-test.png", size: .medium, offline: true)
    }
    #expect(try await reopened.load(second, source: "/gauja-test.png", size: .medium, offline: true).width > 0)
    await reopened.close()
}
