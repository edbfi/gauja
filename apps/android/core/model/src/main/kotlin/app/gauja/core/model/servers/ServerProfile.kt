// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.servers

import app.gauja.core.model.ServerAddress
import java.time.Instant
import java.util.UUID

@JvmInline value class ProfileId(val value: UUID)

sealed interface TlsMode {
    data object System : TlsMode

    data class Pinned(val fingerprint: CertificateFingerprint) : TlsMode
}

@JvmInline
value class CertificateFingerprint private constructor(val hex: String) {
    companion object {
        fun parse(raw: String): CertificateFingerprint? {
            val normalized = raw.replace(":", "").lowercase()
            return if (normalized.matches(Regex("[0-9a-f]{64}"))) CertificateFingerprint(normalized)
            else null
        }
    }
}

enum class AuthMethod {
    SESSION,
    API_KEY,
}

data class ServerProfile(
    val id: ProfileId,
    val displayName: String,
    val address: ServerAddress,
    val tlsMode: TlsMode = TlsMode.System,
    val authMethod: AuthMethod = AuthMethod.SESSION,
    val basicAuthUsername: String? = null,
    val operatorAcknowledged: Boolean = false,
    val status: Cached<ServerStatus>? = null,
    val publicSettings: Cached<PublicSettings>? = null,
) {
    init {
        require(displayName.isNotBlank())
        require(authMethod != AuthMethod.API_KEY || operatorAcknowledged)
    }

    override fun toString(): String = "ServerProfile($id)"
}

data class Cached<T>(val value: T, val fetchedAt: Instant)
