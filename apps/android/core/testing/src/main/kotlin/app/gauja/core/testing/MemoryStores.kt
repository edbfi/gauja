// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.testing

import app.gauja.core.common.Secret
import app.gauja.core.datastore.profiles.ServerProfileStore
import app.gauja.core.datastore.secrets.SecretKind
import app.gauja.core.datastore.secrets.SecretStore
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class MemorySecrets : SecretStore {
    private val values = ConcurrentHashMap<Pair<ProfileId, SecretKind>, Secret>()

    override suspend fun read(profileId: ProfileId, kind: SecretKind): Secret? =
        values[profileId to kind]

    override suspend fun write(profileId: ProfileId, kind: SecretKind, value: Secret?) {
        if (value == null) values.remove(profileId to kind) else values[profileId to kind] = value
    }

    override suspend fun clear(profileId: ProfileId) {
        values.keys.removeIf { it.first == profileId }
    }
}

class MemoryProfiles : ServerProfileStore {
    override val profiles = MutableStateFlow<List<ServerProfile>>(emptyList())

    override suspend fun save(profile: ServerProfile) {
        profiles.update { it.filterNot { old -> old.id == profile.id } + profile }
    }

    override suspend fun remove(id: ProfileId) {
        profiles.update { it.filterNot { old -> old.id == id } }
    }

    override suspend fun reorder(ids: List<ProfileId>) {
        profiles.update { rows -> ids.map { id -> rows.first { it.id == id } } }
    }
}
