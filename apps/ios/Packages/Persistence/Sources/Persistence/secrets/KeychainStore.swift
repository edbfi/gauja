// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import CryptoKit
import Foundation
import Model
import Security

public enum SecretKind: String, Sendable, CaseIterable {
    case sessionCookie, apiKey, basicAuthPassword, plexToken
}

public protocol SecretStore: Sendable {
    func read(profile: ServerProfile, kind: SecretKind) async throws -> Secret?
    func write(profile: ServerProfile, kind: SecretKind, value: Secret?) async throws
    func clear(profileID: ProfileID) async throws
}

public actor KeychainStore: SecretStore {
    private let service: String
    public init(service: String = "app.gauja.secrets") { self.service = service }

    private func query(_ id: ProfileID, _ kind: SecretKind) -> [String: Any] {
        [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: "\(id.rawValue.uuidString).\(kind.rawValue)",
            kSecUseDataProtectionKeychain as String: true,
        ]
    }

    private func query(_ profile: ServerProfile, _ kind: SecretKind) -> [String: Any] {
        let scope = SHA256.hash(data: Data(profile.address.origin.utf8)).map { String(format: "%02x", $0) }.joined()
        var request = query(profile.id, kind)
        request[kSecAttrAccount as String] = "\(profile.id.rawValue.uuidString).\(scope).\(kind.rawValue)"
        request[kSecAttrGeneric as String] = Data(profile.id.rawValue.uuidString.utf8)
        return request
    }

    public func read(profile: ServerProfile, kind: SecretKind) throws -> Secret? {
        var request = query(profile, kind)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess else { throw KeychainFailure(status: status) }
        guard let data = result as? Data else { throw AppError.unknown }
        return Secret(data)
    }

    public func write(profile: ServerProfile, kind: SecretKind, value: Secret?) throws {
        let request = query(profile, kind)
        guard let value else {
            let status = SecItemDelete(request as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainFailure(status: status) }
            return
        }
        try value.withBytes { bytes in
            let attributes = [kSecValueData as String: bytes] as CFDictionary
            let updated = SecItemUpdate(request as CFDictionary, attributes)
            if updated == errSecItemNotFound {
                var insertion = request
                insertion[kSecValueData as String] = bytes
                insertion[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
                let added = SecItemAdd(insertion as CFDictionary, nil)
                guard added == errSecSuccess else { throw KeychainFailure(status: added) }
            } else if updated != errSecSuccess {
                throw KeychainFailure(status: updated)
            }
        }
    }

    public func clear(profileID: ProfileID) throws {
        var scoped = query(profileID, .sessionCookie)
        scoped.removeValue(forKey: kSecAttrAccount as String)
        scoped[kSecAttrGeneric as String] = Data(profileID.rawValue.uuidString.utf8)
        try delete(scoped)
        // Remove old unbound records too, without ever adopting their credentials.
        for kind in SecretKind.allCases { try delete(query(profileID, kind)) }
    }

    private func delete(_ request: [String: Any]) throws {
        let status = SecItemDelete(request as CFDictionary)
        guard status == errSecSuccess || status == errSecItemNotFound else { throw KeychainFailure(status: status) }
    }
}

// Preserve only the system status for diagnosis, never query attributes or secret data.
private struct KeychainFailure: Error {
    let status: OSStatus
}
