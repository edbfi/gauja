// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model

import app.gauja.core.model.status.MediaServerType

data class ServerSnapshot(
    val address: ServerAddress,
    val version: String?,
    val title: String?,
    val initialized: Boolean?,
    val restartRequired: Boolean?,
    val localLogin: Boolean?,
    val mediaServerLogin: Boolean?,
    val mediaServerType: MediaServerType,
    val compatibility: Compatibility,
)

enum class Compatibility {
    TESTED,
    TOO_OLD,
    UNTESTED,
    UNKNOWN,
}
