// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/media.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class MediaRequestStatus(val rawValue: Int?) {
    data object PENDING : MediaRequestStatus(1)

    data object APPROVED : MediaRequestStatus(2)

    data object DECLINED : MediaRequestStatus(3)

    data object FAILED : MediaRequestStatus(4)

    data object COMPLETED : MediaRequestStatus(5)

    data class Unrecognized(val value: Int?) : MediaRequestStatus(value)

    companion object {
        fun fromWire(raw: Int?): MediaRequestStatus =
            when (raw) {
                1 -> PENDING
                2 -> APPROVED
                3 -> DECLINED
                4 -> FAILED
                5 -> COMPLETED
                else -> Unrecognized(raw)
            }
    }
}
