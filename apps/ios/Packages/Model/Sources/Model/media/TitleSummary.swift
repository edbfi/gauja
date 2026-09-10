// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct TMDBID: RawRepresentable, Hashable, Sendable, Codable {
    public let rawValue: Int
    public init?(rawValue: Int) {
        guard rawValue > 0 else { return nil }
        self.rawValue = rawValue
    }
    public init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        let raw = try container.decode(Int.self)
        guard let value = Self(rawValue: raw) else {
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Invalid media identifier")
        }
        self = value
    }
    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(rawValue)
    }

}

public struct TitleSummary: Sendable, Equatable, Codable {
    public let id: TMDBID
    public let mediaType: MediaType
    public let title: String?
    public let year: Int?
    public let posterPath: String?
    public let rating: Double?
    public let status: MediaStatus
    public let status4K: MediaStatus?
    public init(
        id: TMDBID, mediaType: MediaType, title: String?, year: Int?, posterPath: String?,
        rating: Double?, status: MediaStatus, status4K: MediaStatus? = nil
    ) {
        self.id = id
        self.mediaType = mediaType
        self.title = title
        self.year = year
        self.posterPath = posterPath
        self.rating = rating
        self.status = status
        self.status4K = status4K
    }
}
