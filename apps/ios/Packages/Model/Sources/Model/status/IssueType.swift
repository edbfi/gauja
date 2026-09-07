// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/issue.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum IssueType: Sendable, Equatable, Codable {
    case video
    case audio
    case subtitles
    case other
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .video
        case 2: self = .audio
        case 3: self = .subtitles
        case 4: self = .other
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .video: 1
        case .audio: 2
        case .subtitles: 3
        case .other: 4
        case .unrecognized(let raw): raw
        }
    }
}
