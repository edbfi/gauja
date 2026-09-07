// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
import DesignSystem
import Model
import SwiftUI
import UI

#if canImport(UIKit)
    import UIKit
#else
    import AppKit
#endif

public enum CachedTitleRenderer {
    public static func render(_ title: TitleSummary, largeText: Bool = false, poster: CGImage? = nil) -> Bool {
        let content = GaujaTheme {
            TitleCard(title: title, poster: poster.map { Image(decorative: $0, scale: 1) }) { _, _ in }.frame(
                width: 220
            )
            .environment(\.dynamicTypeSize, largeText ? .accessibility5 : .large)
        }
        #if canImport(UIKit)
            let host = UIHostingController(rootView: content)
            host.view.frame = CGRect(x: 0, y: 0, width: 240, height: 1600)
            host.view.setNeedsLayout()
            host.view.layoutIfNeeded()
            let image = UIGraphicsImageRenderer(size: host.view.bounds.size).image { _ in
                host.view.drawHierarchy(in: host.view.bounds, afterScreenUpdates: true)
            }
            return image.cgImage?.width == 240
        #else
            let host = NSHostingView(rootView: content)
            host.frame = CGRect(x: 0, y: 0, width: 240, height: 1600)
            host.layoutSubtreeIfNeeded()
            guard let representation = host.bitmapImageRepForCachingDisplay(in: host.bounds) else { return false }
            host.cacheDisplay(in: host.bounds, to: representation)
            return representation.pixelsWide > 0
        #endif
    }
}
