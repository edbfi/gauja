// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/server.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class MediaServerType(val rawValue: Int?) {
    data object PLEX : MediaServerType(1)

    data object JELLYFIN : MediaServerType(2)

    data object EMBY : MediaServerType(3)

    data object NOT_CONFIGURED : MediaServerType(4)

    data class Unrecognized(val value: Int?) : MediaServerType(value)

    companion object {
        fun fromWire(raw: Int?): MediaServerType =
            when (raw) {
                1 -> PLEX
                2 -> JELLYFIN
                3 -> EMBY
                4 -> NOT_CONFIGURED
                else -> Unrecognized(raw)
            }
    }
}
