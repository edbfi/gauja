---
type: "agent_requested"
description: "Gauja monorepo rules: directory ownership, CI lane triggers, the apps/android ↔ apps/ios boundary"
---

# Monorepo

Gauja is two independent native apps plus a shared contract in one repository (PRD §13, Appendix C decision 1). This file is normative for what lives where, what triggers which CI lane, and what may cross the `apps/` boundary.

## Layout and ownership

| Path | Contents | Owners (`.github/CODEOWNERS`) |
|---|---|---|
| `api/` | Vendored spec, `UPSTREAM_COMMIT`, overlays, fixtures, `compat.json` | Both platform maintainers |
| `design/` | `tokens.json`, `screens/<area>/<screen>.md`, `assets/` | Both platform maintainers |
| `apps/android/` | Gradle project (`app`, `core/*`, `feature/*`, `build-logic/`) | Android maintainers |
| `apps/ios/` | `project.yml`, `App/`, `Packages/*` | iOS maintainers |
| `tools/` | `codegen/`, `tokens/`, `api-drift/`, `ci/`, `tests/` | Project leads |
| `docs/` | PRD, implementation plan, `THIRD_PARTY.md` | Project leads |
| `.agents/rules/` | Normative rule files | Project leads |
| root | `LICENSE`, `APPSTORE_EXCEPTION.md`, `REUSE.toml`, `prek.toml`, `deny.toml`, `renovate.json`, `CONTRIBUTING.md`, `SECURITY.md`, `README.md` | Project leads |

A change under `apps/android/` needs no iOS reviewer and vice versa. A change under `api/` or `design/` needs both, because both apps consume it.

## Documentation ownership

Each fact has one authoritative owner; other documents link to it. The PRD owns product
commitments and decision rationale; the implementation plan owns sequencing and completion.
This file owns repository boundaries and workflow responsibilities; modularity owns the allowed
dependency graph. API maintenance lives in `api/README.md`, generator usage and constraints in
`tools/codegen/README.md`, attribution in `docs/THIRD_PARTY.md`, and contributor setup in
`CONTRIBUTING.md`. Feature contracts own unique behavior and acceptance criteria.

Do not create overview files or tutorial copies for directories. Put a decision with its owning
concern; a separate document requires substantial unique content and a current consumer.
The Swift and Kotlin coding guidelines remain authoritative for platform usage. Exact executable
pins belong to the relevant manifests; prose should link to them instead of adding parallel pin tables.

## The cross-boundary rule

- **Nothing under `apps/android/` references anything under `apps/ios/`, and vice versa.** No imports, no relative paths, no Gradle `includeBuild`, no SPM `path:` dependency, no symlink, no shared script. `ci.yml` greps for the other tree's path and fails the PR.
- **No shared runtime code.** No Kotlin Multiplatform, no shared library, no shared `common/` directory. What both apps share is the contract: `api/`, `design/`, and the generators in `tools/` that read them.
- **Artifacts flow one way.** `api/` → generated clients and bundled compatibility metadata; `design/tokens.json` → generated themes; `design/screens/` → both apps' behaviour. Nothing flows from an app into `api/` or `design/`, and nothing flows sideways between the apps. If both apps need the same fact, it becomes part of the contract first.
- Each app builds, tests and lints alone. A contributor with only a JDK builds Android; one with only Xcode builds iOS. A lane never installs the other platform's toolchain.

## CI ownership (`.github/workflows/`)

Workflow configurations own executable commands and triggers. [prek.toml](../../prek.toml) owns local hook filters. Prose defines required outcomes; add tooling and CI with the first real consumer, without passing placeholders.

| Active workflow | Responsibility |
|---|---|
| [ci.yml](../../.github/workflows/ci.yml) | Hygiene hooks (including screen and API import boundaries), REUSE, history secret scan, commit/DCO checks, all tooling test suites, platform separation |
| [codegen-check.yml](../../.github/workflows/codegen-check.yml) | Contract pairing/coverage and upstream bytes |
| [android.yml](../../.github/workflows/android.yml) | Android build, JVM/Robolectric/Hilt tests, lint, dependency/license graph, client regeneration, public fixture replay, transport egress and per-ABI size report |
| [ios.yml](../../.github/workflows/ios.yml) | iOS build, Swift Testing/XCUITest, lint, package/license graph, client regeneration, public fixture replay, transport egress and device size report |
| [contract.yml](../../.github/workflows/contract.yml) | Initial public status/settings responses against the pinned upstream container; full authenticated scenarios remain Phase 11 |
| [tokens-check.yml](../../.github/workflows/tokens-check.yml) | Theme regeneration, byte comparison and iOS primitive typechecking |

Local contract/theme and platform lint hooks mirror their dedicated CI owners and are skipped in CI's prek job. Tool tests exercise validators with fixtures; they do not rerun production assertions as extra steps. Staged secret checks and history scanning have different scopes.

Planned gates: authenticated container contracts and upstream discovery in Phase 11; release/SBOM/bundled notices in Phase 12. Transfer existing checks to their replacement owner when equivalent real app checks land; retire smoke manifests and locks only after each platform independently compiles and passes serialization/redaction tests.

- Every action uses a full version tag under the shared Renovate policy, as selected for this rollout.
- The top-level CI runs every required lane for every PR and main push. Platform workflows remain independently dispatchable and install only their own toolchain.
- Preserve required checks (`REUSE`, `prek`, `commit-messages`, `gitleaks`, `tool-tests`, `boundary`); inspect repository branch protection/rulesets before removing or renaming jobs.
- Complexity and length lints remain advisory (PRD §12.2).

## Branches, commits, merges

- `main` is protected. Never commit to it directly (the `no-commit-to-branch` hook refuses).
- Conventional Commits, signed off (`git commit -s`). Squash-merge only; the PR title is the commit subject.
- One module, one screen or one hook per PR where practical. A PR touching both `apps/android/` and `apps/ios/` is acceptable only for a contract change that both must consume in lockstep (a spec sync, a token change) and then each app's change must be reviewable on its own.

## Anti-patterns

| Wrong | Why | Right |
|---|---|---|
| `apps/ios/Packages/Model` reads `apps/android/core/model` fixtures | Sideways dependency | Fixtures live in `api/fixtures/` |
| A `shared/` directory with Kotlin and Swift side by side | Runtime sharing by the back door (PRD Appendix C decision 1) | Put the fact in `api/` or `design/`; generate per platform |
| Hand-copying a token value into a Swift theme | Drifts from `design/tokens.json` | Run `tools/tokens/`; the theme is generated |
| `uses: actions/checkout@v7` | Floating major hides individual releases | A full version tag updated by Renovate |
| Installing Xcode in the Android lane to run a shared script | Breaks build-one-platform independence | Keep orchestration in `tools/`; token generation is bash/Python stdlib. Contract tooling may use hash-pinned YAML, JSON Schema and JSONPath dependencies in an isolated Python environment, plus the selected platform generator. These are build-time only; neither lane needs the other platform’s toolchain. |
