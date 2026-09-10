// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/discover.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class DiscoverSliderType(val rawValue: Int?) {
    data object RECENTLY_ADDED : DiscoverSliderType(1)

    data object RECENT_REQUESTS : DiscoverSliderType(2)

    data object PLEX_WATCHLIST : DiscoverSliderType(3)

    data object TRENDING : DiscoverSliderType(4)

    data object POPULAR_MOVIES : DiscoverSliderType(5)

    data object MOVIE_GENRES : DiscoverSliderType(6)

    data object UPCOMING_MOVIES : DiscoverSliderType(7)

    data object STUDIOS : DiscoverSliderType(8)

    data object POPULAR_TV : DiscoverSliderType(9)

    data object TV_GENRES : DiscoverSliderType(10)

    data object UPCOMING_TV : DiscoverSliderType(11)

    data object NETWORKS : DiscoverSliderType(12)

    data object TMDB_MOVIE_KEYWORD : DiscoverSliderType(13)

    data object TMDB_MOVIE_GENRE : DiscoverSliderType(14)

    data object TMDB_TV_KEYWORD : DiscoverSliderType(15)

    data object TMDB_TV_GENRE : DiscoverSliderType(16)

    data object TMDB_SEARCH : DiscoverSliderType(17)

    data object TMDB_STUDIO : DiscoverSliderType(18)

    data object TMDB_NETWORK : DiscoverSliderType(19)

    data object TMDB_MOVIE_STREAMING_SERVICES : DiscoverSliderType(20)

    data object TMDB_TV_STREAMING_SERVICES : DiscoverSliderType(21)

    data class Unrecognized(val value: Int?) : DiscoverSliderType(value)

    companion object {
        fun fromWire(raw: Int?): DiscoverSliderType =
            when (raw) {
                1 -> RECENTLY_ADDED
                2 -> RECENT_REQUESTS
                3 -> PLEX_WATCHLIST
                4 -> TRENDING
                5 -> POPULAR_MOVIES
                6 -> MOVIE_GENRES
                7 -> UPCOMING_MOVIES
                8 -> STUDIOS
                9 -> POPULAR_TV
                10 -> TV_GENRES
                11 -> UPCOMING_TV
                12 -> NETWORKS
                13 -> TMDB_MOVIE_KEYWORD
                14 -> TMDB_MOVIE_GENRE
                15 -> TMDB_TV_KEYWORD
                16 -> TMDB_TV_GENRE
                17 -> TMDB_SEARCH
                18 -> TMDB_STUDIO
                19 -> TMDB_NETWORK
                20 -> TMDB_MOVIE_STREAMING_SERVICES
                21 -> TMDB_TV_STREAMING_SERVICES
                else -> Unrecognized(raw)
            }
    }
}
