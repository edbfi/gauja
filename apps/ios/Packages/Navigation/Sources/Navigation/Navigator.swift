// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Observation
import SwiftUI

@Observable public final class Navigator {
    public var path = NavigationPath()
    public var selection: ServerRoute? = .check
    public init() {}
    public func navigate(_ route: ServerRoute) { path.append(route) }
    public func back() { if !path.isEmpty { path.removeLast() } }
}
