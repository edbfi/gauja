// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public enum Redaction {
    public static func text(_ value: String, secrets: [Secret] = [], hosts: [String] = []) -> String {
        var safe = value
        for secret in secrets {
            safe = secret.withBytes { bytes in
                guard let token = String(data: bytes, encoding: .utf8), !token.isEmpty else { return safe }
                return safe.replacingOccurrences(of: token, with: "[REDACTED]")
            }
        }
        for host in hosts.filter({ !$0.isEmpty }).sorted(by: { $0.count > $1.count }) {
            safe = safe.replacingOccurrences(of: host, with: "[HOST]", options: .caseInsensitive)
        }
        safe = safe.replacingOccurrences(of: #"(?i)https?://[^\s<>]+"#, with: "[URL]", options: .regularExpression)
        return safe.replacingOccurrences(
            of: #"(?im)(cookie|set-cookie|authorization|x-api-key)\s*[:=][^\r\n]*"#,
            with: "$1: [REDACTED]", options: .regularExpression)
    }
}
