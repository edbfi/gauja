// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

import app.gauja.core.model.media.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class WireStatusTest {
    @Test
    fun mediastatusPreservesUpstreamValues() {
        val expected =
            listOf(
                MediaStatus.UNKNOWN,
                MediaStatus.PENDING,
                MediaStatus.PROCESSING,
                MediaStatus.PARTIALLY_AVAILABLE,
                MediaStatus.AVAILABLE,
                MediaStatus.BLOCKLISTED,
                MediaStatus.DELETED,
            )
        assertEquals(expected, (1..7).map(MediaStatus::fromWire))
        assertEquals(MediaStatus.Unrecognized(999), MediaStatus.fromWire(999))
        assertEquals(MediaStatus.Unrecognized(null), MediaStatus.fromWire(null))
    }

    @Test
    fun mediarequeststatusPreservesUpstreamValues() {
        val expected =
            listOf(
                MediaRequestStatus.PENDING,
                MediaRequestStatus.APPROVED,
                MediaRequestStatus.DECLINED,
                MediaRequestStatus.FAILED,
                MediaRequestStatus.COMPLETED,
            )
        assertEquals(expected, (1..5).map(MediaRequestStatus::fromWire))
        assertEquals(MediaRequestStatus.Unrecognized(999), MediaRequestStatus.fromWire(999))
        assertEquals(MediaRequestStatus.Unrecognized(null), MediaRequestStatus.fromWire(null))
    }

    @Test
    fun issuetypePreservesUpstreamValues() {
        val expected =
            listOf(IssueType.VIDEO, IssueType.AUDIO, IssueType.SUBTITLES, IssueType.OTHER)
        assertEquals(expected, (1..4).map(IssueType::fromWire))
        assertEquals(IssueType.Unrecognized(999), IssueType.fromWire(999))
        assertEquals(IssueType.Unrecognized(null), IssueType.fromWire(null))
    }

    @Test
    fun issuestatusPreservesUpstreamValues() {
        val expected = listOf(IssueStatus.OPEN, IssueStatus.RESOLVED)
        assertEquals(expected, (1..2).map(IssueStatus::fromWire))
        assertEquals(IssueStatus.Unrecognized(999), IssueStatus.fromWire(999))
        assertEquals(IssueStatus.Unrecognized(null), IssueStatus.fromWire(null))
    }

    @Test
    fun mediaservertypePreservesUpstreamValues() {
        val expected =
            listOf(
                MediaServerType.PLEX,
                MediaServerType.JELLYFIN,
                MediaServerType.EMBY,
                MediaServerType.NOT_CONFIGURED,
            )
        assertEquals(expected, (1..4).map(MediaServerType::fromWire))
        assertEquals(MediaServerType.Unrecognized(999), MediaServerType.fromWire(999))
        assertEquals(MediaServerType.Unrecognized(null), MediaServerType.fromWire(null))
    }

    @Test
    fun discoverslidertypePreservesUpstreamValues() {
        val expected =
            listOf(
                DiscoverSliderType.RECENTLY_ADDED,
                DiscoverSliderType.RECENT_REQUESTS,
                DiscoverSliderType.PLEX_WATCHLIST,
                DiscoverSliderType.TRENDING,
                DiscoverSliderType.POPULAR_MOVIES,
                DiscoverSliderType.MOVIE_GENRES,
                DiscoverSliderType.UPCOMING_MOVIES,
                DiscoverSliderType.STUDIOS,
                DiscoverSliderType.POPULAR_TV,
                DiscoverSliderType.TV_GENRES,
                DiscoverSliderType.UPCOMING_TV,
                DiscoverSliderType.NETWORKS,
                DiscoverSliderType.TMDB_MOVIE_KEYWORD,
                DiscoverSliderType.TMDB_MOVIE_GENRE,
                DiscoverSliderType.TMDB_TV_KEYWORD,
                DiscoverSliderType.TMDB_TV_GENRE,
                DiscoverSliderType.TMDB_SEARCH,
                DiscoverSliderType.TMDB_STUDIO,
                DiscoverSliderType.TMDB_NETWORK,
                DiscoverSliderType.TMDB_MOVIE_STREAMING_SERVICES,
                DiscoverSliderType.TMDB_TV_STREAMING_SERVICES,
            )
        assertEquals(expected, (1..21).map(DiscoverSliderType::fromWire))
        assertEquals(DiscoverSliderType.Unrecognized(999), DiscoverSliderType.fromWire(999))
        assertEquals(DiscoverSliderType.Unrecognized(null), DiscoverSliderType.fromWire(null))
    }

    @Test
    fun mediaTypePreservesUnknownStrings() {
        assertEquals(MediaType.MOVIE, MediaType.fromWire("movie"))
        assertEquals(MediaType.TV, MediaType.fromWire("tv"))
        assertEquals(MediaType.Unrecognized("future"), MediaType.fromWire("future"))
    }
}
