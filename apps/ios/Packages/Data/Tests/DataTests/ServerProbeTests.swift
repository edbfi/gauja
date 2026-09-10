// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import SeerrAPI
import Testing

@testable import Data

@Test func unknownWireValuesRemainRepresentable() throws {
    let address = try #require(ServerAddress("https://example.invalid/seerr"))
    let status = try JSONDecoder().decode(
        Operations.getStatus.Output.Ok.Body.jsonPayload.self,
        from: Foundation.Data(
            #"{"version":"3.4.1","future":123}"#.utf8))
    let settings = try JSONDecoder().decode(
        Components.Schemas.PublicSettings.self,
        from: Foundation.Data(
            #"""
            {"initialized":false,"plexClientIdentifier":"synthetic",
             "applicationTitle":"Library","mediaServerType":999}
            """#
            .utf8))
    let result = mapServer(address, status, settings)
    #expect(result.title == "Library")
    #expect(result.mediaServerType == .unrecognized(999))
    #expect(result.initialized == false)
    #expect(result.compatibility == .tested)
}

@Test(.enabled(if: ProcessInfo.processInfo.environment["GAUJA_CONTRACT_SERVER"] != nil))
func pinnedContainerContract() async throws {
    let base = try #require(ProcessInfo.processInfo.environment["GAUJA_CONTRACT_SERVER"])
    let address = try #require(ServerAddress(base))
    let result = try await LiveServerProbe().check(address)
    #expect(result.version == "3.4.1")
    #expect(result.title == "Seerr")
    #expect(result.initialized == false)
    #expect(result.compatibility == .tested)
}
