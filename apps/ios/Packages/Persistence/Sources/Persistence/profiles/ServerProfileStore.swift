// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model
import SwiftData

@ModelActor
public actor ServerProfileStore {
    private var observers: [UUID: AsyncThrowingStream<[ServerProfile], Error>.Continuation] = [:]

    public func profiles() throws -> [ServerProfile] {
        try modelContext.fetch(FetchDescriptor<CacheSchemaV1.ProfileRow>(sortBy: [SortDescriptor(\.position)]))
            .map { try JSONDecoder().decode(ProfileRecord.self, from: $0.payload).domain() }
    }

    public func observe() -> AsyncThrowingStream<[ServerProfile], Error> {
        let token = UUID()
        let (stream, continuation) = AsyncThrowingStream<[ServerProfile], Error>.makeStream()
        observers[token] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.removeObserver(token) } }
        do { continuation.yield(try profiles()) } catch { continuation.finish(throwing: AppError.unknown) }
        return stream
    }

    public func save(_ profile: ServerProfile) throws {
        let record = ProfileRecord(profile)
        _ = try record.domain()
        let bytes = try JSONEncoder().encode(record)
        let id = profile.id.rawValue
        let rows = try modelContext.fetch(
            FetchDescriptor<CacheSchemaV1.ProfileRow>(predicate: #Predicate { $0.profileID == id }))
        if let row = rows.first {
            row.payload = bytes
        } else {
            let position = try modelContext.fetchCount(FetchDescriptor<CacheSchemaV1.ProfileRow>())
            modelContext.insert(CacheSchemaV1.ProfileRow(profileID: id, position: position, payload: bytes))
        }
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish()
    }

    public func remove(_ profileID: ProfileID) throws {
        let id = profileID.rawValue
        try modelContext.delete(model: CacheSchemaV1.ProfileRow.self, where: #Predicate { $0.profileID == id })
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish()
    }

    public func reorder(_ ids: [ProfileID]) throws {
        let rows = try modelContext.fetch(FetchDescriptor<CacheSchemaV1.ProfileRow>())
        guard Set(ids.map(\.rawValue)) == Set(rows.map(\.profileID)), ids.count == rows.count else {
            throw AppError.validation
        }
        for (position, id) in ids.enumerated() { rows.first { $0.profileID == id.rawValue }?.position = position }
        do { try modelContext.save() } catch {
            modelContext.rollback()
            throw AppError.unknown
        }
        publish()
    }

    private func publish() {
        do {
            let values = try profiles()
            for observer in observers.values { observer.yield(values) }
        } catch {
            for observer in observers.values { observer.finish(throwing: AppError.unknown) }
            observers.removeAll()
        }
    }

    private func removeObserver(_ id: UUID) { observers[id] = nil }
}
