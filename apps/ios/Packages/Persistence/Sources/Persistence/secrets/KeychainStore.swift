// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Security

public enum SecretKind: String, Sendable, CaseIterable {
    case sessionCookie, apiKey, basicAuthPassword, plexToken
}

public protocol SecretStore: Sendable {
    func read(profileID: ProfileID, kind: SecretKind) async throws -> Secret?
    func write(profileID: ProfileID, kind: SecretKind, value: Secret?) async throws
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

    public func read(profileID: ProfileID, kind: SecretKind) throws -> Secret? {
        var request = query(profileID, kind)
        request[kSecReturnData as String] = true
        request[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(request as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data else { throw AppError.unknown }
        return Secret(data)
    }

    public func write(profileID: ProfileID, kind: SecretKind, value: Secret?) throws {
        let request = query(profileID, kind)
        guard let value else {
            let status = SecItemDelete(request as CFDictionary)
            guard status == errSecSuccess || status == errSecItemNotFound else { throw AppError.unknown }
            return
        }
        try value.withBytes { bytes in
            let attributes = [kSecValueData as String: bytes] as CFDictionary
            let updated = SecItemUpdate(request as CFDictionary, attributes)
            if updated == errSecItemNotFound {
                var insertion = request
                insertion[kSecValueData as String] = bytes
                insertion[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
                guard SecItemAdd(insertion as CFDictionary, nil) == errSecSuccess else { throw AppError.unknown }
            } else if updated != errSecSuccess {
                throw AppError.unknown
            }
        }
    }

    public func clear(profileID: ProfileID) throws {
        for kind in SecretKind.allCases { try write(profileID: profileID, kind: kind, value: nil) }
    }
}
