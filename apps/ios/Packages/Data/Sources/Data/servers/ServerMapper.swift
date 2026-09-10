// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Compat
import Model
internal import SeerrAPI

func mapServer(
    _ address: ServerAddress, _ status: Operations.getStatus.Output.Ok.Body.jsonPayload,
    _ settings: Components.Schemas.PublicSettings
) -> ServerSnapshot {
    let mediaServerType = MediaServerType(rawValue: settings.mediaServerType.flatMap { Int(exactly: $0) })
    return ServerSnapshot(
        address: address, version: status.version, title: settings.applicationTitle,
        initialized: settings.initialized, restartRequired: status.restartRequired,
        localLogin: settings.localLogin, mediaServerLogin: settings.mediaServerLogin,
        mediaServerType: mediaServerType,
        compatibility: ServerVersion(status.version)?.compatibility ?? .unknown)
}
