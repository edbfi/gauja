// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.common

object Redaction {
    fun text(
        value: String,
        secrets: List<Secret> = emptyList(),
        hosts: List<String> = emptyList(),
    ): String {
        var safe = value
        for (secret in secrets) {
            safe =
                secret.useBytes { bytes ->
                    val token = bytes.toString(Charsets.UTF_8)
                    if (token.isEmpty()) safe else safe.replace(token, "[REDACTED]")
                }
        }
        for (host in hosts.filter(String::isNotEmpty).sortedByDescending(String::length)) {
            safe = safe.replace(host, "[HOST]", ignoreCase = true)
        }
        return safe
            .replace(Regex("(?i)https?://[^\\s<>]+"), "[URL]")
            .replace(
                Regex("(?im)(cookie|set-cookie|authorization|x-api-key)\\s*[:=][^\\r\\n]*"),
                "$1: [REDACTED]",
            )
    }
}
