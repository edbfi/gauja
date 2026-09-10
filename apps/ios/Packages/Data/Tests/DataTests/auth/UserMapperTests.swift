// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import SeerrAPI
import Testing

@testable import Data

struct UserMapperTests {
    private let dto = Components.Schemas.User(
        id: 1, email: "reader@example.invalid", createdAt: "2026-01-01", updatedAt: "2026-01-01")

    @Test(arguments: [-1.0, 0.5, Double.nan, Double.infinity, 4294967296.0])
    func malformedPermissionMasksFailAtTheBoundary(_ value: Double) {
        var input = dto
        input.permissions = value
        #expect(throws: AppError.validation) { try mapUser(input) }
    }

    @Test func absentPermissionsDenyByDefaultAndUnknownBitsSurvive() throws {
        #expect(try mapUser(dto).permissions == 0)
        #expect(try mapUser(dto).displayName == dto.email)
        var input = dto
        input.plexUsername = "Reader"
        input.permissions = 536_870_912
        #expect(try mapUser(input).displayName == "Reader")
        #expect(try mapUser(input).permissions == 536_870_912)
        input.id = 0
        #expect(throws: AppError.validation) { try mapUser(input) }
    }
}
