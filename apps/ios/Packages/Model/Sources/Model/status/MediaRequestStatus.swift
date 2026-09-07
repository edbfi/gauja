// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum MediaRequestStatus: Sendable, Equatable, Codable {
    case pending
    case approved
    case declined
    case failed
    case completed
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .pending
        case 2: self = .approved
        case 3: self = .declined
        case 4: self = .failed
        case 5: self = .completed
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .pending: 1
        case .approved: 2
        case .declined: 3
        case .failed: 4
        case .completed: 5
        case .unrecognized(let raw): raw
        }
    }
}
