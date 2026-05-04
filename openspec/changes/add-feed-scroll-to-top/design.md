## Context

Two complementary user gestures need to converge on a single behavior — "scroll the visible feature's list back to position 0":

- **FAB tap**: a `SmallFloatingActionButton` appearing in `FeedScreen` after the user has scrolled past N items. Discoverable, matches Bluesky's official Android client convention.
- **Bottom-nav tab re-tap**: tapping an already-active top-level tab. Invisible muscle memory, matches iOS convention.

Constraint set:

- `:feature:feed:impl` MUST NOT import any MainShell type. The feed is a navigation consumer, not a navigation co-author.
- The bottom-nav tab handler lives in `:app/MainShell` (`MainShellNavState` machinery). MainShell MUST NOT import `:feature:feed:impl` (already enforced — MainShell only depends on `:feature:*:api` modules per the api/impl convention from CLAUDE.md).
- ViewModels CANNOT access `CompositionLocal` (the per-CLAUDE.md MVI rule that explicitly forbids `LocalMainShellNavState` injection into VMs). Whatever signal carries the scroll request, it must terminate at the screen Composable.
- Future features (`:feature:profile:impl`, `:feature:search:impl`) should opt into the same gesture without requiring any new contract design — repeat the consumer pattern, done.

The clean seam that satisfies all four constraints: a `CompositionLocal<SharedFlow<Unit>>` provided by MainShell and observed by any feature screen that wants the gesture.

## Goals / Non-Goals

**Goals:**

- Tapping the bottom-nav tab while already on Feed scrolls Feed to position 0.
- Tapping the bottom-nav tab to SWITCH from another tab to Feed does NOT auto-scroll (the destination tab restores its last position; only re-tap is overloaded).
- After scrolling past N items in Feed, a small `KeyboardArrowUp` FAB appears via `AnimatedVisibility` in the top-right region (M3 FAB default position). Tapping it scrolls to position 0; the FAB then fades out.
- Other features can opt in by repeating the consumer pattern (~5 lines of Composable code) without any module-graph changes or new contract design.
- VM-pure architecture preserved. `FeedViewModel` doesn't gain a single field, event, or effect.

**Non-Goals:**

- **VM-side scroll state**. List scroll position is a Compose runtime concern (`LazyListState`), not a VM state field. Routing it through the VM would couple presentation lifecycle to UI lifecycle for no benefit.
- **Custom motion graphs.** `animateScrollToItem(0)` is the M3 default and produces the right curve at 120hz. No custom physics.
- **Per-tab-restore navigation logic.** Tab switching's "restore last position" behavior is owned by Nav3 + the existing `LazyListState` `rememberSaveable` saver. Out of scope.
- **A new `:designsystem` composable wrapping `SmallFloatingActionButton`.** One consumer doesn't justify a wrapper. If three feature modules end up shipping the same FAB shape, lift it then.
- **Tab-switch as a scroll-to-top trigger.** Only RE-TAP fires the signal. Switching tabs leaves the destination tab's scroll position untouched (the user expects to return to where they left off).
- **A "scroll-to-bottom" inverse affordance.** Different gesture, different motivation; file a separate proposal if needed.

## Decisions

### Decision 1: `CompositionLocal<SharedFlow<Unit>>` over alternatives

**Choice:** Add `LocalScrollToTopSignal: ProvidableCompositionLocal<SharedFlow<Unit>>` to `:core:common:navigation`. MainShell creates a `MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 0)` and exposes its read-only view via `CompositionLocalProvider` alongside the existing `LocalMainShellNavState`. Any feature screen that wants the gesture collects the flow inside a `LaunchedEffect`.

**Why this over alternatives:**

- *Inject a `MutableStateFlow<Int>` "tab-retap counter" into ViewModels via Hilt* — VMs would have to expose a flow that the screen Composable observes, leaking navigation lifecycle into the presentation contract. Also breaks the CLAUDE.md MVI rule that VMs don't see CompositionLocals. Rejected.
- *Use a `Channel<Unit>` instead of `SharedFlow`* — `Channel` is single-consumer; if the same MainShell composition hosts multiple list-bearing feature screens (e.g., split-pane on a tablet), a Channel would deliver the signal to exactly one of them. SharedFlow with replay=0 broadcasts to all current subscribers, which is what we want.
- *A counter / `State<Int>` that increments per re-tap* — works but more complex; `LaunchedEffect` would have to key on the counter and consumers would need defensive logic for the "0 → 0" no-event case. SharedFlow's "fire and forget if no listener" semantic is clearer.
- *Nav3 reselect callback (if such exists)* — Nav3's API doesn't currently expose a tab-reselect callback; emulating it via the SharedFlow is straightforward and forward-compatible if Nav3 adds one later (the SharedFlow becomes an implementation detail behind the public `LocalScrollToTopSignal` contract).

The hot SharedFlow with `replay = 0, buffer = 0` is "fire and forget if no one listens." Tabs that aren't currently composed don't have a collector running; their re-tap signals are dropped. Tabs that ARE composed see the signal in `LaunchedEffect` and call `animateScrollToItem(0)`. Exactly the right behavior.

### Decision 2: Default empty SharedFlow so previews don't need a provider

**Choice:** `compositionLocalOf { MutableSharedFlow<Unit>(replay = 0).asSharedFlow() }` — the default is an empty silent flow.

**Why this over `compositionLocalOf { error("not provided") }`:**

- Existing screenshot fixtures and `@Preview` composables for `FeedScreen` would either need to be updated to wrap in a custom `CompositionLocalProvider`, or break with a runtime error. Both options are bad: the former churns existing baselines for no behavioral reason, the latter regresses preview ergonomics.
- The default flow never emits, so the `LaunchedEffect` collector runs forever waiting for an emission that never comes. Semantically equivalent to "tap-to-top is unwired" — the FAB still works (it doesn't depend on the flow), and the tab re-tap behavior simply doesn't fire (correct: there's no MainShell to fire it from in a preview).

### Decision 3: MainShell only emits on RE-TAP, never on tab SWITCH

**Choice:** MainShell's tab-tap handler check: `if (tapped == activeTab) tryEmit(Unit) else navigateToTab(tapped)`.

**Why this over alternatives:**

- *Always emit on tab tap (switch or re-tap)* — would auto-scroll Feed to position 0 every time the user switches FROM Profile back TO Feed, even though the user explicitly wanted to return to where they were. Wrong UX; defeats the "tab restores last scroll position" mental model that Nav3's per-tab back-stack already provides.
- *Emit only on long-press of an active tab* — discoverability is bad; iOS users don't expect long-press for this gesture.

The "tap-already-active = scroll-to-top" pattern is what every iOS-style and Android-style social app does. No documentation needed; users discover it by accident on the first re-tap.

### Decision 4: Threshold gates the FAB at `firstVisibleItemIndex >= 5`

**Choice:** `private const val SCROLL_TO_TOP_FAB_THRESHOLD = 5`. The FAB shows when `listState.firstVisibleItemIndex >= 5`.

**Why this over alternatives:**

- *`firstVisibleItemIndex > 0`* — would show the FAB after one tiny scroll. Too eager; fights the user.
- *Pixel offset (e.g., `> screen-height-worth-of-pixels`)* — more precise but introduces dependency on screen density and orientation. Item-count threshold is cleaner.
- *Absolute scroll distance via `lazyListState.layoutInfo`* — same density dependency.

Five items maps to roughly one screen of feed posts on a phone, which is the right "the user definitely scrolled" signal. Tunable; if product feedback says it's wrong, it's a one-line change.

### Decision 5: FAB lives in FeedScreen's existing `Scaffold.floatingActionButton` slot, NOT in MainShell

**Choice:** `FeedScreen`'s existing `Scaffold` composable hosts the FAB. MainShell doesn't grow a FAB slot.

**Why this over alternatives:**

- *MainShell hosts the FAB* — would force MainShell to know "Feed is currently the active tab AND wants a scroll-to-top FAB AND has scrolled past N items." MainShell would have to either reach into the feature's `LazyListState` (impossible without leaking) or accept a flag from the feature (a cross-module callback). Both worse than letting Feed own its own Scaffold slot.
- *A separate `MainShellFloatingActionButton` slot exposed via `LocalMainShellFab`* — over-engineered for one consumer. If three features ship FABs, file a separate proposal to consolidate.

The Feed's `Scaffold` already exists; adding a `floatingActionButton` slot is one parameter. PostDetail does the same thing with its Reply FAB (m28.5.2). Pattern is established.

### Decision 6: `derivedStateOf` for FAB visibility, not direct read of `firstVisibleItemIndex`

**Choice:** `val showFab by remember { derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD } }`.

**Why this over a direct read:**

- A direct `listState.firstVisibleItemIndex` read in composition would re-trigger the surrounding Composable on EVERY scroll position change (60-120/sec during a fling). `derivedStateOf` debounces: the surrounding Composable only invalidates when the boolean flips, i.e., when the user crosses the threshold (zero to a few times per scroll session).

This is the standard Compose-perf pattern for derived booleans driven by frequently-changing state. (Same pattern the m28.5.2 FAB uses for its visibility gating.)

### Decision 7: Consumer pattern is opt-in per feature

**Choice:** No global "every feature collects this signal" hook. Each feature that wants the gesture adds the `LaunchedEffect` collector + FAB explicitly.

**Why this over alternatives:**

- *Auto-wire via a `ScreenWithScrollToTop` higher-order composable* — abstracts away exactly the lines that future readers need to see (the `LaunchedEffect`, the listState binding, the FAB position). Worse for code review and onboarding.
- *Centralize in `:designsystem` as a `NubecitaScreen` wrapper* — same over-abstraction, plus would force `:designsystem` to know about `LocalScrollToTopSignal` (one more knowledge dependency).

Five lines of opt-in code per feature is cheaper than abstracting them away. If at three features we notice the lines are identical, lift then.

## Risks / Trade-offs

- **Hot flow with no replay can drop emissions.** If MainShell emits before any feature has subscribed (e.g., during a configuration change), the emission is dropped. Acceptable: the user wasn't on Feed when the signal fired (the LaunchedEffect collector wasn't running), so there's no scroll-to-top expectation.
- **Tab-switch + re-tap race.** If the user double-taps Feed's tab quickly during a switch animation, the second tap might be interpreted as either re-tap (scroll signal) or tab switch (no-op). The handler uses the post-mutation `activeTab`, so the second tap correctly sees Feed-as-active and emits the signal. Verified by the existing tab-tap test pattern in MainShell.
- **`derivedStateOf` allocation.** `remember { derivedStateOf { ... } }` allocates a `State<Boolean>` per composition. One per FeedScreen instance — negligible.
- **Future feature opt-in churn.** When `:feature:profile:impl` adds the gesture, it duplicates the LaunchedEffect + FAB pattern. If the duplication grows past 3 sites, lift to a `:designsystem` helper. Acceptable cost for v1.
- **Multiple list-bearing screens in one feature.** If Feed ever hosts a TabRow with multiple LazyColumns (e.g., Following / Discover sub-tabs), the screen has to decide which list responds to the signal. Punted to v2; for now Feed has one canonical list.

## Open Questions

None at proposal time. Decisions 1–7 cover every architectural choice raised in the brainstorming round. Implementation may surface visual nits with the FAB position relative to the bottom-nav suite (overlap on small screens?), but those are visual tuning, not spec re-opens.
