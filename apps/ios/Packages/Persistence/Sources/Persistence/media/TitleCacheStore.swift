// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import SwiftData

@ModelActor
public actor TitleCacheStore {
    private struct Observer {
        let profile: ProfileID
        let mediaType: String
        let tmdbID: Int
        let continuation: AsyncThrowingStream<Cached<TitleSummary>?, Error>.Continuation
    }
    private var observers: [UUID: Observer] = [:]

    public func read(profileID: ProfileID, mediaType: String, tmdbID: Int) throws -> Cached<TitleSummary>? {
        let id = profileID.rawValue
        let rows = try modelContext.fetch(
            FetchDescriptor<CacheSchemaV1.TitleRow>(
                predicate: #Predicate {
                    $0.profileID == id && $0.mediaType == mediaType && $0.tmdbID == tmdbID
                }))
        guard let row = rows.first else { return nil }
        return Cached(try JSONDecoder().decode(TitleSummary.self, from: row.payload), fetchedAt: row.fetchedAt)
    }

    public func observe(profileID: ProfileID, mediaType: String, tmdbID: Int) -> AsyncThrowingStream<
        Cached<TitleSummary>?, Error
    > {
        let token = UUID()
        let (stream, continuation) = AsyncThrowingStream<Cached<TitleSummary>?, Error>.makeStream()
        observers[token] = Observer(
            profile: profileID, mediaType: mediaType, tmdbID: tmdbID, continuation: continuation)
        continuation.onTermination = { [weak self] _ in Task { await self?.removeObserver(token) } }
        do { continuation.yield(try read(profileID: profileID, mediaType: mediaType, tmdbID: tmdbID)) } catch {
            continuation.finish(throwing: AppError.unknown)
        }
        return stream
    }

    public func save(_ value: Cached<TitleSummary>, profileID: ProfileID) throws {
        guard let mediaType = value.value.mediaType.rawValue else { throw AppError.validation }
        let id = profileID.rawValue
        let tmdbID = value.value.id.rawValue
        let payload = try JSONEncoder().encode(value.value)
        let rows = try modelContext.fetch(
            FetchDescriptor<CacheSchemaV1.TitleRow>(
                predicate: #Predicate {
                    $0.profileID == id && $0.mediaType == mediaType && $0.tmdbID == tmdbID
                }))
        if let row = rows.first {
            row.payload = payload
            row.fetchedAt = value.fetchedAt
        } else {
            modelContext.insert(
                CacheSchemaV1.TitleRow(
                    profileID: id, mediaType: mediaType, tmdbID: tmdbID, payload: payload, fetchedAt: value.fetchedAt))
        }
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish(profileID)
    }

    public func clear(_ profileID: ProfileID) throws {
        let id = profileID.rawValue
        try modelContext.delete(model: CacheSchemaV1.TitleRow.self, where: #Predicate { $0.profileID == id })
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish(profileID)
    }

    private func publish(_ id: ProfileID) {
        for observer in observers.values where observer.profile == id {
            do {
                observer.continuation.yield(
                    try read(profileID: observer.profile, mediaType: observer.mediaType, tmdbID: observer.tmdbID))
            } catch {
                observer.continuation.finish(throwing: AppError.unknown)
            }
        }
    }

    private func removeObserver(_ id: UUID) { observers[id] = nil }
}
