// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class MediaStatus(val rawValue: Int?) {
    data object UNKNOWN : MediaStatus(1)

    data object PENDING : MediaStatus(2)

    data object PROCESSING : MediaStatus(3)

    data object PARTIALLY_AVAILABLE : MediaStatus(4)

    data object AVAILABLE : MediaStatus(5)

    data object BLOCKLISTED : MediaStatus(6)

    data object DELETED : MediaStatus(7)

    data class Unrecognized(val value: Int?) : MediaStatus(value)

    companion object {
        fun fromWire(raw: Int?): MediaStatus =
            when (raw) {
                1 -> UNKNOWN
                2 -> PENDING
                3 -> PROCESSING
                4 -> PARTIALLY_AVAILABLE
                5 -> AVAILABLE
                6 -> BLOCKLISTED
                7 -> DELETED
                else -> Unrecognized(raw)
            }
    }
}
