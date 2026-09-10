// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public enum LinkTarget: Sendable, Equatable {
    public enum Kind: String, Sendable { case movie, tv, person, collection }
    case media(Kind, TMDBID)
    case requests
    case issue(Int)
    case resetPassword(UUID)
}
public struct DeepLink: Sendable, Equatable {
    public let profileIDs: [ProfileID]
    public let target: LinkTarget
}

public func parseDeepLink(_ raw: String, profiles: [ServerProfile]) -> DeepLink? {
    guard let url = URLComponents(string: raw), url.user == nil, url.password == nil, url.query == nil,
        url.fragment == nil,
        !url.percentEncodedPath.contains("%"), !url.path.contains("\\")
    else { return nil }
    let candidates: [ProfileID]
    let route: String
    if url.scheme == "gauja", url.host == "server", url.port == nil {
        let parts = url.path.dropFirst().split(separator: "/", maxSplits: 1, omittingEmptySubsequences: false)
        guard parts.count == 2, let uuid = UUID(uuidString: String(parts[0])) else { return nil }
        candidates = profiles.filter { $0.id.rawValue == uuid }.map(\.id)
        route = String(parts[1])
    } else {
        let matches = profiles.filter {
            url.scheme == $0.address.url.scheme && url.host == $0.address.url.host && url.port == $0.address.url.port
                && url.path.hasPrefix($0.address.url.path + "/")
        }
        guard let prefix = matches.map({ $0.address.url.path.count }).max() else { return nil }
        candidates = matches.filter { $0.address.url.path.count == prefix }.map(\.id)
        route = String(url.path.dropFirst(prefix + 1))
    }
    guard !candidates.isEmpty else { return nil }
    let parts = route.components(separatedBy: "/")
    let target: LinkTarget
    if parts == ["requests"] {
        target = .requests
    } else {
        guard parts.count == 2 else { return nil }
        switch parts[0] {
        case "resetpassword", "reset-password":
            guard let uuid = UUID(uuidString: parts[1]) else { return nil }
            target = .resetPassword(uuid)
        case "issues":
            guard let id = Int(parts[1]), id > 0 else { return nil }
            target = .issue(id)
        default:
            guard let kind = LinkTarget.Kind(rawValue: parts[0]), let rawID = Int(parts[1]),
                let id = TMDBID(rawValue: rawID)
            else { return nil }
            target = .media(kind, id)
        }
    }
    return DeepLink(profileIDs: candidates, target: target)
}
