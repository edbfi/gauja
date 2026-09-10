// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
package app.gauja.core.data.auth

import app.gauja.core.api.models.User
import app.gauja.core.common.AppError
import app.gauja.core.common.AppException
import app.gauja.core.model.servers.Cached
import app.gauja.core.model.servers.ProfileId
import java.time.Instant
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserMapperTest {
    private val dto = User(1, "reader@example.invalid", "2026-01-01", "2026-01-01")

    @Test
    fun malformedIdentifiersAndPermissionMasksFailAtTheBoundary() {
        for (value in listOf(-1.0, 0.5, Double.NaN, Double.POSITIVE_INFINITY, 4294967296.0)) {
            val error =
                assertThrows(AppException::class.java) { dto.copy(permissions = value).domain() }
            assertEquals(AppError.VALIDATION, error.reason)
        }
        assertThrows(AppException::class.java) { dto.copy(id = 0).domain() }
    }

    @Test
    fun absentPermissionsDenyByDefaultAndCachePreservesTimestampAndUnknownBits() {
        assertEquals(0L, dto.domain().permissions)
        assertEquals(dto.email, dto.domain().displayName)
        val user = dto.copy(permissions = 536870912.0, plexUsername = "Reader").domain()
        val cached = Cached(user, Instant.parse("2026-01-01T12:34:56Z"))
        assertEquals("Reader", user.displayName)
        assertEquals(cached, cached.entity(ProfileId(UUID.randomUUID())).domain())
    }
}
