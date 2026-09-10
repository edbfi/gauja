// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.compat

import app.gauja.core.model.Compatibility

// The supported baseline is the recorded release in api/README.md; future versions are not tested
// claims.
data class ServerVersion(val major: Int, val minor: Int, val patch: Int, val suffix: String) :
    Comparable<ServerVersion> {
    override fun compareTo(other: ServerVersion): Int {
        val core = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })
        if (core != 0) return core
        val left = prerelease()
        val right = other.prerelease()
        if (left == null) return if (right == null) 0 else 1
        if (right == null) return -1
        for ((a, b) in left.zip(right)) {
            val numericA = a.all(Char::isDigit)
            val numericB = b.all(Char::isDigit)
            val order =
                when {
                    numericA && numericB ->
                        a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
                    numericA -> -1
                    numericB -> 1
                    else -> a.compareTo(b)
                }
            if (order != 0) return order
        }
        return left.size.compareTo(right.size)
    }

    private fun prerelease(): List<String>? =
        if (suffix.startsWith('-')) suffix.drop(1).substringBefore('+').split('.') else null

    fun compatibility(): Compatibility {
        val baseline = ServerVersion(3, 4, 1, "")
        return when {
            compareValuesBy(this, baseline, { it.major }, { it.minor }, { it.patch }) < 0 ->
                Compatibility.TOO_OLD
            this != baseline -> Compatibility.UNTESTED
            else -> Compatibility.TESTED
        }
    }

    companion object {
        fun parse(raw: String?): ServerVersion? {
            val match =
                Regex(
                        "^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)((?:-[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?)$"
                    )
                    .matchEntire(raw.orEmpty()) ?: return null
            val suffix = match.groupValues[4]
            if (
                suffix.startsWith('-') &&
                    suffix.drop(1).substringBefore('+').split('.').any {
                        it.length > 1 && it.startsWith('0') && it.all(Char::isDigit)
                    }
            )
                return null
            return ServerVersion(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
                suffix,
            )
        }
    }
}
