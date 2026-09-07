// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Testing

@testable import Compat

@Test func bundledCapabilitiesFailClosed() throws {
    let gate = try FeatureGate.bundled()
    #expect(gate.isSupported("blocklist", version: ServerVersion("3.4.1")))
    #expect(gate.isSupported("blocklist", version: ServerVersion("4.0.0+build")))
    #expect(!gate.isSupported("blocklist", version: ServerVersion("3.4.0")))
    #expect(!gate.isSupported("blocklist", version: ServerVersion("3.4.1-rc.1")))
    #expect(!gate.isSupported("blocklist", version: nil))
    #expect(!gate.isSupported("missing", version: ServerVersion("3.4.1")))
}

@Test func semverPrereleaseOrderingAndValidation() throws {
    let values = ["3.4.1-alpha", "3.4.1-alpha.1", "3.4.1-alpha.2", "3.4.1-alpha.10", "3.4.1-beta", "3.4.1"]
    let parsed = try values.map { try #require(ServerVersion($0)) }
    #expect(zip(parsed, parsed.dropFirst()).allSatisfy { $0 < $1 })
    for value in ["3.4.1-01", "3.4.1-a..b", "3.4.1+", "3.4.1-a+b+c"] { #expect(ServerVersion(value) == nil) }
}

@Test func maximumIsInclusive() throws {
    let gate = try FeatureGate(
        data: Data(#"{"limited":{"min":"3.4.1","max":"3.5.0","endpoint":"/test","note":"test"}}"#.utf8))
    #expect(gate.isSupported("limited", version: ServerVersion("3.5.0")))
    #expect(!gate.isSupported("limited", version: ServerVersion("3.5.1")))
}
