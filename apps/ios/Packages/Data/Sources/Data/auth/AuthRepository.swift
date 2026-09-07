// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Model
import Persistence
internal import SeerrAPI

public protocol AuthRepository: Sendable {
    func observeUser(_ id: ProfileID) async -> AsyncThrowingStream<Cached<User>?, Error>
    func signInLocal(_ id: ProfileID, email: String, password: Secret) async throws
    func refresh(_ id: ProfileID) async throws
    func logout(_ id: ProfileID) async throws
}

struct LiveAuthRepository: AuthRepository {
    let sessions: APISession
    let users: UserCacheStore
    let clock: WallClock

    func observeUser(_ id: ProfileID) async -> AsyncThrowingStream<Cached<User>?, Error> { await users.observe(id) }

    func signInLocal(_ id: ProfileID, email: String, password: Secret) async throws {
        try await sessions.use(id) { profile, client, transport in
            guard profile.authMethod == .session else { throw AppError.validation }
            do {
                let input = Operations.postAuthLocal.Input(
                    body: .json(.init(email: email, password: try password.utf8())))
                switch try await client.postAuthLocal(input) {
                case .ok: break
                case .undocumented(let code, _): throw responseError(code)
                }
                try await cacheUser(client, id)
            } catch {
                transport.clearCookie()
                try await users.clear(id)
                throw error
            }
        }
    }

    func refresh(_ id: ProfileID) async throws {
        try await sessions.use(id) { _, client, _ in
            do { try await cacheUser(client, id) } catch AppError.auth {
                try await users.clear(id)
                throw AppError.auth
            }
        }
    }

    func logout(_ id: ProfileID) async throws {
        try await sessions.use(id) { _, client, transport in
            do {
                switch try await client.postAuthLogout() {
                case .ok: break
                case .undocumented(let code, _): throw responseError(code)
                }
            } catch {
                transport.clearCookie()
                try await users.clear(id)
                throw error
            }
            transport.clearCookie()
            try await users.clear(id)
        }
    }

    private func cacheUser(_ client: Client, _ id: ProfileID) async throws {
        switch try await client.getAuthMe() {
        case .ok(let response):
            let user = try mapUser(response.body.json)
            try Task.checkCancellation()
            try await users.save(Cached(user, fetchedAt: clock.now()), profileID: id)
        case .undocumented(let code, _): throw responseError(code)
        }
    }
}
