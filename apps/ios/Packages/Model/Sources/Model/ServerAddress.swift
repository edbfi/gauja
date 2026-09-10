// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct ServerAddress: Sendable, Equatable, Hashable, CustomStringConvertible {
    public let url: URL
    public var description: String { "[SERVER]" }
    public var isPlainHTTP: Bool { url.scheme == "http" }
    public var apiBase: URL { url.appending(path: "api/v1") }

    // Validated URLs always have a scheme and host. Paths do not change credential scope.
    public var origin: String {
        "\(url.scheme ?? "")://\(url.host() ?? ""):\(url.port ?? (isPlainHTTP ? 80 : 443))"
    }

    public init?(_ input: String) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !text.contains(where: { $0.isWhitespace || $0 == "\\" }) else { return nil }
        let value = text.contains("://") ? text : "https://" + text
        guard var parts = URLComponents(string: value),
            let scheme = parts.scheme?.lowercased(), ["https", "http"].contains(scheme),
            let host = parts.host, !host.isEmpty, parts.user == nil, parts.password == nil,
            parts.query == nil, parts.fragment == nil,
            parts.port.map({ (1...65535).contains($0) }) ?? true,
            !parts.percentEncodedPath.lowercased().contains("%2f"),
            !parts.percentEncodedPath.lowercased().contains("%5c"),
            !parts.path.split(separator: "/").contains(where: { $0 == "." || $0 == ".." })
        else { return nil }
        parts.scheme = scheme
        parts.host = host.lowercased()
        while parts.path.hasSuffix("/") { parts.path.removeLast() }
        guard let url = parts.url else { return nil }
        self.url = url
    }
}
