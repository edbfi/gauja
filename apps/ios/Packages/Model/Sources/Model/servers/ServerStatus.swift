// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
public struct ServerStatus: Sendable, Equatable, Codable {
    public let version: String?
    public let commitTag: String?
    public let updateAvailable: Bool?
    public let restartRequired: Bool?
    public init(version: String?, commitTag: String?, updateAvailable: Bool?, restartRequired: Bool?) {
        self.version = version
        self.commitTag = commitTag
        self.updateAvailable = updateAvailable
        self.restartRequired = restartRequired
    }
}

public struct PublicSettings: Sendable, Equatable, Codable {
    public let title: String?
    public let initialized: Bool?
    public let localLogin: Bool?
    public let mediaServerLogin: Bool?
    public let mediaServerType: MediaServerType
    public let cacheImages: Bool?
    public let jellyfinQuickConnect: Bool?
    public let newPlexLogin: Bool?
    public init(
        title: String?, initialized: Bool?, localLogin: Bool?, mediaServerLogin: Bool?,
        mediaServerType: MediaServerType, cacheImages: Bool?, jellyfinQuickConnect: Bool? = nil,
        newPlexLogin: Bool? = nil
    ) {
        self.title = title
        self.initialized = initialized
        self.localLogin = localLogin
        self.mediaServerLogin = mediaServerLogin
        self.mediaServerType = mediaServerType
        self.cacheImages = cacheImages
        self.jellyfinQuickConnect = jellyfinQuickConnect
        self.newPlexLogin = newPlexLogin
    }
}
