// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Model
internal import SeerrAPI

func mapUser(_ dto: Components.Schemas.User) throws -> User {
    guard let id = UserID(rawValue: dto.id), let bits = UInt32(exactly: dto.permissions ?? 0) else {
        throw AppError.validation
    }
    return User(id: id, displayName: dto.username ?? dto.plexUsername ?? dto.email, email: dto.email, permissions: bits)
}
