// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum MediaType: Sendable, Equatable, Codable {
    case movie, tv
    case unrecognized(String?)

    public init(rawValue: String?) {
        switch rawValue {
        case "movie": self = .movie
        case "tv": self = .tv
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: String? {
        switch self {
        case .movie: "movie"
        case .tv: "tv"
        case .unrecognized(let raw): raw
        }
    }
}
