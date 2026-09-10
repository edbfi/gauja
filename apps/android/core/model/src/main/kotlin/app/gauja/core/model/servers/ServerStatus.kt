// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.servers

import app.gauja.core.model.status.MediaServerType

data class ServerStatus(
    val version: String?,
    val commitTag: String?,
    val updateAvailable: Boolean?,
    val restartRequired: Boolean?,
)

data class PublicSettings(
    val title: String?,
    val initialized: Boolean?,
    val localLogin: Boolean?,
    val mediaServerLogin: Boolean?,
    val mediaServerType: MediaServerType,
    val cacheImages: Boolean?,
    val jellyfinQuickConnect: Boolean? = null,
    val newPlexLogin: Boolean? = null,
)
