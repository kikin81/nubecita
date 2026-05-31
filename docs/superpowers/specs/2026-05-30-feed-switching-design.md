# Epic: Feed switching on the main feed

**Date:** 2026-05-30
**Status:** Design approved — pending spec review → implementation plan
**Author:** Brainstormed with Claude (superpowers:brainstorming)

## Summary

Let the user switch the main Feed between their **pinned feeds** via a Material 3
filter-chip row at the top of the feed, instead of Bluesky-style tabs. Chips are
driven by the user's server-side saved-feeds preferences. Each feed retains its
loaded posts and scroll position when switched away and back. Pinned **lists**
collapse into a single Gmail-style disclosure chip with a bottom-sheet picker. A
trailing button routes to a placeholder for the (separate) Feeds-management epic.

## Scope

**In scope**

- A scroll-away chip row on the main Feed for switching between pinned feeds.
- Reading the user's pinned feeds from server preferences (`getPreferences` →
  `SavedFeedsPrefV2`), hydrating display name + avatar.
- Per-feed state retention (posts, cursor, scroll) on switch.
- Restore-last-selected feed on cold start.
- Pinned lists collapsed into one disclosure chip + bottom-sheet single-select.
- A trailing `[＋]` button routing to an `:api`-stub Feeds placeholder.
- One new design-system glyph (`LocalFireDepartment`) in a standalone PR.

**Out of scope (separate "Feeds management" epic)**

- The Feeds management/search screen, pin/unpin, reorder, follow/unfollow feeds.
- Per-feed view preferences (`FeedViewPref`: hide replies/reposts/etc.).

**Deferred (separate follow-up)**

- A Feed **top app bar** with app-wide shortcuts (bookmarks, overflow, quick-settings).
  See "Deferred follow-up: feed top app bar" below for the captured analysis.

## Decisions

| # | Decision | Choice |
|---|----------|--------|
| 1 | Chip source | Server pinned feeds via `getPreferences` → `SavedFeedsPrefV2.items.filter { pinned }`; fall back to `[Following, Discover]` for new accounts / fetch failure. |
| 2 | Switch behavior | Retain per-feed state. Per-feed `FeedViewModel` keyed by feed URI (retains posts + cursor) + `SaveableStateHolder` keyed by URI (retains scroll). A trivial host owns only the chip list + selection. |
| 3 | Trailing button | Ship now; route to a `:feature:feeds:api` stub placeholder ("Manage feeds — coming soon") per the `:api`-first convention. |
| 4 | Chip-row scroll behavior | Scroll-away: hide on downward scroll, reveal on upward (enterAlways-style), connected to the active pane's `LazyColumn`. |
| 5 | Initial feed | Restore last-selected feed URI from DataStore (`:core:preferences`); validate against the current pinned set, else fall back to Following. |
| 6 | Lists | Collapse all pinned lists into one disclosure chip `[ Lists ⌄ ]` → `ModalBottomSheet` single-select radio. Avoids a many-chip row. Distinguishable via `SavedFeed.type`. |
| 7 | Default-feed icons | The two defaults use **local glyphs** (work offline / pre-fetch): Following → `Home`, Discover → `LocalFireDepartment` ("Popular"). User-pinned generator feeds use their remote `GeneratorView.avatar`. |
| 8 | Selected-chip visual | Keep the avatar/icon visible and signal selection via the filled `secondaryContainer` container — a documented departure from the default `FilterChip` checkmark-swap, because the avatar is the feed's identity. |

## Module layout

| Module | Change | Rationale |
|--------|--------|-----------|
| `:core:feeds` **(new)** — `nubecita.android.library` + `nubecita.android.hilt` | `PinnedFeedsRepository`: reads `getPreferences` → `SavedFeedsPrefV2`, splits by `type`, hydrates feed metadata via `getFeedGenerators` (batch). | Non-UI shared capability; the Feeds-management epic reuses it. Conventional `:core:*` home. |
| `:data:models` | `PinnedFeedUi(id, uri, kind, displayName, avatarUrl)` + `enum FeedKind { Following, Generator, List }`. `@Stable`, with fixtures. | UI-ready chip model. |
| `:core:preferences` | `+ lastSelectedFeedUri: Flow<String?>` / `suspend fun setLastSelectedFeedUri(uri: String)`. | Restore-last-selected. |
| `:feature:feed:impl` | New `FeedHost` + `FeedHostViewModel` + `FeedChipRow` + `PinnedListsSheet`; existing `FeedViewModel`/`FeedScreen` become a pane parameterized by `feedUri`; `FeedRepository` gains `getFeed` + `getListFeed` paths. | The switcher + retention live here. |
| `:feature:feeds:api` **(new, stub)** | `Feeds : NavKey`; `:app` registers a `@MainShell` placeholder Composable. | Trailing-button target via `:api`-first convention. |
| `:designsystem` **(standalone PR)** | Add `LocalFireDepartment("\uEF55")` to `NubecitaIconName`, regenerate the subset font. | Owns repo-wide screenshot-baseline regen; must NOT be bundled with feature work (icon-font pitfall). |

## Architecture — Option B retention

The Feed nav entry hosts a thin switcher; each feed is an isolated ViewModel.

```
entry<Feed>  ──►  FeedHost                          ← single nav entry, one ViewModelStore
                   │
                   ├─ FeedHostViewModel              ← TINY: chips + selectedUri + persistence
                   │     pinnedFeeds: ImmutableList<PinnedFeedUi>
                   │     pinnedLists: ImmutableList<PinnedFeedUi>
                   │     selectedFeedUri: String?
                   │     status: FeedHostStatus       (Loading / Ready / Error-fallback)
                   │
                   ├─ FeedChipRow (scroll-away)       ← LazyRow<FilterChip> + [Lists ⌄] + [＋]→Feeds
                   │
                   └─ SaveableStateProvider(selectedUri)   ← retains SCROLL
                        └─ FeedPane(feedUri, kind)
                             └─ hiltViewModel(key = feedUri)   ← retains POSTS + cursor
                                  = existing FeedViewModel, bound to (feedUri, kind)
```

**Why no monster VM:** `FeedHostViewModel` holds a list + an index + persistence — it
has nothing to grow into. All pagination / dedupe / chain-merge / interaction logic
stays untouched in the per-feed `FeedViewModel`.

**Retention split:**

- `hiltViewModel(key = feedUri)` keeps each visited feed's `FeedViewModel` (posts +
  cursor) alive in the Feed nav entry's `ViewModelStore` until the Feed tab pops.
- `SaveableStateProvider(feedUri)` keeps each feed's scroll offset (the `LazyListState`
  is saveable).
- Only the **active** pane is composed → one `LazyColumn` measuring at a time → no
  120 Hz scrolling regression. Memory bounded by visited-pinned count; LRU eviction
  deferred (YAGNI).

### MVI shape (per conventions)

`FeedHostViewModel : MviViewModel<FeedHostState, FeedHostEvent, FeedHostEffect>`

```kotlin
@Immutable
data class FeedHostState(
    val status: FeedHostStatus = FeedHostStatus.Loading,
    val feedChips: ImmutableList<PinnedFeedUi> = persistentListOf(),
    val pinnedLists: ImmutableList<PinnedFeedUi> = persistentListOf(),
    val selectedFeedUri: String? = null,         // active feed or list URI
    val selectedListUri: String? = null,         // last-picked list (for disclosure label)
) : UiState

sealed interface FeedHostStatus {               // mutually-exclusive lifecycle → sealed
    data object Loading : FeedHostStatus
    data object Ready : FeedHostStatus
    data object ErrorFallback : FeedHostStatus  // defaults shown; prefs fetch failed
}

sealed interface FeedHostEvent : UiEvent {
    data object Load : FeedHostEvent
    data object Retry : FeedHostEvent
    data class SelectFeed(val uri: String) : FeedHostEvent
    data class SelectList(val uri: String) : FeedHostEvent
}

sealed interface FeedHostEffect : UiEffect {
    data class ShowError(val message: String) : FeedHostEffect   // non-sticky snackbar
}
```

- `SelectFeed` / `SelectList` → `setState` + persist `lastSelectedFeedUri`.
- `Load` → fetch pinned feeds; on success set `Ready`; on failure set `ErrorFallback`
  with `[Following, Discover]` and emit `ShowError`.
- Initial selection: read `lastSelectedFeedUri`, validate against the loaded set, else
  first chip (Following).
- Navigation to the Feeds placeholder follows the existing tab-internal-nav pattern:
  the screen Composable calls `LocalMainShellNavState.current.add(Feeds)` (the VM does
  not inject nav state).

## Data flow — dispatch & special cases

`SavedFeedsPrefV2.items[*]` carries `type`:

| `type` | Meaning | `FeedKind` | Fetch | Chip |
|--------|---------|-----------|-------|------|
| `"timeline"` (`value="following"`) | Following timeline | `Following` | `getTimeline(...)` | individual, `Home` glyph |
| `"feed"` | Custom / Discover generator | `Generator` | `getFeed(feed = AtUri(uri), ...)` | individual, remote avatar (Discover → local flame) |
| `"list"` | Pinned list | `List` | `getListFeed(list = AtUri(uri), ...)` | collapsed into disclosure chip |

All three fetches return `List<FeedViewPost>`, so the **existing `toFeedItemsUi()`
mapper, dedupe, and self-thread chain-merge are reused verbatim** — no mapping changes.

**Binding the feed into the pane VM:** `hiltViewModel(key = feedUri)` + a one-shot
`LaunchedEffect(feedUri) { vm.handleEvent(FeedEvent.Bind(feedUri, kind)) }`.
`FeedViewModel.load/refresh/loadMore` dispatch by the bound `kind`.

**Fallback / resilience:**

- No `savedFeedsPrefV2`, empty pinned set, or `getPreferences` failure → default chips
  `[Following, Discover]` (Discover = the known `whats-hot` generator URI constant),
  status `ErrorFallback`, non-sticky snackbar only on hard failure.
- Restore-last-selected validates the persisted URI is still pinned; else Following.

## Chip row — UI

```
[●Following] [🔥Discover] [🎨Art] [📰News]   [ Lists ⌄ ]   ⋯   [＋]
└────────── individual feed chips (scroll) ──────────┘ └ disclosure ┘  └→ Feeds (stub)
```

- **Feeds** (`Following`, `Generator`) → one `FilterChip` each, pinned order,
  horizontally scrollable (`LazyRow`, `Arrangement.spacedBy(8.dp)`,
  `contentPadding` horizontal 16.dp). Many pinned feeds simply scroll.
- **Lists** → a single disclosure chip, rendered only if ≥1 pinned list:
  - Tap → `ModalBottomSheet` titled "Pinned lists", single-selection radio list
    (avatar + name per row).
  - Select → sheet dismisses, that list becomes the active pane, chip relabels to
    `[ List: <name> ⌄ ]` with selected styling.
  - When a feed chip is active again, the disclosure chip reverts to `[ Lists ⌄ ]`
    (unselected); the sheet pre-checks the last pick on reopen.
- **Individual chip visuals:**
  - Generator feeds & user-pinned feeds: circle-clipped remote `avatar` in
    `leadingIcon` at `FilterChipDefaults.IconSize` (18 dp).
  - Following → `NubecitaIcon(Home)`; Discover (default) → `NubecitaIcon(LocalFireDepartment)`.
  - Selected: filled `secondaryContainer`, avatar **kept** (no checkmark-swap).
  - Disclosure chip uses `NubecitaIcon(ExpandMore)` as the trailing chevron.
  - Trailing `[＋]`: `AssistChip` / icon button with `NubecitaIcon(Add)` →
    `LocalMainShellNavState.add(Feeds)`.
- **Accessibility:** row gets `Role.Tab` semantics (TalkBack announces exclusive
  selection); trailing button has its own `contentDescription` ("Manage feeds");
  decorative selection-only icons use `contentDescription = null`.
- **Scroll-away:** chip row lives in the host above the `SaveableStateProvider`; an
  enterAlways-style nested-scroll connection hides on downward scroll, reveals on
  upward, re-attaching to each pane's `LazyListState`. Reveal state resets to "shown"
  on feed switch.

## Adaptive / tablet layout

Nubecita's main Feed already follows the **List-detail canonical layout** (adopted in
`openspec/changes/archive/2026-04-30-adopt-list-detail-scene-strategy`), **not** the
standalone *Feed* canonical layout. This determines where the chip row lives across
form factors.

**Canonical reference (M3):**

- *Feed* canonical layout = rail + one full-width pane with a chip Bar spanning the
  pane over a **multi-column card grid**. Suited to media/catalog content. **We do not
  adopt this** — timeline posts are text-heavy single-column reads, so Feed stays a
  single-column `LazyColumn` (no grid).
- *List-detail* canonical layout = rail + **Pane 1** (list, with its own top bar) +
  **Pane 2** (detail, with its own header). The bar is **per-pane**. **This is our
  model:** Feed = Pane 1, PostDetail = Pane 2.

**Window-size behavior** (`material3-adaptive` 1.3.0-beta02; two-pane enabled at
*medium* via `calculatePaneScaffoldDirectiveWithTwoPanesOnMediumWidth`):

| Width class | Navigation | Feed | PostDetail |
|-------------|------------|------|------------|
| Compact (<600dp) | `ShortNavigationBarCompact` (bottom) | Full-width single pane | Pushed full-screen |
| Medium (600–839dp) | `NavigationRail` (left) | List pane, **412dp** | Detail pane, or `PostDetailPaneEmptyState` placeholder |
| Expanded (≥840dp) | `NavigationRail` (left) | List pane, **440dp** | Detail pane, or placeholder |

**Chip row placement:** the chip row is a child of Feed's own `Scaffold` content, so it
spans the **full width on compact** and the **list-pane width (412/440dp) on
medium/expanded** — matching Image #3's Bar over Pane 1, and matching how the **Search
tab's input field already renders today** (the direct in-repo precedent). The detail
pane keeps its own separate `TopAppBar` (PostDetail's), per the per-pane model. No
shell-level / full-window header is introduced.

**Retention across form factors:** the shell's `NavDisplay` already installs
`rememberSaveableStateHolderNavEntryDecorator()` + `rememberViewModelStoreNavEntryDecorator()`
per nav entry, so the Feed entry owns its own `SaveableStateHolder` + `ViewModelStore`.
Option B (`hiltViewModel(key = feedUri)` + nested `SaveableStateProvider(feedUri)`)
slots into that existing infrastructure and behaves **identically on phone and tablet** —
switching feeds in the list pane retains each feed's posts + scroll at any width.

**Scroll-away header on tablet:** the collapsing chip row is tied to the **list pane's**
`LazyListState`; the detail pane is unaffected. Behavior is the same across widths.

**Screenshot coverage:** extend the existing `MainShellListDetailScreenshotTest`
baselines (compact / medium / expanded, with and without detail) to include the chip
row, so list-pane-width rendering is locked across form factors.

## Deferred follow-up: feed top app bar

Captured here so the follow-up starts from this analysis rather than rediscovering it.

**Intent:** a top app bar offering **bookmarks** (primary ask), an **overflow menu**, and
a **quick-settings shortcut** (today Settings is only reachable via Profile → Settings).

**Desired motion:** the top app bar and chip row scroll **in/out together** as one
collapsing header block (no pinned bar — preserve the full-screen feed feel).

**Why deferred (not just scope-trimming):** these are **app-wide** navigation shortcuts,
but app bars in this codebase are **per-pane** (PostDetail has its own; Feed has none;
there is no shell-level bar spanning panes). A Feed-scoped bar would render only above
the **412dp list pane** on tablet, so app-wide actions (bookmarks, settings) would sit
cramped above the list while the detail pane carries its own bar — wrong on tablet.
**Where app-wide chrome lives on tablet is a navigation-architecture decision**, not a
feed-feature one, and deserves its own design.

**Migration-friendliness:** this epic's chip row is built as a self-contained header so a
top app bar can later drop in **above** it within the same scroll-away block, without
reworking the chip row.

## Design-system glyph (standalone PR — lands first)

Add exactly one entry to `NubecitaIconName` (alphabetical, between `Language` and
`LockPerson`):

```kotlin
LocalFireDepartment("\uEF55"),
```

Then `./scripts/update_material_symbols.sh`, run
`./gradlew :designsystem:testDebugUnitTest`, regenerate/commit the subset font, and
update the icon-showcase screenshot baselines. **This is its own `:designsystem` PR**
— it owns repo-wide baseline regen and must not be bundled into feature PRs (icon-font
pitfall). `Home`, `ExpandMore`, `ChevronRight`, `Add` already exist.

## Testing

- **`PinnedFeedsRepository`** (JVM): parse `SavedFeedsPrefV2`; split by `type`; hydrate
  via mocked `getFeedGenerators`; new-account/empty/failure fallback to
  `[Following, Discover]`; preserve pinned order.
- **`FeedHostViewModel`** (JVM): pinned load → `Ready`; failure → `ErrorFallback` +
  `ShowError`; restore-last-selected (valid + stale URI); `SelectFeed`/`SelectList`
  persist; disclosure-label state transitions.
- **`FeedViewModel`** (JVM): the three dispatch branches (Following/Generator/List);
  existing tests keep covering Following.
- **`:core:preferences`** (JVM): `lastSelectedFeedUri` read/write.
- **Screenshot tests:** `FeedChipRow` (selected/unselected, avatar vs glyph, with/
  without disclosure chip), `PinnedListsSheet`, scrolled-away state.
- Existing `FeedScreen` tests stay green; the host wraps panes without changing pane
  internals.

## Risks & mitigations

- **Nested-scroll header + pane swap** — header reveal state in the host, re-attaches to
  each pane's `LazyListState`; verify no jank on switch (validate on a 120 Hz device /
  the macrobench if warranted).
- **VM memory growth** — bounded by pinned count, freed when the Feed tab pops; LRU
  eviction deferred until a power-user proves it.
- **Discover URI hardcoding** — the `whats-hot` generator URI is a constant; document it
  and keep it in one place (`:core:feeds`).
- **Lists with no avatar** — fall back to a generic list glyph in the sheet row.

## Suggested epic breakdown (bd children)

1. `:designsystem` — add `LocalFireDepartment` glyph (standalone, lands first).
2. `:core:feeds` — `PinnedFeedsRepository` + `PinnedFeedUi` model + Discover constant.
3. `:core:preferences` — `lastSelectedFeedUri`.
4. `:feature:feeds:api` stub + `:app` `@MainShell` placeholder.
5. `:feature:feed:impl` — `FeedViewModel` `Bind` + `getFeed`/`getListFeed` dispatch.
6. `:feature:feed:impl` — `FeedHost` + `FeedHostViewModel` + retention (Option B).
7. `:feature:feed:impl` — `FeedChipRow` + scroll-away + `PinnedListsSheet`.
8. Screenshot + integration polish.
```
