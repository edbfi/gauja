// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import Network
import OpenAPIRuntime
import Persistence
internal import SeerrAPI

struct APISession: Sendable {
    let profiles: ServerProfileStore
    let transport: ProfileTransport

    func use<Value: Sendable>(
        _ id: ProfileID,
        operation: @Sendable (ServerProfile, Client, AuthenticatedTransport) async throws -> Value
    ) async throws -> Value {
        do {
            guard let profile = try await profiles.profiles().first(where: { $0.id == id }) else {
                throw AppError.notFound
            }
            return try await transport.withProfile(profile) { transport in
                try await operation(
                    profile, Client(serverURL: profile.address.apiBase, transport: transport), transport)
            }
        } catch {
            if Task.isCancelled { throw CancellationError() }
            throw appError(error)
        }
    }
}

func appError(_ error: any Error) -> AppError {
    if let client = error as? ClientError { return appError(client.underlyingError) }
    if let error = error as? AppError { return error }
    if error is DecodingError { return .validation }
    if let url = error as? URLError {
        switch url.code {
        case .notConnectedToInternet, .cannotConnectToHost, .cannotFindHost: return .offline
        case .serverCertificateUntrusted, .serverCertificateHasBadDate, .serverCertificateNotYetValid,
            .serverCertificateHasUnknownRoot, .secureConnectionFailed:
            return .tls
        default: return .network
        }
    }
    return .unknown
}

func responseError(_ code: Int) -> AppError {
    switch code {
    case 401: .auth
    case 403: .permission
    case 404: .notFound
    case 400..<500: .validation
    default: .network
    }
}
