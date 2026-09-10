<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Native design contract

`tokens.json` uses the DTCG 2025.10 format, with token descriptions and provenance extensions. Seerr's pinned Tailwind config has no custom palette. Stock Tailwind shades and the StatusBadge/Common Badge/Common Button classes are the starting point; native semantic shade adjustments preserve label contrast. Status never relies on color alone. Dark is the default, with an explicitly generated light theme.

Run `tools/tokens/generate.sh` or `tools/tokens/generate.sh --check`. These require only Python's standard library; they never import an app or install a platform toolchain. Invalid values, alias cycles, missing references and output drift fail. One design pixel maps to one dp/point; typography becomes sp on Android and UIFontMetrics-scaled system fonts on iOS. Generated font functions require the component's `@Environment(\.dynamicTypeSize)` value and resolve UIFontMetrics with matching traits, so system changes and accessibility previews both rescale the text. Respect reduced motion by passing the native preference into the generated motion functions.

The generated theme primitives are committed now; composition wrappers, previews and content components belong to Phase 4. No icons, artwork, bundled fonts or media playback are included. See `screens/INVENTORY.md` for the complete v1 surface and `screens/components/INVENTORY.md` for shared component contracts.
