// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// Seerr server/constants/discover.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
public enum DiscoverSliderType: Sendable, Equatable, Codable {
    case recentlyAdded
    case recentRequests
    case plexWatchlist
    case trending
    case popularMovies
    case movieGenres
    case upcomingMovies
    case studios
    case popularTv
    case tvGenres
    case upcomingTv
    case networks
    case tmdbMovieKeyword
    case tmdbMovieGenre
    case tmdbTvKeyword
    case tmdbTvGenre
    case tmdbSearch
    case tmdbStudio
    case tmdbNetwork
    case tmdbMovieStreamingServices
    case tmdbTvStreamingServices
    case unrecognized(Int?)

    public init(rawValue: Int?) {
        switch rawValue {
        case 1: self = .recentlyAdded
        case 2: self = .recentRequests
        case 3: self = .plexWatchlist
        case 4: self = .trending
        case 5: self = .popularMovies
        case 6: self = .movieGenres
        case 7: self = .upcomingMovies
        case 8: self = .studios
        case 9: self = .popularTv
        case 10: self = .tvGenres
        case 11: self = .upcomingTv
        case 12: self = .networks
        case 13: self = .tmdbMovieKeyword
        case 14: self = .tmdbMovieGenre
        case 15: self = .tmdbTvKeyword
        case 16: self = .tmdbTvGenre
        case 17: self = .tmdbSearch
        case 18: self = .tmdbStudio
        case 19: self = .tmdbNetwork
        case 20: self = .tmdbMovieStreamingServices
        case 21: self = .tmdbTvStreamingServices
        default: self = .unrecognized(rawValue)
        }
    }

    public var rawValue: Int? {
        switch self {
        case .recentlyAdded: 1
        case .recentRequests: 2
        case .plexWatchlist: 3
        case .trending: 4
        case .popularMovies: 5
        case .movieGenres: 6
        case .upcomingMovies: 7
        case .studios: 8
        case .popularTv: 9
        case .tvGenres: 10
        case .upcomingTv: 11
        case .networks: 12
        case .tmdbMovieKeyword: 13
        case .tmdbMovieGenre: 14
        case .tmdbTvKeyword: 15
        case .tmdbTvGenre: 16
        case .tmdbSearch: 17
        case .tmdbStudio: 18
        case .tmdbNetwork: 19
        case .tmdbMovieStreamingServices: 20
        case .tmdbTvStreamingServices: 21
        case .unrecognized(let raw): raw
        }
    }
}
