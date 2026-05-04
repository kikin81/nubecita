## ADDED Requirements

### Requirement: `FeedScreen` consumes `LocalScrollToTopSignal` and exposes a scroll-to-top FAB

`FeedScreen` SHALL opt into the `core-common-navigation` scroll-to-top contract. Two coordinated behaviors:

1. **Signal collector.** A `LaunchedEffect` keyed on `(LocalScrollToTopSignal.current, listState)` collects the flow and calls `listState.animateScrollToItem(0)` on each emission. Both keys are required so the collector restarts cleanly if either reference changes (e.g., a new MainShell composition or a fresh `LazyListState` from `rememberSaveable` after process death).
2. **FAB.** A `SmallFloatingActionButton` rendered in the existing `Scaffold.floatingActionButton` slot, gated by `derivedStateOf { listState.firstVisibleItemIndex >= SCROLL_TO_TOP_FAB_THRESHOLD }` (threshold = 5). The FAB uses an `AnimatedVisibility` wrapper with `fadeIn() + scaleIn()` enter and `fadeOut() + scaleOut()` exit. Its content is an `Icon(Icons.Default.KeyboardArrowUp, ...)` with a localized content description (`R.string.feed_scroll_to_top`). The FAB's `onClick` calls `listState.animateScrollToItem(0)` — the same scroll function the signal collector uses.

The `FeedViewModel` is unchanged. No new state field, no new event, no new effect. Both the FAB visibility derivation and the signal collector run at the screen Composable layer; they don't cross the VM boundary (per the `mvi-foundation` capability's "VMs don't see CompositionLocals" rule).

#### Scenario: FAB is hidden at scroll position 0

- **WHEN** the feed renders fresh (or after `animateScrollToItem(0)` completes)
- **THEN** `firstVisibleItemIndex == 0`, the `derivedStateOf` boolean is `false`, and `AnimatedVisibility` collapses the FAB. No floating action button is visible. Existing `loaded-light` / `loaded-dark` screenshot baselines remain byte-for-byte unchanged.

#### Scenario: FAB appears after scrolling past the threshold

- **WHEN** the user scrolls until `firstVisibleItemIndex >= 5`
- **THEN** the `derivedStateOf` boolean flips to `true` and `AnimatedVisibility` runs the enter transition (`fadeIn + scaleIn`), revealing the `SmallFloatingActionButton` at the M3 default position. A new screenshot fixture captures this state.

#### Scenario: FAB tap scrolls to top

- **WHEN** the FAB is visible and the user taps it
- **THEN** `listState.animateScrollToItem(0)` is invoked. After the animation completes, `firstVisibleItemIndex == 0` and the FAB fades out via the exit transition.

#### Scenario: Re-tapping the active bottom-nav tab scrolls Feed to top

- **WHEN** the user is on the Feed tab with `firstVisibleItemIndex > 0` and re-taps the Feed tab
- **THEN** MainShell emits `Unit` via `LocalScrollToTopSignal`, the `FeedScreen` `LaunchedEffect` collector receives the emission, and `listState.animateScrollToItem(0)` runs. The user observes the same scroll-to-top behavior as a FAB tap.

#### Scenario: FAB visibility derives via `derivedStateOf`, not a direct read

- **WHEN** the user scrolls with `firstVisibleItemIndex` changing rapidly (e.g., 60 times per second during a fling)
- **THEN** the `FeedScreen` Composable does NOT recompose 60 times per second. The `derivedStateOf` boolean only invalidates when its value actually flips (zero or a few times per scroll session), debouncing the surrounding composition correctly.

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this change
- **THEN** there are NO additions or modifications. Scroll-to-top is purely a screen-layer concern.

### Requirement: Existing FeedScreen test surface is unchanged

The introduction of the scroll-to-top consumer SHALL NOT modify any existing `FeedViewModel` unit test, any existing `FeedScreen` screenshot fixture (light or dark), or any existing string resource that didn't already exist. The change is additive: one new string (`feed_scroll_to_top` content description) and one new screenshot fixture covering the FAB-visible state.

#### Scenario: Existing screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after this change merges
- **THEN** every fixture that existed pre-merge matches its baseline byte-for-byte. The new `loaded-with-fab-visible-light` (or equivalent) fixture is the only addition.

#### Scenario: VM tests unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after this change merges
- **THEN** every existing test method passes without source-level modification.
