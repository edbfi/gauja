// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation
import Model

public actor PreferencesStore {
    private let defaults: UserDefaults
    private var observers: [UUID: AsyncThrowingStream<Preferences, Error>.Continuation] = [:]

    public init(suiteName: String = "app.gauja.preferences") throws {
        guard let defaults = UserDefaults(suiteName: suiteName) else { throw AppError.unknown }
        self.defaults = defaults
    }

    public func read() throws -> Preferences {
        guard let data = defaults.data(forKey: "preferences") else { return Preferences() }
        return try JSONDecoder().decode(Preferences.self, from: data)
    }

    public func observe() -> AsyncThrowingStream<Preferences, Error> {
        let token = UUID()
        let (stream, continuation) = AsyncThrowingStream<Preferences, Error>.makeStream()
        observers[token] = continuation
        continuation.onTermination = { [weak self] _ in Task { await self?.removeObserver(token) } }
        do { continuation.yield(try read()) } catch { continuation.finish(throwing: AppError.unknown) }
        return stream
    }

    public func save(_ value: Preferences) throws {
        defaults.set(try JSONEncoder().encode(value), forKey: "preferences")
        for observer in observers.values { observer.yield(value) }
    }

    public func discover(_ id: ProfileID) throws -> DiscoverPreferences {
        guard let data = defaults.data(forKey: discoverKey(id)) else { return DiscoverPreferences() }
        return try JSONDecoder().decode(DiscoverPreferences.self, from: data)
    }

    public func saveDiscover(_ value: DiscoverPreferences, profileID: ProfileID) throws {
        defaults.set(try JSONEncoder().encode(value), forKey: discoverKey(profileID))
    }

    public func clear(_ id: ProfileID) throws {
        defaults.removeObject(forKey: discoverKey(id))
        var value = try read()
        if value.activeProfileID == id {
            value.activeProfileID = nil
            try save(value)
        }
    }

    private func discoverKey(_ id: ProfileID) -> String { "\(id.rawValue.uuidString).discover" }
    private func removeObserver(_ id: UUID) { observers[id] = nil }
}
