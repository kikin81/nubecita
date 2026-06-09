# Glance feed widgets — offline-first feeds + home-screen widgets

**Status:** Design agreed (brainstorm) — 2026-06-08
**Epic:** `nubecita-lgoo`
**Sub-projects:** `nubecita-lgoo.1`–`.5` (see [Decomposition](#decomposition))

## Overview

Add Jetpack Glance home-screen widgets for Bluesky feeds, backed by an
**offline-first Room feed cache** shared by the app and the widgets. The MVP
targets a **single account** but the cache schema is **keyed by account DID**
from day one, so a future multi-account epic adds sessions and an account
switcher without a schema migration.

Two free widgets — **Following** and a fixed **Discover** — plus a **Pro**
**configurable** widget that shows any of the user's saved custom feeds.

This is the first capability in a broader **offline-first** theme; profile and
thread offline caching are sibling capabilities for later epics (see
[Out of scope](#out-of-scope--future)).

## Goals

- Home-screen widgets that render a feed's most recent posts and tap through to
  the app.
- Offline-first feeds: instant cold-start render from cache, background refresh,
  offline scrolling.
- A Pro differentiator (configurable feed widget) that reuses the existing
  `isPro` entitlement gate.
- Stay within the project's battery-first rule — no fighting Doze, no foreground
  service, no wakelocks.

## Decisions

| # | Decision | Rationale |
|---|---|---|
| D1 | **Both feeds in MVP**: Following (`getTimeline`, authenticated) + Discover (fixed feed generator, `getFeed`). | Enables the free-vs-Pro split and matches the product intent. Authenticated background fetch is already a solved pattern (the DM-notification worker). |
| D2 | **Pro = configurable widget** (pick any saved custom feed via a Glance configuration activity); free = Following + fixed Discover. | Ties Pro to a tangible capability; reuses `EntitlementRepository.isPro`. |
| D3 | **Full offline-first** via **Paging 3 + `RemoteMediator` + Room**; the app feed and the widgets share one cache (one source of truth). | The "proper" offline-first end-state the team chose over a widget-only cache. |
| D4 | **Denormalized snapshot rows** for the cache (not normalized post/author/membership tables). | Feeds are point-in-time snapshots; no joins on the 120hz read path; trivial eviction; storage cost is negligible (a few MB). `authorDid` stays a first-class column so a shared actor table can be layered later. |
| D5 | **DID-keyed schema** even in the single-account MVP. | Cheap future-proofing for multi-account; "delete account" becomes `DELETE WHERE accountDid = ?`. |
| D6 | **Eviction = A + B + C**: (A) `REFRESH` clears the `(accountDid, feedType, feedUri)` partition and reinserts page 1; (B) count-cap trim on `APPEND` (~500/feed); (C) logout/account-removal partition delete. Optional TTL later. | Bounded storage, fresh data, clean multi-account teardown — all in Room transactions, no extra worker. `PagingConfig.maxSize` is unusable with `RemoteMediator`, so eviction must be DB-level deletes. |
| D7 | **Foundation-first sequencing**: build `:core:feed` + cache + widget before migrating the app feed screen. | De-risks the 120hz-critical feed-screen migration; the widget ships without waiting on it. |
| D8 | **Widget reads the Room head directly** (`ORDER BY position LIMIT n`), not via Paging. | A widget shows only a handful of posts; tail eviction never affects it. |
| D9 | **Battery-cooperative refresh** — periodic WorkManager + on-add/manual, network constraint, Doze-tolerant; reuses the DM-notification worker scaffolding. | Project battery rule. |

## Architecture

### Data layer — `:core:feed` (new module)

Today the timeline fetch lives **inside `feature/feed/impl`** (`DefaultFeedRepository.fetchPage` →
cursor-based `getTimeline`/`getFeed`/`getListFeed`) and the feed has **no Paging 3**
(it paginates manually via the `FeedLoadStatus` sealed sum). A widget cannot
depend on a feature `:impl`, so the fetch + mapping is **extracted into a new
`:core:feed`** module that both `feature/feed` and the widget refresh worker use.

`:core:feed` owns:
- The Room cache (tables below), via the `nubecita.android.room` convention plugin.
- A `RemoteMediator<Int, FeedPostEntity>` per `(accountDid, feedType, feedUri)`
  that fetches from atproto, write-throughs to Room, and applies eviction (D6).
- A `Pager` / `Flow<PagingData<PostUi>>` for the app feed screen (consumed in E).
- A **head DAO query** (`ORDER BY position LIMIT n`) for the widget (D8).
- A saved-feeds fetch (`app.bsky.actor.getPreferences` + feed-generator metadata)
  for the Pro configuration picker (related to existing custom-feeds work
  `nubecita-lq9t.3.2` and feed-switching `nubecita-a580`).

### Room schema (denormalized, DID-keyed)

`NubecitaDatabase` is currently at version 4 (`RecentSearchEntity`, `ActorEntity`).
This adds version 5 with:

- **`feed_post`** — one row per cached position:
  - Keyed/partitioned by `(accountDid, feedType, feedUri, position)`.
  - Queryable columns: `uri`, `cid`, `authorDid`, `indexedAt`, `position`, `text`.
  - `embed` / extras as a **serialized blob** (TypeConverter) — keeps queries and
    `ORDER BY position` cheap without a normalized embed graph.
  - `authorDid` is first-class so a future shared `actor`/`profile` table (likely
    extending `ActorEntity`, driven by profile-offline) can be layered in without
    reworking feed rows.
- **`feed_remote_keys`** — RemoteMediator paging cursors per `(accountDid, feedType, feedUri)`:
  stores the atproto `cursor` for the next `APPEND`. `REFRESH` starts from a null
  cursor; `PREPEND` returns `endOfPaginationReached = true` (reverse-chron feed —
  refresh for newer, never prepend-page).

`feedType` distinguishes Following vs Discover vs a custom feed; `feedUri` holds
the generator AT-URI for Discover/custom feeds (empty/sentinel for Following).
Schema JSON for v5 is committed and an `@AutoMigration` (or manual `Migration`)
is registered per `:core:database` convention.

### Background refresh — widget worker (B)

A WorkManager worker (reusing the DM-notification scaffolding: `@HiltWorker`,
`HiltWorkerFactory`, periodic request, scheduler seam, network constraint)
refreshes the cached feed(s) and calls `GlanceAppWidget.update()`. Triggered
periodically, on widget add, and on a manual refresh tap. Battery-cooperative
(D9): no expedited work, Doze deferral accepted.

### Widgets (C) + Pro gating (D)

`GlanceAppWidget` instances read the Room **head** (D8) and render a compact post
list; tapping a post opens it via a `nubecita://` deep link (mirrors the push /
DM notification tap-intent pattern). Three surfaces:
- **Following** (free), **Discover** fixed (free).
- **Configurable** (Pro) + a Glance **configuration activity** that lists the
  user's saved feeds. Gated by `EntitlementRepository.isPro`: non-Pro users get
  an upsell state that deep-links to the paywall (D).

### App feed migration (E) — separate sub-project

`feature/feed` migrates from manual `FeedLoadStatus`/`ImmutableList<PostUi>` to
`Flow<PagingData<PostUi>>` + `collectAsLazyPagingItems()` — the **dual-flow MVI**
pattern (a PagingData flow for the list + a small `UiState` flow for non-list
state). This is the highest-blast-radius change (introduces `androidx.paging` and
rewrites the 120hz-critical screen) and is therefore its own, heavily-tested
sub-project after the foundation lands.

## Decomposition

Each sub-project is its own openspec change → plan → PR-set.

| Bead | Sub-project | Depends on |
|---|---|---|
| `nubecita-lgoo.1` | **A** — `:core:feed` + offline Room cache (Paging 3 RemoteMediator + denormalized snapshot + eviction) | — |
| `nubecita-lgoo.2` | **B** — Widget feed-refresh background worker | A |
| `nubecita-lgoo.3` | **C** — Glance widgets (Following + Discover) + configurable widget & config activity | A, B |
| `nubecita-lgoo.4` | **D** — Gate configurable widget behind `isPro` + paywall upsell | C |
| `nubecita-lgoo.5` | **E** — Migrate feed screen to PagingData/RemoteMediator (offline-first read-through) | A |

The widget (B/C/D) does **not** depend on E, so it ships before the risky feed
migration.

## Risks / trade-offs

- **120hz feed migration (E).** Introducing Paging 3 + dual-flow MVI on the most
  performance-sensitive screen. Mitigation: separate sub-project, foundation
  built and proven by the widget first, dedicated perf/regression testing.
- **Cross-feed state drift** (consequence of D4). A like/count change in one feed
  isn't reflected in another cached feed until refresh. Mitigation: in-app
  interactions overlay live state via `:core:post-interactions`; refresh re-syncs.
  Acceptable for snapshot feeds.
- **Authenticated background fetch** for Following. Mitigation: reuse the
  `:core:auth` refresh-mutex path the DM worker already uses (no logout race).
- **MVI departure.** Dual-flow PagingData deviates from the documented flat-state
  MVI; scoped to the feed screen and follows the `compose-expert` paging-MVI
  reference.

## Out of scope / future

- **Multi-account** — N `OAuthSession`s + active-account selector + account
  switcher. The DID-keyed schema is ready; this is a separate later epic.
- **Profile-offline / thread-offline** — sibling capabilities in the offline-first
  theme; their own caches (a profile/actor table + that profile's posts), ideally
  all reading one shared actor table eventually.
- **Inline Direct Reply** from widgets, multiple widget instances of the same
  feed, configurable refresh cadence UI.

## Open questions (deferred to each sub-project's openspec)

- Refresh cadence / minimum interval and widget post-count (`n` for the head query).
- Exact count-cap value (~500 starting point).
- Widget visual layout, sizes, and resize behavior — designed with a mockup
  companion at sub-project C.
- Whether saved-feeds metadata reuses any existing custom-feeds work
  (`nubecita-lq9t.3.2`).
