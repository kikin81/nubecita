## ADDED Requirements

### Requirement: `FeedScreen` consumes `LocalScrollToTopSignal` and hosts the compose FAB

`FeedScreen` SHALL opt into the `core-common-navigation` scroll-to-top contract AND host the compose-new-post entry point in its Scaffold's `floatingActionButton` slot. Two coordinated behaviors:

1. **Signal collector.** A `LaunchedEffect` keyed on `(LocalScrollToTopSignal.current, listState)` collects the flow and calls `listState.animateScrollToItem(0)` on each emission. Both keys are required so the collector restarts cleanly if either reference changes (e.g., a new MainShell composition or a fresh `LazyListState` from `rememberSaveable` after process death). This behavior is preserved verbatim from the prior scroll-to-top change.
2. **Compose FAB.** A badge-wrappable, icon-only FAB (`FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — NOT `ExtendedFloatingActionButton`, which the `:core:drafts` follow-up cannot cleanly badge) rendered in the existing `Scaffold.floatingActionButton` slot whenever the feed view-state is `FeedScreenViewState.Loaded`. The FAB size SHALL adapt to width class: `FloatingActionButton` (56dp) at Compact width, `LargeFloatingActionButton` (96dp) at Medium and Expanded widths per the M3 expressive guidance. The FAB MUST NOT be gated on scroll position — it is visible at `firstVisibleItemIndex == 0` and at any deeper position. Its content is an `Icon(Icons.Default.Edit, ...)` (or M3's expressive create-equivalent) with a localized content description (`R.string.feed_compose_new_post`). Its `onClick` invokes a width-class-conditional launcher: at Compact width it pushes `ComposerRoute()` onto `LocalMainShellNavState.current`; at Medium/Expanded widths it transitions the `MainShell`-scoped composer-launcher state holder to `Open(replyToUri = null)`. The FAB MUST NOT appear over `InitialLoading`, `Empty`, or `InitialError` view-states.

The `FeedViewModel` is unchanged. No new state field, no new event, no new effect. Both the compose FAB onClick and the signal collector run at the screen Composable layer; they don't cross the VM boundary (per the `mvi-foundation` capability's "VMs don't see CompositionLocals" rule).

#### Scenario: Compose FAB visible over a loaded feed at scroll position 0

- **WHEN** the feed has rendered into `FeedScreenViewState.Loaded` and `firstVisibleItemIndex == 0`
- **THEN** the `Scaffold.floatingActionButton` slot renders the compose FAB carrying `Icons.Default.Edit` and `contentDescription == R.string.feed_compose_new_post`

#### Scenario: Compose FAB visible after deep scroll

- **WHEN** the user scrolls so that `firstVisibleItemIndex >= 20`
- **THEN** the compose FAB remains visible without any `AnimatedVisibility` enter/exit transition firing — its visibility is independent of scroll position

#### Scenario: Compose FAB hidden during InitialLoading

- **WHEN** the feed view-state is `FeedScreenViewState.InitialLoading`
- **THEN** the `Scaffold.floatingActionButton` slot is empty (no compose FAB rendered)

#### Scenario: Compose FAB hidden during Empty / InitialError

- **WHEN** the feed view-state is `FeedScreenViewState.Empty` or `FeedScreenViewState.InitialError`
- **THEN** the `Scaffold.floatingActionButton` slot is empty (no compose FAB rendered)

#### Scenario: Compose FAB tap pushes ComposerRoute at Compact

- **WHEN** the compose FAB is visible, the active `WindowWidthSizeClass` is `COMPACT`, and the user taps it
- **THEN** `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = null))` is invoked exactly once and the composer-launcher state holder is NOT mutated

#### Scenario: Compose FAB tap opens Dialog overlay at Medium/Expanded

- **WHEN** the compose FAB is visible, the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED`, and the user taps it
- **THEN** the `MainShell`-scoped composer-launcher state holder transitions to `Open(replyToUri = null)` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: Compose FAB component is icon-only and badge-wrappable

- **WHEN** the source of the compose FAB is inspected
- **THEN** the FAB is `FloatingActionButton`, `LargeFloatingActionButton`, or `SmallFloatingActionButton` — it is NOT `ExtendedFloatingActionButton`

#### Scenario: Compose FAB scales to Large at Expanded width

- **WHEN** the active `WindowWidthSizeClass` is `EXPANDED` and the feed view-state is `Loaded`
- **THEN** the rendered FAB is `LargeFloatingActionButton` (96dp), not the Compact-default `FloatingActionButton`

#### Scenario: Re-tapping the active bottom-nav tab still scrolls Feed to top

- **WHEN** the user is on the Feed tab with `firstVisibleItemIndex > 0` and re-taps the Feed tab
- **THEN** MainShell emits `Unit` via `LocalScrollToTopSignal`, the `FeedScreen` `LaunchedEffect` collector receives the emission, and `listState.animateScrollToItem(0)` runs

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this change
- **THEN** there are NO additions or modifications. The compose FAB tap path does not pass through the VM.

### Requirement: Each post in the feed exposes a reply tap target that opens the composer in reply mode via the width-class-conditional launcher

The system SHALL render a reply affordance on every `PostCard` in `FeedScreen`'s loaded list. Tapping the affordance MUST invoke the same width-class-conditional `launchComposer(replyToUri = post.uri.toString())` helper used by the Feed-tab compose FAB (see the *Adaptive container* requirement in `feature-composer`'s spec) — at Compact width that pushes `ComposerRoute(...)` onto `LocalMainShellNavState.current`; at Medium/Expanded width it transitions the `MainShell`-scoped composer-launcher state to `Open`. The affordance MUST be reachable through the existing `PostCard` action row (no new card-level state shape). The tap path MUST NOT involve `FeedViewModel` — navigation flows from the card's onClick lambda through a screen-level handler that calls the launcher directly. An earlier draft of this requirement hard-coded `LocalMainShellNavState.current.add(...)` for both width classes; that conflicted with the adaptive-container requirement and is corrected here.

#### Scenario: Reply tap at Compact width pushes ComposerRoute

- **WHEN** the active `WindowWidthSizeClass` is `COMPACT` and the user taps the reply affordance on a `PostCard` whose backing `PostUi.uri == AtUri("at://did:plc:abc/app.bsky.feed.post/xyz")`
- **THEN** `LocalMainShellNavState.current.add(ComposerRoute(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz"))` is invoked exactly once and the composer-launcher state holder is NOT mutated

#### Scenario: Reply tap at Medium/Expanded width opens the launcher overlay

- **WHEN** the active `WindowWidthSizeClass` is `MEDIUM` or `EXPANDED` and the user taps the reply affordance on the same `PostCard`
- **THEN** the `MainShell`-scoped composer-launcher state holder transitions to `Open(replyToUri = "at://did:plc:abc/app.bsky.feed.post/xyz")` exactly once and `LocalMainShellNavState.current` is NOT mutated

#### Scenario: VM is unchanged

- **WHEN** the source tree of `FeedViewModel` / `FeedState` / `FeedEvent` / `FeedEffect` is diffed before / after this requirement
- **THEN** there are NO additions related to reply navigation. The reply tap is a pure screen-layer concern.

#### Scenario: Reply affordance present on every loaded post

- **WHEN** `FeedScreen` is in `FeedScreenViewState.Loaded` with N posts visible
- **THEN** every `PostCard` exposes the reply tap target — no card is special-cased to omit it
