// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

// Intentionally not Codable; wire encoding and secure persistence are explicit boundaries.
public struct Secret: Sendable, CustomStringConvertible, CustomDebugStringConvertible, CustomReflectable {
    private let bytes: Data
    public init(_ bytes: Data) { self.bytes = bytes }

    public func withBytes<Value>(_ body: (Data) throws -> Value) rethrows -> Value { try body(bytes) }
    public var description: String { "[REDACTED]" }
    public var debugDescription: String { description }
    public var customMirror: Mirror { Mirror(self, children: [:]) }
}

extension Secret {
    public func utf8() throws -> String {
        try withBytes {
            guard let value = String(data: $0, encoding: .utf8) else { throw AppError.validation }
            return value
        }
    }
}
