<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Gauja — Product Requirements Document

| Field | Value |
|---|---|
| Status | Draft v0.1 |
| Date | 2026-09-05 |
| Product | Gauja — native Android and iOS companion apps for Seerr |
| License | AGPL-3.0-or-later, with the App Store Distribution Exception (Appendix A) |
| Companion documents | `docs/gauja-implementation-plan.md` (order), `design/screens/` (screen inventory); decisions are logged in Appendix C |
| Normative coding guidelines | `.agents/rules/kotlin-2_4-android-app.md`, `.agents/rules/swift-6_3-ios-app.md` (see §12.1) |

This document owns **what** Gauja is and **why** its product decisions were made. Each fact has one authoritative owner; other documents link to it. The [documentation ownership rules](../.agents/rules/monorepo.md#documentation-ownership) define the boundaries. Keep executable details in configuration, work sequencing in the implementation plan, and unique behavior in feature contracts.

The name: Gauja was a VEF portable transistor radio built in Riga in the early 1960s, the predecessor of the Spidola. It is one word, pronounced *GOW-ya*.

---

## 1. Summary

Gauja is an unofficial, fully native companion to [Seerr](https://github.com/seerr-team/seerr) — one Android app written in Kotlin and one iOS app written in Swift, developed together in a single repository. It lets users browse, search, and request media, and lets administrators manage requests, users, and the complete server configuration, all through native UI that follows Seerr's information architecture and visual language.

Gauja depends on nothing but the user's own Seerr server. It ships no telemetry, analytics or crash-reporting SDKs, no project-hosted services, and no web views. It is not affiliated with the Seerr project and requires no changes upstream.

---

## 2. Goals and non-goals

### 2.1 Goals

1. **Full native coverage** of the Seerr API surface: user features, admin operations, and server configuration (§5).
2. **Two first-class native apps** that share an information architecture, design tokens, and content-component vocabulary, while each respects its platform's conventions (§8).
3. **Fast and light**: measurable performance budgets (§9), small binaries, offline-tolerant reads.
4. **Private by construction**: the only network peer is the user's Seerr server, plus the image source that server is configured to use (§10).
5. **A codebase that is a pleasure to contribute to**: cohesive files and justified modules, folders that mirror the domain, mechanically enforced boundaries (§12).
6. **Maintainable against a moving upstream**: a pinned, vendored API contract with automated drift detection and per-version feature gating (§4).

### 2.2 Non-goals

- **No Kotlin Multiplatform, no shared runtime code.** Android is pure Kotlin; iOS is pure Swift. Only the *contract* (API spec, design tokens, screen specs, fixtures) is shared.
- **No upstream dependency.** Gauja never requires a PR to Seerr, a Seerr plugin, or a Seerr feature flag.
- **No hosted infrastructure** operated by the project in v1. (Push notifications are deferred for exactly this reason — §7.)
- **No web views.** If a Seerr feature cannot be rendered natively, it is either built natively or explicitly listed as unsupported; it is never embedded.
- **No telemetry, analytics, or crash-reporting SDKs**, opt-in or otherwise.
- **No Google Play Services or Firebase dependencies.** One Android build, F-Droid eligible.
- **No media playback.** Gauja requests media; it does not play it.
- **Not a Seerr setup wizard.** `/settings/initialize` and first-run server setup remain the web UI's job; Gauja targets an already-initialized server.

---

## 3. Users

| Persona | Needs | Typical device use |
|---|---|---|
| **Requester** | Discover, search, view details, request movies and TV (per-season), track request status, manage a watchlist, report and follow issues, edit own profile and notification preferences. | Phone, short sessions, often away from home. |
| **Admin** | Everything above, plus: approve/decline/retry requests, manage users (permissions, quotas, linked accounts), triage issues, and change any server setting Seerr's web UI exposes. | Phone for triage; tablet or foldable for configuration. |
| **Instance operator** | Same as Admin, but also cares about: self-signed certificates, reverse proxies with basic auth, multiple servers (home and a friend's), API-key sign-in for automation accounts, and being told when the server version is outside the app's support window. | Any. |

All three personas are assumed to be capable adults running self-hosted software. The app explains technical states plainly rather than hiding them.

---

## 4. Compatibility policy

The OpenAPI document’s `info.version` is not the Seerr application version. The supported stable baseline is recorded once in [api/README.md](../api/README.md), pinned by `api/UPSTREAM_COMMIT`; derive path/operation counts from the effective contract. The hand-maintained specification may drift from server behavior.

### 4.1 The vendored contract

- `api/seerr-api.yml` is a verbatim copy of upstream's `seerr-api.yml` (OpenAPI 3.0.2).
- `api/UPSTREAM_COMMIT` records the exact upstream commit the copy was taken from. The two files change together or not at all; a pre-commit hook and a CI check enforce this (§14).
- `LICENSES/MIT.txt` carries Seerr's MIT license text, which covers the vendored specification.
- `api/overlays/` holds OpenAPI overlay documents that correct upstream spec defects (missing `required`, wrong types) without editing the vendored file. Every overlay entry cites the upstream issue or the observed server behaviour that justifies it.

### 4.2 Version gating

- On connect and on every foreground, Gauja calls `/status` and records `version`, `commitTag`, `updateAvailable` and `restartRequired` for the active server profile.
- `api/compat.json` maps feature identifiers to minimum (and, where needed, maximum) server versions. Features outside the window are hidden or disabled with an inline explanation, never crashed.
- Each Gauja release states a **supported Seerr range** in its release notes. The floor is the version whose spec is vendored; the ceiling is "latest known at release" with a soft warning beyond it.

### 4.3 Deprecation signals

Seerr emits RFC 8594 headers (`Deprecation`, `Sunset`, `Link rel="successor-version"`) on deprecated routes. Gauja's network layer records these per endpoint and surfaces them in the About → Diagnostics screen and in debug logs. The planned Phase 11 contract job fails when a vendored endpoint Gauja calls is marked deprecated with a `Sunset` date inside the next 90 days.

### 4.4 Upstream drift detection

Planned in Phase 11: scheduled upstream discovery compares changes, including develop, with the pinned contract. A baseline upgrade selects a stable release and proposes its verbatim spec, pin, coverage and regenerated clients together. Discovery does not automatically change the supported baseline. Humans review; nothing merges automatically.

### 4.5 Contract tests

Run a real Seerr container (upstream `Dockerfile`, SQLite) with the first consuming app flow, then extend recorded request/response contract tests as features land. Phase 11 verifies coverage of every endpoint Gauja uses. Recorded fixtures live in `api/fixtures/<seerr-version>/` and are scanned for credentials on every commit (§14).

---

## 5. Feature inventory

Organised by API tag family. **Parity** is required unless a cell says otherwise; "platform-adapted" means the same information and actions with platform-native chrome. Every row here maps to one or more screen specs in `design/screens/`.

### 5.1 Authentication and servers (`/auth`, `/status`)

| Feature | Endpoints | Notes |
|---|---|---|
| Add server by URL | `/status`, `/settings/public` | Validates reachability and version; reads public settings to learn which sign-in methods are enabled. |
| Plex sign-in | `/auth/plex` | PIN flow against plex.tv in a system browser tab; the token is exchanged with Seerr. |
| Jellyfin / Emby sign-in | `/auth/jellyfin` | Username + password. |
| Jellyfin Quick Connect | `/auth/jellyfin/quickconnect/*` | Code display, polling, authenticate. |
| Local sign-in | `/auth/local` | Email + password. |
| API-key sign-in | `X-Api-Key` header | For automation/service accounts; key stored in Keystore/Keychain. |
| Password reset | `/auth/reset-password`, `/auth/reset-password/{guid}` | Request + deep-link completion. |
| Session | `/auth/me`, `/auth/logout` | Per-server cookie jar (`connect.sid`). |
| Multiple servers | — | Named profiles, one active at a time, quick switcher. |
| TLS trust | — | Per-profile: system trust (default), or pinned self-signed certificate fingerprint accepted after explicit user confirmation. |
| Reverse-proxy basic auth | — | Optional per-profile credentials sent as `Authorization: Basic`. |

### 5.2 Discover (`/discover`, `/genres`, `/studio`, `/network`, `/keyword`, `/watchproviders`, `/regions`, `/languages`, `/certifications`, `/backdrops`)

| Feature | Notes |
|---|---|
| Home sliders | Server-defined slider set from `/settings/discover`; each slider maps to its `/discover/*` endpoint. Order and visibility follow the server. |
| Trending, popular movies/TV, upcoming movies/TV | Paginated grids with infinite scroll. |
| Genre, studio, network, keyword, language browse | `/discover/movies/genre/{id}` etc., with the genre slider endpoints for the entry cards. |
| Watch-provider filter | `/watchproviders/*` and `/regions`, persisted per profile. |
| Plex/Jellyfin watchlist slider | `/discover/watchlist` when the server exposes it. |
| Admin: slider management | Add, reorder, enable/disable, reset (`/settings/discover/*`). Platform-adapted (drag reorder). |

### 5.3 Search (`/search`, `/search/company`, `/search/keyword`)

Debounced multi-search across movies, TV and people, with company and keyword lookups used by the discover filters. Results page uses the same content cards as discover.

### 5.4 Media details (`/movie`, `/tv`, `/person`, `/collection`, `/media`)

| Feature | Notes |
|---|---|
| Movie, TV, season, person, collection pages | Full metadata, cast/crew, ratings (`/ratings`, `/ratingscombined`), recommendations, similar, external links, trailers (link-out). |
| Availability and request state | Derived from the embedded `mediaInfo`; season-level for TV. |
| Watch data | `/media/{id}/watch_data` (Tautulli) shown when the server has it. |
| Admin: media management | Mark available / partially available / unknown, delete media, view file paths (`/media/{id}/*`). |
| Blocklist / blacklist | Add/remove titles and collections (`/blocklist/*`); blocklisted-tag badges. The sunset `/blacklist` alias is excluded at the supported baseline. |

### 5.5 Requests (`/request`, `/service`, `/overrideRule`)

| Feature | Notes |
|---|---|
| Create request | Movie; TV with season picker; 4K variant when enabled; advanced options (server, profile, root folder, tags, language profile) when the user has permission, populated from `/service/radarr|sonarr/*`. |
| My requests | List with filters (pending, approved, available, declined, failed) and sort. |
| Request detail | Status timeline, requester, modified-by, downloads. |
| Admin: manage requests | Approve, decline, retry, edit, delete; bulk actions from the list. |
| Admin: override rules | CRUD on `/overrideRule/*`. |
| Counts | `/request/count` for badges. |

### 5.6 Issues (`/issue`, `/issueComment`)

Create issue on media (type, season/episode, message); list and filter; detail with comment thread; comment, edit, delete own comment; admin: resolve, reopen, delete. `/issue/count` for badges.

### 5.7 Watchlist (`/watchlist`)

Add/remove, list, sync status with linked Plex/Jellyfin watchlist where the server supports it.

### 5.8 Profile and user settings (`/user/{id}/*`, `/auth/me`)

Own profile with request history and quotas; general settings (display name, locale, region, original language, discover region); password change; linked accounts (Plex, Jellyfin incl. Quick Connect); **notification preferences** (`/user/{id}/settings/notifications`) — types per enabled agent, Discord/Telegram/Pushover/Pushbullet identifiers. See §7 for what is *not* included.

### 5.9 Admin: users (`/user`, `/user/{id}/permissions`, `/user/{id}/quota`, `/user/import-from-*`)

User list with search and sort; create local user; edit any user's settings, permissions (full Seerr permission matrix), quotas; import users from Plex/Jellyfin; delete. Bulk permission edit.

### 5.10 Admin: server settings (`/settings/*`) — full native coverage

| Seerr section | Endpoints | Gauja screens |
|---|---|---|
| General | `/settings/main`, `/settings/main/regenerate`, `/settings/public` | Application title/URL, API key (view, regenerate), locale, region, language, hide-available, CSRF, cache images, trust proxy, etc. |
| Users | (see §5.9) plus default permissions, default quotas, local login, new Plex login toggles in `/settings/main` | |
| Plex | `/settings/plex`, `/settings/plex/devices/servers`, `/settings/plex/library*`, `/settings/plex/sync`, `/settings/plex/users` | Server picker from devices, manual host, libraries with sync, full/recent scan controls, Tautulli (`/settings/tautulli`). |
| Jellyfin / Emby | `/settings/jellyfin*` | Host, external URL, libraries, sync, users. |
| Services | `/settings/radarr*`, `/settings/sonarr*` | List, add/edit/delete, test connection, profiles/root folders/tags loaded after test. Default-server rules. |
| Metadata | `/settings/metadatas`, `/settings/metadatas/test` | Provider selection and test. |
| Network | `/settings/network` | Proxy configuration, DNS cache, IP forwarding. |
| Notifications | `/settings/notifications/*` (every agent, incl. `test`) | Native forms for email, Discord, Pushbullet, Pushover, Gotify, ntfy, Slack, Telegram, web push, webhook. Webhook JSON template gets a monospaced editor with template-variable insertion. |
| Discover | `/settings/discover*` | (see §5.2) |
| Jobs & Cache | `/settings/jobs*`, `/settings/cache*` | Run, cancel, reschedule (cron editor with presets); flush caches and DNS entries. |
| Logs | `/settings/logs` | Filterable, searchable, copyable log viewer. |
| About | `/settings/about`, `/status`, `/status/appdata` | Version, commit, update availability, restart-required banner, data directory, appdata warning. |

Settings screens are **adaptive**: a list/detail layout on tablets and foldables, a stacked navigation on phones.

### 5.11 Cross-cutting

- Permission-aware UI: every action is shown only when `/auth/me` permissions allow it, mirroring Seerr's `lib/permissions`.
- Deep links: `gauja://server/<profileId>/movie/<tmdbId>` etc., plus Seerr web URLs (`https://<host>/movie/<id>`) registered as app links / universal links.
- Offline: last successful discover, requests, watchlist and profile responses are cached locally and shown with a staleness indicator; all writes require connectivity and fail visibly.
- Accessibility: Dynamic Type / font scaling, TalkBack / VoiceOver labels on all content cards, reduced-motion respected.
- Localization: see §16.

---

## 6. Authentication and server model

- A **server profile** is: display name, base URL, TLS trust mode (system | pinned fingerprint), optional basic-auth credentials, auth method (cookie session | API key), and the cached `/status` and `/settings/public` snapshots.
- Secrets (session cookie, API key, basic-auth password, Plex token) live only in Android Keystore-backed encrypted storage and iOS Keychain. They never appear in logs, fixtures, crash output or exported diagnostics (§10, §14).
- Each profile owns an isolated cookie jar. Switching profiles never mixes sessions.
- Plex sign-in opens plex.tv in a system browser tab (Custom Tabs / `ASWebAuthenticationSession`), never an embedded web view.
- The app detects `/status.restartRequired` and `/status/appdata` and shows the same banners Seerr's web UI does.

---

## 7. Notifications — deferred

**Decision:** push notifications are out of scope for v1. The design that was investigated is recorded at the end of this section so that the eventual implementation starts from a decided architecture.

What v1 ships:

- A greyed-out **Notifications** entry in Gauja's app settings labelled "Coming later", with a one-line explanation, repeated in the About screen.
- The per-user **notification preferences** screen (§5.8), because it is an ordinary Seerr settings endpoint and works today for every agent the admin has enabled.
- The admin **notification agent** configuration screens (§5.10), for the same reason.

What v1 deliberately does not ship: any background polling worker, any push subscription, any relay, any `push` module. That is a v2 conversation.

**Investigated design (the starting point for v2).** Seerr's per-user, multi-device channel is **web push** (`/user/{id}/settings/notifications` with the `webpush` agent and `/user/{id}/pushSubscription`): VAPID-signed RFC 8030 requests to a push-service endpoint, payload encrypted end-to-end with RFC 8291 (aes128gcm, RFC 8188). Gauja would register a push subscription per profile exactly as the web UI does, so the server needs no change.

- **Android without Google:** [UnifiedPush](https://unifiedpush.org). The app registers with a user-chosen distributor (ntfy, NextPush, …), receives an endpoint URL and hands it to Seerr as the subscription endpoint. The distributor relays opaque bytes; the app decrypts with the RFC 8291 keys it generated (an ECDH P-256 keypair and auth secret per subscription, stored in `SecretStore`). No Play Services, no Firebase, no project-run server.
- **iOS:** the only wake-up path is APNs, which Seerr cannot address directly. It needs a project-operated **relay**: an HTTPS endpoint that accepts web push ciphertext and forwards it to APNs as a Notification Service Extension payload; the extension decrypts on device. The relay never sees plaintext, but it is project-hosted infrastructure with availability, abuse and privacy obligations, and the one component the AGPL network clause would govern.
- **Encryption** holds end-to-end on every tier: the server encrypts to the device's public key; distributors and the relay forward ciphertext only.
- **Background execution:** Android decrypts and posts the notification in a short worker triggered by the UnifiedPush broadcast; the iOS service extension runs for the notification only. No background polling anywhere.

What blocks v1: the iOS path requires project-hosted infrastructure (§2.2), UnifiedPush needs a distributor-chooser UX and a fallback story, and both need a privacy-statement update. None of it changes Seerr.

---

## 8. UX principles

Keep the sized [screen inventory](../design/screens/INVENTORY.md) and [shared component baseline](../design/screens/components/INVENTORY.md#shared-behavior-baseline). Write detailed specifications with each feature; document only applicable states and acceptance criteria. The full planned native administration scope remains unchanged.

**Same information architecture, same tokens, same content components. Platform-native chrome.**

- **Information architecture** follows Seerr's web UI: Discover, Requests, Issues, Users (admin), Settings (admin), Profile, Search. Screen names, groupings and the order of settings sections match Seerr so that anyone who knows the web UI knows Gauja.
- **Design tokens** live in `design/tokens.json` — the single source of truth for colour, spacing, radii, elevation, typography scale and motion durations. Both platform themes are *generated* from it (`tools/tokens/`); hand-editing a generated theme is a CI failure. The initial token set is derived from Seerr's Tailwind configuration: gray-900/800/700 surfaces, indigo-600 accent, the indigo-400→purple-400 gradient for hero text, Seerr's badge colour semantics for request status.
- **Content components** are shared by name and behaviour, not by code: `TitleCard`, `MediaSlider`, `RequestCard`, `IssueBlock`, `StatusBadge`, `PersonCard`, `DownloadBlock`, `AirDateBadge`, and so on, mirroring Seerr's `src/components/` vocabulary. Each has a behavior contract, which may be a section in the shared component inventory; each platform implements it idiomatically.
- **Chrome is native.** Navigation bars, tabs, sheets, dialogs, pull-to-refresh, haptics, context menus, and system back behave as Material 3 (Adaptive) on Android and as iOS 18 SwiftUI on iOS. Gauja does not imitate one platform on the other.
- **Dark first.** Seerr is dark; Gauja's default theme is dark with a light theme generated from the same tokens. Follow-system is available.
- **Adaptive layouts** are a requirement, not a stretch goal: list/detail for settings and requests on tablets and foldables, multi-column discover grids, Material 3 Adaptive on Android and `NavigationSplitView` on iOS.

Behavior contracts in `design/screens/` keep the two apps aligned. A contract may be a section in an existing document; create a separate file only for substantial unique behavior. Keep stable inventory identities and observable acceptance criteria, and describe states, actions and permissions only when applicable. Validators check identities, links and acceptance presence; review checks behavioral completeness.

---

## 9. Performance and efficiency targets

Targets are measured on a mid-range reference device (Android: Pixel 8a or equivalent; iOS: iPhone 13) in release builds, with a warm server on a LAN.

| Metric | Target |
|---|---|
| Cold start to interactive Discover (cached) | ≤ 800 ms Android, ≤ 600 ms iOS |
| Cold start to interactive Discover (uncached, network) | ≤ 1.5 s |
| Scroll jank on Discover and Requests lists | < 1 % janky frames over a 30 s scroll |
| Image loading | Progressive; memory cache ≤ 64 MB, disk cache ≤ 256 MB, configurable; images requested through Seerr's `/imageproxy` when the server has image caching enabled, otherwise from the TMDB CDN at the size the layout needs |
| Install size | ≤ 15 MB Android (per-ABI split), ≤ 20 MB iOS (thinned) |
| Battery | No background work in v1 (§7); foreground network only |
| Memory | No retained-heap growth across 50 navigation cycles (leak check in CI) |
| Offline reads | Cached discover/requests/watchlist/profile render in ≤ 300 ms with no network |

The targets above are product commitments. Record measured implementation decisions with the owning feature or package; do not create a parallel technical specification.

---

## 10. Privacy and security

- **Network peers**: the active Seerr server, plex.tv during Plex sign-in only, and the image CDN the server is configured to use. Nothing else, ever. CI includes a network-egress test that fails on any other host.
- **No identifiers**: no advertising ID, no device fingerprinting, no analytics.
- **Secrets**: Android Keystore (encrypted DataStore) and iOS Keychain only. A secret-logging guard hook (§14) rejects code that formats a secret into a log call. Diagnostics export redacts hosts, cookies and keys.
- **TLS**: system trust by default; pinned self-signed fingerprints require explicit per-profile confirmation showing the fingerprint. Plain HTTP is allowed (LAN use) with a persistent warning.
- **Permissions**: Android — `INTERNET` only (plus `POST_NOTIFICATIONS` reserved for v2). iOS — none beyond network.
- **Local data**: server profiles and caches are removable per profile and in bulk; deleting a profile wipes its cookie jar, keys and cache.
- **Supply chain**: dependency versions are pinned; `gitleaks` runs in CI; resolved-dependency enforcement against the license allow-list lands with real app manifests; the SBOM is published with each release.

---

## 11. Tech stack

### 11.1 Shared contract (`api/`, `design/`)

- OpenAPI 3.0.2 specification vendored from upstream (§4).
- `design/tokens.json` in the W3C Design Tokens format.
- Screen specifications in Markdown.
- Recorded response fixtures per Seerr version.

### 11.2 Android (`apps/android/`)

Governed by `.agents/rules/kotlin-2_4-android-app.md`.

| Concern | Choice |
|---|---|
| Language / build | Kotlin 2.4 (K2), AGP 9.x with built-in Kotlin, KSP2, Gradle version catalog |
| SDK | minSdk 30 (Android 11; fixed by the guideline — build behind availability checks, do not raise), targetSdk/compileSdk 37 (per the Kotlin rule file) |
| UI | Jetpack Compose (BOM-governed), Material 3, Material 3 Adaptive |
| Navigation | Navigation 3, single Activity, owned back stack |
| DI | Hilt via KSP |
| Networking | OkHttp 5 + Retrofit 3 with kotlinx-serialization; generated client from openapi-generator (`kotlin` generator, `jvm-retrofit2` library) isolated in `core/api` |
| Persistence | Room (KSP) for caches; DataStore (Preferences + encrypted Proto) for settings and profiles |
| Images | Coil 3 with the OkHttp network fetcher |
| Concurrency | Coroutines + Flow, structured; no `GlobalScope` |
| Testing | JUnit, Turbine, Compose UI tests, Robolectric for units, an emulator smoke lane |
| Quality | ktfmt, detekt (with the Compose ruleset), Android Lint, baseline profiles |

One build flavour. No Google Play Services, no Firebase.

### 11.3 iOS (`apps/ios/`)

Governed by `.agents/rules/swift-6_3-ios-app.md`.

| Concern | Choice |
|---|---|
| Language / build | Swift 6.3, Swift 6 language mode, strict concurrency, `SWIFT_DEFAULT_ACTOR_ISOLATION = MainActor`; Xcode project generated by XcodeGen from `project.yml`, never committed |
| Target | iOS 18.0+ |
| UI | SwiftUI, `NavigationStack` / `NavigationSplitView` |
| State | Observation (`@Observable`), not `ObservableObject` |
| DI | `swift-dependencies` |
| Networking | `URLSession`; generated client from `swift-openapi-generator` (pinned executable; committed output) isolated in the `SeerrAPI` package |
| Persistence | SwiftData for caches; Keychain for secrets; `UserDefaults` via a typed wrapper for non-secret settings |
| Images | Own `URLSession` pipeline with ImageIO downsampling, `NSCache` (64 MiB) and a profile-isolated disk cache (256 MiB); measured decision recorded in §18 risk 7 |
| Testing | Swift Testing; XCUITest smoke lane on a simulator |
| Quality | swift-format (toolchain), SwiftLint strict |

### 11.4 Tooling (`tools/`, root)

prek (pre-commit), gitleaks, conventional commits, DCO, REUSE/SPDX, Crowdin, openapi-generator and swift-openapi-generator invoked via `tools/codegen/`, token generation via `tools/tokens/`, API drift check via `tools/api-drift/`.

---

## 12. Codebase doctrine

This section is normative. It reads as rules because it is enforced as rules: by lint configuration, by module dependency constraints, and by code review.

### 12.1 Rule files

Normative coding guidelines live in `.agents/rules/` and **must be followed** for their respective platforms. They are authoritative over this document on matters of language, framework and library usage; where they are silent, this section applies.

| File | Scope |
|---|---|
| `.agents/rules/kotlin-2_4-android-app.md` | All Kotlin under `apps/android/` |
| `.agents/rules/swift-6_3-ios-app.md` | All Swift under `apps/ios/` |
| `.agents/rules/modularity.md` | Module boundaries, dependency direction, file-organisation rules (this section, expanded) — both platforms |
| `.agents/rules/api-contract.md` | How generated clients are produced, isolated and wrapped; how overlays and fixtures are maintained |
| `.agents/rules/monorepo.md` | Directory ownership, CI lanes, what may and may not cross the `apps/` boundary |

The three project-level files are written during Phase 1 of the implementation plan (the plan's numbering; this document's earlier "Phase 0") and are treated as living documents; a change to a rule file is a PR with a rationale, reviewed like code.

### 12.2 Cohesive responsibilities

Follow [modularity.md](../.agents/rules/modularity.md) for file cohesion, generated-code isolation
and the allowed dependency graph. Related types may share files. Create modules only for an
enforced boundary or demonstrated build/ownership benefit; folders are the default within a
boundary. Generated output follows supported generator structure and is exempt from advisory
size limits. Do not manually split it.

### 12.3 Module boundaries and dependency direction

Preserve API → Data → domain exposure and feature isolation. The complete allowed dependency
tables and enforcement responsibilities live in [modularity.md](../.agents/rules/modularity.md#dependency-direction).
The API import guard is active; graph checks arrive with working modules.

### 12.4 Android responsibilities

The implementation plan §3.2 introduces the working Android modules incrementally. Begin with
the modules needed by the first server/profile/auth flow, one Data module, and folders for
aggregates. Settings begins as one feature module when consumed. No catalog of future modules
must be created to complete an earlier phase.

### 12.5 iOS responsibilities

The implementation plan §3.3 introduces the working iOS targets incrementally. Begin with the
same responsibility boundaries as Android, using folders inside Data and Settings. Native
platform behavior and independent builds remain requirements; package count is not a target.

---

## 13. Monorepo layout

[Monorepo rules](../.agents/rules/monorepo.md#layout-and-ownership) own directory responsibilities,
platform separation and workflow ownership. Add directories and tooling with their first real
consumer. Shared inputs are the API/design contracts and tooling; no shared runtime code or
cross-platform source references are permitted.

---

## 14. Quality gates

Two layers: fast local hooks via prek, and authoritative CI. The local layer mirrors CI's format/lint failures so they surface before a push; heavier gates (full compilation, emulator/simulator smoke, REUSE lint, contract tests) run only in CI.

### 14.1 Local hooks

[prek.toml](../prek.toml) owns commands, filters and exclusions. Hooks enforce file hygiene, commit conventions/sign-off, secret protection, contract/coverage consistency, theme drift, screen consistency and generated API boundaries. Handwritten platform formatting/lint hooks become applicable with app sources. Complexity and length remain advisory.

Translation validation is planned with real catalogs; resolved-dependency license enforcement is planned with real manifests. Neither is an active hook today. The secret-logging scanner is a heuristic supplemented by review and runtime redaction tests.

### 14.2 CI lanes

Configurations own triggers and commands. [CI ownership](../.agents/rules/monorepo.md#ci-ownership-githubworkflows) assigns each assertion one owner:

- `pr-hygiene` enforces repository hygiene, REUSE, history secret scanning, commit/DCO checks, tooling tests and platform separation. Its prek job owns screen/API import checks.
- `codegen-check` validates contract pairing/coverage and upstream bytes, plus independent client generation and synthetic compile/serialization/redaction checks.
- `tokens-check` validates theme drift and typechecks iOS primitives.

Phase 3 adds independent Android/iOS app builds, lint, graph and UI smoke checks. Real-server checks grow with consuming flows; Phase 11 completes contract coverage and adds performance/egress audits and weekly upstream discovery; Phase 12 adds release builds, SBOM, bundled notices and store upload. Add each lane with meaningful assertions; do not create passing placeholders. Transfer an existing assertion when its replacement becomes authoritative rather than running duplicate CI steps.

Preserve required status checks and inspect branch protection/rulesets before removing or renaming jobs. `main` remains protected; use Conventional Commits and DCO sign-off, with squash merges.

---

## 15. Licensing and distribution

### 15.1 License

Gauja is licensed **AGPL-3.0-or-later**. Every source file carries `SPDX-FileCopyrightText` and `SPDX-License-Identifier` headers; the repository is REUSE-compliant. Vendored upstream material (`api/seerr-api.yml`) is MIT and retains its copyright and permission notice. Generated descriptions and imported palette material retain inherited attribution in `REUSE.toml`; Gauja contributions keep their existing license. REUSE metadata completeness and resolved-dependency license checks are separate responsibilities. Release packaging must include the complete applicable notices for material actually distributed, as specified in [THIRD_PARTY.md](THIRD_PARTY.md).

For the mobile apps the AGPL's network clause is effectively dormant; it is retained so that any future server-side component (the deferred notification relay, §7) is covered automatically.

### 15.2 App Store Distribution Exception

Apple's App Store terms (and, to a lesser but real degree, Google Play's distribution agreement) impose restrictions on recipients that the (A)GPL does not permit. The original author may distribute their own code there, but every third-party contributor's copyright also governs distribution once their contribution is merged. Gauja therefore grants an **additional permission under AGPL-3.0 §7** for distribution through Apple's and Google's app stores and comparable services. The full text is [APPSTORE_EXCEPTION.md](../APPSTORE_EXCEPTION.md), linked in Appendix A.

To close the gap between the DCO's "license indicated in the file" and this exception, the root `LICENSE` file states:

> The license of this project is the GNU Affero General Public License, version 3 or (at your option) any later version, **together with** the additional permission set out in `APPSTORE_EXCEPTION.md`. Every reference to "the license" in this repository, including in SPDX headers and in the Developer Certificate of Origin, means the AGPL together with that additional permission.

`CONTRIBUTING.md` repeats that sentence in its DCO section.

### 15.3 Contributions

- Every commit is signed off (`git commit -s`) under the Developer Certificate of Origin 1.1; the `commit-messages` CI check (§14.2) is a required status.
- No CLA. Contributors retain copyright; the DCO plus §15.2 is the whole grant.
- Conventional Commits are required; the changelog is generated from them.

### 15.4 Distribution channels

| Channel | Notes |
|---|---|
| Google Play | Single build; no Play Services dependencies |
| F-Droid | Eligible by construction (no proprietary dependencies, reproducible build); metadata maintained in-repo |
| GitHub Releases | Signed APKs and the iOS source archive |
| Apple App Store | Requires the exception in §15.2; TestFlight for beta |

Gauja is unaffiliated with the Seerr project. Store listings say so, use the name "Seerr" only descriptively, and use none of Seerr's marks or artwork.

---

## 16. Localization

- Source language: English (US). Catalogs: `strings.xml` per locale on Android, `Localizable.xcstrings` on iOS.
- With real catalogs, Phase 11 enables Crowdin, catalog validation and shared semantic coverage across platforms, allowing platform-specific keys and strings.
- Seerr's own translation catalogs (`src/i18n/locale/`, MIT) may be used as a **seed** for UI strings that are identical in meaning (status names, permission labels, settings section titles), with attribution in `docs/THIRD_PARTY.md`. Seeding is a one-time import, not an ongoing dependency.
- Both apps honour the user's Seerr locale setting for server-provided content and the device locale for UI, matching Seerr's behaviour.

---

## 17. Release and versioning

- Each app is versioned independently with semantic versions (`android/v1.2.0`, `ios/v1.2.0`), because store review cadences differ. A shared `CHANGELOG.md` is generated from conventional commits, sectioned per app.
- `api/compat.json` and the supported Seerr range in release notes are updated on every release.
- Reproducible builds are a release requirement (F-Droid verification).
- Store-listing screenshots are generated from the simulator/emulator smoke lanes so they stay in sync with the UI.

---

## 18. Risks and open questions

| # | Risk / question | Mitigation or owner |
|---|---|---|
| 1 | **Settings surface size.** The pinned `/settings` surface and every notification agent form account for much of the screen count; derive counts from the effective contract. | Screen inventory sized before Phase 2; settings sections implemented in Seerr's sidebar order so partial progress is coherent; sections use folders within Settings initially and feature-sized PRs. |
| 2 | **Spec drift from real behaviour.** Upstream's hand-maintained spec may not match the server. | Overlays (§4.1) plus contract tests against a real container (§4.5). |
| 3 | **Maintainer bandwidth per platform.** Two native codebases. | CODEOWNERS; each app buildable alone; screen specs let one platform lead. |
| 4 | **Apple review of an AGPL app.** | Exception (§15.2); precedent exists for GPL-family apps with such exceptions. |
| 5 | **Auth edge cases** (proxies, self-signed TLS, Plex token expiry, Quick Connect timing). | Dedicated test matrix in `design/screens/auth/`; per-profile trust modes (§6). |
| 6 | **Android minSdk.** A floor of 30 covers ~87% of active devices (April 2026), roughly parity with the iOS 18 floor; the remaining ~13% are Android 8–10 devices. | Decided: 30. The rule file is amended to match. Revisit only by amending the rule file first. |
| 7 | **iOS image pipeline.** Own `URLSession` loader vs. a third-party library. | Decided in Phase 4: retain Gauja’s loader described in §11.3. Native simulator acceptance renders a cached `TitleCard` and artwork within the §9 300 ms offline budget with zero HTTP requests; disk-cache reopen and per-profile deletion tests pass. This measures an already interactive app using a synthetic 2 × 3 poster, not cold start or a realistic scrolling workload. Scroll jank, heap growth, full-size artwork and release-install budgets remain part of the Phase 11 audit; add a library only if measurements justify it. [Native acceptance evidence](https://github.com/edbpede/gauja/actions/runs/34148352646). |
| 8 | **Seerr's Overseerr/Jellyseerr merge is ongoing** (`server/lib/overseerrMerge.ts`); endpoints may be renamed with `Deprecation` headers. | §4.3 and §4.4. |
| 9 | **Webhook JSON editor.** Native editing of Seerr's templated JSON is fiddly. | Monospaced editor with variable-insertion palette and server-side validation via `/settings/notifications/webhook/test`. |
| 10 | **Notifications (deferred).** Users will ask. | Greyed-out entry, §7 explanation in About, roadmap statement in README. |

---

## Appendix A — App Store Distribution Exception

The authoritative permission text is [APPSTORE_EXCEPTION.md](../APPSTORE_EXCEPTION.md). The project license and contribution grant are defined in [LICENSE](../LICENSE) and [CONTRIBUTING.md](../CONTRIBUTING.md).

## Appendix B — Local hook configuration

[prek.toml](../prek.toml) is the authoritative hook configuration, including commands, path filters and generated-file exclusions. Setup instructions live in [CONTRIBUTING.md](../CONTRIBUTING.md).

## Appendix C — Decision log

Settled decisions. Changing one is a PR that edits this table with a rationale, reviewed like code.

| # | Decision |
|---|---|
| 1 | Pure Kotlin + pure Swift in one repository; no KMP; contract-only sharing |
| 2 | Notifications deferred; investigated design recorded (web push channel, UnifiedPush on Android, APNs relay on iOS, RFC 8291 end-to-end encryption) |
| 3 | AGPL-3.0-or-later with App Store Distribution Exception; DCO, no CLA |
| 4 | Single Android build; no Google Play Services / Firebase; F-Droid eligible |
| 5 | Vendored OpenAPI spec pinned by upstream commit; overlays; contract tests against a real container |
| 6 | Same IA / tokens / content components; platform-native chrome |
| 7 | Full native admin configuration in v1 |
| 8 | Android minSdk 30 (Android 11): parity with the iOS 18 floor, modern insets APIs, system dark theme; rule file amended to match |
| 9 | Name: Gauja |

---

## Appendix D — Glossary

| Term | Meaning |
|---|---|
| **Seerr** | The upstream media-request server (merger of Overseerr and Jellyseerr). |
| **Server profile** | Gauja's record of one Seerr server plus how to reach and authenticate to it (§6). |
| **Content component** | A named, spec'd UI element shared across platforms by behaviour, not code (§8). |
| **Contract** | The shared inputs to both apps: API spec, overlays, fixtures, compat manifest, tokens, screen specs. |
| **Generated / leaf module** | Code produced by a generator, isolated, never edited, verified in CI (§12.2). |
| **Rule file** | A normative guideline in `.agents/rules/` (§12.1). |
| **DCO** | Developer Certificate of Origin, the sign-off-based contribution certification (§15.3). |
