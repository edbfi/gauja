// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model

import java.net.URI

@JvmInline
value class ServerAddress private constructor(val value: String) {
    val apiBase: String
        get() = value.trimEnd('/') + "/api/v1/"

    val isPlainHttp: Boolean
        get() = value.startsWith("http://")

    // Paths do not change credential scope; explicit default ports are equivalent.
    val origin: String
        get() {
            val uri = URI(value)
            val port = if (uri.port == -1) if (isPlainHttp) 80 else 443 else uri.port
            return "${uri.scheme}://${uri.host}:$port"
        }

    override fun toString(): String = "[SERVER]"

    companion object {
        fun parse(input: String): ServerAddress? {
            val trimmed = input.trim()
            if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() || it == '\\' }) return null
            val value = if ("://" in trimmed) trimmed else "https://$trimmed"
            val uri =
                try {
                    URI(value)
                } catch (_: java.net.URISyntaxException) {
                    return null
                }
            if (
                uri.scheme?.lowercase() !in setOf("https", "http") ||
                    uri.host.isNullOrBlank() ||
                    uri.rawUserInfo != null ||
                    uri.rawQuery != null ||
                    uri.rawFragment != null ||
                    uri.port !in -1..65535 ||
                    uri.port == 0 ||
                    uri.normalize().rawPath != uri.rawPath ||
                    uri.path.split('/').any { it == "." || it == ".." } ||
                    Regex("(?i)%2f|%5c").containsMatchIn(uri.rawPath)
            )
                return null
            return ServerAddress(
                URI(
                        uri.scheme.lowercase(),
                        null,
                        uri.host.lowercase(),
                        uri.port,
                        uri.path.trimEnd('/'),
                        null,
                        null,
                    )
                    .toASCIIString()
            )
        }
    }
}
