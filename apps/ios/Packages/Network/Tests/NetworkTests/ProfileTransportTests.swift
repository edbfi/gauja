// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import GaujaTesting
import HTTPTypes
import Model
import Persistence
import Testing

@testable import Network

@Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"] != nil))
func profileCookiesRestoreAndOperatorNeverSendsThem() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"])
    let address = try #require(ServerAddress(base))
    let first = try #require(ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "First", address: address))
    let second = try #require(ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "Second", address: address))
    let secrets = MemorySecrets()
    let clock = WallClock(now: { Date() })
    let factory = ProfileTransport(secrets: secrets, clock: clock, diagnostics: DeprecationStore())
    _ = try await response(factory, first, "/session/a")
    _ = try await response(factory, second, "/session/b")
    #expect(try await response(factory, first, "/cookie") == "connect.sid=a")
    let reopened = ProfileTransport(secrets: secrets, clock: clock, diagnostics: DeprecationStore())
    #expect(try await response(reopened, first, "/cookie") == "connect.sid=a")
    _ = try await response(reopened, first, "/401")
    #expect(await secrets.read(profile: first, kind: .sessionCookie) == nil)
    #expect(try await response(factory, second, "/cookie") == "connect.sid=b")
    let operatorProfile = try #require(
        ServerProfile(
            id: ProfileID(rawValue: UUID()), displayName: "Operator", address: address,
            authMethod: .apiKey, basicAuthUsername: "operator", operatorAcknowledged: true))
    await secrets.write(profile: operatorProfile, kind: .apiKey, value: Secret(Data("synthetic-key".utf8)))
    await secrets.write(
        profile: operatorProfile, kind: .basicAuthPassword, value: Secret(Data("synthetic-password".utf8)))
    #expect(try await response(factory, operatorProfile, "/operator") == "operator-safe")
    #expect(try await response(factory, operatorProfile, "/operator") == "operator-safe")
    #expect(await secrets.read(profile: operatorProfile, kind: .sessionCookie) == nil)
    try await factory.delete(first.id) {}
    try await factory.delete(second.id) {}
    try await factory.delete(operatorProfile.id) {}
    try await reopened.delete(first.id) {}
}

private func response(_ factory: ProfileTransport, _ profile: ServerProfile, _ path: String) async throws -> String {
    try await factory.withProfile(profile) { transport in
        var request = HTTPRequest(method: .get, scheme: nil, authority: nil, path: path)
        if let name = HTTPField.Name("X-API-User") { request.headerFields[name] = "99" }
        let (_, body) = try await transport.send(
            request, body: nil, baseURL: profile.address.url, operationID: "profile-test")
        return try await String(collecting: #require(body), upTo: 4096)
    }
}

@Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_TLS_SERVER"] != nil))
func tlsRequiresBothConfirmedFingerprintAndMatchingHostname() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GAUJA_TLS_SERVER"])
    let rawFingerprint = try #require(ProcessInfo.processInfo.environment["GAUJA_TLS_FINGERPRINT"])
    let fingerprint = try #require(CertificateFingerprint(rawFingerprint))
    let wrong = try #require(CertificateFingerprint(String(repeating: "0", count: 64)))
    let address = try #require(ServerAddress(base))
    let factory = ProfileTransport(secrets: MemorySecrets(), clock: .system, diagnostics: DeprecationStore())
    for (index, scenario) in [
        (TLSMode.system, address),
        (.pinned(fingerprint), address),
        (.pinned(wrong), address),
        (
            .pinned(fingerprint),
            try #require(ServerAddress(base.replacingOccurrences(of: "localhost", with: "127.0.0.1")))
        ),
    ].enumerated() {
        let (mode, url) = scenario
        let profile = try #require(
            ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "TLS test", address: url, tlsMode: mode))
        if index == 1 {
            #expect(try await response(factory, profile, "/tls") == "trusted")
        } else {
            await #expect(throws: (any Error).self) { _ = try await response(factory, profile, "/tls") }
        }
        try await factory.delete(profile.id) {}
    }
}

@Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"] != nil))
func changingOriginDoesNotReuseStoredCredentials() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GAUJA_EGRESS_SERVER"])
    let id = ProfileID(rawValue: UUID())
    let firstAddress = try #require(ServerAddress(base))
    let secondAddress = try #require(ServerAddress(base.replacingOccurrences(of: "127.0.0.1", with: "localhost")))
    let original = try #require(
        ServerProfile(
            id: id, displayName: "Original", address: firstAddress, authMethod: .apiKey, basicAuthUsername: "operator",
            operatorAcknowledged: true))
    let changed = try #require(
        ServerProfile(
            id: id, displayName: "Changed", address: secondAddress, authMethod: .apiKey, basicAuthUsername: "operator",
            operatorAcknowledged: true))
    let secrets = MemorySecrets()
    await secrets.write(profile: original, kind: .apiKey, value: Secret(Data("synthetic-key".utf8)))
    await secrets.write(profile: original, kind: .basicAuthPassword, value: Secret(Data("synthetic-password".utf8)))
    let factory = ProfileTransport(secrets: secrets, clock: .system, diagnostics: DeprecationStore())
    #expect(try await response(factory, original, "/operator") == "operator-safe")
    #expect(try await response(factory, changed, "/credentials") == "no-credentials")
    let restarted = ProfileTransport(secrets: secrets, clock: .system, diagnostics: DeprecationStore())
    #expect(try await response(restarted, changed, "/credentials") == "no-credentials")
    try await restarted.delete(id) {}
    try await factory.delete(id) {}
}
