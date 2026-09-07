import Dependencies
// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Foundation

public struct WallClock: Sendable {
    public var now: @Sendable () -> Date
    public init(now: @escaping @Sendable () -> Date) { self.now = now }
    public static var system: Self { Self(now: { Date() }) }
}

private enum WallClockKey: DependencyKey {
    static let liveValue = WallClock.system
    static let testValue = WallClock(now: { Date(timeIntervalSince1970: 0) })
}

extension DependencyValues {
    public var wallClock: WallClock {
        get { self[WallClockKey.self] }
        set { self[WallClockKey.self] = newValue }
    }
}
