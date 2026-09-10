// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.servers

enum class ThemeMode {
    DARK,
    LIGHT,
    SYSTEM,
}

data class Preferences(
    val theme: ThemeMode = ThemeMode.DARK,
    val activeProfileId: ProfileId? = null,
    val reduceMotion: Boolean = false,
)

data class DiscoverPreferences(val region: String? = null, val providerIds: Set<Int> = emptySet())
