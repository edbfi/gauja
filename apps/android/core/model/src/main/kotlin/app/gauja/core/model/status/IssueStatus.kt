// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.status

// Seerr server/constants/issue.ts at 69f73a6f1486fdb51b8ddae9a94a8dfb629f461c.
sealed class IssueStatus(val rawValue: Int?) {
    data object OPEN : IssueStatus(1)

    data object RESOLVED : IssueStatus(2)

    data class Unrecognized(val value: Int?) : IssueStatus(value)

    companion object {
        fun fromWire(raw: Int?): IssueStatus =
            when (raw) {
                1 -> OPEN
                2 -> RESOLVED
                else -> Unrecognized(raw)
            }
    }
}
