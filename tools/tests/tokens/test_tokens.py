# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools/tokens"))
from document import load


class TokenTests(unittest.TestCase):
    def test_alias_cycles_missing_values_and_bad_colors_fail(self):
        for value in ["{missing}", "{palette.gray900}", {"colorSpace": "srgb", "components": [2, 0, 0]}]:
            data = json.loads((ROOT / "design/tokens.json").read_text())
            data["palette"]["gray900"]["$value"] = value
            with tempfile.TemporaryDirectory() as temp:
                path = Path(temp) / "tokens.json"
                path.write_text(json.dumps(data))
                with self.assertRaises(ValueError):
                    load(path)

    def test_group_types_are_inherited_and_overridden(self):
        provenance = {"$description": "test", "$extensions": {"app.gauja.provenance": "test"}}
        data = {"$type": "dimension", "group": {
            "size": {**provenance, "$value": {"value": 8, "unit": "px"}},
            "alias": {**provenance, "$value": "{group.size}"},
            "motion": {"$type": "duration", "fast": {**provenance, "$value": {"value": 100, "unit": "ms"}}},
            "override": {**provenance, "$type": "duration", "$value": {"value": 200, "unit": "ms"}},
        }}
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "tokens.json"
            path.write_text(json.dumps(data), encoding="utf-8")
            values = load(path)
            self.assertEqual(values["group.alias"], {"value": 8, "unit": "px"})
            self.assertEqual(values["group.motion.fast"], {"value": 100, "unit": "ms"})
            self.assertEqual(values["group.override"], {"value": 200, "unit": "ms"})
            data["group"]["alias"]["$value"] = "{group.motion.fast}"
            path.write_text(json.dumps(data), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Reference type mismatch"):
                load(path)

    def test_invalid_components_and_extensions_raise_value_error(self):
        for field in ("components", "$extensions"):
            for invalid in (None, 1, "invalid", True):
                with self.subTest(field=field, invalid=invalid), tempfile.TemporaryDirectory() as temp:
                    data = json.loads((ROOT / "design/tokens.json").read_text())
                    token = data["palette"]["gray900"]
                    (token["$value"] if field == "components" else token)[field] = invalid
                    path = Path(temp) / "tokens.json"
                    path.write_text(json.dumps(data), encoding="utf-8")
                    with self.assertRaises(ValueError):
                        load(path)

    def test_failed_second_generator_preserves_both_destinations(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            shutil.copytree(ROOT / "tools/tokens", root / "tools/tokens")
            (root / "tools/codegen").mkdir()
            shutil.copyfile(ROOT / "tools/codegen/outputs.py", root / "tools/codegen/outputs.py")
            destinations = [
                root / "apps/android/core/designsystem/src/main/kotlin/app/gauja/core/designsystem/generated",
                root / "apps/ios/Packages/DesignSystem/Sources/DesignSystem/Generated",
            ]
            for destination in destinations:
                destination.mkdir(parents=True)
                (destination / "existing.txt").write_bytes(b"preserve me")
            data = json.loads((ROOT / "design/tokens.json").read_text())
            del data["color"]["light"]["primary"]
            tokens = root / "tokens.json"
            tokens.write_text(json.dumps(data), encoding="utf-8")
            result = subprocess.run([sys.executable, str(root / "tools/tokens/generate.py"), str(tokens)], capture_output=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn(b"KeyError", result.stderr)
            for destination in destinations:
                self.assertEqual({p.name: p.read_bytes() for p in destination.iterdir()}, {"existing.txt": b"preserve me"})

    def test_generators_use_utf8_even_with_ascii_defaults(self):
        with tempfile.TemporaryDirectory() as temp:
            for platform in ("compose", "swiftui"):
                snapshots = []
                for utf8_mode in ("1", "0"):
                    out = Path(temp) / platform / utf8_mode
                    env = {**os.environ, "LC_ALL": "C", "PYTHONUTF8": utf8_mode, "PYTHONCOERCECLOCALE": "0"}
                    subprocess.run([sys.executable, str(ROOT / f"tools/tokens/generate-{platform}.py"), "--tokens", str(ROOT / "design/tokens.json"), "--output", str(out)], check=True, env=env)
                    snapshots.append({p.name: p.read_bytes() for p in out.iterdir()})
                self.assertEqual(*snapshots)

    def test_swift_typography_preserves_shared_metrics(self):
        with tempfile.TemporaryDirectory() as temp:
            data = json.loads((ROOT / "design/tokens.json").read_text())
            value = data["typography"]["bodyLarge"]["$value"]
            value["lineHeight"] = 1.75
            value["letterSpacing"]["value"] = 0.75
            tokens = Path(temp) / "tokens.json"
            tokens.write_text(json.dumps(data), encoding="utf-8")
            output = Path(temp) / "output"
            subprocess.run([sys.executable, str(ROOT / "tools/tokens/generate-swiftui.py"), "--tokens", str(tokens), "--output", str(output)], check=True)
            styles = (output / "GaujaTypographyStyle.swift").read_text(encoding="utf-8")
            self.assertIn("lineHeight: 1.75, letterSpacing: 0.75", styles)
            self.assertIn("_ category: DynamicTypeSize", styles)
            self.assertIn("case .accessibility5: category = .accessibilityExtraExtraExtraLarge", styles)
            self.assertIn("@unknown default: category = .large", styles)
            self.assertIn("metrics.scaledValue(for: size * lineHeight, compatibleWith: traits)", styles)
            self.assertIn("metrics.scaledValue(for: letterSpacing, compatibleWith: traits)", styles)
            self.assertIn("GaujaTypographyStyle.bodyLarge(category).font", (output / "Font+Gauja.swift").read_text(encoding="utf-8"))

    def test_color_theme_parity_and_contrast(self):
        values = load(ROOT / "design/tokens.json")
        dark = {k.removeprefix("color.dark.") for k in values if k.startswith("color.dark.")}
        light = {k.removeprefix("color.light.") for k in values if k.startswith("color.light.")}
        self.assertEqual(dark, light)
        def luminance(value):
            c = [v / 12.92 if v <= .04045 else ((v + .055) / 1.055) ** 2.4 for v in value["components"]]
            return sum(a * b for a, b in zip(c, [.2126, .7152, .0722]))
        for theme in ("dark", "light"):
            pairs = [("primary", "onPrimary"), ("surface", "onSurface"), ("background", "onBackground")]
            pairs += [(key, key.replace("Background", "Foreground")) for key in dark if key.endswith("Background") and key != "onBackground"]
            for bg, fg in pairs:
                l = sorted([luminance(values[f"color.{theme}.{name}"]) for name in (bg, fg)])
                self.assertGreaterEqual((l[1] + .05) / (l[0] + .05), 4.5, (theme, bg, fg))

    def test_both_generators_are_deterministic(self):
        with tempfile.TemporaryDirectory() as temp:
            for platform in ("compose", "swiftui"):
                snapshots = []
                for run in ("first", "second"):
                    out = Path(temp) / platform / run
                    subprocess.run([sys.executable, str(ROOT / f"tools/tokens/generate-{platform}.py"), "--tokens", str(ROOT / "design/tokens.json"), "--output", str(out)], check=True)
                    snapshots.append({p.name: p.read_bytes() for p in out.iterdir()})
                self.assertEqual(*snapshots)
                text = "\n".join(v.decode() for v in snapshots[0].values())
                self.assertIn("reduceMotion", text)
                self.assertIn("onBackground" if platform == "compose" else "gaujaOnBackground", text)


if __name__ == "__main__":
    unittest.main()
