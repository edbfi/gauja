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

        public static func render(_ title: TitleSummary, largeText: Bool = false, poster: CGImage? = nil) -> Bool {
            render(
                .offline(Cached(title, fetchedAt: Date(timeIntervalSince1970: 0))), largeText: largeText, poster: poster
            )
        }

        public static func render(_ state: State, largeText: Bool = false, poster: CGImage? = nil) -> Bool {
            let content = GaujaTheme {
                CachedTitleContent(state: state, poster: poster).frame(width: 220)
                    .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large)
            }
            let host = UIHostingController(rootView: content)
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
            let image = UIGraphicsImageRenderer(size: host.view.bounds.size).image { _ in
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            return image.cgImage?.width == Int(host.view.bounds.width * image.scale)
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
