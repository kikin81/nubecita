## Why

Today the main Feed only ever shows the Following timeline — there is no way to view a
custom feed (Discover, algorithmic feeds, or feeds the user pinned in another client)
without leaving the app's primary surface. Bluesky users live across multiple feeds, and
the only first-class feed in Nubecita is hard-wired to `app.bsky.feed.getTimeline`. The
updated `atproto-kotlin` 9.1.0 now exposes `getFeed`, `getListFeed`, `getFeedGenerators`,
and the `SavedFeedsPrefV2` preference, so we can let users switch between their pinned
feeds directly on the main Feed via a Material 3 filter-chip row — without the dated tab
UI the official client uses.

Full design rationale (brainstormed and approved):
`docs/superpowers/specs/2026-05-30-feed-switching-design.md`.

## What Changes

- Add a **scroll-away filter-chip row** at the top of the main Feed. Chips are the user's
  **pinned feeds**, read from server preferences (`getPreferences` → `SavedFeedsPrefV2`,
  filtered to `pinned`), hydrated with display name + avatar via `getFeedGenerators`.
  New accounts / fetch failures fall back to the defaults `[Following, Discover]`.
- **Per-feed state retention** on switch: each feed pane is its own `FeedViewModel`
  obtained via `hiltViewModel(key = feedUri)` (retains posts + cursor), wrapped in a
  `SaveableStateHolder` keyed by feed URI (retains scroll). A thin `FeedHostViewModel`
  owns only the chip list + selection — no per-feed logic.
- `FeedViewModel`/`FeedRepository` gain **multi-kind dispatch**: Following →
  `getTimeline`, generator → `getFeed`, list → `getListFeed`. All three return
  `List<FeedViewPost>`, so the existing mapper/dedupe/chain-merge are reused unchanged.
- **Pinned lists collapse** into a single Gmail-style **disclosure chip** (`[ Lists ⌄ ]`)
  that opens a `ModalBottomSheet` single-select picker, keeping the chip row narrow.
- **Restore-last-selected feed** on cold start, persisted to DataStore.
- A **trailing button** at the end of the chip row routes to a new `:feature:feeds:api`
  stub (placeholder "Manage feeds — coming soon"), per the `:api`-first convention.
- Add one **vendored Material Symbols glyph** (`LocalFireDepartment`, codepoint `EF55`)
  for the Discover/"Popular" default, in a standalone `:designsystem` PR that owns the
  repo-wide screenshot-baseline regeneration (icon-font pitfall).
- The chip row is **adaptive**: full-width on compact, list-pane-width (412/440dp) on
  medium/expanded, consistent with the existing List-detail layout and the Search tab.

## Capabilities

### New Capabilities

- `core-feeds`: a non-UI repository that reads the user's pinned/saved feeds from
  `SavedFeedsPrefV2`, splits them by `type` (timeline/feed/list), hydrates generator
  metadata (display name + avatar) via `getFeedGenerators`, exposes the default fallback
  (`Following` + the `whats-hot`/Discover generator), and remembers the last-selected
  feed URI.
- `feature-feeds`: the navigation stub for the (deferred) Feeds-management screen — a
  `Feeds` `NavKey` in `:feature:feeds:api` plus an `:app` `@MainShell` placeholder route
  that the chip row's trailing button targets.

### Modified Capabilities

- `feature-feed`: the main Feed gains feed switching — a `FeedHost` + `FeedHostViewModel`
  hosting a scroll-away chip row and a per-feed `FeedViewModel` keyed by feed URI;
  `FeedRepository` extends from `getTimeline`-only to also fetch generator feeds
  (`getFeed`) and list feeds (`getListFeed`); adaptive list-pane placement.

(No spec-level requirement changes to `data-models` or `design-system`: the new
`@Stable PinnedFeedUi` / `FeedKind` model obeys the existing `data-models` requirements,
and the new `LocalFireDepartment` glyph is governed by `design-system`'s existing
"Adding a new glyph requires an enum entry" requirement. Both are captured as tasks; the
`PinnedFeedUi` contract is specified under `core-feeds`.)

## Impact

- **New modules:** `:core:feeds` (`nubecita.android.library` + `nubecita.android.hilt`),
  `:feature:feeds:api` (NavKey-only stub).
- **Modified modules:** `:feature:feed:impl` (host, chip row, retention, dispatch),
  `:data:models` (PinnedFeedUi, FeedKind, fixtures), `:core:preferences`
  (`lastSelectedFeedUri`), `:designsystem` (glyph + regenerated subset font + baselines),
  `:app` (`@MainShell` Feeds placeholder provider).
- **SDK surface used (`atproto-kotlin` 9.1.0):** `FeedService.getFeed`,
  `FeedService.getListFeed`, `FeedService.getFeedGenerators`,
  `ActorService.getPreferences`, and the `SavedFeedsPrefV2` / `SavedFeed` types.
- **Screenshot baselines:** new `FeedChipRow` / `PinnedListsSheet` baselines; extend
  `MainShellListDetailScreenshotTest` for list-pane-width rendering; a separate
  `:designsystem` baseline regen for the new glyph.
- **No new third-party dependencies.**

## Non-goals

- The **Feeds management/search screen** (browse, pin/unpin, reorder, follow/unfollow) —
  separate epic; only its navigation stub is in scope here.
- A **Feed top app bar** (bookmarks, overflow, quick-settings shortcut) — deferred; it is
  an app-wide navigation-shortcuts concern, not feed-scoped (see design doc).
- **Per-feed view preferences** (`FeedViewPref`: hide replies/reposts/etc.).
- **Multi-column feed grid** (the standalone M3 *Feed* canonical layout) — Nubecita keeps
  a single-column `LazyColumn` timeline within the List-detail layout.

## Baseline deviations to call out

- **MVI:** introduces a small **`FeedHostViewModel`** (a non-screen "host" presenter that
  owns only chip list + selection) and the **per-feed-`ViewModel`-keyed-by-URI** pattern
  via `hiltViewModel(key = feedUri)` + `SaveableStateHolder`. This stays within the MVI
  baseline (each VM extends `MviViewModel`); it is documented because the host/pane split
  is a new structural pattern for a tab-home screen.
- **Compose Material 3:** the **selected `FilterChip` keeps the feed avatar visible**
  rather than swapping the leading slot to the default selection checkmark, because the
  avatar is the feed's identity. This is a deliberate departure from the stock
  `FilterChip` selected-state behavior.
