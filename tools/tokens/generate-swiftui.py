#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generate SwiftUI theme primitives with native Dynamic Type fonts."""
import argparse
from pathlib import Path
from document import load

HEADER = "// SPDX-FileCopyrightText: 2026 Gauja contributors\n// SPDX-License-Identifier: AGPL-3.0-or-later\n// GENERATED — do not edit. Run tools/tokens/generate.sh.\n"


def generate(tokens, output):
    values = load(tokens)
    output.mkdir(parents=True, exist_ok=True)
    lines = [HEADER, "import SwiftUI", "", "public extension Color {"]
    for key in sorted(k for k in values if k.startswith("color.dark.")):
        name = key.split(".")[-1]
        lines += [f"    static func gauja{name[0].upper() + name[1:]}(_ scheme: ColorScheme = .dark) -> Color {{", "        switch scheme {"]
        for theme in ("light", "dark"):
            v = values[f"color.{theme}.{name}"]
            rgb = ", ".join(f"{n}: {x:.9f}" for n, x in zip(["red", "green", "blue"], v["components"]))
            lines.append(f"        case .{theme}: Color(.sRGB, {rgb}, opacity: {v.get('alpha', 1):.9f})")
        lines += [f"        @unknown default: gauja{name[0].upper() + name[1:]}(.dark)", "        }", "    }", ""]
    (output / "Color+Gauja.swift").write_text("\n".join(lines) + "}\n", encoding="utf-8")
    for group in ("spacing", "radii", "elevation"):
        name = "Gauja" + group.title()
        members = [f"    public static let {k.split('.')[-1]}: CGFloat = {v['value']:g}" for k, v in values.items() if k.startswith(group + ".")]
        (output / f"{name}.swift").write_text(HEADER + "import CoreGraphics\n\n" + f"public enum {name} {{\n" + "\n".join(members) + "\n}\n", encoding="utf-8")
    weights = {400: "regular", 500: "medium", 600: "semibold", 700: "bold"}
    lines = [HEADER, "import SwiftUI", "", "public extension Font {"]
    styles = [HEADER, "import SwiftUI", "import UIKit", "", "public struct GaujaTypographyStyle {",
              "    public let font: Font", "    public let lineHeight: CGFloat", "    public let letterSpacing: CGFloat", "",
              "    private init(size: CGFloat, weight: UIFont.Weight, textStyle: UIFont.TextStyle, lineHeight: CGFloat, letterSpacing: CGFloat, category: DynamicTypeSize) {",
              "        let metrics = UIFontMetrics(forTextStyle: textStyle)",
              "        let traits = Self.traits(for: category)",
              "        font = Font(metrics.scaledFont(for: UIFont.systemFont(ofSize: size, weight: weight), compatibleWith: traits))",
              "        self.lineHeight = metrics.scaledValue(for: size * lineHeight, compatibleWith: traits)",
              "        self.letterSpacing = metrics.scaledValue(for: letterSpacing, compatibleWith: traits)", "    }", ""]
    for key, v in values.items():
        if key.startswith("typography."):
            name = key.split(".")[-1]
            style = "largeTitle" if name.startswith("display") else "title1" if name.startswith("headline") else "headline" if name.startswith("title") else "caption1" if name.startswith("label") else "body"
            lines += [f"    static func gauja{name[0].upper() + name[1:]}(_ category: DynamicTypeSize) -> Font {{",
                      f"        GaujaTypographyStyle.{name}(category).font", "    }", ""]
            styles += [f"    public static func {name}(_ category: DynamicTypeSize) -> GaujaTypographyStyle {{",
                       f"        GaujaTypographyStyle(size: {v['fontSize']['value']:g}, weight: .{weights[v['fontWeight']]}, textStyle: .{style},",
                       f"            lineHeight: {v['lineHeight']:g}, letterSpacing: {v['letterSpacing']['value']:g}, category: category)", "    }", ""]
    (output / "Font+Gauja.swift").write_text("\n".join(lines) + "}\n", encoding="utf-8")
    styles += ["    private static func traits(for size: DynamicTypeSize) -> UITraitCollection {",
               "        let category: UIContentSizeCategory", "        switch size {"]
    for swiftui, uikit in [("xSmall", "extraSmall"), ("small", "small"), ("medium", "medium"),
                           ("large", "large"), ("xLarge", "extraLarge"), ("xxLarge", "extraExtraLarge"),
                           ("xxxLarge", "extraExtraExtraLarge"), ("accessibility1", "accessibilityMedium"),
                           ("accessibility2", "accessibilityLarge"), ("accessibility3", "accessibilityExtraLarge"),
                           ("accessibility4", "accessibilityExtraExtraLarge"),
                           ("accessibility5", "accessibilityExtraExtraExtraLarge")]:
        styles.append(f"        case .{swiftui}: category = .{uikit}")
    styles += ["        @unknown default: category = .large", "        }",
               "        return UITraitCollection(preferredContentSizeCategory: category)", "    }", ""]
    (output / "GaujaTypographyStyle.swift").write_text("\n".join(styles) + "}\n", encoding="utf-8")
    members = [f"    public static func {k.split('.')[-1]}(reduceMotion: Bool = false) -> Double {{ reduceMotion ? 0 : {v['value'] / 1000:g} }}" for k, v in values.items() if k.startswith("motion.")]
    (output / "GaujaMotion.swift").write_text(HEADER + "public enum GaujaMotion {\n" + "\n".join(members) + "\n}\n", encoding="utf-8")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tokens", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    generate(args.tokens, args.output)
