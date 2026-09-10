// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import HTTPTypes
import Model
import OSLog
import OpenAPIRuntime
import OpenAPIURLSession
import Persistence

public actor ProfileTransport {
    private let secrets: any SecretStore
    private let clock: WallClock
    public let diagnostics: DeprecationStore
    private var sessions: [ProfileID: AuthenticatedTransport] = [:]
    private var gates: [ProfileID: ProfileGate] = [:]
    private var deleted: Set<ProfileID> = []

    public init(secrets: any SecretStore, clock: WallClock, diagnostics: DeprecationStore) {
        self.secrets = secrets
        self.clock = clock
        self.diagnostics = diagnostics
    }

    public func withProfile<Value: Sendable>(
        _ profile: ServerProfile,
        operation: @Sendable (AuthenticatedTransport) async throws -> Value
    ) async throws -> Value {
        try await withProfile(
            profile.id, resolve: { profile },
            operation: { _, transport in
                try await operation(transport)
            })
    }

    public func withProfile<Value: Sendable>(
        _ id: ProfileID,
        resolve: @Sendable () async throws -> ServerProfile,
        operation: @Sendable (ServerProfile, AuthenticatedTransport) async throws -> Value
    ) async throws -> Value {
        try await withCache(id) {
            let profile = try await resolve()
            guard profile.id == id else { throw AppError.validation }
            let transport = try await self.session(profile)
            let value: Value
            do { value = try await operation(profile, transport) } catch {
                try await self.persist(transport)
                throw error
            }
            try await self.persist(transport)
            return value
        }
    }

    // Cache reads share profile-deletion ordering without opening an API session
    // or writing credentials when the device is offline.
    public func withCache<Value: Sendable>(
        _ id: ProfileID, operation: @Sendable () async throws -> Value
    ) async throws -> Value {
        let gate = gate(id)
        await gate.acquire()
        do {
            try Task.checkCancellation()
            guard !deleted.contains(id) else { throw AppError.notFound }
            let value = try await operation()
            await gate.release()
            return value
        } catch {
            await gate.release()
            throw error
        }
    }

    public func delete(_ id: ProfileID, wipe: @Sendable () async throws -> Void) async throws {
        deleted.insert(id)
        sessions[id]?.close()
        let gate = gate(id)
        await gate.acquire()
        do {
            sessions.removeValue(forKey: id)?.close()
            try await wipe()
            await diagnostics.clear(id)
            await gate.release()
        } catch {
            await gate.release()
            throw error
        }
    }

    public func clearCredentials(_ profile: ServerProfile) async throws {
        let id = profile.id
        sessions[id]?.clearCookie()
        try await secrets.write(profile: profile, kind: .sessionCookie, value: nil)
        try await secrets.write(profile: profile, kind: .apiKey, value: nil)
        try await secrets.write(profile: profile, kind: .plexToken, value: nil)
        sessions.removeValue(forKey: id)?.close()
    }

    private func gate(_ id: ProfileID) -> ProfileGate {
        if let gate = gates[id] { return gate }
        let gate = ProfileGate()
        gates[id] = gate
        return gate
    }

    private func session(_ profile: ServerProfile) async throws -> AuthenticatedTransport {
        if let existing = sessions[profile.id], existing.profile == profile { return existing }
        sessions.removeValue(forKey: profile.id)?.close()
        let cookie =
            profile.authMethod == .session
            ? try await secrets.read(profile: profile, kind: .sessionCookie) : nil
        let apiKey =
            profile.authMethod == .apiKey
            ? try await secrets.read(profile: profile, kind: .apiKey) : nil
        let password = try await secrets.read(profile: profile, kind: .basicAuthPassword)
        let transport = AuthenticatedTransport(
            profile: profile, cookie: cookie, apiKey: apiKey, basicPassword: password,
            clock: clock, diagnostics: diagnostics)
        sessions[profile.id] = transport
        return transport
    }

    private func persist(_ transport: AuthenticatedTransport) async throws {
        try await secrets.write(profile: transport.profile, kind: .sessionCookie, value: transport.savedCookie())
    }
}

// Actor reentrancy alone does not serialize a request followed by an awaited cache write.
private actor ProfileGate {
    private var occupied = false
    private var waiters: [CheckedContinuation<Void, Never>] = []
    func acquire() async {
        if !occupied {
            occupied = true
            return
        }
        await withCheckedContinuation { waiters.append($0) }
    }
    func release() {
        if waiters.isEmpty { occupied = false } else { waiters.removeFirst().resume() }
    }
}

public final class AuthenticatedTransport: ClientTransport {
    public let profile: ServerProfile
    private let session: URLSession
    private let transport: URLSessionTransport
    private let cookies: HTTPCookieStorage?
    private let apiKey: Secret?
    private let basicPassword: Secret?
    private let clock: WallClock
    private let diagnostics: DeprecationStore

    fileprivate init(
        profile: ServerProfile, cookie: Secret?, apiKey: Secret?, basicPassword: Secret?,
        clock: WallClock, diagnostics: DeprecationStore
    ) {
        self.profile = profile
        self.apiKey = apiKey
        self.basicPassword = basicPassword
        self.clock = clock
        self.diagnostics = diagnostics
        let configuration = URLSessionConfiguration.ephemeral
        configuration.urlCache = nil
        configuration.httpShouldSetCookies = profile.authMethod == .session
        // Each ephemeral configuration owns its own in-memory cookie storage.
        if profile.authMethod == .apiKey { configuration.httpCookieStorage = nil }
        let storage = configuration.httpCookieStorage
        cookies = storage
        cookie?.withBytes { bytes in
            if let fields = try? PropertyListSerialization.propertyList(from: bytes, format: nil) as? [String: Any],
                let restored = HTTPCookie(
                    properties: Dictionary(
                        uniqueKeysWithValues: fields.map { (HTTPCookiePropertyKey($0.key), $0.value) })),
                restored.name == "connect.sid", restored.expiresDate.map({ $0 > clock.now() }) ?? true
            {
                storage?.setCookie(restored)
            }
        }
        configuration.timeoutIntervalForRequest = 20
        configuration.timeoutIntervalForResource = 20
        let session = URLSession(
            configuration: configuration, delegate: ProfileSessionDelegate(mode: profile.tlsMode), delegateQueue: nil)
        self.session = session
        transport = URLSessionTransport(configuration: .init(session: session))
    }

    public func clearCookie() {
        for cookie in cookies?.cookies ?? [] { cookies?.deleteCookie(cookie) }
    }

    public func close() {
        clearCookie()
        session.invalidateAndCancel()
    }

    fileprivate func savedCookie() -> Secret? {
        guard let cookie = cookies?.cookies?.first(where: { $0.name == "connect.sid" }),
            cookie.expiresDate.map({ $0 > clock.now() }) ?? true, let properties = cookie.properties,
            let data = try? PropertyListSerialization.data(
                fromPropertyList: Dictionary(uniqueKeysWithValues: properties.map { ($0.key.rawValue, $0.value) }),
                format: .binary, options: 0)
        else { return nil }
        return Secret(data)
    }

    public func send(_ request: HTTPRequest, body: HTTPBody?, baseURL: URL, operationID: String) async throws
        -> (HTTPResponse, HTTPBody?)
    {
        guard baseURL.scheme == profile.address.url.scheme, baseURL.host == profile.address.url.host,
            baseURL.port == profile.address.url.port,
            let path = request.path, !path.hasPrefix("//"), !path.contains("://")
        else {
            Logger(subsystem: "app.gauja", category: "network").error("EGRESS_REJECTED")
            throw AppError.network
        }
        var authenticated = request
        authenticated.headerFields[.cookie] = nil
        authenticated.headerFields[.authorization] = nil
        if let name = HTTPField.Name("X-API-User") { authenticated.headerFields[name] = nil }
        if let name = HTTPField.Name("X-Api-Key") {
            let value = try apiKey?.utf8()
            guard value?.unicodeScalars.allSatisfy({ $0.value >= 32 && $0.value != 127 }) ?? true else {
                throw AppError.validation
            }
            authenticated.headerFields[name] = value
        }
        if let username = profile.basicAuthUsername, let password = basicPassword {
            let value = try password.utf8()
            authenticated.headerFields[.authorization] =
                "Basic " + Data((username + ":" + value).utf8).base64EncodedString()
        }
        let result = try await transport.send(authenticated, body: body, baseURL: baseURL, operationID: operationID)
        await diagnostics.record(
            profile.id, endpoint: path.components(separatedBy: "?")[0], fields: result.0.headerFields)
        if result.0.status.code == 401 { clearCookie() }
        return result
    }
}
