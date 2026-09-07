// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Model
import Network
import Persistence
internal import SeerrAPI

public protocol ServersRepository: Sendable {
    func observe() async -> AsyncThrowingStream<[ServerProfile], Error>
    func save(_ profile: ServerProfile) async throws
    func refresh(_ id: ProfileID) async throws
    func delete(_ id: ProfileID) async throws
}

struct LiveServersRepository: ServersRepository {
    let sessions: APISession
    let secrets: any SecretStore
    let preferences: PreferencesStore
    let users: UserCacheStore
    let titles: TitleCacheStore
    let clock: WallClock
    let images: ProfileImages

    func observe() async -> AsyncThrowingStream<[ServerProfile], Error> { await sessions.profiles.observe() }
    func save(_ profile: ServerProfile) async throws { try await sessions.profiles.save(profile) }

    func refresh(_ id: ProfileID) async throws {
        try await sessions.use(id) { profile, client, _ in
            let status: Operations.getStatus.Output.Ok.Body.jsonPayload
            switch try await client.getStatus(.init(query: .init(checkUpdateAvailable: false))) {
            case .ok(let response): status = try response.body.json
            case .undocumented(let code, _): throw responseError(code)
            }
            let settings: Components.Schemas.PublicSettings
            switch try await client.getSettingsPublic() {
            case .ok(let response): settings = try response.body.json
            case .undocumented(let code, _): throw responseError(code)
            }
            var updated = profile
            updated.status = Cached(
                ServerStatus(
                    version: status.version, commitTag: status.commitTag,
                    updateAvailable: status.updateAvailable, restartRequired: status.restartRequired),
                fetchedAt: clock.now())
            updated.publicSettings = Cached(
                PublicSettings(
                    title: settings.applicationTitle, initialized: settings.initialized,
                    localLogin: settings.localLogin,
                    mediaServerLogin: settings.mediaServerLogin,
                    mediaServerType: MediaServerType(rawValue: settings.mediaServerType.flatMap(Int.init(exactly:))),
                    cacheImages: settings.cacheImages, newPlexLogin: settings.newPlexLogin), fetchedAt: clock.now())
            try Task.checkCancellation()
            try await sessions.profiles.save(updated)
        }
    }

    func delete(_ id: ProfileID) async throws {
        try await sessions.transport.delete(id) {
            try await secrets.clear(profileID: id)
            try await users.clear(id)
            try await titles.clear(id)
            try await preferences.clear(id)
            try await images.clear(id)
            try await sessions.profiles.remove(id)
        }
    }
}
