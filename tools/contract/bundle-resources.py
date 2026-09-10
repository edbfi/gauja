#!/usr/bin/env python3
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Copy compatibility metadata into native resource roots; --check never writes."""
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
DESTINATIONS = (
    "apps/android/core/compat/src/main/resources/compat.json",
    "apps/ios/Packages/Compat/Sources/Compat/Resources/compat.json",
)


def bundle(root, check):
    expected = (root / "api/compat.json").read_bytes()
    for name in DESTINATIONS:
        target = root / name
        if check:
            if not target.is_file() or target.read_bytes() != expected:
                raise ValueError(f"Stale compatibility resource: {name}")
        else:
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(expected)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    bundle(ROOT, args.check)
