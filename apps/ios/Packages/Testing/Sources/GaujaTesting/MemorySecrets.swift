// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Model
import Persistence

public actor MemorySecrets: SecretStore {
    private var values: [ProfileID: [String: [SecretKind: Secret]]] = [:]
    public init() {}
    public func read(profile: ServerProfile, kind: SecretKind) -> Secret? {
        values[profile.id]?[profile.address.origin]?[kind]
    }
    public func write(profile: ServerProfile, kind: SecretKind, value: Secret?) {
        values[profile.id, default: [:]][profile.address.origin, default: [:]][kind] = value
    }
    public func clear(profileID: ProfileID) { values[profileID] = nil }
}
