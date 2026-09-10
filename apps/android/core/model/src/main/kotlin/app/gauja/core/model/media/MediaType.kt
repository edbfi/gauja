// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.media

// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class MediaType(val rawValue: String?) {
    data object MOVIE : MediaType("movie")

    data object TV : MediaType("tv")

    data class Unrecognized(val value: String?) : MediaType(value)

    companion object {
        fun fromWire(raw: String?): MediaType =
            when (raw) {
                "movie" -> MOVIE
                "tv" -> TV
                else -> Unrecognized(raw)
            }
    }
}
