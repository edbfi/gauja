// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/server.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum MediaServerType: Sendable, Equatable, Codable {
    case plex
    case jellyfin
    case emby
    case notConfigured
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .plex
        case 2: self = .jellyfin
        case 3: self = .emby
        case 4: self = .notConfigured
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .plex: 1
        case .jellyfin: 2
        case .emby: 3
        case .notConfigured: 4
        case .unrecognized(let raw): raw
        }
    }
}
