## Context

The main Feed (`:feature:feed:impl`) is hard-wired to the Following timeline:
`DefaultFeedRepository.getTimeline` is the only feed fetch, `Feed` is a bare
`data object` `NavKey`, and `:core:preferences` stores almost nothing. On tablets the
Feed is the **list pane** of a `ListDetailPaneScaffold` (PostDetail is the detail pane),
with a `NavigationRail` on the left; the shell's `NavDisplay` already installs
`rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`
per entry. `atproto-kotlin` 9.1.0 adds `getFeed`, `getListFeed`, `getFeedGenerators`,
and the `SavedFeedsPrefV2` / `SavedFeed` preference types.

This design is the condensed architecture record. The full brainstorm (with the option
analysis and ASCII mockups) is the authoritative source:
**`docs/superpowers/specs/2026-05-30-feed-switching-design.md`**. See `proposal.md` for
motivation and `specs/` for the testable requirements.

## Goals / Non-Goals

**Goals:**

- Switch the main Feed between the user's pinned feeds via an M3 filter-chip row.
- Retain each feed's posts, cursor, and scroll position across switches — without a
  large parent ViewModel and without regressing 120 Hz scrolling.
- Drive chips from server preferences (`SavedFeedsPrefV2`), with a sane offline/new-
  account fallback.
- Keep the chip row correct on phone and tablet (list-detail) with no shell changes.

**Non-Goals:**

- The Feeds management/search screen (only its nav stub here).
- A Feed top app bar / app-wide shortcuts (deferred).
- A multi-column feed grid; per-feed view preferences.

## Decisions

### D1 — Per-feed ViewModel + `SaveableStateHolder` (retention)

Each feed pane is the existing `FeedViewModel` obtained via `hiltViewModel(key = feedUri)`
(retains posts + cursor in the Feed entry's `ViewModelStore`), wrapped in
`rememberSaveableStateHolder().SaveableStateProvider(feedUri)` (retains scroll). A thin
`FeedHostViewModel` holds only `pinnedFeeds`, `pinnedLists`, and `selectedFeedUri`.

- **Why:** retention lives in framework infrastructure already present per nav entry;
  all pagination/dedupe/interaction logic stays in the per-feed VM. The host cannot grow
  into a monster — it has no per-feed state. Only the active pane is composed, so one
  `LazyColumn` measures at a time (protects 120 Hz).
- **Alternatives:** (a) *Reload-fresh single VM* — simplest but loses position on every
  peek; rejected for UX. (b) *One big parent VM holding a `Map<feedUri, paneState>`* —
  the "monster VM" we explicitly avoid. (c) *`HorizontalPager` over feeds* — gives swipe
  but keeps neighbor panes composed (measuring cost) and couples chips to pager state;
  rejected as over-built for a chip switcher.

### D2 — Multi-kind dispatch reusing one mapper

`FeedViewModel.bind(feedUri, kind)` dispatches: `Following → getTimeline`,
`Generator → getFeed(feed = AtUri)`, `List → getListFeed(list = AtUri)`. All three return
`List<FeedViewPost>`.

- **Why:** identical wire shape means `toFeedItemsUi()`, dedupe, and self-thread chain
  merge are reused verbatim — zero mapping changes, lowest-risk extension. `FeedRepository`
  stays `internal` to `:feature:feed:impl` (the existing spec's "promote to `:core:feed`
  only when a second consumer appears" still holds — there is no second consumer).

### D3 — Server-driven chips with default fallback (`core-feeds`)

`PinnedFeedsRepository` reads `getPreferences` → `SavedFeedsPrefV2.items.filter { pinned }`,
preserving order, then batch-hydrates `type="feed"` items via `getFeedGenerators`.
`type="timeline"` (`value="following"`) → a synthesized Following entry. No prefs / empty
/ failure → `[Following, Discover]` (Discover = the constant `whats-hot` generator URI).

- **Why:** reflects feeds the user pinned in any client; the fallback keeps the feature
  usable offline and for brand-new accounts. Lives in `:core:feeds` because the future
  Feeds-management epic reuses the same read path.

### D4 — Lists collapse into a disclosure chip

Pinned lists (`type="list"`) render as one `[ Lists ⌄ ]` chip (shown only if ≥1) opening
a `ModalBottomSheet` single-select radio; selecting relabels to `[ List: <name> ⌄ ]`.

- **Why:** a user can pin many feeds *and* lists; individual chips for every pin would
  overflow the row. Feeds (the primary switching axis) stay individual chips; lists fold
  into one affordance, distinguishable cleanly via `SavedFeed.type`.

### D5 — Scroll-away header, list-pane-scoped (adaptive)

The chip row lives in `FeedHost` above the `SaveableStateProvider`, hides on downward
scroll / reveals on upward via a nested-scroll connection to the **active pane's**
`LazyListState`. Because it is a child of Feed's `Scaffold`, it spans full width on
compact and list-pane width (412/440dp) on medium/expanded.

- **Why:** maximizes reading space (the full-screen feed feel) and matches the M3
  **List-detail** canonical layout (per-pane bars) that Nubecita already adopted — the
  detail pane keeps PostDetail's own `TopAppBar`. The Search tab's input field is the
  in-repo precedent for a list-pane-width header. The multi-column *Feed* canonical layout
  is intentionally not adopted (text timeline, not a media grid).

### D6 — Selected chip keeps the avatar; restore-last-selected

Selection is signaled by the filled `secondaryContainer`, not the default checkmark swap,
so the feed avatar (its identity) stays visible. `selectedFeedUri` is persisted to
DataStore (`:core:preferences.lastSelectedFeedUri`) and restored on launch, validated
against the live pinned set (else Following).

### D7 — Glyph addition is a standalone `:designsystem` PR

Add `LocalFireDepartment("")` to `NubecitaIconName`, regenerate the subset font,
update icon-showcase baselines — as its own PR that lands first.

- **Why:** `scripts/update_material_symbols.sh` re-subsets from upstream and regenerates
  every glyph outline, causing repo-wide screenshot-baseline drift. Bundling it into a
  feature PR would blow that PR's blast radius. `Home`, `ExpandMore`, `Add` already exist.

## Risks / Trade-offs

- **Nested-scroll header + pane swap jank** → keep header reveal state in the host, re-
  attach to each pane's `LazyListState` on switch (reset to "shown"); validate on a 120 Hz
  device / macrobench if warranted.
- **VM memory grows with visited feeds** → bounded by pinned count, freed when the Feed
  tab pops; LRU eviction deferred (YAGNI) and would be a localized host change.
- **Hardcoded Discover (`whats-hot`) URI** → keep it as a single named constant in
  `:core:feeds`; documented.
- **Lists without avatars** → fall back to a generic list glyph in the sheet rows.
- **Selected-chip avatar departs from stock `FilterChip`** → documented deviation; covered
  by screenshot tests for both states.

## Migration Plan

Land in dependency order (each its own PR; see `tasks.md`): (1) standalone glyph PR →
(2) `:core:feeds` + `:data:models` model + `:core:preferences` key → (3) `:feature:feeds:api`
stub + `:app` placeholder → (4) `FeedViewModel` bind/dispatch → (5) `FeedHost` + retention
→ (6) chip row + scroll-away + lists sheet → (7) screenshot/integration polish. No data
migration; no rollback concerns (additive). The `Feed` `NavKey` stays a `data object` — the
host manages selection internally, so no nav-graph migration.

## Open Questions

- None blocking. The deferred top app bar (bookmarks / overflow / quick-settings) is
  captured for a follow-up; where app-wide chrome lives on tablet is its design's problem.
