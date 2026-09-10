// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Navigation
import Testing

@Test func appDoesNotRegisterBackgroundWorkOrThirdPartyQueries() {
    let info = Bundle.main.infoDictionary ?? [:]
    #expect(info["UIBackgroundModes"] == nil)
    #expect(info["LSApplicationQueriesSchemes"] == nil)
    #expect(info["NSUserTrackingUsageDescription"] == nil)
    #expect(info["MinimumOSVersion"] as? String == "18.0")
}

@Test func rootNavigatorKeepsItsBoundPathAndDoesNotPopPastRoot() {
    let navigator = Navigator()
    navigator.back()
    #expect(navigator.path.isEmpty)
    navigator.navigate(.check)
    #expect(navigator.path.count == 1)
    navigator.back()
    #expect(navigator.path.isEmpty)
    #expect(navigator.selection == .check)
}
