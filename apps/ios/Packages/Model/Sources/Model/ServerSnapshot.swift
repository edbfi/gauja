// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
public struct ServerSnapshot: Sendable, Equatable {
    public let address: ServerAddress
    public let version: String?
    public let title: String?
    public let initialized: Bool?
    public let restartRequired: Bool?
    public let localLogin: Bool?
    public let mediaServerLogin: Bool?
    public let mediaServerType: MediaServerType
    public let compatibility: Compatibility

    public init(
        address: ServerAddress, version: String?, title: String?, initialized: Bool?,
        restartRequired: Bool?, localLogin: Bool?, mediaServerLogin: Bool?,
        mediaServerType: MediaServerType, compatibility: Compatibility
    ) {
        self.address = address
        self.version = version
        self.title = title
        self.initialized = initialized
        self.restartRequired = restartRequired
        self.localLogin = localLogin
        self.mediaServerLogin = mediaServerLogin
        self.mediaServerType = mediaServerType
        self.compatibility = compatibility
    }
}

public enum Compatibility: Sendable { case tested, tooOld, untested, unknown }
