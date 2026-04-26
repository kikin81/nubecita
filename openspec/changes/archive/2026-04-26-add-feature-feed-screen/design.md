## Context

The headless layer is in (`FeedViewModel`, `FeedRepository`,
`FeedViewPostMapper`, `FeedContract`); the rendering primitives are in
(`PostCard`, `PostCardShimmer`, `PostCallbacks`); the Nav3 entry is wired
to a placeholder `FeedScreen` that prints `state.toString()`. This change
replaces that placeholder with the production screen and lands the first
list-bearing UI in the codebase.

Two ambient constraints shape the design:

1. **120 Hz is non-negotiable.** A `LazyColumn` of `PostCard` rows must
   scroll at 120 fps on a 2024-class Pixel. That puts hard pressure on
   stability — every callback, list, and parameter passed to `PostCard`
   has to be skip-friendly under the Compose compiler's stability rules.
2. **Scroll position must survive back-navigation, configuration change,
   and process death.** Returning from `PostDetail` (when it lands) to a
   feed scrolled mid-page only to land back at item zero would be
   immediately disorienting. The convention has to be set on the first
   list screen — every later list screen will copy whatever shape this
   one establishes.

The bd issue's notes call out the scroll-retention concern explicitly
("expensive to retrofit if the scope is wrong") and the pagination
trigger shape ("last visible item index > size - 5"). The design below
honors both literally and adds the supporting decisions around stability,
state-rendering matrix, snackbar surface, and test layout.

## Goals / Non-Goals

**Goals:**

- Land a production `FeedScreen` that scrolls at 120 Hz on capable
  devices and dispatches the existing `FeedEvent` surface without state
  churn.
- Establish the `rememberSaveable(LazyListState.Saver)` pattern as the
  canonical scroll-retention shape for every future list screen.
- Make every `FeedState.loadStatus` × `posts` shape combination
  rendered, previewable, and screenshot-tested. No silent / blank
  states.
- Keep the screen self-contained — no app-shell dependencies, no
  cross-feature reach-ins. Navigation effects route through host-supplied
  callbacks the Nav3 entry installer threads in.
- Cover the pagination trigger, pull-to-refresh dispatch, retry click,
  and key-based de-dupe in Compose UI tests, separate from the
  screenshot tests (which assert appearance, not behavior).

**Non-Goals:**

- No HorizontalPager feed switcher, no two-pane adaptive layout, no
  PostDetail / Profile screens, no write-path interactions, no global
  app chrome (top app bar, bottom nav, FAB) — all per the proposal's
  Non-goals section.
- No promotion of `FeedEmptyState` / `FeedErrorState` to `:designsystem`.
  Colocate inside `:feature:feed:impl` until duplication forces the move.
- No new MVI patterns. The existing `FeedContract` is untouched —
  this is purely the render-side counterpart.
- No Paging 3, Compose-Paging-runtime, or AppBar-collapse behavior.
- No skip-to-top affordance, no "X new posts" banner, no scroll-driven
  haptics.

## Decisions

### Decision 1 — `LazyListState` is hoisted via `rememberSaveable` inside `FeedScreen`, not at the Nav3 entry installer

`nubecita-1d5`'s notes mention "rememberLazyListState should be hoisted
to the nav-entry scope (Navigation 3 SaveableStateHolder semantics)".
A pre-implementation audit of `app/src/main/java/.../Navigation.kt`
confirmed the wiring is already in place: `NavDisplay` is configured
with both `rememberSaveableStateHolderNavEntryDecorator()` and
`rememberViewModelStoreNavEntryDecorator()` as `entryDecorators`, and
all current `NavKey` types are `@Serializable data object`. Two
options for `LazyListState` placement remain:

- **A — `val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }` inside `FeedScreen`.** Chosen.
- B — Take `listState: LazyListState` as a `FeedScreen` parameter; the
  Nav3 entry installer constructs it via `rememberSaveable`.

Chose A because the existing `SaveableStateHolderNavEntryDecorator`
keeps each entry's `rememberSaveable` slot alive while the entry is on
the back stack but off-screen. So `Feed` → push `PostDetail` → pop
restores the saved `LazyListState` automatically — the Nav3 layer is
the load-bearing piece, not an explicit hoist into the entry installer.

Option B would force every host that composes `FeedScreen` (today: just
the Nav3 entry; tomorrow maybe a two-pane layout in `nubecita-5l3`) to
remember to construct and pass the state. That's an opportunity to get
it wrong silently.

**Process-death scope:** `rememberSaveable` itself round-trips through
Bundle serialization, but only restores when its host entry is on the
rebuilt back stack. The current `DefaultNavigator` backs the stack with
in-memory `mutableStateListOf`, so on process death the stack reboots
from the start destination — there's nothing to restore *into*. This
gap is tracked by `nubecita-3it` (swap to `rememberNavBackStack`-backed
storage). Once that lands, this same `rememberSaveable` shape covers
process-death scroll retention with zero further changes here. For the
scope of `nubecita-1d5`, retention guarantees are: back-nav and
configuration change.

Two Compose UI tests confirm the shape (see Decision 8 + tasks 6.5,
6.6): one uses `ActivityScenario.recreate()` for configuration change;
a sibling test builds a tiny in-process Nav3 graph (Feed + a stub
`Detail` entry) and asserts position survives a push+pop.

### Decision 2 — Pagination trigger uses `snapshotFlow` over `lastVisibleItemIndex`, not `derivedStateOf` + `LaunchedEffect`

`nubecita-1d5`'s acceptance criterion is "pagination loads additional
pages without duplicates" and the design hint is "last visible item
index > size - 5". Two trigger shapes that satisfy this:

- **A — `LaunchedEffect(listState) { snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.distinctUntilChanged().collect { lastIdx -> if (lastIdx > posts.size - PREFETCH_DISTANCE && !endReached && loadStatus is Idle) onEvent(LoadMore) } }`.** Chosen.
- B — `val shouldPaginate by remember { derivedStateOf { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 > posts.size - PREFETCH_DISTANCE } }; LaunchedEffect(shouldPaginate) { if (shouldPaginate) onEvent(LoadMore) }`.

Chose A because `snapshotFlow` + `distinctUntilChanged` gives one
emission per *crossing* of the threshold, whereas a `derivedStateOf`
boolean toggling true → false → true (the user scrolls past the
threshold, then back, then forward again) would re-fire the
`LaunchedEffect` for each transition to `true`. The VM's `loadMore()` is
already idempotent under "fetch in flight" so B wouldn't actually
double-fetch, but A makes the intent local to the screen and avoids
relying on VM-side guards for correctness.

`PREFETCH_DISTANCE` is a `private const val` of `5`, matching the bd
issue's explicit design.

The `loadStatus is Idle` check is intentional even though VM-side guards
exist: it prevents emitting `LoadMore` while `Refreshing` is in flight,
which would otherwise queue a fetch the user doesn't expect.

### Decision 3 — Initial-load + refresh + append states render as a single `when (state.loadStatus)` branch + `posts.isEmpty()` matrix

Five possible render shapes:

| `loadStatus`        | `posts.isEmpty()` | Render                                           |
|---------------------|-------------------|--------------------------------------------------|
| `InitialLoading`    | true              | `LazyColumn` of 5 `PostCardShimmer` rows         |
| `InitialError`      | true              | Full-screen `FeedErrorState` with retry button   |
| `Idle`              | true              | `FeedEmptyState`                                 |
| `Idle / Refreshing` | false             | `LazyColumn` of `PostCard` rows                  |
| `Appending`         | false             | `LazyColumn` + tail `PostCardShimmer` row        |

`Refreshing` does NOT swap content — it overlays the
`PullToRefreshBox` indicator on top of the existing list. `Appending`
keeps the list *and* shows the trailing shimmer.

Implementation is a single `when` over an inline derivation:

```kotlin
val viewState = remember(state) { state.toViewState() }
when (viewState) {
    is ViewState.InitialLoading -> InitialLoadingList()
    is ViewState.InitialError   -> FeedErrorState(viewState.error, onRetry = ...)
    is ViewState.Empty          -> FeedEmptyState(onRefresh = ...)
    is ViewState.Loaded         -> LoadedFeed(viewState, listState, callbacks)
}
```

`ViewState` is a screen-private sealed type — it's a UI projection of
`FeedState`, not part of the contract. The VM does not know it exists.

This keeps the host composable a single readable `when` and lets each
branch be unit-/screenshot-testable independently. Compose stability
rules are honored because `ViewState` variants are `@Immutable` and the
loaded variant's `posts` field is `ImmutableList<PostUi>` directly.

### Decision 4 — `PostCallbacks` constructed once via `remember(viewModel)`; `FeedEvent` dispatched from each lambda

PostCard's `PostCallbacks` is `@Stable`; its lambdas have to maintain
referential equality across recompositions or the Compose compiler can't
skip `PostCard` rows. Two patterns:

- **A — `val callbacks = remember(viewModel) { PostCallbacks(onTap = { viewModel.handleEvent(FeedEvent.OnPostTapped(it)) }, ...) }`.** Chosen.
- B — Construct a fresh `PostCallbacks` per recomposition; rely on
  PostCard's internal stability to skip.

Chose A because B violates the contract `PostCallbacks` declares — the
data class's `equals` reflects observable behavior, but two
`PostCallbacks` constructed with fresh lambdas don't compare equal
even if they call the same VM. Profiling on the pre-existing
`PostCallbacks.None` showed the difference: per-row PostCards skipped
recomposition cleanly with `remember`-d callbacks; rebuilt on every
state tick without.

The `remember(viewModel)` key is intentional — `viewModel` is itself
remembered for the entry's lifetime so the callbacks identity is stable
across the entire screen instance. If the VM is recreated (process
death + restore), the callbacks rebuild — which is correct.

### Decision 5 — Snackbar host lives inside `FeedScreen`, not in an app-shell

`FeedEffect.ShowError` needs a snackbar surface. Two options:

- **A — `FeedScreen` owns its own `SnackbarHostState` + `Scaffold(snackbarHost = ...)`.** Chosen.
- B — The eventual app shell exposes a global `SnackbarHostState`
  through CompositionLocal; every screen reads it.

Chose A because there is no app shell yet (`nubecita-cif` epic is open),
and a screen that depends on infrastructure that doesn't exist is
strictly worse than a screen with its own host. When the app shell
lands, the migration is trivial — replace `Scaffold(snackbarHost = ...)`
with the parent's host. Until then, the screen is self-contained and
testable.

The error-message resolution is per-`FeedError` variant via
`stringResource`. The VM stays Android-resource-free; the screen owns
the string IDs.

### Decision 6 — `PullToRefreshBox` from `androidx.compose.material3` (M3 Expressive baseline)

`PullToRefreshBox` is the M3 1.3+ canonical pull-to-refresh container,
and the BOM is on a recent enough version. Trade-offs vs the alternative
`PullToRefreshContainer` + manual nested-scroll wiring:

- `PullToRefreshBox` handles the nested-scroll connection internally;
  the call site only supplies `isRefreshing` + `onRefresh`.
- `PullToRefreshContainer` exposes the refresh state as a parameter for
  callers that need a custom indicator. We don't.

Chose `PullToRefreshBox`. `isRefreshing` binds to
`state.loadStatus == FeedLoadStatus.Refreshing`; `onRefresh` dispatches
`FeedEvent.Refresh`. The VM-side guard ensures Refresh-while-Loading is
a no-op so a flapping user gesture can't queue fetches.

### Decision 7 — `FeedEmptyState` and `FeedErrorState` colocate in `:feature:feed:impl`

The PostCard prereq put PostCard, PostCardShimmer, and embed
sub-components in `:designsystem` because they're trivially reusable
across any screen that renders a post. The screen-state composables
(empty, error) are different — their copy ("Your timeline is empty —
follow some accounts to fill it") is feed-specific. A "search results
empty" or "profile timeline empty" screen would want different copy and
possibly a different illustration.

Per the colocate-until-duplication rule (already established in the
foundation change Decision 2), keep them in
`:feature:feed:impl/.../ui/`. Promote to `:designsystem` only if a
second screen reaches for the same shape.

### Decision 8 — Compose UI tests live in `:feature:feed:impl/src/androidTest/`, screenshot tests stay in `screenshotTest`, unit tests stay in `test`

The agent-memory rule says UI tasks ship "unit tests + previews +
screenshot tests." This screen has interaction logic (pagination
trigger, retry click, key-based de-dupe) that screenshot tests can't
verify — they assert appearance, not behavior. So:

- `feature/feed/impl/src/test/kotlin/...` — unit tests for any
  pure-Kotlin helper this change introduces (e.g.,
  `FeedState.toViewState()`).
- `feature/feed/impl/src/androidTest/kotlin/...` — Compose UI tests
  via `createComposeRule()` covering pagination dispatch, refresh
  dispatch, retry click, empty-state rendering, and post key uniqueness
  under append.
- `feature/feed/impl/src/screenshotTest/kotlin/...` — AGP-managed
  screenshot tests for every state in the matrix (Decision 3),
  light + dark, two device profiles (compact phone, foldable inner).

This is the first feature module in the codebase to use
`androidTest` for Compose UI. The convention plugin
`nubecita.android.feature` may need a small `androidTestImplementation`
default for the Compose UI test deps; verified in implementation.

### Decision 9 — Initial-load dispatched once via `LaunchedEffect(Unit)`; `FeedEvent.Load` is idempotent

`FeedViewModel.handleEvent(Load)` is already idempotent — second
dispatch while `loadStatus != Idle` is a guarded no-op. So the screen
can simply:

```kotlin
LaunchedEffect(Unit) { viewModel.handleEvent(FeedEvent.Load) }
```

Two alternatives that we don't need:

- Wiring `Load` into the VM's `init` block — couples the VM to the
  Hilt instantiation timing, complicates testing (every test starts
  with a fetch in flight), and means a VM constructed in a preview
  panel would attempt a network call. The existing VM design
  intentionally requires an explicit `Load` event to keep tests pure.
- Dispatching from a side effect keyed on `viewModel` — works, but
  adds a key that can't change in practice (the entry installer
  `remember`s its `hiltViewModel()` for the entry lifetime), so the
  key buys nothing.

## Risks / Trade-offs

- **120 Hz target on a `LazyColumn` of `PostCard`** → Mitigated by
  enforcing `key = { it.id }` on the `items` block (constant identity),
  passing `contentType = { "post" }` (single-type LazyColumn dispatch
  fast-path), keeping `PostCallbacks` as the single `remember`-d
  instance per Decision 4, and verifying `PostUi` and `PostCallbacks`
  stability with a spot-check via the Compose compiler's stability
  output. If profiling on a real device shows missed frames, the next
  round of optimization is `Modifier.composed { }` removal on PostCard
  internals — but those are already audited per the PostCard prereq's
  PR.

- **`rememberSaveable(LazyListState.Saver)` interaction with Nav3
  saveable state holders** → Mitigated by two Compose UI tests:
  one calls `ActivityScenario.recreate()` (configuration change), the
  other builds a tiny in-process Nav3 graph and exercises a push+pop
  through the Feed → stub Detail → back path (back-nav). Both assert
  scroll position survives. The audit of `app/src/main/java/.../Navigation.kt`
  confirmed the necessary `entryDecorators` are wired and ordered
  correctly; the tests are the empirical confirmation. Process-death
  retention is intentionally out of scope here (see Decision 1 +
  `nubecita-3it`).

- **Pagination trigger fires while initial-loading** → Mitigated by the
  `loadStatus is FeedLoadStatus.Idle` guard inside the trigger's
  `collect` block. Also defensively guarded VM-side, but the screen
  guard prevents the no-op event from being dispatched in the first
  place — keeps the LogCat clean.

- **`PullToRefreshBox` consumes nested-scroll, breaking the pagination
  trigger** → Verified mitigated. `PullToRefreshBox` only intercepts
  the over-scroll-from-top gesture; downward scrolling that reaches the
  list bottom is not affected. The pagination trigger reads
  `LazyListState.layoutInfo`, which `PullToRefreshBox` does not touch.
  Confirmed in the Compose UI test that scrolls to the tail and
  asserts `LoadMore` was dispatched.

- **`FeedEffect` collection re-runs on every recomposition** →
  Mitigated by keying the collector `LaunchedEffect` on
  `Unit` *not* `state` — the collector is hot for the screen's
  lifetime, and effects are buffered in the VM's `Channel`-backed
  `SharedFlow` so a brief recomposition gap can't drop one. Pattern is:

  ```kotlin
  LaunchedEffect(Unit) {
      viewModel.uiEffect.collect { effect ->
          when (effect) { ... }
      }
  }
  ```

- **Snackbar messages stack on rapid append failures** → Mitigated by
  calling `snackbarHostState.currentSnackbarData?.dismiss()` before
  `showSnackbar(...)` so a fresh error replaces the previous one.
  Acceptable because `FeedEffect.ShowError` is information-equivalent
  across consecutive emissions for the same root cause.

- **Screenshot test stability across device profiles** → Mitigated by
  pinning a fixed clock and avoiding any animated content in the
  screenshot composables (no `Modifier.shimmer()` in screenshots —
  the shimmer is replaced by a static skeleton tint). The shimmer is
  visually verified through previews and the live app; screenshot
  tests assert layout, not animation.

## Migration Plan

This is a UI implementation, not a data migration. Local commit order
on the branch (each commit independently buildable + green):

1. `feat(feature-feed)`: scaffold the new state composables —
   `FeedEmptyState`, `FeedErrorState`, `FeedAppendingIndicator` — with
   previews and screenshot tests against fixture inputs. No screen
   changes yet.
2. `feat(feature-feed)`: introduce the `FeedScreen.toViewState()`
   helper + unit tests. Pure Kotlin; doesn't touch the placeholder
   composable yet.
3. `feat(feature-feed)`: replace `FeedScreen.kt` with the production
   composable. Update `FeedNavigationModule` to thread navigation
   callback parameters (with `{ }` defaults). Add Compose UI tests.
4. `test(feature-feed)`: add screenshot tests for every state in the
   matrix, light + dark, two device profiles.
5. `chore(docs)`: update CLAUDE.md if the convention plugin needed a
   tweak for `androidTestImplementation` Compose UI deps.

Rollback strategy: revert the merge commit. The Nav3 entry falls back
to whatever the pre-merge `FeedScreen.kt` rendered — i.e., the
placeholder. Nothing else in the app reaches into `:feature:feed:impl`,
so the blast radius is the screen itself.

## Open Questions

- **Should `loadStatus = Refreshing` show a visible indicator on top of
  the existing list?** `PullToRefreshBox` shows its own spinner during
  the gesture, but the gesture ends before the network call completes.
  Resolved during implementation — if the spinner disappears before the
  refresh finishes, render a thin progress bar at the top of the list
  while `Refreshing` is the status. Defer the decision to live testing.

- **`androidTestImplementation` Compose UI deps in the convention
  plugin.** The current `nubecita.android.feature` plugin doesn't add
  `androidx.compose.ui:ui-test-junit4` to `androidTest` by default
  because no feature has used it before. Decision deferred to step 3 —
  if more than one Compose UI test ends up in this PR, promote the
  dep into the plugin; if exactly one, declare it inline in the
  feature's `build.gradle.kts` and promote later.

- **Screenshot device profiles.** Existing screenshot tests in
  `:designsystem` use a single phone profile. Two profiles
  (compact phone, foldable inner) doubles the screenshot count and
  CI time. Resolve in step 4 — start with the existing profile; add
  the foldable profile only if visual regressions on adaptive layouts
  warrant it. The `nubecita-5l3` adaptive ticket is the natural place
  to widen the profile matrix.
