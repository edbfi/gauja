// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.images

import app.gauja.core.model.ServerAddress
import java.net.URI

enum class PosterSize(val path: String) {
    SMALL("w185"),
    MEDIUM("w342"),
    LARGE("w500"),
}

fun imageUrl(
    source: String?,
    address: ServerAddress,
    cacheImages: Boolean,
    size: PosterSize,
): String? {
    if (source.isNullOrBlank()) return null
    val url =
        try {
            URI(
                if (source.startsWith("/")) "https://image.tmdb.org/t/p/${size.path}$source"
                else source
            )
        } catch (_: java.net.URISyntaxException) {
            return null
        }
    if (
        url.scheme != "https" ||
            url.userInfo != null ||
            url.port != -1 ||
            url.query != null ||
            url.fragment != null
    )
        return null
    val path = url.rawPath ?: return null
    if (
        path.contains("%") || path.contains("\\") || path.split("/").any { it == "." || it == ".." }
    )
        return null
    return when (url.host) {
        "image.tmdb.org" -> {
            val parts = path.split("/")
            if (
                parts.size < 5 ||
                    parts[1] != "t" ||
                    parts[2] != "p" ||
                    parts.drop(4).any(String::isBlank)
            )
                return null
            val sized = "/t/p/${size.path}/" + parts.drop(4).joinToString("/")
            if (cacheImages) "${address.value}/imageproxy/tmdb$sized"
            else "https://image.tmdb.org$sized"
        }
        "artworks.thetvdb.com" ->
            if (cacheImages) "${address.value}/imageproxy/tvdb$path" else url.toString()
        else -> null
    }
}
