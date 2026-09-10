// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Persistence
import Security
import Testing

@Suite(.serialized)
struct KeychainStoreTests {
    @Test func secretsSurviveReopeningAndWipeOnlyTheirProfile() async throws {
        let service = "app.gauja.test." + UUID().uuidString
        let firstAddress = try #require(ServerAddress("https://first.example"))
        let first = try #require(
            ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "first", address: firstAddress))
        let secondAddress = try #require(ServerAddress("https://second.example"))
        let second = try #require(
            ServerProfile(id: ProfileID(rawValue: UUID()), displayName: "second", address: secondAddress))
        let store = KeychainStore(service: service)
        do {
            for kind in SecretKind.allCases {
                try await store.write(profile: first, kind: kind, value: Secret(Data("synthetic-first".utf8)))
                try await store.write(profile: second, kind: kind, value: Secret(Data("synthetic-second".utf8)))
            }
            let reopened = KeychainStore(service: service)
            for kind in SecretKind.allCases {
                let value = try #require(try await reopened.read(profile: first, kind: kind))
                #expect(try value.utf8() == "synthetic-first")
            }
            try await reopened.clear(profileID: first.id)
            for kind in SecretKind.allCases {
                #expect(try await reopened.read(profile: first, kind: kind) == nil)
                let value = try #require(try await reopened.read(profile: second, kind: kind))
                #expect(try value.utf8() == "synthetic-second")
            }
            try await reopened.clear(profileID: second.id)
        } catch {
            try? await store.clear(profileID: first.id)
            try? await store.clear(profileID: second.id)
            throw error
        }
    }
    @Test func originsRemainBoundAfterReopeningAndLegacySecretsAreNotAdopted() async throws {
        let service = "app.gauja.test." + UUID().uuidString
        let id = ProfileID(rawValue: UUID())
        let originalAddress = try #require(ServerAddress("https://first.example"))
        let original = try #require(ServerProfile(id: id, displayName: "Original", address: originalAddress))
        let changedAddress = try #require(ServerAddress("https://second.example"))
        let changed = try #require(ServerProfile(id: id, displayName: "Changed", address: changedAddress))
        let renamedAddress = try #require(ServerAddress("https://FIRST.example:443/path"))
        let renamed = try #require(ServerProfile(id: id, displayName: "Renamed", address: renamedAddress))
        let store = KeychainStore(service: service)
        do {
            for kind in SecretKind.allCases {
                let legacy: [String: Any] = [
                    kSecClass as String: kSecClassGenericPassword,
                    kSecAttrService as String: service,
                    kSecAttrAccount as String: "\(id.rawValue.uuidString).\(kind.rawValue)",
                    kSecUseDataProtectionKeychain as String: true,
                    kSecValueData as String: Data("synthetic-legacy".utf8),
                    kSecAttrAccessible as String: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                ]
                #expect(SecItemAdd(legacy as CFDictionary, nil) == errSecSuccess)
                #expect(try await store.read(profile: original, kind: kind) == nil)
                try await store.write(profile: original, kind: kind, value: Secret(Data("synthetic-bound".utf8)))
            }
            let reopened = KeychainStore(service: service)
            for kind in SecretKind.allCases {
                #expect(try await reopened.read(profile: changed, kind: kind) == nil)
                #expect(try await reopened.read(profile: renamed, kind: kind)?.utf8() == "synthetic-bound")
                try await reopened.write(profile: changed, kind: kind, value: Secret(Data("synthetic-new".utf8)))
            }
            try await reopened.clear(profileID: id)
            for kind in SecretKind.allCases {
                #expect(try await reopened.read(profile: original, kind: kind) == nil)
                #expect(try await reopened.read(profile: changed, kind: kind) == nil)
            }
            let legacyQuery: [String: Any] = [
                kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service,
                kSecUseDataProtectionKeychain as String: true,
            ]
            #expect(SecItemCopyMatching(legacyQuery as CFDictionary, nil) == errSecItemNotFound)
        } catch {
            try? await store.clear(profileID: id)
            throw error
        }
    }

}
