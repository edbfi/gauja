// SPDX-FileCopyrightText: 2026 Gauja contributors
// SPDX-License-Identifier: AGPL-3.0-or-later
// GENERATED — do not edit. Run tools/tokens/generate.sh.

import SwiftUI
import UIKit

public struct GaujaTypographyStyle {
    public let font: Font
    public let lineHeight: CGFloat
    public let letterSpacing: CGFloat

    private init(size: CGFloat, weight: UIFont.Weight, textStyle: UIFont.TextStyle, lineHeight: CGFloat, letterSpacing: CGFloat, category: DynamicTypeSize) {
        let metrics = UIFontMetrics(forTextStyle: textStyle)
        let traits = Self.traits(for: category)
        font = Font(metrics.scaledFont(for: UIFont.systemFont(ofSize: size, weight: weight), compatibleWith: traits))
        self.lineHeight = metrics.scaledValue(for: size * lineHeight, compatibleWith: traits)
        self.letterSpacing = metrics.scaledValue(for: letterSpacing, compatibleWith: traits)
    }

    public static func bodyLarge(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 16, weight: .regular, textStyle: .body,
            lineHeight: 1.5, letterSpacing: 0, category: category)
    }

    public static func bodyMedium(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 14, weight: .regular, textStyle: .body,
            lineHeight: 1.5, letterSpacing: 0, category: category)
    }

    public static func bodySmall(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 12, weight: .regular, textStyle: .body,
            lineHeight: 1.5, letterSpacing: 0, category: category)
    }

    public static func displayLarge(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 60, weight: .regular, textStyle: .largeTitle,
            lineHeight: 1.1, letterSpacing: 0, category: category)
    }

    public static func displayMedium(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 48, weight: .regular, textStyle: .largeTitle,
            lineHeight: 1.1, letterSpacing: 0, category: category)
    }

    public static func displaySmall(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 36, weight: .regular, textStyle: .largeTitle,
            lineHeight: 1.2, letterSpacing: 0, category: category)
    }

    public static func headlineLarge(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 32, weight: .regular, textStyle: .title1,
            lineHeight: 1.25, letterSpacing: 0, category: category)
    }

    public static func headlineMedium(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 28, weight: .regular, textStyle: .title1,
            lineHeight: 1.3, letterSpacing: 0, category: category)
    }

    public static func headlineSmall(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 24, weight: .regular, textStyle: .title1,
            lineHeight: 1.3, letterSpacing: 0, category: category)
    }

    public static func labelLarge(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 14, weight: .medium, textStyle: .caption1,
            lineHeight: 1.4, letterSpacing: 0, category: category)
    }

    public static func labelMedium(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 12, weight: .semibold, textStyle: .caption1,
            lineHeight: 1.4, letterSpacing: 0, category: category)
    }

    public static func labelSmall(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 11, weight: .semibold, textStyle: .caption1,
            lineHeight: 1.4, letterSpacing: 0, category: category)
    }

    public static func titleLarge(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 22, weight: .medium, textStyle: .headline,
            lineHeight: 1.3, letterSpacing: 0, category: category)
    }

    public static func titleMedium(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 16, weight: .medium, textStyle: .headline,
            lineHeight: 1.5, letterSpacing: 0, category: category)
    }

    public static func titleSmall(_ category: DynamicTypeSize) -> GaujaTypographyStyle {
        GaujaTypographyStyle(size: 14, weight: .medium, textStyle: .headline,
            lineHeight: 1.4, letterSpacing: 0, category: category)
    }

    private static func traits(for size: DynamicTypeSize) -> UITraitCollection {
        let category: UIContentSizeCategory
        switch size {
        case .xSmall: category = .extraSmall
        case .small: category = .small
        case .medium: category = .medium
        case .large: category = .large
        case .xLarge: category = .extraLarge
        case .xxLarge: category = .extraExtraLarge
        case .xxxLarge: category = .extraExtraExtraLarge
        case .accessibility1: category = .accessibilityMedium
        case .accessibility2: category = .accessibilityLarge
        case .accessibility3: category = .accessibilityExtraLarge
        case .accessibility4: category = .accessibilityExtraExtraLarge
        case .accessibility5: category = .accessibilityExtraExtraExtraLarge
        @unknown default: category = .large
        }
        return UITraitCollection(preferredContentSizeCategory: category)
    }
}
