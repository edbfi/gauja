// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.model.users

@JvmInline
value class UserId(val value: Int) {
    init {
        require(value > 0)
    }
}

data class User(val id: UserId, val displayName: String, val email: String, val permissions: Long) {
    init {
        require(permissions in 0..0xffffffffL)
    }

    override fun toString(): String = "User($id)"
}
