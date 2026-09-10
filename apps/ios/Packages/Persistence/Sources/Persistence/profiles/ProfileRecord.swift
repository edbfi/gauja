// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model

struct ProfileRecord: Codable {
    let id: ProfileID
    let displayName: String
    let address: String
    let tlsMode: TLSMode
    let authMethod: AuthMethod
    let basicAuthUsername: String?
    let operatorAcknowledged: Bool
    let status: ServerStatus?
    let statusDate: Date?
    let settings: PublicSettings?
    let settingsDate: Date?

    init(_ profile: ServerProfile) {
        id = profile.id
        displayName = profile.displayName
        address = profile.address.url.absoluteString
        tlsMode = profile.tlsMode
        authMethod = profile.authMethod
        basicAuthUsername = profile.basicAuthUsername
        operatorAcknowledged = profile.operatorAcknowledged
        status = profile.status?.value
        statusDate = profile.status?.fetchedAt
        settings = profile.publicSettings?.value
        settingsDate = profile.publicSettings?.fetchedAt
    }

    func domain() throws -> ServerProfile {
        guard let address = ServerAddress(address),
            let profile = ServerProfile(
                id: id, displayName: displayName, address: address, tlsMode: tlsMode, authMethod: authMethod,
                basicAuthUsername: basicAuthUsername, operatorAcknowledged: operatorAcknowledged,
                status: status.flatMap { value in statusDate.map { Cached(value, fetchedAt: $0) } },
                publicSettings: settings.flatMap { value in settingsDate.map { Cached(value, fetchedAt: $0) } })
        else { throw AppError.validation }
        return profile
    }
}
