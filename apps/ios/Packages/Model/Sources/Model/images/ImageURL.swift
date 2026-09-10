// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public enum PosterSize: String, Sendable {
    case small = "w185"
    case medium = "w342"
    case large = "w500"
}

public func imageURL(source: String?, address: ServerAddress, cacheImages: Bool, size: PosterSize) -> URL? {
    guard let source, !source.isEmpty,
        let url = URLComponents(
            string: source.hasPrefix("/") ? "https://image.tmdb.org/t/p/\(size.rawValue)\(source)" : source),
        url.scheme == "https", url.user == nil, url.password == nil, url.port == nil,
        url.query == nil, url.fragment == nil
    else { return nil }
    let path = url.percentEncodedPath
    guard !path.contains("%"), !path.contains("\\"),
        !path.split(separator: "/").contains(where: { $0 == "." || $0 == ".." })
    else { return nil }
    switch url.host {
    case "image.tmdb.org":
        let parts = path.components(separatedBy: "/")
        guard parts.count >= 5, parts[1] == "t", parts[2] == "p", !parts.dropFirst(4).contains("") else { return nil }
        let sized = "/t/p/\(size.rawValue)/" + parts.dropFirst(4).joined(separator: "/")
        return URL(
            string: cacheImages
                ? "\(address.url.absoluteString)/imageproxy/tmdb\(sized)" : "https://image.tmdb.org\(sized)")
    case "artworks.thetvdb.com":
        return cacheImages ? URL(string: "\(address.url.absoluteString)/imageproxy/tvdb\(path)") : url.url
    default: return nil
    }
}
