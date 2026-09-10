// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct UserID: RawRepresentable, Hashable, Sendable, Codable {
    public let rawValue: Int
    public init?(rawValue: Int) {
        guard rawValue > 0 else { return nil }
        self.rawValue = rawValue
    }
    public init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(Int.self)
        guard let value = Self(rawValue: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid user identifier")
        }
        self = value
    }
    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

}

public struct User: Sendable, Equatable, Codable {
    public let id: UserID
    public let displayName: String
    public let email: String
    public let permissions: UInt32
    public init(id: UserID, displayName: String, email: String, permissions: UInt32) {
        self.id = id
        self.displayName = displayName
        self.email = email
        self.permissions = permissions
    }
}
