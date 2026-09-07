// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.datastore.profiles

import app.gauja.core.model.ServerAddress
import app.gauja.core.model.servers.AuthMethod
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.CertificateFingerprint
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.PublicSettings
import app.gauja.core.model.servers.ServerProfile
import app.gauja.core.model.servers.ServerStatus
import app.gauja.core.model.servers.TlsMode
import app.gauja.core.model.status.MediaServerType
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable

// Proto field order is persisted: append fields; never reorder or reuse their numbers.
@Serializable internal data class ProfileDocument(val profiles: List<ProfileRecord> = emptyList())

@Serializable
internal data class ProfileRecord(
    val id: String,
    val name: String,
    val address: String,
    val fingerprint: String? = null,
    val authMethod: String = AuthMethod.SESSION.name,
    val basicUsername: String? = null,
    val operatorAcknowledged: Boolean = false,
    val status: StatusRecord? = null,
    val settings: SettingsRecord? = null,
) {
    fun domain(): ServerProfile =
        ServerProfile(
            ProfileId(UUID.fromString(id)),
            name,
            requireNotNull(ServerAddress.parse(address)),
            fingerprint?.let { TlsMode.Pinned(requireNotNull(CertificateFingerprint.parse(it))) }
                ?: TlsMode.System,
            AuthMethod.valueOf(authMethod),
            basicUsername,
            operatorAcknowledged,
            status?.domain(),
            settings?.domain(),
        )

    override fun toString(): String = "ProfileRecord($id)"

    companion object {
        fun from(profile: ServerProfile): ProfileRecord =
            ProfileRecord(
                profile.id.value.toString(),
                profile.displayName,
                profile.address.value,
                (profile.tlsMode as? TlsMode.Pinned)?.fingerprint?.hex,
                profile.authMethod.name,
                profile.basicAuthUsername,
                profile.operatorAcknowledged,
                profile.status?.let(StatusRecord::from),
                profile.publicSettings?.let(SettingsRecord::from),
            )
    }
}

@Serializable
internal data class StatusRecord(
    val version: String? = null,
    val commitTag: String? = null,
    val updateAvailable: Boolean? = null,
    val restartRequired: Boolean? = null,
    val fetchedAt: Long,
) {
    fun domain(): Cached<ServerStatus> =
        Cached(
            ServerStatus(version, commitTag, updateAvailable, restartRequired),
            Instant.ofEpochMilli(fetchedAt),
        )

    companion object {
        fun from(cache: Cached<ServerStatus>): StatusRecord =
            with(cache.value) {
                StatusRecord(
                    version,
                    commitTag,
                    updateAvailable,
                    restartRequired,
                    cache.fetchedAt.toEpochMilli(),
                )
            }
    }
}

@Serializable
internal data class SettingsRecord(
    val title: String? = null,
    val initialized: Boolean? = null,
    val localLogin: Boolean? = null,
    val mediaServerLogin: Boolean? = null,
    val mediaServerType: Int? = null,
    val cacheImages: Boolean? = null,
    val quickConnect: Boolean? = null,
    val newPlexLogin: Boolean? = null,
    val fetchedAt: Long,
) {
    fun domain(): Cached<PublicSettings> =
        Cached(
            PublicSettings(
                title,
                initialized,
                localLogin,
                mediaServerLogin,
                MediaServerType.fromWire(mediaServerType),
                cacheImages,
                quickConnect,
                newPlexLogin,
            ),
            Instant.ofEpochMilli(fetchedAt),
        )

    companion object {
        fun from(cache: Cached<PublicSettings>): SettingsRecord =
            with(cache.value) {
                SettingsRecord(
                    title,
                    initialized,
                    localLogin,
                    mediaServerLogin,
                    mediaServerType.rawValue,
                    cacheImages,
                    jellyfinQuickConnect,
                    newPlexLogin,
                    cache.fetchedAt.toEpochMilli(),
                )
            }
    }
}
