// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum MediaStatus: Sendable, Equatable, Codable {
    case unknown
    case pending
    case processing
    case partiallyAvailable
    case available
    case blocklisted
    case deleted
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .unknown
        case 2: self = .pending
        case 3: self = .processing
        case 4: self = .partiallyAvailable
        case 5: self = .available
        case 6: self = .blocklisted
        case 7: self = .deleted
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .unknown: 1
        case .pending: 2
        case .processing: 3
        case .partiallyAvailable: 4
        case .available: 5
        case .blocklisted: 6
        case .deleted: 7
        case .unrecognized(let raw): raw
        }
    }
}
