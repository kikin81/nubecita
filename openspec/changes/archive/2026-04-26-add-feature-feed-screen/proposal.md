## Why

`FeedViewModel` already serves a fully populated `FeedState` (PR #43) and
`PostCard` already renders a `PostUi` (PR #42). The only thing standing
between Nubecita and a working Following timeline is the screen
composable that consumes them. The current `FeedScreen` is a placeholder
that calls `state.toString()`; the `Feed` Nav3 entry resolves but renders
nothing useful.

Closing that gap is the whole point of `nubecita-1d5` and unblocks every
downstream feed feature — adaptive two-pane (`nubecita-5l3`),
HorizontalPager feed switcher, deep-linking into post detail, and the
read→write loop. It's also the first screen in the codebase with hard
performance requirements (120 Hz scrolling) and the first with
non-trivial scroll-state-retention semantics under Navigation 3, so the
patterns it lands set precedent for every subsequent list-bearing screen.

## What Changes

- **Real `FeedScreen` composable** in `:feature:feed:impl`, replacing
  the placeholder:
  - `Scaffold` hosting a `SnackbarHostState` for `FeedEffect.ShowError`.
  - `PullToRefreshBox` from Material 3 wrapping the list, dispatching
    `FeedEvent.Refresh` and reflecting `FeedLoadStatus.Refreshing`.
  - `LazyColumn` of `PostCard(state.posts[i], callbacks)` items, keyed
    by `PostUi.id`. `PostCallbacks` constructed once via `remember` so
    Compose's stability inference can skip per-row recomposition.
  - Append-on-scroll trigger via a `derivedStateOf { lastVisibleIndex >
    size - 5 }` snapshot collected in a `LaunchedEffect`, dispatching
    `FeedEvent.LoadMore` exactly once per crossing.
  - Tail loading indicator (a `PostCardShimmer` row) appended to the
    `LazyColumn` while `loadStatus == Appending`.
  - `FeedEmptyState` composable when `posts.isEmpty() && loadStatus ==
    Idle`.
  - `FeedErrorState` composable rendered as a full-screen retry layout
    when `loadStatus is FeedLoadStatus.InitialError`, with a Retry
    button dispatching `FeedEvent.Retry`. Variant text per
    `FeedError.{Network, Unauthenticated, Unknown}`.
  - `LazyColumn` of `PostCardShimmer` items when `posts.isEmpty() &&
    loadStatus == InitialLoading`.
- **`LazyListState` hoisted via `rememberSaveable(LazyListState.Saver)`**
  inside `FeedScreen`. Combined with the existing
  `rememberSaveableStateHolderNavEntryDecorator` +
  `rememberViewModelStoreNavEntryDecorator` wiring in
  `app/src/main/java/.../Navigation.kt`, this gives back-nav scroll
  retention (entry stays on the back stack while `PostDetail` is on top,
  its slot is preserved, restored on pop) and configuration-change
  retention (rotation) for free. Process-death back-stack persistence is
  out of scope here — the back stack is currently in-memory
  (`mutableStateListOf` in `DefaultNavigator`), tracked by `nubecita-3it`
  as a follow-on to swap to `rememberNavBackStack`-backed storage.
- **`LaunchedEffect(Unit)` initial-load dispatch** of `FeedEvent.Load`
  on first composition. The VM's `load()` is already idempotent, so a
  repeated dispatch from configuration change is a no-op.
- **`FeedEffect` collection** in a single `LaunchedEffect(viewModel)`
  that pattern-matches `ShowError` → snackbar message,
  `NavigateToPost` / `NavigateToAuthor` → typed nav callbacks the
  Nav3 entry passes through.
- **Nav3 entry update** in `FeedNavigationModule` to thread the
  navigation callbacks (initially `{ }` no-ops since post detail and
  profile screens don't exist yet — this change does NOT introduce them).
- **Previews** for every screen state: empty, initial-loading,
  loaded, refreshing, appending, initial-error (one per `FeedError`
  variant), all in light + dark.
- **Screenshot tests** (AGP-managed `screenshotTest` source set)
  capturing the same matrix.
- **Compose UI tests** (`androidx.compose.ui.test`) covering the
  pagination trigger, pull-to-refresh dispatch, retry button click,
  empty-state rendering, and key-based list de-duplication.

## Capabilities

### New Capabilities

<!-- None — this change extends an existing capability. -->

### Modified Capabilities

- `feature-feed`: Adds the screen-layer requirements — `FeedScreen` is
  the single rendering composable for the Following timeline; pagination
  is triggered by visible-item-index proximity to the tail; pull-to-refresh
  is a Material 3 `PullToRefreshBox`; `LazyListState` is hoisted to the
  Nav3 entry scope and survives back-navigation, configuration change,
  and process death; empty / initial-loading / initial-error / appending
  states each render their own composable per `FeedState.loadStatus` and
  `posts` shape.

## Impact

**Code:**
- Modified: `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt`
  (placeholder → real implementation).
- Modified: `feature/feed/impl/src/main/kotlin/.../di/FeedNavigationModule.kt`
  (nav-callback wiring).
- New: `FeedEmptyState.kt`, `FeedErrorState.kt`, `FeedAppendingIndicator.kt`
  in `feature/feed/impl/src/main/kotlin/.../ui/` (private to the module
  per the colocate-until-duplication rule; promote to `:designsystem`
  only when a second screen needs them).
- New previews + screenshot tests + Compose UI tests under
  `feature/feed/impl/src/main/kotlin/.../FeedScreen.kt`,
  `feature/feed/impl/src/screenshotTest/kotlin/...`, and
  `feature/feed/impl/src/androidTest/kotlin/...`.

**Dependencies (likely additions to `gradle/libs.versions.toml`):**
- `androidx.compose.material3:material3` already on the catalog —
  `PullToRefreshBox` is in the existing version, no version bump needed.
- `androidx.compose.ui:ui-test-junit4` for Compose UI tests if not
  already wired in the convention plugin's `androidTestImplementation`
  defaults; verified in the implementation step.

**Conventions:**
- This change follows the agent-memory rule "UI tasks need unit tests +
  previews + screenshot tests" — the test trio is part of acceptance,
  not a follow-on.
- Sets the precedent for `LazyListState` hoisting via `rememberSaveable`
  for every future list screen. Documented inline on `FeedScreen` so
  the next list-screen author can copy the shape.

**Bd ticket coordination:**
- Closes `nubecita-1d5`.
- Unblocks `nubecita-5l3` (adaptive two-pane), `nubecita-zk2`
  (PostStat accessibility labels — easier to verify against a real
  feed screen), and any future feed-switcher ticket.
- Coordinates with `nubecita-3it` (rememberNavBackStack migration for
  process-death back-stack persistence) — independent landing order; once
  both ship, `FeedScreen` scroll position is preserved across
  back-nav, configuration change, AND process death without further
  per-screen plumbing.

**Non-goals:**
- No HorizontalPager feed switcher (Following / Discover / custom).
  Single Following timeline only — feed-switcher is a follow-on change.
- No adaptive two-pane list-detail layout — that's `nubecita-5l3`,
  which this change unblocks.
- No write-path interactions. The `OnLikeClicked` / `OnRepostClicked` /
  `OnReplyClicked` / `OnShareClicked` callbacks dispatch the existing
  `FeedEvent` variants whose VM handlers are no-ops; visual feedback is
  out of scope.
- No `PostDetail` or `Profile` screens. `NavigateToPost` /
  `NavigateToAuthor` effects route to host-supplied callbacks that
  default to `{ }` no-ops until those screens land.
- No global top app bar, bottom nav, or FAB. The reference design
  scaffolds those, but they belong to the app-shell ticket
  (`nubecita-cif` epic). `FeedScreen` is a content-only Scaffold whose
  parent will eventually host the app-level chrome.
- No scroll-to-top affordance, new-post indicator, or "X new posts"
  banner.
- No deep-link handling on the `Feed` `NavKey`.
- No promotion of `FeedEmptyState` / `FeedErrorState` to `:designsystem`.
  Colocate inside `:feature:feed:impl`; promote only when a second
  consumer needs them.
- No Paging 3 — manual cursor pagination per the explicit design.
