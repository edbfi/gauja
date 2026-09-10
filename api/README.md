<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# Seerr contract

The supported baseline is **Seerr v3.4.1**, commit `69f73a6f1486fdb51b8ddae9a94a8dfb629f461c`, fetched 2026-09-05. The vendored OpenAPI 3.0.2 document is unchanged upstream material (MIT; complete pinned notice in [LICENSES/MIT.txt](../LICENSES/MIT.txt)); its `info.version: 1.0.0` is not the server release version. The base path is `/api/v1`.

`UPSTREAM_COMMIT` contains the SHA on line one and a commented fetch date on line two. Update it with the verbatim spec in the same commit. Check upstream bytes before accepting any sync. The supported floor is 3.4.1; later untested releases receive a soft warning in Phase 4. Per-feature `max: null` means no known removal, not a claim that future releases are tested.

Derive path and operation counts from the effective contract with the endpoint renderer below. Develop adds `/settings/{plex|jellyfin}/library/{libraryId}` and `/settings/{plex|jellyfin}/library/sync` (four paths); these require a later baseline upgrade. Full planned library management uses the pinned release’s operations. Upstream discovery does not itself upgrade the supported stable baseline.

Install shared tooling with `tools/contract/python.sh --install`. Validate with `tools/api-drift/check-local.sh --working-tree`. Run `tools/codegen/generate.sh` to update both clients, or add `--platform android` / `--platform ios`. `--check` compares without rewriting. See [codegen setup](../tools/codegen/README.md) for toolchain setup.

`coverage.json` is editable implementation status, keyed by operation ID. Keep every effective operation, including exclusions. `phase` and `status` are required; `note` is required for an exclusion and optional for a unique implementation constraint. Operation summaries are derived from the spec, never copied into the ledger. Render the endpoint checklist to stdout with `tools/contract/python.sh tools/contract/endpoints.py`, or add `--output api/ENDPOINTS.md` for an ignored local report. The drift validator checks complete coverage independently of this report. No feature is implemented merely because its client exists. Excluded initialization, push-subscription and sunset blacklist operations must not be called by Gauja.

Missing operation IDs are synthesized only for the effective spec: HTTP method, then camel-cased path segments, with `By` before each parameter name (`GET /user/{userId}` → `getUserByUserId`). Existing upstream IDs are retained; invalid names and collisions fail. Both generators and future recordings use these IDs.

The JSON Schema governs `compat.json`: required `min`, nullable `max`, `endpoint`, and explanatory `note`. Initial minima are Gauja's supported floor, not historical introduction claims. Phase 4 bundles this JSON as a resource in each app and decodes it into `FeatureGate` metadata; there is no separate table generator. Capabilities also depend on server configuration and permissions.

## OpenAPI overlays

One corrected operation or schema per `<area>-<operation-or-schema>.yml`, in OpenAPI Overlay 1.0.0 format. The top comment block identifies the Seerr version, operation/schema, rationale and evidence. Cite an upstream issue/PR or recorded behavior. Before container recording, a precise `blob/<40-character SHA>/...` source/spec link is accepted by maintainer decision. Verify source-backed corrections against a real container with their first consuming flow; Phase 11 checks complete coverage.

Actions use RFC 9535 JSONPath. Objects merge recursively, arrays append, scalars replace, and `remove: true` deletes the selected property or element. To replace an array, remove it first and then update its parent. Filename order determines application order. Every target must match. Changes to the operation set, external references and missing citations fail validation. The effective document is temporary; never edit the vendored YAML or commit the effective document.

The initial overlays fix the three undeclared tags (`tmdb`, `issue`, `overriderule`), watchlist creation's required media type and 201 response, person numeric fields/birthday, override-rule IDs, the nullable request modifier, media-status documentation, the public settings schema (`settings-public.yml`) and a missing array type. Corrections are scoped to evidence; no blanket removal of required fields or nullability expansion is permitted.

On each sync, compare every correction with the new upstream document. Delete overlays that upstream has fixed in the same spec/pin commit. Synthetic tooling tests live under `tools/tests/`; they are never described as recorded server behavior.

## Recorded server fixtures

The initial server-check fixtures record an uninitialized upstream container, the state required by this flow. Record authenticated operations against an initialized, seeded container. Plan §11.6 permits the first recordings alongside the initial app flow; extend them with consuming features. No response fixtures were recorded or hand-authored in Phase 2.

Layout: `<seerr-version>/<tag>/<operationId>.json` for the default scenario; `<operationId>-<scenario>.json` for alternatives such as `page1`, `empty`, `forbidden`, or `restart-required`. Use the effective operation IDs keyed in `coverage.json`. Create fixture directories with the first actual recordings. One file holds one recorded response body. The recorder tracks method, path, status and selected non-secret headers separately; it must not save raw auth headers.

Before committing, scrub credentials to `REDACTED` and run `tools/ci/check-fixture-secrets.sh` plus gitleaks. Never record cookies, API keys, Basic passwords, Plex/Jellyfin tokens, Quick Connect secrets, VAPID private material or PEM keys. Do not print rejected values. Private server recordings use ignored `*.local.*` names and are never uploaded to CI.

Both platforms consume these files from the shared contract. Decode failures become evidence for an overlay; they are never solved by editing the recording or weakening a mapper. Replay source-backed overlays against the container as their consumers land; Phase 11 must cover every retained overlay. Synthetic serialization tests now live in each app’s API module; generator-transform tests remain in `tools/tests/codegen/`.

Re-record the initial public scenario with `tools/contract/python.sh tools/ci/public-contract.py --base http://127.0.0.1:5057 --record`. The container must use the pinned upstream Dockerfile. `recording.json` stores the request/status/header provenance separately; instance identifiers are scrubbed. Each app lane replays these responses through its real Data client with `python3 tools/ci/replay-contract.py android|ios`. `contract.yml` validates the live public responses and sunset headers; the complete initialized/seeded audit remains Phase 11.

The initialized local-auth fixtures use `tools/ci/seed-local-auth.cjs` against an empty pinned
upstream database, followed by `tools/contract/python.sh tools/ci/local-auth-contract.py
--base http://127.0.0.1:5057 --credentials <ignored-local-json> --initialize --record`.
The credential file contains synthetic `email` and `password` fields and must have mode 0600.
The seed uses Seerr's own password hashing; the initialization call is harness-only and never
ships in either app. `recording-auth.json` records response provenance without session values.
The scoped auth-local and User overlays capture the observed minimal login response and
nullable Plex username; generated clients must decode those recordings unchanged.

Run `python3 tools/ci/hello-server.py android|ios` for the native Phase 4 acceptance flow.
The harness starts a disposable server from the pinned upstream source (Docker on Linux,
the upstream-pinned Node runtime on macOS), initializes synthetic local users, and runs the
platform test. An optional `--base` and `--credentials` pair reuses an initialized localhost
instance. The native repository signs in, fetches `/auth/me`, and persists its domain value.
The test explicitly seeds its title and uses `design/assets/test/poster.png`; neither is
presented as a recorded media response. After cache fill, the harness rejects and counts
all HTTP requests while the test measures cached title/artwork rendering against 300 ms.
The harness fails if login, user fetch, artwork fetch, or the offline checkpoint did not run.
