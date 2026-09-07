// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.compat

import app.gauja.core.model.servers.ServerStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class FeatureMetadata(
    val min: String,
    val max: String?,
    val endpoint: String,
    val note: String,
)

class FeatureGate(private val metadata: Map<String, FeatureMetadata>) {
    fun isSupported(featureId: String, version: ServerVersion?): Boolean {
        val feature = metadata[featureId] ?: return false
        val minimum = ServerVersion.parse(feature.min) ?: return false
        val maximum = feature.max?.let { ServerVersion.parse(it) ?: return false }
        return version != null && version >= minimum && (maximum == null || version <= maximum)
    }

    fun outsideSupportedRange(status: ServerStatus): Boolean {
        val version = ServerVersion.parse(status.version)
        return metadata.keys.none { isSupported(it, version) }
    }

    fun metadata(featureId: String): FeatureMetadata? = metadata[featureId]

    companion object {
        fun bundled(): FeatureGate =
            requireNotNull(FeatureGate::class.java.getResourceAsStream("/compat.json")).use {
                decode(it.bufferedReader().readText())
            }

        fun decode(json: String): FeatureGate =
            FeatureGate(Json { ignoreUnknownKeys = true }.decodeFromString(json))
    }
}
