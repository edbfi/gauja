#!/usr/bin/env bash
# SPDX-FileCopyrightText: 2026 Gauja contributors
# SPDX-License-Identifier: AGPL-3.0-or-later
set -euo pipefail
root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
source "$root/tools/ci/apple-tools.env"
cache="$root/.cache/apple-tools/$XCODEGEN_VERSION-$SWIFTLINT_VERSION-$XCODES_VERSION"
mkdir -p "$cache"
if [[ ! -x "$cache/xcodegen/bin/xcodegen" ]]; then
  curl --fail --location --retry 3 "https://github.com/yonaskolb/XcodeGen/releases/download/$XCODEGEN_VERSION/xcodegen.zip" -o "$cache/xcodegen.zip"
  unzip -oq "$cache/xcodegen.zip" -d "$cache"
fi
if [[ ! -x "$cache/swiftlint/swiftlint" ]]; then
  curl --fail --location --retry 3 "https://github.com/realm/SwiftLint/releases/download/$SWIFTLINT_VERSION/portable_swiftlint.zip" -o "$cache/swiftlint.zip"
  unzip -oq "$cache/swiftlint.zip" -d "$cache/swiftlint"
fi
if [[ ! -x "$cache/xcodes/xcodes" ]]; then
  curl --fail --location --retry 3 "https://github.com/XcodesOrg/xcodes/releases/download/$XCODES_VERSION/xcodes.zip" -o "$cache/xcodes.zip"
  unzip -oq "$cache/xcodes.zip" -d "$cache/xcodes"
fi
"$cache/xcodegen/bin/xcodegen" --version
"$cache/swiftlint/swiftlint" version
"$cache/xcodes/xcodes" version
if [[ -n "${GITHUB_PATH:-}" ]]; then
  printf '%s\n' "$cache/xcodegen/bin" "$cache/swiftlint" "$cache/xcodes" >> "$GITHUB_PATH"
else
  printf 'Add these directories to PATH:\n%s\n%s\n%s\n' "$cache/xcodegen/bin" "$cache/swiftlint" "$cache/xcodes"
fi
