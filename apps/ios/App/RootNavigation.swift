// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Navigation
import Servers
import SwiftUI

struct RootNavigation: View {
    @State private var navigator = Navigator()

    var body: some View {
        // Native collapse keeps the detail's model alive when the window changes size.
        NavigationSplitView(preferredCompactColumn: .constant(.detail)) {
            List(selection: $navigator.selection) {
                NavigationLink("Add server", value: ServerRoute.check)
            }.navigationTitle("Servers")
        } detail: {
            NavigationStack(path: $navigator.path) {
                ServersView().navigationDestination(for: ServerRoute.self) { route in
                    switch route {
                    case .check: ServersView()
                    }
                }
            }
        }
    }
}
