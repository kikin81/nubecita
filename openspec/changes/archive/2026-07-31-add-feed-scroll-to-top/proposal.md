## Why

After the m28.x epic shipped the full Following timeline (cross-author clusters, same-author chains, post-detail click-through), the feed is finally a place users want to spend real time scrolling through. The next missing affordance is the one Bluesky's official Android client and every iOS-style app already provides: a way to **return to the top** without flicking back through hundreds of items by hand.

Two equivalent gestures exist in the wild — a floating up-arrow FAB after scrolling past N items (Android-style, discoverable), and a re-tap on the currently-active bottom-nav tab (iOS-style, invisible muscle memory). Power users use both interchangeably. Shipping only one would leave a third of users without their preferred path.

The architectural challenge is keeping `:feature:feed:impl` from leaking navigation knowledge upward (it doesn't know about MainShell or the bottom nav) while still letting MainShell's tab-tap reach into a feature's `LazyListState`. The cleanest seam is a `CompositionLocal<SharedFlow<Unit>>` provided by MainShell and observed by any feature screen that opts in — feature-agnostic by construction.

## What Changes

### Contract layer (`:core:common:navigation`)

- Add `LocalScrollToTopSignal: ProvidableCompositionLocal<SharedFlow<Unit>>` — a hot SharedFlow (replay=0, single-slot buffer with `BufferOverflow.DROP_OLDEST`) that fires `Unit` when the current top-level tab is re-tapped. Default value is an empty `SharedFlow<Unit>` so previews / screenshot tests / detached previews don't need to wrap composition in a custom provider.

### Producer (`:app/MainShell`)

- MainShell creates a `MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)`, exposes a `remember`-d `asSharedFlow()` view via `LocalScrollToTopSignal` in the existing `CompositionLocalProvider` block, and updates the tab-tap handler so that re-tapping the already-active tab calls `tryEmit(Unit)`. Switching tabs (`tappedTab != activeTab`) navigates as before — no signal fires on a fresh tab landing. The single-slot buffer is required so `tryEmit` always succeeds (rendezvous semantics drop emissions during the LaunchedEffect-restart window between recompositions, which manifests as silently-ignored taps).

### Consumer (`:feature:feed:impl/FeedScreen`)

- Collect the signal in a `LaunchedEffect` keyed on `(signal, listState)` and call `listState.animateScrollToItem(0)`.
- Add a `KeyboardArrowUp` `SmallFloatingActionButton` to the existing `Scaffold.floatingActionButton` slot, gated by `derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD }`. Use `AnimatedVisibility` with `fadeIn() + scaleIn()` enter / `fadeOut() + scaleOut()` exit. The FAB's onClick calls the same scroll function the signal collector uses.
- Add a string resource `feed_scroll_to_top` for the FAB content description.

### Tests

- New screenshot fixture `loaded-with-fab-visible-light` rendering a FeedScreen pre-scrolled past `SCROLL_TO_TOP_FAB_THRESHOLD` so the FAB is captured. (Existing `loaded-light` / `loaded-dark` fixtures stay byte-for-byte unchanged because they render at scroll position 0 where the FAB is hidden.)
- Optional: a small Compose UI / instrumentation test asserting "tap-to-top via signal scrolls a pre-scrolled list back to position 0". Defer to a follow-up if the unit-test surface is sufficient.

## Capabilities

### New Capabilities

None — the scroll-to-top contract is a small addition to the existing `core-common-navigation` capability surface.

### Modified Capabilities

- `core-common-navigation`: adds `LocalScrollToTopSignal` as a public `CompositionLocal<SharedFlow<Unit>>`. MainShell-side producer wiring is part of the same capability (MainShell already owns the `CompositionLocalProvider` for `LocalMainShellNavState`).
- `feature-feed`: `FeedScreen` opts into the signal as a consumer and gains a scroll-to-top FAB. Existing `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` are unchanged.

## Impact

- **Affected modules**: `:core:common:navigation` (new file, no new deps), `:app` (MainShell tab-tap wiring), `:feature:feed:impl` (FAB + signal collector + new string resource + new screenshot fixture).
- **Affected specs**: `core-common-navigation` (delta — new requirement on `LocalScrollToTopSignal`), `feature-feed` (delta — new requirement on FeedScreen consuming the signal + rendering the FAB).
- **Out of scope for this change**:
  - Per-feature opt-in beyond Feed. `:feature:profile:impl`, `:feature:search:impl`, `:feature:postdetail:impl`, etc. file their own follow-up tickets if/when they ship lists worth scrolling.
  - Animation tuning beyond the M3 default `animateScrollToItem`. No custom motion graphs (matches the project's "M3 vocabulary only, no hand-rolled primitives" rule).
  - A "scroll-to-bottom" inverse affordance (different gesture, different scope, defer to a separate proposal).
  - Tab-switch behavior (e.g. "switching to a tab restores scroll position"). The tab-switch path is unchanged; only re-tap fires the signal.
- **Dependencies**: no new library deps. `kotlinx-coroutines-core` already on the catalog provides `MutableSharedFlow` / `asSharedFlow()`.
- **Backwards compatibility**: additive. MainShell gains one extra `CompositionLocalProvider` value; FeedScreen gains a `Scaffold.floatingActionButton` slot. Existing screen-level tests and screenshot baselines are unaffected (the FAB is hidden at scroll position 0 where every existing fixture renders).

## Non-goals

- **Custom scroll animation curves.** `LazyListState.animateScrollToItem(0)` is the M3 default and produces the right behavior on 120hz panels.
- **VM-side state for "is the FAB visible".** The FAB visibility derives from `listState.firstVisibleItemIndex` — a Compose runtime value that doesn't belong in the VM. The VM stays pure; per the project's MVI conventions, `LocalScrollToTopSignal` is consumed at the screen Composable layer, NOT injected into the VM.
- **A new `:designsystem` composable.** The FAB uses `androidx.compose.material3.SmallFloatingActionButton` directly. No design-system wrapping is justified by one consumer.
- **Persisting scroll position across process death.** Out of scope; orthogonal to scroll-to-top.
