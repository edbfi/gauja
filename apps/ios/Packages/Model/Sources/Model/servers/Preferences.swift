// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
public enum ThemeMode: String, Sendable, Codable { case dark, light, system }

public struct Preferences: Sendable, Equatable, Codable {
    public var theme: ThemeMode = .dark
    public var activeProfileID: ProfileID?
    public var reduceMotion = false
    public init() {}
}

public struct DiscoverPreferences: Sendable, Equatable, Codable {
    public var region: String?
    public var providerIDs: Set<Int> = []
    public init() {}
}
