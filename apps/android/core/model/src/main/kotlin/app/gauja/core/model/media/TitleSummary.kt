// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.media

import app.gauja.core.model.status.MediaStatus

@JvmInline
value class TmdbId(val value: Int) {
    init {
        require(value > 0)
    }
}

data class TitleSummary(
    val id: TmdbId,
    val mediaType: MediaType,
    val title: String?,
    val year: Int?,
    val posterPath: String?,
    val rating: Double?,
    val status: MediaStatus,
    val status4k: MediaStatus? = null,
)
