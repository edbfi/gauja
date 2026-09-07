// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation
import Testing

@testable import Common

@Test func everySecretAndHostIsRemovedFromDiagnostics() {
    let values = ["session-value", "operator-value", "basic-value", "plex-value"]
    let secrets = values.map { Secret(Data($0.utf8)) }
    let raw =
        values.joined(separator: "\n")
        + "\nhttps://private.example/seerr\nAuthorization: Basic encoded\nCookie: connect.sid=unlisted"
    let safe = Redaction.text(raw, secrets: secrets, hosts: ["private.example"])
    for value in values + ["private.example", "encoded", "unlisted"] { #expect(!safe.contains(value)) }
    for secret in secrets {
        #expect(String(describing: secret) == "[REDACTED]")
        #expect(String(reflecting: secret) == "[REDACTED]")
        #expect(Mirror(reflecting: secret).children.isEmpty)
    }
}
