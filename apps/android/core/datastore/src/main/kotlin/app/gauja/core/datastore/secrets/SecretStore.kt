// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.secrets

import androidx.datastore.core.DataStore
import app.gauja.core.common.Secret
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable

enum class SecretKind {
    SESSION_COOKIE,
    API_KEY,
    BASIC_AUTH_PASSWORD,
    PLEX_TOKEN,
}

interface SecretStore {
    suspend fun read(profile: ServerProfile, kind: SecretKind): Secret?

    suspend fun write(profile: ServerProfile, kind: SecretKind, value: Secret?)

    suspend fun clear(profileId: ProfileId)
}

@Serializable
internal data class SecretRecord(
    val profileId: String,
    val kind: String,
    val bytes: ByteArray,
    // Appended for protobuf compatibility. Unbound legacy records are never read.
    val origin: String? = null,
) {
    override fun toString(): String = "[REDACTED]"
}

@Serializable
internal data class SecretDocument(val records: List<SecretRecord> = emptyList()) {
    override fun toString(): String = "[REDACTED]"
}

internal class EncryptedSecretStore
@Inject
constructor(private val store: DataStore<SecretDocument>) : SecretStore {
    override suspend fun read(profile: ServerProfile, kind: SecretKind): Secret? =
        store.data
            .first()
            .records
            .firstOrNull {
                it.profileId == profile.id.value.toString() &&
                    it.kind == kind.name &&
                    it.origin == profile.address.origin
            }
            ?.let { Secret(it.bytes) }

    override suspend fun write(profile: ServerProfile, kind: SecretKind, value: Secret?) {
        store.updateData { document ->
            val remaining =
                document.records.filterNot {
                    it.profileId == profile.id.value.toString() && it.kind == kind.name
                }
            val record =
                value?.useBytes {
                    SecretRecord(
                        profile.id.value.toString(),
                        kind.name,
                        it.copyOf(),
                        profile.address.origin,
                    )
                }
            SecretDocument(if (record == null) remaining else remaining + record)
        }
    }

    override suspend fun clear(profileId: ProfileId) {
        store.updateData { document ->
            SecretDocument(
                document.records.filterNot { it.profileId == profileId.value.toString() }
            )
        }
    }
}
