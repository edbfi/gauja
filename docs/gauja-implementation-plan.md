<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Gauja — Implementation Plan

| Field | Value |
|---|---|
| Status | Draft v0.1 |
| Date | 2026-09-05 |
| Derived from | `docs/gauja-prd.md` (Draft v0.1) |
| Normative coding guidelines | `.agents/rules/kotlin-2_4-android-app.md`, `.agents/rules/swift-6_3-ios-app.md` |
| Reference implementation | Seerr source (`seerr-api.yml`, `server/`, `src/components/`) — inspiration only, never a dependency |

This document is the **order** in which Gauja is built. The PRD says *what* and *why*; this plan says *when* and *in what shape*. Every item is a checkbox so that progress is visible in the file itself.

---

## How to use this plan

- Phases organize completion; they are not blanket start gates. A phase is done when its retained tasks are checked **and** its exit criteria hold in CI. Start a task when its actual dependencies are ready. Early-start markers document known dependencies, not the only permitted overlaps. Preserve unfinished scope in the checklist; do not require future modules or infrastructure to finish a working flow.
- Follow explicit task dependencies and the dependencies of the selected behavior. Begin with a thin server/profile/local-auth flow across Phases 3–5, then grow features and shared infrastructure together. Add initial container evidence using §11.6 alongside that flow; Phase 11 remains the complete quality audit.
- Keep each PR focused on a coherent, reviewable outcome; a vertical slice may include its working modules, behavior contract and tests. Squash-merge with a Conventional Commit title; sign off every commit (`git commit -s`).
- **Apply the project rules in `.agents/rules/*.md` wherever relevant:**
  - `.agents/rules/kotlin-2_4-android-app.md`
  - `.agents/rules/swift-6_3-ios-app.md`
  - `.agents/rules/modularity.md`
  - `.agents/rules/api-contract.md`
  - `.agents/rules/monorepo.md`

  These rule files are authoritative over the PRD and over this plan on language, framework and library usage (PRD §12.1). Where this plan and a rule file disagree, the rule file wins and this plan gets a fix-up PR. The three project-level rule files (`modularity.md`, `api-contract.md`, `monorepo.md`) were written in Phase 1.
- Numbering note: the PRD refers to rule-file authoring and prek setup as "Phase 0". In this plan that work is **Phase 1**, because tooling is the first thing that lands and the phase numbers here are the ones the repository uses from now on.
- Add tooling and CI with their first real consumer. Do not create passing placeholders, empty-directory READMEs, empty module test suites, or all future feature modules upfront. Completed tasks describe the current retained result; git history records replaced scaffolding.
- Checkbox conventions: `- [ ]` open, `- [x]` done. Update finished items when their result changes; retain phase traceability.

### Phase overview

| Phase | Name | Output | Main consumers |
|---|---|---|---|
| 1 | Developer tooling and repository hygiene | prek, meaningful hygiene CI, licensing, rule files | all work |
| 2 | Shared contract | `api/`, `design/`, `tools/codegen`, `tools/tokens` | 3, 4 |
| 3 | Modular monorepo skeleton | both apps build with working boundary consumers | 4–12 |
| 4 | Core platform layers | `core/*` and `Packages/*` non-feature code | 5–10 |
| 5 | Auth and server profiles | sign in to a real server | 6–10 |
| 6 | Discover and search | first usable screens | 7 |
| 7 | Media details, requests, issues, watchlist | requester persona complete | 8 |
| 8 | Profile and user settings | own account management | — |
| 9 | Admin: users | user management | — |
| 10 | Admin: server settings | full native configuration | — |
| 11 | Cross-cutting quality | deep links, a11y, l10n, perf, contract tests | 12 |
| 12 | Release | stores, F-Droid, reproducible builds | — |

---

## Phase 1 — Developer tooling and repository hygiene

**Goal.** Every commit from here on is formatted, linted, signed off, license-tagged and secret-scanned before it leaves a laptop, and CI enforces the same rules. Establish the relevant protections before their first production inputs; unconsumed tooling does not block feature work.

**Exit criteria.** `prek run --all-files` passes on `main`; the `commit-messages` (DCO) and REUSE checks are required status checks; a PR that violates any hook fails locally *and* in CI; the three project rule files exist and are referenced from the PRD.

### 1.1 prek and local hooks

- [x] Maintain `prek.toml` at the repository root as the authoritative hook configuration:
  - [x] Replace the `ktlint` hook with a Gradle-driven `ktfmtCheck` hook (`apps/android/gradlew --project-dir apps/android ktfmtCheck`). The Kotlin rule file names ktfmt as the formatter and states ktlint is not preferred; the rule file is authoritative (PRD §12.1).
  - [x] Keep `detekt` Gradle-driven so it loads the Compose ruleset (`io.nlopez.compose.rules`). Note: the Gradle- and Xcode-driven hooks are inert until §3.2 / §3.3 exist, because their `files` filters match nothing before then; that is expected, not a gap.
  - [x] Keep every `exclude` for generated directories exactly as listed in the `GENERATED` comment so generated output stays byte-identical.
- [x] Run `prek install` (installs `pre-commit` and `commit-msg` shims); document the command in `CONTRIBUTING.md`.
- [x] Verify the builtin hooks fire: trailing whitespace, EOF fixer, LF endings, merge/case-conflict, large-file guard (512 KB), private-key detection, JSON/TOML/YAML validity, no direct commits to `main`.
- [x] Verify `conventional-pre-commit` rejects a non-conventional message and `dco-signoff` rejects a message without `Signed-off-by:`.
- [x] Verify `gitleaks` runs (install instructions for macOS/Linux in `CONTRIBUTING.md`).
- [x] Add `.editorconfig` (LF, final newline, 4-space Kotlin/Swift, 2-space YAML/JSON/TOML) and `.gitattributes` (`* text=auto eol=lf`, binary rules for store art).
- [x] Add `.gitignore` covering Gradle (`.gradle/`, `build/`, `local.properties`), Xcode (`*.xcodeproj`, `*.xcworkspace`, `DerivedData/`, `.build/`, `.swiftpm/`), IDE folders, and `api/fixtures/**/*.local.*`.

### 1.2 Tooling with meaningful consumers

- [x] `tools/api-drift/check-local.sh` validates staged/range spec-pin pairing, effective contract, compatibility and complete operation coverage.
- [x] `tools/ci/check-fixture-secrets.sh` rejects credentials in recordings; synthetic fixtures test the guard before Phase 11 recordings exist.
- [x] `tools/ci/check-secret-logging.sh` heuristically scans Kotlin/Swift log calls. Keep its tested protection; do not expand it into a language parser.
- [x] `tools/tokens/generate.sh --check` verifies generated themes against tokens.
- [x] Scripts have SPDX metadata, fail on errors and document their CLI. Python suites are discovered centrally by `tools/tests/run.sh`.
- [ ] Enable resolved-dependency license enforcement with the first real app manifests (§3.4); do not keep an allow-list-only placeholder or tests that merely bless it.
- [ ] Enable translation hooks with real catalogs (§11.3); create the validator with catalog consumers, not beforehand.

### 1.3 Licensing and governance files

- [x] `APPSTORE_EXCEPTION.md` — authoritative additional permission, linked from PRD Appendix A.
- [x] Append the PRD §15.2 sentence to `LICENSE` (the license means AGPL-3.0-or-later *together with* the additional permission).
- [x] `REUSE.toml` — annotations for the whole tree; `api/seerr-api.yml` marked MIT with the notice in `LICENSES/MIT.txt`; generated material annotated with Gauja’s AGPL contribution and inherited MIT attribution; preserve upstream notices.
- [x] `deny.toml` — dependency license allow-list (Apache-2.0, MIT, BSD-2/3, ISC, MPL-2.0, EPL-2.0 for Gradle plugins; deny GPL-incompatible and unknown).
- [x] `CONTRIBUTING.md` — DCO section repeating the §15.2 sentence, `git commit -s`, prek setup, branch/PR conventions, focused PRs, how to build one platform without the other's toolchain.
- [x] `SECURITY.md` — private disclosure channel, supported versions statement, secret-handling promise from PRD §10.
- [x] `.github/CODEOWNERS` — `apps/android/` and `apps/ios/` to platform maintainers, `api/` and `design/` to both, `docs/` and root config to project leads.
- [x] `.github/PULL_REQUEST_TEMPLATE.md` — problem/result, behavior-contract link when applicable, tradeoffs and validation; CI owns mechanical checks.
- [x] `.github/ISSUE_TEMPLATE/` — bug (with Seerr version + Gauja version + platform), feature, settings-parity gap.
- [x] `README.md` — one-paragraph description, unaffiliated-with-Seerr statement, supported Seerr baseline link, roadmap line for notifications (PRD §18 risk 10), build-one-platform instructions.
- [x] `docs/THIRD_PARTY.md` — current contract, palette and tooling provenance; add translation-seed provenance if imported in Phase 11.

### 1.4 Project rule files (`.agents/rules/`)

- [x] `.agents/rules/modularity.md` — PRD §12.2 and §12.3 expanded: cohesive responsibilities, folders default within justified modules, no grab-bag files, advisory size limits (300-line file / 40-line function warnings, never errors), generated code isolated, names never repeat paths, meaningful tests follow sources, allowed dependency graph for both platforms.
- [x] `.agents/rules/api-contract.md` — how `api/` is vendored, how overlays are written and justified, how fixtures are recorded and scrubbed, how generated clients are produced, isolated and wrapped by hand-written domain models, how `compat.json` gates features.
- [x] `.agents/rules/monorepo.md` — directory ownership, CI lane triggers, the cross-boundary rule (`apps/android/` and `apps/ios/` never reference each other; artifacts flow from `api/` and `design/` into the apps only).
- [x] Amend `docs/gauja-prd.md` in this PR (folded in by maintainer decision): §11.2 quality tools (ktfmt, not ktlint), §11.2 SDK line (compileSdk/targetSdk 37 per the Kotlin rule file), §14.1 hook table, Appendix B hook entry, and the companion-document filenames (`gauja-prd.md`, `gauja-implementation-plan.md`). Record the reason in the PR body: rule files are authoritative (§12.1).
- [x] Add the new rule files to the rule-file list in this plan's "How to use this plan" section.

### 1.5 CI with real assertions (`.github/workflows/`)

- [x] `pr-hygiene.yml` — runs on every PR: `prek run --all-files`, REUSE lint (`reuse lint`), gitleaks. Mark REUSE and `commit-messages` (the DCO check: sign-off present and matching the author or committer email; no GitHub app) as required status checks; protect `main`; squash-merge only.
- [x] Add active `codegen-check.yml` and `tokens-check.yml` with their contract/theme consumers. Passing Android, iOS, contract, API-sync and release placeholders have been removed; add those lanes with the owning phases.
- [x] Pin every action by commit SHA; add Renovate config (`renovate.json`) for actions, Gradle, SwiftPM, the prek hook revisions and the gitleaks pin. (Renovate, not Dependabot, by maintainer decision: every edbpede repository uses Renovate, and two bots would open duplicate PRs.)

---

## Phase 2 — Shared contract (`api/`, `design/`, `tools/`)

**Goal.** Everything both apps consume from a single source of truth exists, is pinned, and is checked for drift before the corresponding app behavior depends on it.

**Exit criteria.** `api-drift` and `tokens-check` hooks are real and green; both code generators run from `tools/codegen/` and produce clients into the paths Phase 3 expects; the screen inventory is complete enough to size Phases 5–10.

### 2.1 Vendored API contract (`api/`)

- [x] Copy upstream `seerr-api.yml` verbatim from the stable baseline documented in `api/README.md`; derive counts from the effective contract.
- [x] Write `api/UPSTREAM_COMMIT` with the exact upstream commit SHA and the fetch date.
- [x] Add `LICENSES/MIT.txt` (Seerr's MIT text).
- [x] Make `tools/api-drift/check-local.sh` real: fail when `seerr-api.yml` changes without `UPSTREAM_COMMIT` changing or vice versa (compare staged paths).
- [x] Document overlays in `api/README.md`, describing the OpenAPI Overlay format and the rule that every entry cites an upstream issue or observed server behaviour.
- [x] Record the first overlays while generating clients: missing `required` arrays, wrong integer/string types, the undeclared operation tags (`tmdb`, `issue`, `overriderule`), and any `nullable` gaps the generators choke on.
- [x] Create `api/compat.json` with a JSON Schema (`api/compat.schema.json`): `{ featureId: { min: "x.y.z", max: "x.y.z" | null, endpoint, note } }`. Seed with `blocklist` (prefer `/blocklist`; `/blacklist` is deprecated with `Sunset: 2026-06-01`), `discover.sliders`, `quickconnect`, `metadata.providers`.
- [x] Document the future `api/fixtures/<seerr-version>/<tag>/<operationId>.json` recording convention in `api/README.md`. Create directories with actual recordings in Phase 11.
- [x] Keep `api/coverage.json` as the complete editable operation-to-phase/status/exclusion ledger. Validate coverage independently; render endpoint/tag counts on demand with `tools/contract/endpoints.py` instead of committing `ENDPOINTS.md`.

### 2.2 Code generation wrappers (`tools/codegen/`)

- [x] The output directories named in the two tasks below are created here; §3.2 and §3.3 build their `core/api` module and `SeerrAPI` package around them rather than choosing new paths.
- [x] `tools/codegen/android/` — openapi-generator config (`kotlin` generator, `jvm-retrofit2` library, kotlinx-serialization, `useCoroutines`), overlay application step, output to `apps/android/core/api/src/main/kotlin/` with a `GENERATED — do not edit` banner. Pinned generator version.
- [x] `tools/codegen/ios/` — `swift-openapi-generator` config (`types`, `client`; URLSession transport supplied separately), overlay application step, output to `apps/ios/Packages/SeerrAPI/Generated/`. Pinned executable dependency in the tooling `Package.swift`; the app consumes committed output.
- [x] `tools/codegen/generate.sh` — generates both by default; `--platform` selects one. Normal generation writes owned output; `--check` compares temporary output with committed files without rewriting.
- [x] Decide and document the wrapper boundary in `.agents/rules/api-contract.md`: generated DTOs are accessible to `core/data` / `Data` and tests only. Data maps them to `core/model` / `Model` types and exposes domain values outward; aggregate-focused mappers use folders within Data.

### 2.3 Design tokens (`design/tokens.json`, `tools/tokens/`)

- [x] Author `design/tokens.json` in the W3C Design Tokens format: colour (gray-900/800/700 surfaces, indigo-600 accent, indigo-400→purple-400 hero gradient, request-status badge semantics), spacing, radii, elevation, typography scale, motion durations.
- [x] Note in the file header that Seerr's `tailwind.config.js` defines **no** custom palette; token values come from stock Tailwind indigo/gray plus the classes used in `src/components/StatusBadge/`, `src/components/Common/Badge/` and `src/components/Common/Button/`. Record which class each token was derived from.
- [x] Add a light theme derived from the same tokens (dark is the default, PRD §8).
- [x] `tools/tokens/generate-compose.py` → `apps/android/core/designsystem/src/main/kotlin/**/generated/` (`ColorScheme`, `Typography`, `Shapes`, spacing object, motion durations).
- [x] `tools/tokens/generate-swiftui.py` → `apps/ios/Packages/DesignSystem/Sources/DesignSystem/Generated/` (`Color` extensions, `Font` scale, spacing, radii, motion).
- [x] Use `tools/tokens/generate.sh --check`: regenerate both and fail on diff. Wire `tokens-check.yml`.

### 2.4 Screen specifications (`design/screens/`)

- [x] *(can start early: after §1.3)* `design/screens/TEMPLATE.md` — optional review prompts for content, states (loading, empty, error, offline, permission-denied), actions, adaptive behaviour (compact / medium / expanded), acceptance criteria, endpoints used, permissions required, content components used. A contract can be an inventory section; require stable identity, links and observable acceptance criteria, not fixed headings.
- [x] *(can start early: after §1.3)* Screen inventory (`design/screens/INVENTORY.md`) listing every screen by area with a size estimate (S/M/L), so PRD §18 risk 1 is quantified before Phase 5 begins. Areas: auth, servers, discover, search, media (movie, tv, season, person, collection), requests, issues, watchlist, profile, users, settings (one folder per Seerr sidebar section), about.
- [x] Content-component inventory (`design/screens/components/INVENTORY.md`): `TitleCard`, `MediaSlider`, `RequestCard`, `RequestBlock`, `RequestButton`, `IssueBlock`, `StatusBadge`, `AirDateBadge`, `PersonCard`, `CompanyCard`, `GenreCard`, `GenreTag`, `KeywordTag`, `DownloadBlock`, `ExternalLinkBlock`, `BlocklistedTagsBadge`, `PermissionEdit`, `PermissionOption`, `QuotaSelector`, `NotificationTypeSelector`, `JSONEditor` — retain unique component behavior and a shared baseline. Small contracts live as sections in the inventory; use separate files for substantial behavior. Refine with the consuming feature, recording only applicable states and acceptance criteria.
- [x] *(can start early: after §1.3)* Write the auth specs first (`design/screens/auth/*.md`) including the test matrix from PRD §18 risk 5: reverse proxy with basic auth, self-signed TLS, plain HTTP, Plex token expiry, Quick Connect timing.

---

## Phase 3 — Modular monorepo skeleton

**Goal.** Both apps compile, test and lint with working consumers for their initial boundaries and real independent CI lanes. Preserve API/Data/domain and feature isolation. Create only modules needed by the initial app flow; use folders within Data and Settings. The PRD maps describe eventual responsibilities, not required stubs.

**Exit criteria.** `android.yml` and `ios.yml` run build + unit tests + lint + module-graph check + generated-code drift check on path-filtered PRs; a deliberate cross-feature import fails the build on both platforms; a contributor can build one platform without installing the other's toolchain.

- [ ] **Apply the project rules in `.agents/rules/*.md` wherever relevant:**
  - `.agents/rules/kotlin-2_4-android-app.md`
  - `.agents/rules/swift-6_3-ios-app.md`

### 3.1 Repository layout

- [x] Add the following planned paths only with working consumers. Do not create empty directories, purpose-only READMEs or future workflow placeholders.

```
gauja/
  .agents/rules/                kotlin-2_4-android-app.md · swift-6_3-ios-app.md · modularity.md · api-contract.md · monorepo.md
  .github/
    workflows/                  pr-hygiene.yml · android.yml · ios.yml · contract.yml · api-sync.yml · tokens-check.yml · release.yml
    CODEOWNERS · PULL_REQUEST_TEMPLATE.md · ISSUE_TEMPLATE/
  api/                          seerr-api.yml · UPSTREAM_COMMIT · README.md · coverage.json · compat.json · compat.schema.json
    overlays/                   <area>-<operation-or-schema>.yml
    fixtures/<seerr-version>/   <tag>/<operationId>.json
  apps/
    android/                    (§3.2)
    ios/                        (§3.3)
  design/
    tokens.json
    screens/                      inventories, shared contract sections, substantial screen/component specs, template
    assets/                     icons/ · svg/ · store/
  docs/                         gauja-prd.md · gauja-implementation-plan.md · THIRD_PARTY.md
  tools/
    codegen/                    android/ · ios/ · generate.sh
    tokens/                     generate-compose.py · generate-swiftui.py · generate.sh [--check]
    api-drift/                  check-local.sh · diff-upstream.sh
    ci/                         check-secret-logging.sh · check-fixture-secrets.sh (license/egress checks with consumers)
    community/                  catalog validation/import tooling only when consumed
  LICENSE · APPSTORE_EXCEPTION.md · REUSE.toml · prek.toml · renovate.json · crowdin.yml · deny.toml
  CONTRIBUTING.md · SECURITY.md · README.md · CHANGELOG.md
```

### 3.2 Android skeleton (`apps/android/`)

Follow the [Android coding guidelines](../.agents/rules/kotlin-2_4-android-app.md).
Pin only consumed libraries in the actual version catalog; use `tools/codegen/versions.env`
for the existing API smoke dependencies until the real modules take ownership. Keep the
Android deployment floor and native stack specified by the guidelines.

- [x] `settings.gradle.kts` with `pluginManagement`, `dependencyResolutionManagement` (`FAIL_ON_PROJECT_REPOS`), `includeBuild("build-logic")`, and working modules included by path.
- [x] `gradle/libs.versions.toml` pins consumed libraries from the initial selection; add the following only with their consumers: `dependency-analysis`, `kotlinx-collections-immutable`, `androidx.sqlite` driver, `datastore-core`/`protobuf-javalite` (encrypted Proto DataStore), `androidx.browser` (Custom Tabs), `androidx.security` (Keystore-backed key), `okhttp-tls`, `robolectric`, `hilt-android-testing`.
- [x] Extract shared Gradle convention plugins when multiple working modules need the same configuration. Add Compose, Hilt, Room and lint conventions with their consumers.
- [x] Root `build.gradle.kts` declaring `kotlin-compose`, `kotlin-serialization`, `ksp`, `hilt`, `detekt` with `apply false` so KGP 2.4.10 overrides AGP's bundled 2.2.10.
- [x] `config/detekt.yml` (Compose ruleset active; complexity/length rules set to **warn**), `config/lint.xml`, ktfmt via the `com.ncorti.ktfmt.gradle` plugin with `ktfmtCheck`/`ktfmtFormat` tasks.
- [x] Working modules, each with its required manifest/build configuration and meaningful behavior tests. The responsibility map below grows incrementally; it does not require a module per folder:

```
apps/android/
  settings.gradle.kts · build.gradle.kts · gradle.properties · gradlew · gradle/libs.versions.toml · gradle/wrapper/
  build-logic/convention/src/main/kotlin/...
  config/detekt.yml · config/lint.xml
  app/                          MainActivity (single Activity), GaujaApplication (@HiltAndroidApp), NavDisplay wiring,
                                deep-link entry, HiltTestRunner
  core/
    api/                        GENERATED openapi client (leaf; excluded from ktfmt/detekt/lint)
    common/                     Result, AppError taxonomy, dispatchers qualifiers, Clock
    compat/                     ServerVersion, FeatureGate, compat.json loader
    data/                       one Data module, folders per aggregate:
      auth/ discover/ media/ requests/ issues/ watchlist/ users/ settings/ servers/
    database/                   Room 3 database, DAOs, related entities, AndroidSQLiteDriver
    datastore/                  ServerProfileStore (Proto), PreferencesStore, SecretStore (Keystore-encrypted)
    designsystem/               generated/ theme + primitives (GaujaTheme, buttons, cards, chips)
    model/                      pure Kotlin/JVM domain models and enums
    navigation/                 NavKey sealed hierarchy, Navigator, EntryProviderInstaller, result contracts
    network/                    OkHttp/Retrofit factories, per-profile CookieJar, interceptors, TLS, deprecation recorder
    testing/                    fakes, fixture loaders, MainDispatcherRule, Hilt test modules
    ui/                         shared content components (TitleCard, MediaSlider, StatusBadge, …)
  feature/
    auth/ servers/ discover/ search/
    media/movie/ media/tv/ media/person/ media/collection/
    requests/ issues/ watchlist/ profile/ users/
    settings/general/ settings/users/ settings/plex/ settings/jellyfin/
    settings/services/radarr/ settings/services/sonarr/ settings/network/ settings/metadata/
    settings/notifications/<agent>/ (email, discord, gotify, ntfy, pushbullet, pushover, slack, telegram, webhook, webpush)
    settings/discover/ settings/logs/ settings/jobs/ settings/cache/ settings/about/
```

  As needed within a working feature module: `ui/<screen>/` (screen composable, `UiState`, `ViewModel`), `domain/` (use cases), `navigation/` (keys + Hilt `@IntoSet` `EntryProviderInstaller`), `di/`.

- [x] Wire `com.autonomousapps.dependency-analysis` and a `tools/ci/check-module-graph.sh` (or a Gradle task) that asserts the PRD §12.3 graph: `feature/*` never depends on another `feature/*`; only Data imports generated DTOs; storage/network dependencies follow the allowed modularity tables; `core/model` has no Android deps; nothing depends on `app`.
- [x] *(depends on working modules above)* Smoke tests of real consumers: one JVM unit test (JUnit4 + coroutines-test + Turbine), one Robolectric Compose test, one Hilt instrumentation test with `HiltTestRunner`, all green.
- [ ] Add baseline profiles with the measurable start-up/Discover path in Phase 11; do not benchmark a placeholder.
- [x] *(depends on working §3.2 app/build/tests)* `android.yml` real: `assembleDebug`, `testDebugUnitTest`, `detekt`, `ktfmtCheck`, `lint`, module-graph check, `tools/codegen/generate.sh --check --platform android` (transfer ownership from codegen-check), emulator smoke (`connectedDebugAndroidTest` on API 30 and API 37), per-ABI split size report.

  Qualified on 2026-09-06 in [two fresh hosted executions](https://github.com/edbpede/gauja/actions/runs/34036805173) (attempts 1 and 2) at repair head `f0aa806fcc1d0e612011b111deeab3ada6804c5b`, merge checkout `11563abc6710aa8391e8da98ba1cb079b91720a3`, base `5a520ccf16398cefc4e24510e2e8273baf5d3614`. Both API lanes passed the real Hilt test and sustained guest-health checks; all associated build gates passed. Ubuntu image `20260831.293.1` used emulator 37.1.11/build 15917651, API 30 image revision 16 and API 37.0 image revision 6. API 37 receives 4096 MiB and a verified AVD target: the runner's older AVD Manager wrote `android-0`, which made the emulator interpret API 3 and disable required graphics capabilities. Correcting that metadata restored healthy instrumentation without a graphics override. The archive/hash pin and image guards preserve this tested pairing; replacements require the same fresh-run qualification. These passes provide bounded reliability evidence, not a claim that future flakiness is eliminated.

### 3.3 iOS skeleton (`apps/ios/`)

Follow the [iOS coding guidelines](../.agents/rules/swift-6_3-ios-app.md).
Keep the iOS 18 floor, Swift 6 strict concurrency and generated Xcode project. UI targets
use MainActor default isolation; networking/domain targets remain nonisolated. Put exact
executable dependency pins in package manifests/locks, not another prose version table.

- [x] `project.yml` with `options.deploymentTarget.iOS: "18.0"`, guideline build settings under `settings.base`, one `application` target, one `bundle.unit-test` target, one `bundle.ui-testing` target, local packages by path, `swift-dependencies` and `SwiftLintPlugins` by URL, schemes with coverage and randomised parallel tests.
- [x] `.swiftlint.yml` (strict; `excluded: "**/Generated"`; line length 120/160; length/complexity rules at **warning**) and `.swift-format` (JSON, 4 spaces, 120 columns).
- [x] Working packages with `Package.swift`, sources and meaningful tests. Add only targets required by current consumers; the following is the eventual responsibility map:

```
apps/ios/
  project.yml · .swiftlint.yml · .swift-format
  App/                          GaujaApp (@main), RootScene, RootNavigation (NavigationSplitView on regular width), DeepLinkRouter
  AppTests/ · AppUITests/       Swift Testing · XCUITest smoke lane
  Packages/
    SeerrAPI/                   GENERATED swift-openapi client (Generated/ excluded from format/lint; nonisolated)
    Common/                     Result, AppError, Clock, dependency keys (nonisolated)
    Compat/                     ServerVersion, FeatureGate, compat.json loader (nonisolated)
    Model/                      pure Swift domain models and enums (nonisolated)
    Network/                    URLSession config, per-profile HTTPCookieStorage, auth/basic-auth, TLS delegate,
                                deprecation recorder (nonisolated)
    Data/                       one nonisolated target, folders per aggregate
    Persistence/                SwiftData models + @ModelActor stores, KeychainStore, TypedDefaults (nonisolated)
    DesignSystem/               Generated/ theme + primitive views (MainActor)
    UI/                         shared content components (MainActor)
    Navigation/                 Route enums, result types, deep-link parsing (MainActor)
    Testing/                    fakes, fixture loaders, in-memory ModelContainer helpers
    Features/
      Auth/ Servers/ Discover/ Search/
      Media/ (folders for movie, TV, person and collection details)
      Requests/ Issues/ Watchlist/ Profile/ Users/
      Settings/ (one target, folders per Seerr sidebar section: General, Users, Plex, Jellyfin, Services, Network, Metadata,
                 Notifications/<Agent>, Discover, Logs, Jobs, Cache, About)
```

  Every feature package exposes exactly one public entry view and one public route type; everything else is `internal`. Non-UI packages (`SeerrAPI`, `Common`, `Compat`, `Model`, `Network`, `Data`, `Persistence`) do **not** set `.defaultIsolation(MainActor.self)`; UI packages do. All packages enable `NonisolatedNonsendingByDefault` and `InferIsolatedConformances`.

- [x] `tools/ci/check-package-graph.sh` — uses SwiftPM’s evaluated package description plus `project.yml` to assert the PRD §12.3 graph (feature packages never import each other; only `Data` imports generated DTOs; storage/network dependencies follow the allowed modularity tables; `Model` imports nothing in-repo).
- [x] *(depends on working packages above)* Test real serialization/redaction and app behavior with Swift Testing, plus an XCUITest launching the initial root on phone and tablet simulators. No empty stub suites.
- [x] *(depends on every other §3.3 task)* `ios.yml` real: `xcodegen generate`, build, `xcodebuild test` (Swift Testing), `swift format lint --strict`, `swiftlint --strict`, package-graph check, `tools/codegen/generate.sh --check --platform ios` (transfer ownership from codegen-check), simulator smoke, thinned-size report.

### 3.4 Cross-platform checks

- [x] With each platform’s first real manifests, resolve the actual runtime/build/test dependency graph (including transitives), review applicable licenses against `deny.toml`, and enforce the result in that platform’s lane. A Gradle version catalog alone is not a resolved graph; use resolved configurations and SwiftPM resolution. Report unknown metadata as a failure. Do not create an allow-list-only success placeholder or require the other platform’s toolchain.
- [x] Retire each platform’s duplicated smoke manifests/locks only after real API modules independently compile and pass equivalent serialization/redaction tests. Use one dependency/build source per platform. Transfer checks and path filters to their replacement owner in the same PR; keep platform lint/build work out of the Linux shared-hygiene job.
- [x] Measure clean/incremental API-module builds and IDE indexing using representative changes and a recorded machine/toolchain baseline. Retain supported generator structure unless measurements justify a trial; follow the coverage/shared-schema checks in [codegen setup](../tools/codegen/README.md#generated-file-structure).
- [x] Add egress enforcement with the first real transport flow (Phase 4/11), without a passing skeleton.
- [x] Verify the cross-boundary rule with a grep in `pr-hygiene.yml`: no path under `apps/android/` mentions `apps/ios/` and vice versa.

---

## Phase 4 — Core platform layers

**Goal.** Implement the core responsibilities needed by the hello-server flow with meaningful tests. Create additional repositories/components alongside their first feature consumers; do not build all future infrastructure upfront.

**Exit criteria.** A test-only "hello server" flow signs in to a local Seerr with a session cookie, fetches `/auth/me`, caches it, and renders a `TitleCard` from cached data with no network. Secret-logging guard is real and green.

- [ ] **Apply the project rules in `.agents/rules/*.md` wherever relevant:**
  - `.agents/rules/kotlin-2_4-android-app.md`
  - `.agents/rules/swift-6_3-ios-app.md`

### 4.1 `core/model` / `Model`

- [ ] Domain enums ported from Seerr's `server/constants/`: `MediaStatus` (UNKNOWN=1, PENDING=2, PROCESSING=3, PARTIALLY_AVAILABLE=4, AVAILABLE=5, BLOCKLISTED=6, DELETED=7), `MediaRequestStatus` (PENDING=1, APPROVED=2, DECLINED=3, FAILED=4, COMPLETED=5), `MediaType` (movie, tv), `IssueType` (VIDEO=1, AUDIO=2, SUBTITLES=3, OTHER=4), `IssueStatus` (OPEN=1, RESOLVED=2), `MediaServerType` (PLEX=1, JELLYFIN=2, EMBY=3, NOT_CONFIGURED=4), `DiscoverSliderType` (21 members, 1-based). Every `when`/`switch` over these has an explicit unknown branch because upstream adds values.
- [ ] `Permission` as a bitmask with 29 nonzero flags plus `NONE=0` matching `server/lib/permissions.ts` exactly (ADMIN=2, MANAGE_SETTINGS=4, MANAGE_USERS=8, MANAGE_REQUESTS=16, REQUEST=32, VOTE=64, AUTO_APPROVE=128, AUTO_APPROVE_MOVIE=256, AUTO_APPROVE_TV=512, REQUEST_4K=1024, REQUEST_4K_MOVIE=2048, REQUEST_4K_TV=4096, REQUEST_ADVANCED=8192, REQUEST_VIEW=16384, AUTO_APPROVE_4K=32768, AUTO_APPROVE_4K_MOVIE=65536, AUTO_APPROVE_4K_TV=131072, REQUEST_MOVIE=262144, REQUEST_TV=524288, MANAGE_ISSUES=1048576, VIEW_ISSUES=2097152, CREATE_ISSUES=4194304, AUTO_REQUEST=8388608, AUTO_REQUEST_MOVIE=16777216, AUTO_REQUEST_TV=33554432, RECENT_VIEW=67108864, WATCHLIST_VIEW=134217728, MANAGE_BLOCKLIST=268435456, VIEW_BLOCKLIST=1073741824; bit 29 unused).
- [ ] `hasPermission(required, user, mode = and|or)` with the upstream semantics (ADMIN short-circuits true; scalar zero and empty AND arrays are true; empty OR arrays are false for non-admin users); table-driven tests generated from the same table on both platforms.
- [ ] Aggregates: `ServerProfile`, `ServerStatus`, `PublicSettings`, `User`, `UserQuota`, `MediaInfo`, `Movie`, `TvShow`, `Season`, `Episode`, `Person`, `Collection`, `MediaRequest`, `Issue`, `IssueComment`, `WatchlistItem`, `DiscoverSlider`, plus a `Page<T>` wrapper. Group related value types by cohesive responsibility; add aggregates with consumers.

### 4.2 `core/common` / `Common`

- [ ] `AppResult<T>` / `Result` conventions and the `AppError` taxonomy (network, tls, auth, permission, notFound, validation, serverVersion, offline, unknown) with user-facing message keys.
- [ ] Dispatcher qualifiers (`@Dispatcher(IO)`, `@Dispatcher(Default)`) on Android; `DependencyValues` keys (`swift-dependencies`) on iOS; injectable `Clock`.
- [ ] Redaction helpers for diagnostics export (hosts, cookies, keys) — the only place that may touch secret values for output.

### 4.3 `core/network` / `Network`

- [ ] Per-profile `OkHttpClient` / `URLSession` factory keyed by profile id; isolated cookie jar / `HTTPCookieStorage` per profile so sessions never mix.
- [ ] Session cookie handling for `connect.sid` (30-day server TTL): persisted through `SecretStore`/Keychain, restored on launch, cleared on logout or profile deletion.
- [ ] Auth interceptor: cookie session **or** `X-Api-Key`. Because the API key acts as user 1 and `X-API-User` impersonates any user, API-key profiles are labelled "operator" and the UI warns before saving one; `X-API-User` is never sent.
- [ ] Optional reverse-proxy `Authorization: Basic` interceptor per profile.
- [ ] TLS: system trust by default; pinned self-signed SHA-256 fingerprint mode that fails closed until the user confirms the shown fingerprint; plain-HTTP allowed with a persistent warning flag.
- [ ] Deprecation-header recorder: captures `Deprecation`, `Sunset`, `Link rel="successor-version"` per endpoint into a diagnostics store surfaced by About → Diagnostics (Phase 10).
- [ ] Egress allow-list: the profile host, `plex.tv` / `app.plex.tv` during Plex sign-in only, and the image host derived from server settings. Any other host throws in debug and is logged as a violation in release.
- [ ] Image URL resolver: when the server has image caching on, rewrite `https://image.tmdb.org/<path>` to `<baseUrl>/imageproxy/tmdb/<path>` (and `artworks.thetvdb.com` → `/imageproxy/tvdb/`), which is unauthenticated and cookie-free; otherwise request TMDB directly at the size the layout needs. Coil 3 (`coil-network-okhttp`) on Android; `URLSession` + `NSCache` + disk cache on iOS (Nuke decision recorded with the image-loading implementation after measuring, PRD §18 risk 7).

### 4.4 `core/api` / `SeerrAPI` and `core/data` / `Data`

- [ ] Integrate the committed generated clients into the real API modules; CI keeps byte-for-byte verification.
- [ ] *(depends on §4.1, §4.3, §4.5, §4.6)* Repository interfaces in `core/data/<aggregate>` with a default implementation per aggregate (`AuthRepository`, `ServersRepository`, `DiscoverRepository`, `MediaRepository`, `RequestsRepository`, `IssuesRepository`, `WatchlistRepository`, `UsersRepository`, `SettingsRepository`); aggregate-focused DTO → domain mappers; main-safe suspend functions; reactive reads as `Flow` / `AsyncSequence` from the cache.
- [ ] Offline read-through: `refresh()` writes the cache; reads observe the cache; a `staleness` timestamp travels with every cached aggregate.

### 4.5 `core/database` / `Persistence` (caches)

- [ ] Android: Room 3 database (`AndroidSQLiteDriver`, `setQueryCoroutineContext(Dispatchers.IO)`), cohesive entity groups, DAOs with `Flow` reads and `@Upsert` writes, keyed by profile id. Entities for discover pages, media details, requests, issues, watchlist, profile, public settings, status.
- [ ] iOS: SwiftData `@Model` classes with explicit `@Relationship(deleteRule:inverse:)`, `#Index` on hot fetches, a `@ModelActor` store per aggregate; `PersistentIdentifier` crosses actor boundaries, model objects never do. `VersionedSchema` + `SchemaMigrationPlan` from v1.
- [ ] Per-profile wipe: deleting a profile removes its rows, cookie jar, secrets, and image cache entries.

### 4.6 `core/datastore` / `Persistence` (profiles, preferences, secrets)

- [ ] `ServerProfileStore`: encrypted Proto DataStore (Android) / SwiftData + Keychain (iOS) holding display name, base URL, TLS mode + fingerprint, auth method, cached `/status` and `/settings/public` snapshots.
- [ ] `SecretStore`: Keystore-backed encrypted storage / Keychain for session cookie, API key, basic-auth password, Plex token. No `toString`, no `Codable` conformance, no interpolation into logs.
- [ ] `PreferencesStore`: theme (dark default, light, system), active profile id, discover region/watch providers per profile, reduced-motion respect.
- [ ] *(depends on the `SecretStore` task above)* Make `tools/ci/check-secret-logging.sh` real with the actual symbol names from `SecretStore`; add a fixture test that the hook rejects a known-bad sample.

### 4.7 `core/compat` / `Compat`

- [ ] `ServerVersion` parser (semver with Seerr's `commitTag` suffixes) and `FeatureGate.isSupported(featureId)` reading `api/compat.json` bundled as a resource.
- [ ] On connect and on every foreground: refresh `/status`; expose `updateAvailable`, `restartRequired`, and an "outside supported range" flag for the banner in Phase 5.

### 4.8 `core/designsystem` / `DesignSystem` and `core/ui` / `UI`

- [ ] Integrate the committed generated themes from Phase 2; `GaujaTheme` (Material 3, dark default) and the SwiftUI equivalent; Dynamic Type / font scaling verified.
- [ ] Primitive components with previews: buttons, cards, chips, badges, section headers, skeleton loaders, empty/error/offline states.
- [ ] *(depends on the theme and primitives tasks above)* Content components (cohesive files, spec-driven from `design/screens/components/`): `TitleCard`, `MediaSlider`, `RequestCard`, `RequestBlock`, `RequestButton`, `IssueBlock`, `StatusBadge`, `AirDateBadge`, `PersonCard`, `CompanyCard`, `GenreCard`, `GenreTag`, `KeywordTag`, `DownloadBlock`, `ExternalLinkBlock`, `BlocklistedTagsBadge`. Implement the components needed by this phase; add the rest with their feature consumers. Each has applicable TalkBack/VoiceOver labels and a `@PreviewScreenSizes` / multi-device preview.
- [ ] Compose stability: state holders expose `ImmutableList`; `Modifier` is the first optional parameter; no work in composition bodies.

### 4.9 `core/navigation` / `Navigation`

- [ ] Android: `@Serializable` `NavKey` routes added with each screen, a singleton `Navigator` owning the back stack, `EntryProviderInstaller` multibinding, `rememberNavBackStack` at the root, `ListDetailSceneStrategy` for adaptive list-detail areas, both entry decorators wired.
- [ ] iOS: `Route` enums per feature, a root `NavigationPath` owner, `NavigationSplitView` on regular width and `NavigationStack` on compact, `navigationDestination(for:)` centralised at the root, idempotent destination builders.
- [ ] Deep-link key parsing for `gauja://server/<profileId>/...` and Seerr web URLs (`/movie/<id>`, `/tv/<id>`, `/person/<id>`, `/collection/<id>`, `/requests`, `/issues/<id>`, `/reset-password/<guid>`); registration itself lands in Phase 11.

### 4.10 `core/testing` / `Testing`

- [ ] Fixture loaders reading `api/fixtures/<version>/`, fake repositories, `MainDispatcherRule`, Hilt `@TestInstallIn` modules, in-memory `ModelContainer` helpers, a `FakeClock`.

---

## Phase 5 — Auth and server profiles

**Goal.** A user adds a server, signs in by any method the server enables, and lands on a placeholder home with a permission-aware shell. Multiple profiles switch cleanly.

**Exit criteria.** Every row of PRD §5.1 works against a real Seerr on LAN, over a basic-auth reverse proxy, and with a self-signed certificate; the auth test matrix in `design/screens/auth/` passes on both platforms.

- [ ] **Apply the project rules in `.agents/rules/*.md` wherever relevant** (this line applies to Phases 5–10; see "How to use this plan"):
  - `.agents/rules/kotlin-2_4-android-app.md`
  - `.agents/rules/swift-6_3-ios-app.md`

### 5.1 `feature/servers` / `Features/Servers`

- [ ] Add server by URL: normalise scheme/port, call `/status` and `/settings/public`, show version and enabled sign-in methods, warn on plain HTTP, offer fingerprint pinning on TLS failure (show the SHA-256 fingerprint; require explicit confirmation).
- [ ] Optional reverse-proxy basic-auth credentials on the profile form.
- [ ] Profile list, edit, delete (wipes cookie jar, secrets, caches), reorder; quick switcher in the app shell; exactly one active profile.
- [ ] Supported-range banner from `core/compat`; `restartRequired` and `/status/appdata` banners mirroring Seerr's web UI.

### 5.2 `feature/auth` / `Features/Auth`

- [ ] *(depends on §5.1 add-server)* Sign-in method picker driven by `/settings/public` (`localLogin`, `mediaServerLogin`, `mediaServerType`, `jellyfinQuickConnect`, `newPlexLogin`).
- [ ] **Plex**: `POST https://plex.tv/api/v2/pins?strong=true` with `X-Plex-Client-Identifier` (stable per install), `X-Plex-Product: Gauja`, version, platform, device headers → open `https://app.plex.tv/auth#!?clientID=…&code=…&context[device][product]=Gauja` in Custom Tabs / `ASWebAuthenticationSession` → poll `GET /api/v2/pins/{id}` every second until `authToken`, bounded by `expiresAt` and a 15-minute cap → `POST /auth/plex { authToken }`. Plex token stored only in `SecretStore`.
- [ ] **Jellyfin / Emby**: `POST /auth/jellyfin` with username, password, optional hostname/port/urlBase/useSsl when the server asks for them.
- [ ] **Jellyfin Quick Connect**: `POST /auth/jellyfin/quickconnect/initiate` → show code → poll `GET /auth/jellyfin/quickconnect/check` → `POST /auth/jellyfin/quickconnect/authenticate`; handle expiry and cancellation.
- [ ] **Local**: `POST /auth/local` with email and password.
- [ ] **API key**: `X-Api-Key` header profile with the operator warning (acts as the original admin; grants impersonation server-side).
- [ ] **Password reset**: `POST /auth/reset-password` from the sign-in screen; `POST /auth/reset-password/{guid}` reached via the deep link registered in Phase 11.
- [ ] Session: `GET /auth/me` on launch and foreground; `POST /auth/logout` clears the profile's cookie jar; 401 anywhere routes to re-authentication for that profile only.
- [ ] Permission-aware shell: top-level destinations (Discover, Requests, Issues, Users, Settings, Profile) shown per `hasPermission`; admin areas hidden without `ADMIN`/`MANAGE_*`.

---

## Phase 6 — Discover and search

**Goal.** The home experience matches Seerr's Discover page, driven entirely by server configuration, with offline read-through and the performance budget in sight.

**Exit criteria.** PRD §5.2 and §5.3 complete; cached Discover renders in ≤ 300 ms offline; scroll jank < 1 % on the reference devices in a release build.

### 6.1 `feature/discover` / `Features/Discover`

- [ ] Home sliders from `GET /settings/discover` (order, enabled, `isBuiltIn`, `data`), mapped by `DiscoverSliderType`: RECENTLY_ADDED → `/media?filter=allavailable&sort=mediaAdded`; RECENT_REQUESTS → `/request?filter=all&sort=modified`; PLEX_WATCHLIST → `/discover/watchlist`; TRENDING → `/discover/trending`; POPULAR_MOVIES / POPULAR_TV → `/discover/movies` / `/discover/tv`; MOVIE_GENRES / TV_GENRES → `/discover/genreslider/{movie|tv}`; UPCOMING_MOVIES / UPCOMING_TV → date-filtered discover; STUDIOS / NETWORKS → static lists into `/discover/movies/studio/{id}` / `/discover/tv/network/{id}`; TMDB_* custom sliders → keyword, genre, search, studio, network, streaming-service queries with the slider's `data`.
- [ ] `MediaSlider` with horizontal paging and prefetch; "see all" opens the paginated grid.
- [ ] Paginated grids with infinite scroll and stable keys for trending, popular, upcoming; browse by genre, studio, network, keyword, language.
- [ ] Watch-provider filter (`/watchproviders/regions`, `/watchproviders/movies`, `/watchproviders/tv`) persisted per profile.
- [ ] Multi-column grids on medium/expanded widths via `WindowSizeClass` / horizontal size class; never branch on raw dp.
- [ ] Offline: last successful slider payloads cached per profile; staleness indicator; pull-to-refresh.

### 6.2 `feature/search` / `Features/Search`

- [ ] Debounced multi-search (`/search`) across movies, TV and people; results use the same `TitleCard`/`PersonCard` as Discover.
- [ ] Company (`/search/company`) and keyword (`/search/keyword`) lookups feeding the discover filters.
- [ ] Recent searches per profile (non-secret preference).

---

## Phase 7 — Media details, requests, issues, watchlist

**Goal.** The requester persona is complete: browse → details → request → track; report and follow issues; manage a watchlist.

**Exit criteria.** PRD §5.4–§5.7 complete for non-admin users; admin actions present behind permissions; request and issue counts drive badges.

### 7.1 `feature/media/*` / `Features/Media`

- [ ] Movie page (`/movie/{id}`, `/movie/{id}/ratings`, `/movie/{id}/recommendations`, `/movie/{id}/similar`): hero with backdrop and gradient title, metadata, cast/crew, ratings, external links, trailer link-out, `mediaInfo` → availability and request state, `DownloadBlock` when downloads exist.
- [ ] TV page (`/tv/{id}`, `/tv/{id}/season/{n}`, `/tv/{id}/ratings`, recommendations, similar): season list with per-season status, episode list per season.
- [ ] Person page (`/person/{id}`, `/person/{id}/combined_credits`).
- [ ] Collection page (`/collection/{id}`) with request-all.
- [ ] Watch data (`/media/{id}/watch_data`) shown when Tautulli is configured.
- [ ] Blocklist: add/remove (`/blocklist/*`) with `BlocklistedTagsBadge`; use `/blocklist`; `/blacklist` is excluded at the supported floor (`Sunset: 2026-06-01`). Do not add a legacy compatibility implementation without a deliberate support-policy change.
- [ ] Admin media management: mark available / partially available / unknown, delete media, view file paths (`/media/{id}/*`) behind `MANAGE_REQUESTS`/`ADMIN`.

### 7.2 `feature/requests` / `Features/Requests`

- [ ] *(depends on §7.1 movie and TV pages)* Create request sheet: movie; TV with season picker (respect already-requested/available seasons); 4K variant when `REQUEST_4K*` and server 4K settings allow; advanced options (server, quality profile, root folder, tags, language profile) from `/service/radarr`, `/service/radarr/{id}`, `/service/sonarr`, `/service/sonarr/{id}` when `REQUEST_ADVANCED`.
- [ ] My requests list (`/request?filter=…&sort=…`) with filters (pending, approved, available, processing, declined, failed) and sort; `RequestCard`.
- [ ] Request detail: status timeline, requester, modified-by, seasons, downloads.
- [ ] Admin: approve, decline, retry, edit, delete (`/request/{id}/{approve|decline|retry}`, `PUT /request/{id}`, `DELETE`); bulk actions from the list behind `MANAGE_REQUESTS`.
- [ ] Admin: override rules CRUD (`/overrideRule/*`) as a sub-screen of request settings.
- [ ] `/request/count` for the Requests tab badge.

### 7.3 `feature/issues` / `Features/Issues`

- [ ] Create issue from a media page (`POST /issue`): type (VIDEO, AUDIO, SUBTITLES, OTHER), season/episode for TV, message.
- [ ] Issues list (`/issue?filter=…`) with filters (open, resolved) and `IssueBlock`.
- [ ] Issue detail with comment thread (`/issue/{id}`, `/issue/{id}/comment`, `/issueComment/{id}` edit/delete own).
- [ ] Admin: resolve, reopen, delete (`/issue/{id}/{resolved|open}`, `DELETE /issue/{id}`) behind `MANAGE_ISSUES`.
- [ ] `/issue/count` for the Issues tab badge.

### 7.4 `feature/watchlist` / `Features/Watchlist`

- [ ] Add/remove (`POST /watchlist`, `DELETE /watchlist/{tmdbId}`), list (`/watchlist`), sync status with the linked Plex/Jellyfin watchlist where the server exposes it; `WATCHLIST_VIEW` gating.

---

## Phase 8 — Profile and user settings

**Goal.** A user manages their own account exactly as in Seerr's profile area.

**Exit criteria.** PRD §5.8 complete; the greyed-out Notifications entry (PRD §7) is present in app settings.

### 8.1 `feature/profile` / `Features/Profile`

- [ ] Own profile (`/auth/me`, `/user/{id}/requests`, `/user/{id}/quota`): request history, quotas with usage bars, watchlist shortcut.
- [ ] General settings (`/user/{id}/settings/main`): display name, locale, region, original language, discover region, watch providers.
- [ ] Password change (`/user/{id}/settings/password`) when local login applies.
- [ ] Linked accounts (`/user/{id}/settings/linked-accounts/*`): Plex (PIN flow reuse), Jellyfin including Quick Connect.
- [ ] Notification preferences (`/user/{id}/settings/notifications`): notification-type matrix per enabled agent (`NotificationTypeSelector`), Discord/Telegram/Pushover/Pushbullet identifiers.
- [ ] Permissions view (`/user/{id}/settings/permissions`, read-only for self) using `PermissionEdit`/`PermissionOption`.

### 8.2 App settings (in `app` / `App`)

- [ ] Theme (dark default, light, follow system), image cache limits (defaults 64 MB memory / 256 MB disk), clear caches per profile and in bulk, diagnostics export (redacted).
- [ ] **Notifications** entry greyed out and labelled "Coming later", one-line explanation, repeated in About (PRD §7).

---

## Phase 9 — Admin: users

**Goal.** Administrators manage users, permissions, quotas and imports natively.

**Exit criteria.** PRD §5.9 complete behind `MANAGE_USERS`; the permission matrix round-trips every bit.

### 9.1 `feature/users` / `Features/Users`

- [ ] User list (`/user?take=…&skip=…&sort=…`) with search and sort; adaptive list-detail on expanded widths.
- [ ] Create local user (`POST /user`); import from Plex (`/user/import-from-plex`) and Jellyfin (`/user/import-from-jellyfin`).
- [ ] Edit any user's general settings, password, linked accounts, notification preferences (same screens as Phase 8 with the target user id).
- [ ] Permissions editor (`/user/{id}/settings/permissions`): full matrix with dependent-permission rules mirrored from `PermissionEdit` (e.g. `REQUEST_4K_MOVIE` implies `REQUEST_4K` context); bulk permission edit (`PUT /user`).
- [ ] Quotas (`/user/{id}/quota`, `QuotaSelector`): movie/TV limits and windows.
- [ ] Delete user (`DELETE /user/{id}`) with confirmation.

---

## Phase 10 — Admin: server settings

**Goal.** Full native coverage of the supported `/settings/*` surface; derive path/operation counts from the effective contract using the endpoint renderer. Folders within Settings initially, with feature-sized PRs, implemented in Seerr's sidebar order so partial progress is coherent: General → Users → Plex *or* Jellyfin/Emby → Services → Network → Metadata Providers → Notifications → Logs → Jobs → About, then the separate Discover slider management page. (PRD §5.10 lists Metadata before Network; this plan follows the order in Seerr's `SettingsLayout.tsx`.)

**Exit criteria.** Every setting Seerr's web UI exposes can be read and written from Gauja; list/detail layout on tablets and foldables; every form validates server-side via the corresponding `/test` endpoint where one exists.

### 10.1 `feature/settings/general` / `Features/Settings/General`

- [ ] `/settings/main`: application title and URL, API key view + regenerate (`/settings/main/regenerate`), locale, region, original language, hide available, CSRF protection, cache images, trust proxy, and the remaining main-settings fields.

### 10.2 `feature/settings/users` / `Features/Settings/Users`

- [ ] User defaults from `/settings/main`: default permissions (matrix), default quotas, local login toggle, new Plex login toggle.

### 10.3 `feature/settings/plex` and `feature/settings/jellyfin` (mutually exclusive by `MediaServerType`)

- [ ] Plex: server picker from `/settings/plex/devices/servers`, manual host/port/SSL, libraries (`/settings/plex/library`, sync toggle), full and recent scan controls (`/settings/plex/sync`), Plex users (`/settings/plex/users`), Tautulli (`/settings/tautulli`).
- [ ] Jellyfin / Emby: host, external URL, API key, libraries (`/settings/jellyfin/library`), sync (`/settings/jellyfin/sync`), users (`/settings/jellyfin/users`).

### 10.4 `feature/settings/services/radarr` and `.../sonarr`

- [ ] List, add, edit, delete servers (`/settings/radarr`, `/settings/radarr/{id}`; same for sonarr); test connection (`/settings/radarr/test`) and load profiles, root folders, tags, language profiles after a successful test; default-server and 4K rules.

### 10.5 `feature/settings/network`

- [ ] `/settings/network`: proxy configuration, DNS cache, IP forwarding / trusted proxies.

### 10.6 `feature/settings/metadata`

- [ ] `/settings/metadatas` and `/settings/metadatas/test`: provider selection per media type with `MetadataSelector`.

### 10.7 `feature/settings/notifications/<agent>` (folders within Settings, 10 agents)

- [ ] Shared agent form scaffold: enabled toggle, notification-type matrix, test button calling `/settings/notifications/<agent>/test`.
- [ ] email, discord, gotify, ntfy, pushbullet, pushover (+ sound picker from `/settings/notifications/pushover/sounds`), slack, telegram, webpush (VAPID display only; no client subscription in v1), webhook.
- [ ] Webhook JSON template editor (`JSONEditor`): monospaced, template-variable insertion palette, server-side validation through the test endpoint (PRD §18 risk 9).

### 10.8 `feature/settings/logs`

- [ ] `/settings/logs`: filter by level, search, paging, copy line / copy visible, auto-refresh toggle.

### 10.9 `feature/settings/jobs` and `feature/settings/cache`

- [ ] Jobs (`/settings/jobs`, `/settings/jobs/{id}/{run|cancel|schedule}`): run, cancel, reschedule with a cron editor and presets.
- [ ] Cache (`/settings/cache`, `/settings/cache/{id}/flush`): per-cache stats and flush; DNS cache entries.

### 10.10 `feature/settings/about`

- [ ] `/settings/about`, `/status`, `/status/appdata`: version, commit tag, update availability, restart-required banner, data directory, appdata warning.
- [ ] Diagnostics sub-screen: recorded deprecation headers per endpoint, compat gates in effect, egress log (debug builds), redacted export.

### 10.11 `feature/settings/discover`

- [ ] Slider management (`/settings/discover`, `/settings/discover/add`, `PUT /settings/discover/{id}`, `DELETE`, `/settings/discover/reset`): add custom sliders of every `TMDB_*` type, drag reorder (platform-adapted), enable/disable, reset to defaults.

---

## Phase 11 — Cross-cutting quality

**Goal.** The product commitments in PRD §5.11, §9, §10 and §16 are met and enforced by CI, and the contract is tested against a real server.

**Exit criteria.** All CI lanes green including `contract`; performance table in PRD §9 met on the reference devices; egress test passes; translations validated; every deep link resolves.

### 11.1 Deep links and app links

- [ ] Android: `gauja://` scheme plus verified App Links for user-configured Seerr hosts (per-profile `assetlinks.json` is not possible for arbitrary hosts, so document the limitation and support "open in Gauja" via the share sheet).
- [ ] iOS: `gauja://` scheme plus Universal Links where the operator can host `apple-app-site-association`; same documented fallback.
- [ ] Password-reset deep link completes `/auth/reset-password/{guid}`.

### 11.2 Adaptive layouts and accessibility

- [ ] Audit every screen at compact, medium and expanded widths and in tabletop posture; settings and requests use list-detail on expanded widths.
- [ ] Dynamic Type / font scaling to the largest accessibility size without clipping; TalkBack / VoiceOver labels on every content component; reduced-motion honoured; minimum touch targets.

### 11.3 Localization

- [ ] With real catalogs, add catalog validation and Crowdin configuration as needed. Check syntax, duplicate keys and shared semantic coverage, allowing platform-specific keys/strings. Add only the tooling needed by those catalogs; use native validation where it covers the behavior. Do not enforce identical key sets across independent native apps.
- [ ] *(can start early: after §3.3)* `tools/community/seed-from-seerr.py`: one-time import of Seerr's `src/i18n/locale/*.json` (41 locales, underscore variants) for strings identical in meaning (status names, permission labels, settings section titles), normalising locale codes; attribution in `docs/THIRD_PARTY.md`.
- [ ] Server-provided content honours the user's Seerr locale; UI honours the device locale.

### 11.4 Performance and memory

- [ ] Macrobenchmark (Android) and XCTest metrics (iOS) for cold start to interactive Discover, cached and uncached; jank over a 30-second scroll; offline render time. Thresholds from PRD §9 as CI assertions on the emulator/simulator lanes with a documented tolerance.
- [ ] Baseline profile validation in `android.yml`; install-size gates (≤ 15 MB per-ABI Android, ≤ 20 MB thinned iOS).
- [ ] Leak check: 50 navigation cycles with LeakCanary (debug) / Xcode memory graph assertions; no retained-heap growth.
- [ ] Image pipeline decision for iOS recorded with its owning implementation (own loader vs. Nuke) after measurement; do not recreate a parallel technical specification.

### 11.5 Privacy and security enforcement

- [ ] `tools/ci/egress-test.sh` real on both smoke lanes: fail on any host other than the test server (and `plex.tv` during the Plex sign-in test).
- [ ] Verify the secret-logging guard against every `SecretStore` symbol; verify diagnostics export redaction with a fixture containing every secret type.
- [ ] Android manifest permission audit: `INTERNET` only (`POST_NOTIFICATIONS` reserved, not requested). iOS: no privacy-manifest-required APIs beyond networking; `PrivacyInfo.xcprivacy` declares no tracking.
- [ ] SBOM generation (CycloneDX) for both apps in `release.yml`; `deny.toml` check green.

### 11.6 Contract tests (`contract.yml`)

- [ ] *(can start early: after §2.2; run with the first consuming app flow)* Boot upstream Seerr from its `Dockerfile` (SQLite default; no external services), initialise via `/settings/initialize` and the web-UI-equivalent API calls, seed users/media/requests/issues fixtures, obtain the API key for seeding only.
- [ ] *(can start early: after §2.2; depends on the boot task above)* Record request/response fixtures into `api/fixtures/<seerr-version>/` for callable operations tracked in `api/coverage.json`, starting with auth/status and source-backed overlays used by the initial flow; extend with features and complete coverage here; run `tools/ci/check-fixture-secrets.sh` over the output.
- [ ] Execute both generated clients against the container; assert schema conformance (the server itself validates requests with `express-openapi-validator`, so a rejected request is a client bug or an overlay candidate).
- [ ] Fail when any endpoint Gauja calls returns a `Sunset` inside the next 90 days.
- [ ] Weekly schedule plus `api/` and `tools/codegen/` path triggers.

### 11.7 Upstream drift (`api-sync.yml`)

- [ ] *(can start early: after §2.1)* `tools/api-drift/diff-upstream.sh`: discover upstream changes (including develop) against the pin. Propose baseline upgrades only to a deliberately selected stable release, with verbatim spec, updated `UPSTREAM_COMMIT`, coverage, regenerated clients and overlays to re-verify. Discovery alone never upgrades support; humans review and nothing merges automatically.

---

## Phase 12 — Release

**Goal.** Reproducible, signed, store-ready builds for every channel in PRD §15.4, with automated release notes.

**Exit criteria.** A tag on each app produces artifacts that F-Droid can verify, an SBOM, generated screenshots, and a changelog section; TestFlight and Play internal track uploads succeed from CI.

- [ ] Independent semver tags per app (`android/vX.Y.Z`, `ios/vX.Y.Z`); `release.yml` dispatches on tag prefix.
- [ ] Reproducible Android release build (fixed timestamps, deterministic zip, per-ABI splits); F-Droid metadata in `fastlane/metadata/android/` and a `metadata/<applicationId>.yml` template for the F-Droid data repo.
- [ ] Signing: Android upload key and iOS certificates in CI secrets; fastlane lanes for Play internal track and TestFlight.
- [ ] *(depends on §11.4 smoke lanes for screenshots)* Store listings: unaffiliated-with-Seerr statement, "Seerr" used only descriptively, none of Seerr's marks or artwork; screenshots generated from the emulator/simulator smoke lanes so they track the UI.
- [ ] *(can start early: after §1.5)* `CHANGELOG.md` generated from Conventional Commits, sectioned per app; supported Seerr range and `api/compat.json` updated in every release.
- [ ] GitHub Releases: signed APKs and the iOS source archive; SBOM attached.
- [ ] Package and verify complete applicable notices for distributed material using `docs/THIRD_PARTY.md`: Seerr descriptions/spec, Tailwind-derived palette and resolved runtime dependencies. Check source archives and each binary’s bundled notices independently. REUSE lint does not replace dependency resolution or notice verification; preserve AGPL and the App Store permission.
- [ ] Post-release: README supported-range update, record in PRD Appendix C any deviation discovered during store review (PRD §18 risk 4).

---

## Appendix A — Traceability (PRD → phase)

| PRD section | Phase(s) |
|---|---|
| §4 Compatibility policy (vendored contract, gating, deprecation, drift, contract tests) | 2, 4.3, 4.7, 10.10, 11.6, 11.7 |
| §5.1 Authentication and servers | 4.3, 4.6, 5 |
| §5.2 Discover | 6.1, 10.11 |
| §5.3 Search | 6.2 |
| §5.4 Media details | 7.1 |
| §5.5 Requests | 7.2 |
| §5.6 Issues | 7.3 |
| §5.7 Watchlist | 7.4 |
| §5.8 Profile and user settings | 8 |
| §5.9 Admin: users | 9 |
| §5.10 Admin: server settings | 10 |
| §5.11 Cross-cutting (permissions, deep links, offline, a11y, l10n) | 4.1, 4.4, 5.2, 11.1–11.3 |
| §6 Authentication and server model | 4.3, 4.6, 5.1 |
| §7 Notifications deferred | 8.2, 10.7 |
| §8 UX principles (IA, tokens, content components, adaptive) | 2.3, 2.4, 4.8, 4.9, 11.2 |
| §9 Performance targets | 3.2 (baseline profile), 11.4 |
| §10 Privacy and security | 1.1, 1.2, 4.3, 4.6, 11.5 |
| §11 Tech stack | 3 |
| §12 Codebase doctrine | 1.4, 3 |
| §13 Monorepo layout | 3.1 |
| §14 Quality gates | 1, 3.2, 3.3, 11.6 |
| §15 Licensing and distribution | 1.3, 12 |
| §16 Localization | 11.3 |
| §17 Release and versioning | 12 |
| §18 Risks | 2.4 (risk 1, 5), 2.1/11.6 (risk 2), 1.3 (risk 3), 12 (risk 4), 4.3/11.4 (risk 7), 4.3/11.6 (risk 8), 10.7 (risk 9), 8.2 (risk 10) |

## Appendix B — Explicitly out of scope for v1

Carried from PRD §2.2 and §7 so that no phase quietly grows to include them:

- Push notifications, background polling, UnifiedPush, APNs relay, any `push` module (PRD §7).
- Kotlin Multiplatform or any shared runtime code between the apps.
- Web views of any kind; media playback; the Seerr first-run setup wizard (`/settings/initialize`).
- Telemetry, analytics, crash-reporting SDKs, Google Play Services, Firebase.
- Any hosted infrastructure operated by the project.
