// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.links

import app.gauja.core.model.media.TmdbId
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.servers.ServerProfile
import java.net.URI
import java.util.UUID

sealed interface LinkTarget {
    data class Media(val kind: Kind, val id: TmdbId) : LinkTarget

    data object Requests : LinkTarget

    data class Issue(val id: Int) : LinkTarget

    data class ResetPassword(val guid: UUID) : LinkTarget

    enum class Kind {
        MOVIE,
        TV,
        PERSON,
        COLLECTION,
    }
}

data class DeepLink(val profileIds: List<ProfileId>, val target: LinkTarget)

fun parseDeepLink(raw: String, profiles: List<ServerProfile>): DeepLink? {
    val url =
        try {
            URI(raw)
        } catch (_: java.net.URISyntaxException) {
            return null
        }
    if (url.userInfo != null || url.query != null || url.fragment != null) return null
    val path = url.rawPath ?: return null
    if (path.contains("%") || path.contains("\\")) return null
    val candidates: List<ProfileId>
    val route: String
    if (url.scheme == "gauja" && url.host == "server" && url.port == -1) {
        val parts = path.trimStart('/').split('/', limit = 2)
        if (parts.size != 2) return null
        val id =
            try {
                ProfileId(canonicalUuid(parts[0]) ?: return null)
            } catch (_: IllegalArgumentException) {
                return null
            }
        candidates = profiles.filter { it.id == id }.map { it.id }
        route = parts[1]
    } else {
        val matching =
            profiles.filter {
                val base = URI(it.address.value)
                url.scheme == base.scheme &&
                    url.host == base.host &&
                    url.port == base.port &&
                    path.startsWith(base.path + "/")
            }
        if (matching.isEmpty()) return null
        val prefix = matching.maxOf { URI(it.address.value).path.length }
        candidates = matching.filter { URI(it.address.value).path.length == prefix }.map { it.id }
        route = path.drop(prefix + 1)
    }
    if (candidates.isEmpty()) return null
    val parts = route.split('/')
    val target =
        if (parts == listOf("requests")) LinkTarget.Requests
        else {
            if (parts.size != 2) return null
            when (parts[0]) {
                "resetpassword",
                "reset-password" ->
                    try {
                        LinkTarget.ResetPassword(canonicalUuid(parts[1]) ?: return null)
                    } catch (_: IllegalArgumentException) {
                        return null
                    }
                "issues" ->
                    LinkTarget.Issue(parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null)
                else -> {
                    val kind =
                        LinkTarget.Kind.entries.firstOrNull { it.name.lowercase() == parts[0] }
                            ?: return null
                    LinkTarget.Media(
                        kind,
                        TmdbId(parts[1].toIntOrNull()?.takeIf { it > 0 } ?: return null),
                    )
                }
            }
        }
    return DeepLink(candidates, target)
}

private fun canonicalUuid(value: String): UUID? =
    UUID.fromString(value).takeIf { it.toString().equals(value, ignoreCase = true) }
