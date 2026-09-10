// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/issue.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum IssueStatus: Sendable, Equatable, Codable {
    case open
    case resolved
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .open
        case 2: self = .resolved
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .open: 1
        case .resolved: 2
        case .unrecognized(let raw): raw
        }
    }
}
