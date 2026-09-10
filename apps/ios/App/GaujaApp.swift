// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import DesignSystem
import SwiftUI
import UI

@main
struct GaujaApp: App {
    @State private var model = RootModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            GaujaTheme(scheme: model.scheme) {
                if let error = model.storageError { ContentMessage(error: error) } else { RootNavigation() }
            }
            .task { await model.run() }
            .onChange(of: phase) { _, phase in
                if phase == .active { Task { await model.foreground() } }
            }
        }
    }
}
