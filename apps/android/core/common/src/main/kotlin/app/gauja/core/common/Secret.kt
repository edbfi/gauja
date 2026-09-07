// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.common

// Only explicit transport/storage boundaries may borrow the bytes. Never a saved-state value.
class Secret(bytes: ByteArray) {
    private val value = bytes.copyOf()

    fun <T> useBytes(block: (ByteArray) -> T): T {
        val copy = value.copyOf()
        return try {
            block(copy)
        } finally {
            copy.fill(0)
        }
    }

    override fun toString(): String = "[REDACTED]"
}
