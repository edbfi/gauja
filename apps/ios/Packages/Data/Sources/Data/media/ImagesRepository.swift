// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import CoreGraphics
import Model
import Network

public struct ImagesRepository: Sendable {
    let sessions: APISession
    let images: ProfileImages
    public func load(_ id: ProfileID, source: String, size: PosterSize, offline: Bool) async throws -> CGImage {
        guard let profile = try await sessions.profiles.profiles().first(where: { $0.id == id }) else {
            throw AppError.notFound
        }
        return try await sessions.transport.withCache(id) {
            try await images.load(profile, source: source, size: size, offline: offline)
        }
    }
}
