// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Model
import Persistence

public actor MemorySecrets: SecretStore {
    private var values: [ProfileID: [SecretKind: Secret]] = [:]
    public init() {}
    public func read(profileID: ProfileID, kind: SecretKind) -> Secret? { values[profileID]?[kind] }
    public func write(profileID: ProfileID, kind: SecretKind, value: Secret?) {
        values[profileID, default: [:]][kind] = value
    }
    public func clear(profileID: ProfileID) { values[profileID] = nil }
}
