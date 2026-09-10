// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
#if canImport(UIKit)
    import Common
    import DesignSystem
    import Model
    import SwiftUI
    import UI

    import UIKit

    public enum CachedTitleRenderer {
        public enum State {
            case loading, empty, denied
            case failed(AppError)
            case offline(Cached<TitleSummary>)
        }

        public static func contentHeight(_ title: TitleSummary, largeText: Bool) -> CGFloat {
            let content = GaujaTheme {
                TitleCard(title: title) { _, _ in }.frame(width: 220)
                    .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large)
            }
            let host = UIHostingController(rootView: content)
            host.traitOverrides.preferredContentSizeCategory = largeText ? .accessibilityExtraExtraExtraLarge : .large
            return host.sizeThatFits(in: CGSize(width: 220, height: CGFloat.greatestFiniteMagnitude)).height
        }

        public static func render(_ state: State, largeText: Bool = false, poster: CGImage? = nil) -> Bool {
            let content = GaujaTheme {
                CachedTitleContent(state: state, poster: poster).frame(width: 220)
                    .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large)
            }
            let host = UIHostingController(rootView: content)
            host.traitOverrides.preferredContentSizeCategory = largeText ? .accessibilityExtraExtraExtraLarge : .large
            let window = UIWindow(frame: CGRect(x: 0, y: 0, width: 240, height: 1600))
            window.rootViewController = host
            window.isHidden = false
            defer {
                window.isHidden = true
                window.rootViewController = nil
            }
            host.view.frame = window.bounds
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            var rendered = false
            let image = UIGraphicsImageRenderer(size: host.view.bounds.size).image { _ in
                rendered = host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            return rendered && image.cgImage?.width == Int(host.view.bounds.width * image.scale)
        }
    }

    private struct CachedTitleContent: View {
        let state: CachedTitleRenderer.State
        let poster: CGImage?
        var body: some View {
            VStack(alignment: .leading) {
                switch state {
                case .loading: ProgressView("Loading cached title")
                case .empty: Text("No cached titles")
                case .denied: ContentMessage(error: .permission)
                case .failed(let error): ContentMessage(error: error, retry: {})
                case .offline(let cached):
                    Text("Offline · cached \(cached.fetchedAt.formatted())")
                    TitleCard(title: cached.value, poster: poster.map { Image(decorative: $0, scale: 1) }) { _, _ in }
                }
            }
        }
    }

#endif
