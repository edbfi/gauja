// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import Common
import DesignSystem
import SwiftUI

public struct ContentMessage: View {
    private let error: AppError
    private let retry: (() -> Void)?
    public init(error: AppError, retry: (() -> Void)? = nil) {
        self.error = error
        self.retry = retry
    }
    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(message)
            if let retry { Button("Retry", action: retry).buttonStyle(.borderedProminent) }
        }.padding().accessibilityElement(children: .contain)
    }
    private var message: LocalizedStringKey {
        switch error {
        case .network: "The server could not be reached. Try again."
        case .tls: "The secure connection could not be verified. Review this server’s trust settings."
        case .auth: "Sign in to this server again."
        case .permission: "You do not have permission to view this content."
        case .notFound: "This content was not found."
        case .validation: "The server response or input was invalid."
        case .serverVersion: "This feature is not supported by this server version."
        case .offline: "You are offline. Cached content remains available."
        case .unknown: "Saved data could not be read. Try reopening Gauja."
        }
    }
}
#Preview("Offline") { GaujaTheme { ContentMessage(error: .offline) } }
#Preview("Largest text", traits: .fixedLayout(width: 500, height: 600)) {
    GaujaTheme { ContentMessage(error: .network, retry: {}).environment(\.dynamicTypeSize, .accessibility5) }
}
