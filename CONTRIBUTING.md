<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Contributing to Gauja

Gauja is two independent native apps (Kotlin on Android, Swift on iOS) that share only a contract (`api/`, `design/`). Read `docs/gauja-prd.md` for what and why, `docs/gauja-implementation-plan.md` for the order of work, and the rule files in `.agents/rules/` for how code must be written. The rule files are normative and win over both documents.

## Developer Certificate of Origin (DCO)

Every commit must be signed off:

```bash
git commit -s
```

The sign-off certifies the [Developer Certificate of Origin 1.1](https://developercertificate.org/) for that commit. There is no CLA; you keep your copyright.

> The license of this project is the GNU Affero General Public License, version 3 or (at your option) any later version, **together with** the additional permission set out in `APPSTORE_EXCEPTION.md`. Every reference to "the license" in this repository, including in SPDX headers and in the Developer Certificate of Origin, means the AGPL together with that additional permission.

By signing off you license your contribution under AGPL-3.0-or-later together with the App Store Distribution Exception. The `commit-messages` CI check is required on every pull request: it verifies that every commit is signed off and that the sign-off email matches the author or committer. The local `dco-signoff` hook rejects unsigned commits before they leave your machine.

## Local hooks (prek)

[prek](https://prek.j178.dev) runs the hooks in `prek.toml` on `pre-commit` and `commit-msg`.

```bash
tools/contract/python.sh --install  # pinned build-only contract dependencies
prek install            # once per clone: installs both shims
prek run --all-files    # after changing prek.toml, or before opening a PR
```

Install prek from https://prek.j178.dev/installation/ (`brew install prek`, `uv tool install prek`, or the install script).

[prek.toml](prek.toml) owns hook commands, path filters and generated-file exclusions.
It covers file hygiene, commit conventions/sign-off, secrets and the relevant contract/design
checks. The platform hooks become applicable with handwritten app sources.

Formatting and lint hooks for one platform need that platform's toolchain. If you only work on Android, the iOS hooks skip because no Swift files are staged, and vice versa.

The scripts under `tools/` have tests: `tools/tests/run.sh`. Translation tooling lands with real catalogs. Resolved-dependency license checks run in each app lane; the policy is in `deny.toml` and the review procedure in `docs/dependency-license-review.md`.

## Licensing headers (REUSE)

Files carry SPDX copyright and license information, either as headers or through `REUSE.toml`. Gauja contributions retain AGPL-3.0-or-later with the App Store permission; inherited material retains its upstream attribution and notices. See [third-party provenance and distribution responsibilities](docs/THIRD_PARTY.md). CI runs `reuse lint`; run it locally with `pipx install reuse` / `brew install reuse`. Resolved-dependency license enforcement against `deny.toml` is separate from REUSE.

## Branches, commits and pull requests

- Never commit to `main`; branch from it with a `feat/`, `fix/`, `refactor/`, `docs/`, `chore/` or `ci/` prefix.
- Conventional Commits, one logical change per commit, every commit signed off.
- One module, one screen or one hook per PR where practical. Link the owning behavior contract (a file or inventory section) for UI changes; refine its applicable acceptance criteria with the feature.
- Put meaningful behavior tests near the owning sources; do not create empty suites or require a file per type.
- Generated code (`apps/android/core/api/`, `apps/ios/Packages/SeerrAPI/Generated/`, generated themes) is never hand-edited. Regenerate with `tools/codegen/` or `tools/tokens/`.
- `main` is protected: squash-merge only, the PR title becomes the commit subject, and the existing `REUSE`, `prek`, `commit-messages`, `gitleaks`, `tool-tests` and `boundary` checks remain required. Add `ci / required` with strict up-to-date enforcement and administrator enforcement after adoption validation. It positively requires every hygiene, native, codegen, token, and public contract lane; missing, pending, cancelled, failed, or skipped jobs cannot pass.
- Nothing under `apps/android/` may reference `apps/ios/` or vice versa. Artifacts flow from `api/` and `design/` into the apps only.

## Building one platform without the other's toolchain

Each app is a complete, standalone build. Only `api/`, `design/` and `tools/` are shared inputs.

- **Android only:** JDK 17 and Android SDK 37. `cd apps/android && ./gradlew assembleDebug testDebugUnitTest detekt ktfmtCheck lint`. No Xcode needed.
- **iOS only:** Xcode 26 with the Swift 6.3 toolchain, XcodeGen 2.46.x, SwiftLint 0.65.x. `cd apps/ios && xcodegen generate` then build and test from Xcode or `xcodebuild`. No JDK needed.

The initial apps check `/status` and `/settings/public` without saving profiles or signing in.
For Android, install the SDK package `platforms;android-37.0` and set `ANDROID_HOME`
or ignored `apps/android/local.properties`. For iOS, copy `Package.resolved` into
`Gauja.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/` after XcodeGen generation.
Command-line builds use `-skipPackagePluginValidation` for the pinned SwiftLint plugin.
See the platform workflows for complete commands and [contract tooling](tools/codegen/README.md)
for independent generated-client tests. With one toolchain installed, use `SKIP=swift-format,swiftlint`
(Android) or `SKIP=ktfmt,detekt` (iOS) for a repository-wide hook run; staged platform work
skips the other platform naturally.

## Shared automation

[The CI workflow](.github/workflows/ci.yml) runs every lane for every PR, main
push, and manual dispatch. Platform workflows remain independently dispatchable;
the Android lane never installs Apple tools and the iOS lane never installs a
JDK. There are no workflow-level path filters that leave required checks missing.
The shared versioned aggregate comes from `engels74/automation`; existing
platform commands, generated-code exclusions, deployment floors, pinned emulator
pairings, per-ABI size reports, and native evidence artifacts are preserved.

Actions use full version tags. [Renovate configuration](renovate.json) extends
the shared default and mixed presets, restores native Gradle-wrapper and Python
requirements discovery, and tracks the Apple CLI and OpenAPI generator pins.
The history scanner and Java generator require archive-hash review; the Swift
generator's manifest, pin, resolved graph, and generated output must agree.
Compiler/plugin major upgrades require native compatibility review. Automerge
remains off until the shared policy correction and required checks are verified.

[Apple tool installation](tools/ci/install-apple-tools.sh) reads exact releases
from its adjacent environment file instead of installing current Homebrew tools.
The formatter still comes from the selected Xcode toolchain. Follow the printed
PATH instructions for local checks; CI installs that PATH automatically.

The existing public contract exercises status/settings against the pinned Seerr
container. It does not claim authenticated scenario coverage, which remains
Phase 11. Packaging/distribution remains Phase 12; no signing or store publishing
is added. Python build tooling has its existing behavioral suites and hashed
dependencies; broader Python formatting/type-checking coverage is still a gap.

## Reporting security issues

See `SECURITY.md`. Never open a public issue for a vulnerability.

Routine navigation/search should respect `.gitignore` (for example, `rg` or `git ls-files`). Keep `.cache/` and SwiftPM `.build/` dependency trees hidden in editor navigation; do not prune individual dependency files.
