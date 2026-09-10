// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences as StoredPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.gauja.core.model.servers.DiscoverPreferences
import app.gauja.core.model.servers.Preferences
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ThemeMode
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PreferencesStore @Inject constructor(private val store: DataStore<StoredPreferences>) {
    private val theme = stringPreferencesKey("theme")
    private val active = stringPreferencesKey("activeProfile")
    private val motion = booleanPreferencesKey("reduceMotion")

    val preferences: Flow<Preferences> =
        store.data.map { values ->
            Preferences(
                ThemeMode.entries.firstOrNull { it.name == values[theme] } ?: ThemeMode.DARK,
                values[active]?.let { runCatching { ProfileId(UUID.fromString(it)) }.getOrNull() },
                values[motion] ?: false,
            )
        }

    suspend fun save(preferences: Preferences) {
        store.edit { values ->
            values[theme] = preferences.theme.name
            values[motion] = preferences.reduceMotion
            preferences.activeProfileId?.let { values[active] = it.value.toString() }
                ?: values.remove(active)
        }
    }

    fun discover(id: ProfileId): Flow<DiscoverPreferences> =
        store.data.map { values ->
            DiscoverPreferences(
                values[region(id)],
                values[providers(id)].orEmpty().mapNotNull(String::toIntOrNull).toSet(),
            )
        }

    suspend fun saveDiscover(id: ProfileId, preferences: DiscoverPreferences) {
        store.edit { values ->
            preferences.region?.let { values[region(id)] = it } ?: values.remove(region(id))
            values[providers(id)] = preferences.providerIds.map(Int::toString).toSet()
        }
    }

    suspend fun clear(id: ProfileId) {
        store.edit { values ->
            values.remove(region(id))
            values.remove(providers(id))
            if (values[active] == id.value.toString()) values.remove(active)
        }
    }

    private fun region(id: ProfileId) = stringPreferencesKey("${id.value}.region")

    private fun providers(id: ProfileId) = stringSetPreferencesKey("${id.value}.providers")
}
