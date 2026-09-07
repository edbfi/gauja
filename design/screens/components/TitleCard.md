<!--
SPDX-FileCopyrightText: 2026 Gauja contributors
SPDX-License-Identifier: AGPL-3.0-or-later
-->

# TitleCard

## Contract

Compact movie/TV identity shared by Discover, search and related-media lists. Consumed by Phase 4 and the owning feature phases. Reference: Seerr `src/components/TitleCard/` at `69f73a6f1486fdb51b8ddae9a94a8dfb629f461c` (inspiration only). Uses the [shared behavior baseline](INVENTORY.md#shared-behavior-baseline).

## Content

Typed media identity, poster at layout size, title, year, media type, rating when present, availability and 4K markers. Missing artwork retains aspect ratio and a neutral placeholder.

## Actions

Open the correct movie/TV detail using its media type and TMDB ID. Request is a separate RequestButton; avoid competing nested tap regions.

## Endpoints

Read models from GET /search, GET /discover/trending, GET /discover/movies, GET /discover/tv. The card itself performs no I/O.

## Permissions

Signed-in viewing; request actions follow RequestButton. Availability does not imply request permission.

## Acceptance criteria

A recycled cell opens the currently bound identity; missing poster/title never crashes. Status and title remain audible and legible at largest text size.

## Phase 4 hello-server acceptance flow

This is a test-only composition, absent from release navigation. The harness initializes the
pinned local Seerr with synthetic local users, then the native repository signs in through
POST /auth/local, fetches GET /auth/me, and persists the mapped user with its fetch timestamp.
A synthetic title is explicitly seeded into the same profile's title cache; it is not a
recorded media response. The card renders that cached title and cached artwork with network
access disabled. The cache-to-render interval must be at most 300 ms, with zero HTTP requests.

The containing test state shows loading before the cache read, an empty explanation when no
cached title exists, a safe error with retry for failed reads, and an offline timestamp while
retaining the card. A missing authenticated user produces permission-denied content. The card
itself has no request action. Unknown media types disable opening; unknown status ordinals
render a neutral label. Tests cover font scaling, missing artwork, recycled identity, both
themes, profile deletion, session restoration, and a 401 affecting only its own profile.
