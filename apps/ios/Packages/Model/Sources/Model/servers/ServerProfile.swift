// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct ProfileID: RawRepresentable, Hashable, Sendable, Codable {
    public let rawValue: UUID
    public init(rawValue: UUID) { self.rawValue = rawValue }
}

public struct CertificateFingerprint: Sendable, Equatable, Codable {
    public let hex: String
    public init?(_ text: String) {
        let normalized = text.replacingOccurrences(of: ":", with: "").lowercased()
        guard normalized.count == 64, normalized.allSatisfy({ "0123456789abcdef".contains($0) }) else { return nil }
        hex = normalized
    }
    public init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(String.self)
        guard let value = Self(raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid certificate fingerprint")
        }
        self = value
    }
    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(hex)
    }

}

public enum TLSMode: Sendable, Equatable, Codable {
    case system
    case pinned(CertificateFingerprint)
}

public enum AuthMethod: String, Sendable, Codable { case session, apiKey }

public struct ServerProfile: Sendable, Equatable {
    public let id: ProfileID
    public var displayName: String
    public var address: ServerAddress
    public var tlsMode: TLSMode
    public var authMethod: AuthMethod
    public var basicAuthUsername: String?
    public var operatorAcknowledged: Bool
    public var status: Cached<ServerStatus>?
    public var publicSettings: Cached<PublicSettings>?

    public init?(
        id: ProfileID, displayName: String, address: ServerAddress, tlsMode: TLSMode = .system,
        authMethod: AuthMethod = .session, basicAuthUsername: String? = nil, operatorAcknowledged: Bool = false,
        status: Cached<ServerStatus>? = nil, publicSettings: Cached<PublicSettings>? = nil
    ) {
        guard !displayName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
            authMethod != .apiKey || operatorAcknowledged
        else { return nil }
        self.id = id
        self.displayName = displayName
        self.address = address
        self.tlsMode = tlsMode
        self.authMethod = authMethod
        self.basicAuthUsername = basicAuthUsername
        self.operatorAcknowledged = operatorAcknowledged
        self.status = status
        self.publicSettings = publicSettings
    }
}

public struct Cached<Value: Sendable & Equatable>: Sendable, Equatable {
    public let value: Value
    public let fetchedAt: Date
    public init(_ value: Value, fetchedAt: Date) {
        self.value = value
        self.fetchedAt = fetchedAt
    }
}
