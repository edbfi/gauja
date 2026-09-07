// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Network
import Persistence

public struct CorePlatform: Sendable {
    public let auth: any AuthRepository
    public let servers: any ServersRepository
    public let images: ImagesRepository
    public let preferences: PreferencesStore

    public init(
        profiles: ServerProfileStore, users: UserCacheStore, titles: TitleCacheStore,
        secrets: any SecretStore, preferences: PreferencesStore, imageDirectory: URL, clock: WallClock
    ) {
        let transport = ProfileTransport(secrets: secrets, clock: clock, diagnostics: DeprecationStore())
        let sessions = APISession(profiles: profiles, transport: transport)
        let images = ProfileImages(directory: imageDirectory, clock: clock)
        auth = LiveAuthRepository(sessions: sessions, users: users, clock: clock)
        servers = LiveServersRepository(
            sessions: sessions, secrets: secrets, preferences: preferences,
            users: users, titles: titles, clock: clock, images: images)
        self.images = ImagesRepository(sessions: sessions, images: images)
        self.preferences = preferences
    }

    @concurrent public static func live(clock: WallClock) async throws -> CorePlatform {
        let support = try FileManager.default.url(
            for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let caches = try FileManager.default.url(
            for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let container = try PersistenceContainer.make(url: support.appendingPathComponent("gauja.store"))
        return CorePlatform(
            profiles: ServerProfileStore(modelContainer: container), users: UserCacheStore(modelContainer: container),
            titles: TitleCacheStore(modelContainer: container), secrets: KeychainStore(),
            preferences: try PreferencesStore(),
            imageDirectory: caches.appendingPathComponent("artwork"), clock: clock)
    }

    public func foreground() async throws {
        if let id = try await preferences.read().activeProfileID { try await servers.refresh(id) }
    }
}
