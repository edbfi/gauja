// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Testing

@testable import Model

private func rows(_ name: String) throws -> [[String]] {
    var root = URL(fileURLWithPath: #filePath).deletingLastPathComponent()
    while !FileManager.default.fileExists(atPath: root.appendingPathComponent("api/test-vectors").path) {
        let parent = root.deletingLastPathComponent()
        guard parent != root else { throw CocoaError(.fileNoSuchFile) }
        root = parent
    }
    return try String(contentsOf: root.appendingPathComponent("api/test-vectors/\(name).txt"), encoding: .utf8)
        .split(separator: "\n").filter { !$0.hasPrefix("#") }
        .map { $0.split(separator: "|", omittingEmptySubsequences: false).map(String.init) }
}

@Test func upstreamPermissionFlags() throws {
    let expected = try rows("permission-flags").map { try #require(UInt32($0[1])) }
    #expect(expected == Permission.allCases.map(\.rawValue))
    #expect(Permission.allCases.reduce(0) { $0 | $1.rawValue } & ((1 << 29) | 1) == 0)
}

@Test func sharedPermissionCases() throws {
    for row in try rows("permissions") {
        let user = try #require(UInt32(row[2]))
        let actual: Bool
        if row[0] == "scalar" {
            actual = hasPermission(try #require(UInt32(row[1])), user: user)
        } else {
            let required = try row[1].split(separator: ",").map { try #require(UInt32($0)) }
            actual = hasPermission(required, user: user, mode: row[3] == "and" ? .and : .or)
        }
        #expect(actual == (row[4] == "true"), Comment(rawValue: row.joined(separator: "|")))
    }
}
