// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Persistence
import Testing

@Suite(.serialized)
struct KeychainStoreTests {
    @Test func secretsSurviveReopeningAndWipeOnlyTheirProfile() async throws {
        let service = "app.gauja.test." + UUID().uuidString
        let first = ProfileID(rawValue: UUID())
        let second = ProfileID(rawValue: UUID())
        let store = KeychainStore(service: service)
        do {
            for kind in SecretKind.allCases {
                try await store.write(profileID: first, kind: kind, value: Secret(Data("synthetic-first".utf8)))
                try await store.write(profileID: second, kind: kind, value: Secret(Data("synthetic-second".utf8)))
            }
            let reopened = KeychainStore(service: service)
            for kind in SecretKind.allCases {
                let value = try #require(try await reopened.read(profileID: first, kind: kind))
                #expect(try value.utf8() == "synthetic-first")
            }
            try await reopened.clear(profileID: first)
            for kind in SecretKind.allCases {
                #expect(try await reopened.read(profileID: first, kind: kind) == nil)
                let value = try #require(try await reopened.read(profileID: second, kind: kind))
                #expect(try value.utf8() == "synthetic-second")
            }
            try await reopened.clear(profileID: second)
        } catch {
            try? await store.clear(profileID: first)
            try? await store.clear(profileID: second)
            throw error
        }
    }
}
