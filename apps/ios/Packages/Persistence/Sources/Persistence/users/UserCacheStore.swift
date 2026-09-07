// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import SwiftData

@ModelActor
public actor UserCacheStore {
    private var observers: [UUID: (ProfileID, AsyncThrowingStream<Cached<User>?, Error>.Continuation)] = [:]

    public func read(_ profileID: ProfileID) throws -> Cached<User>? {
        let id = profileID.rawValue
        let rows = try modelContext.fetch(
            FetchDescriptor<CacheSchemaV1.UserRow>(predicate: #Predicate { $0.profileID == id }))
        guard let row = rows.first else { return nil }
        return Cached(try JSONDecoder().decode(User.self, from: row.payload), fetchedAt: row.fetchedAt)
    }

    public func observe(_ profileID: ProfileID) -> AsyncThrowingStream<Cached<User>?, Error> {
        let token = UUID()
        let (stream, continuation) = AsyncThrowingStream<Cached<User>?, Error>.makeStream()
        observers[token] = (profileID, continuation)
        continuation.onTermination = { [weak self] _ in Task { await self?.removeObserver(token) } }
        do { continuation.yield(try read(profileID)) } catch { continuation.finish(throwing: AppError.unknown) }
        return stream
    }

    public func save(_ value: Cached<User>, profileID: ProfileID) throws {
        let id = profileID.rawValue
        let payload = try JSONEncoder().encode(value.value)
        let rows = try modelContext.fetch(
            FetchDescriptor<CacheSchemaV1.UserRow>(predicate: #Predicate { $0.profileID == id }))
        if let row = rows.first {
            row.payload = payload
            row.fetchedAt = value.fetchedAt
        } else {
            modelContext.insert(CacheSchemaV1.UserRow(profileID: id, payload: payload, fetchedAt: value.fetchedAt))
        }
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish(profileID)
    }

    public func clear(_ profileID: ProfileID) throws {
        let id = profileID.rawValue
        try modelContext.delete(model: CacheSchemaV1.UserRow.self, where: #Predicate { $0.profileID == id })
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish(profileID)
    }

    private func publish(_ id: ProfileID) {
        do {
            let value = try read(id)
            for (profile, observer) in observers.values where profile == id { observer.yield(value) }
        } catch {
            for (profile, observer) in observers.values where profile == id {
                observer.finish(throwing: AppError.unknown)
            }
        }
    }

    private func removeObserver(_ id: UUID) { observers[id] = nil }
}
