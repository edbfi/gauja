// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.profiles

import androidx.datastore.core.DataStore
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ServerProfileStore {
    val profiles: Flow<List<ServerProfile>>

    suspend fun save(profile: ServerProfile)

    suspend fun remove(id: ProfileId)

    suspend fun reorder(ids: List<ProfileId>)
}

internal class ProtoServerProfileStore
@Inject
constructor(private val store: DataStore<ProfileDocument>) : ServerProfileStore {
    override val profiles: Flow<List<ServerProfile>> =
        store.data.map { it.profiles.map(ProfileRecord::domain) }

    override suspend fun save(profile: ServerProfile) {
        val record = ProfileRecord.from(profile)
        store.updateData { document ->
            val index = document.profiles.indexOfFirst { it.id == record.id }
            val profiles = document.profiles.toMutableList()
            if (index < 0) profiles.add(record) else profiles[index] = record
            ProfileDocument(profiles)
        }
    }

    override suspend fun remove(id: ProfileId) {
        store.updateData { document ->
            ProfileDocument(document.profiles.filterNot { it.id == id.value.toString() })
        }
    }

    override suspend fun reorder(ids: List<ProfileId>) {
        store.updateData { document ->
            val records = document.profiles.associateBy(ProfileRecord::id)
            val order = ids.map { it.value.toString() }
            require(order.size == records.size && order.toSet() == records.keys)
            ProfileDocument(order.map { records.getValue(it) })
        }
    }
}
