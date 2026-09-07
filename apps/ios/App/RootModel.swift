// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import Data
import Dependencies
import Model
import Observation
import SwiftUI

@Observable final class RootModel {
    var preferences = Preferences()
    var storageError: AppError?
    var foregroundError: AppError?
    @ObservationIgnored private var platform: CorePlatform?
    @ObservationIgnored private var bootstrap: Task<CorePlatform, Error>?
    @ObservationIgnored @Dependency(\.wallClock) private var clock

    var scheme: ColorScheme? {
        switch preferences.theme {
        case .dark: .dark
        case .light: .light
        case .system: nil
        }
    }

    func run() async {
        do {
            if bootstrap == nil {
                let clock = clock
                bootstrap = Task { try await CorePlatform.live(clock: clock) }
            }
            guard let bootstrap else { return }
            let platform = try await bootstrap.value
            self.platform = platform
            // Cached preferences must render while a slow server status refresh is in flight.
            async let refresh: Void = foreground()
            for try await preferences in await platform.preferences.observe() {
                self.preferences = preferences
            }
            await refresh
        } catch is CancellationError { return } catch {
            bootstrap = nil
            storageError = .unknown
        }
    }

    func foreground() async {
        do {
            try await platform?.foreground()
            foregroundError = nil
        } catch is CancellationError { return } catch { foregroundError = (error as? AppError) ?? .unknown }
    }
}
