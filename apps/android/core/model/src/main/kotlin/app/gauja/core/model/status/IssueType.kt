// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/issue.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class IssueType(val rawValue: Int?) {
    data object VIDEO : IssueType(1)

    data object AUDIO : IssueType(2)

    data object SUBTITLES : IssueType(3)

    data object OTHER : IssueType(4)

    data class Unrecognized(val value: Int?) : IssueType(value)

    companion object {
        fun fromWire(raw: Int?): IssueType =
            when (raw) {
                1 -> VIDEO
                2 -> AUDIO
                3 -> SUBTITLES
                4 -> OTHER
                else -> Unrecognized(raw)
            }
    }
}
