// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.network

import app.gauja.core.model.servers.ProfileId
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Headers

data class EndpointDeprecation(
    val endpoint: String,
    val deprecation: String?,
    val sunset: String?,
    val successor: String?,
)

@Singleton
class DeprecationStore @Inject constructor() {
    private val entries = mutableMapOf<ProfileId, LinkedHashMap<String, EndpointDeprecation>>()

    @Synchronized
    fun record(id: ProfileId, endpoint: String, headers: Headers) {
        val link =
            headers
                .values("Link")
                .flatMap { it.split(',') }
                .firstOrNull { it.contains(Regex("rel=\"?successor-version\"?")) }
        if (headers["Deprecation"] == null && headers["Sunset"] == null && link == null) return
        val profile = entries.getOrPut(id) { linkedMapOf() }
        profile[endpoint] =
            EndpointDeprecation(endpoint, headers["Deprecation"], headers["Sunset"], link)
        if (profile.size > 512) profile.remove(profile.keys.first())
    }

    @Synchronized
    fun read(id: ProfileId): List<EndpointDeprecation> = entries[id]?.values?.toList().orEmpty()

    @Synchronized
    fun clear(id: ProfileId) {
        entries.remove(id)
    }
}
