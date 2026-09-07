// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Foundation

public struct FakeClock: Sendable {
    public nonisolated let clock: WallClock
    public nonisolated init(now: Date = Date(timeIntervalSince1970: 1_767_225_600)) { clock = WallClock(now: { now }) }
}
