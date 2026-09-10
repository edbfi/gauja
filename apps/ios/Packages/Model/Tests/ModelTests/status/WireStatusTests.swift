// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Testing

@testable import Model

@Test func mediaStatusPreservesUpstreamValues() {
    let expected: [MediaStatus] = [
        .unknown, .pending, .processing, .partiallyAvailable, .available, .blocklisted, .deleted,
    ]
    #expect(expected == (1...7).map { MediaStatus(rawValue: $0) })
    #expect(MediaStatus(rawValue: 999) == .unrecognized(999))
    #expect(MediaStatus(rawValue: nil) == .unrecognized(nil))
}

@Test func mediaRequestStatusPreservesUpstreamValues() {
    let expected: [MediaRequestStatus] = [.pending, .approved, .declined, .failed, .completed]
    #expect(expected == (1...5).map { MediaRequestStatus(rawValue: $0) })
    #expect(MediaRequestStatus(rawValue: 999) == .unrecognized(999))
    #expect(MediaRequestStatus(rawValue: nil) == .unrecognized(nil))
}

@Test func issueTypePreservesUpstreamValues() {
    let expected: [IssueType] = [.video, .audio, .subtitles, .other]
    #expect(expected == (1...4).map { IssueType(rawValue: $0) })
    #expect(IssueType(rawValue: 999) == .unrecognized(999))
    #expect(IssueType(rawValue: nil) == .unrecognized(nil))
}

@Test func issueStatusPreservesUpstreamValues() {
    let expected: [IssueStatus] = [.open, .resolved]
    #expect(expected == (1...2).map { IssueStatus(rawValue: $0) })
    #expect(IssueStatus(rawValue: 999) == .unrecognized(999))
    #expect(IssueStatus(rawValue: nil) == .unrecognized(nil))
}

@Test func mediaServerTypePreservesUpstreamValues() {
    let expected: [MediaServerType] = [.plex, .jellyfin, .emby, .notConfigured]
    #expect(expected == (1...4).map { MediaServerType(rawValue: $0) })
    #expect(MediaServerType(rawValue: 999) == .unrecognized(999))
    #expect(MediaServerType(rawValue: nil) == .unrecognized(nil))
}

@Test func discoverSliderTypePreservesUpstreamValues() {
    let expected: [DiscoverSliderType] = [
        .recentlyAdded, .recentRequests, .plexWatchlist, .trending, .popularMovies, .movieGenres, .upcomingMovies,
        .studios, .popularTv, .tvGenres, .upcomingTv, .networks, .tmdbMovieKeyword, .tmdbMovieGenre, .tmdbTvKeyword,
        .tmdbTvGenre, .tmdbSearch, .tmdbStudio, .tmdbNetwork, .tmdbMovieStreamingServices, .tmdbTvStreamingServices,
    ]
    #expect(expected == (1...21).map { DiscoverSliderType(rawValue: $0) })
    #expect(DiscoverSliderType(rawValue: 999) == .unrecognized(999))
    #expect(DiscoverSliderType(rawValue: nil) == .unrecognized(nil))
}
