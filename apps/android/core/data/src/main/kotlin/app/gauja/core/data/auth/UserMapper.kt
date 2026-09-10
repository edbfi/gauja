// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.database.users.UserEntity
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import app.gauja.core.model.users.User
import app.gauja.core.model.users.UserId
import java.time.Instant

internal fun app.gauja.core.api.models.User.domain(): User {
    val bits = permissions ?: 0.0
    if (id <= 0 || !bits.isFinite() || bits % 1.0 != 0.0 || bits < 0 || bits > 0xffffffffL)
        throw AppException(AppError.VALIDATION)
    return User(UserId(id), username ?: plexUsername ?: email, email, bits.toLong())
}

internal fun Cached<User>.entity(profileId: ProfileId) =
    UserEntity(
        profileId.value.toString(),
        value.id.value,
        value.displayName,
        value.email,
        value.permissions,
        fetchedAt.toEpochMilli(),
    )

internal fun UserEntity.domain() =
    Cached(User(UserId(userId), displayName, email, permissions), Instant.ofEpochMilli(fetchedAt))
