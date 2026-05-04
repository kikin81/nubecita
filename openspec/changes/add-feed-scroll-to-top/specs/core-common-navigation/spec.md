## ADDED Requirements

### Requirement: `LocalScrollToTopSignal` exposes a feature-agnostic tap-to-top broadcast

The system SHALL expose a `ProvidableCompositionLocal<SharedFlow<Unit>>` named `LocalScrollToTopSignal` from `:core:common:navigation`. The contract:

- The flow MUST be a hot `SharedFlow<Unit>` with `replay = 0` and no extra buffer (`extraBufferCapacity = 0`). Emissions while no subscriber is collecting MUST be dropped silently.
- The default value MUST be an empty `SharedFlow<Unit>` (a `MutableSharedFlow<Unit>(replay = 0).asSharedFlow()`) so previews / screenshot tests / detached compositions don't need to wrap the host in a custom `CompositionLocalProvider`. Reading the default and collecting from it MUST be a runtime no-op.
- The producer (typically `MainShell`) is the sole writer; consumers are read-only via the `SharedFlow<Unit>` shape (not `MutableSharedFlow`). The CompositionLocal MUST NOT expose write capability to consumers.
- Consumers (feature screens) collect the flow inside a `LaunchedEffect` keyed on `(signal, listState)` (or equivalent stable keys) and call `LazyListState.animateScrollToItem(0)`. The signal carries no payload — it's a pure trigger.

#### Scenario: Producer emits, single consumer scrolls

- **WHEN** a `LocalScrollToTopSignal` provider emits `Unit` while a feature screen has an active `LaunchedEffect` collector
- **THEN** the collector receives the emission within one frame and calls `animateScrollToItem(0)` on the bound `LazyListState`.

#### Scenario: Emission with no subscriber is dropped silently

- **WHEN** the producer calls `tryEmit(Unit)` and no consumer is currently collecting (e.g., the visible tab doesn't host a list-bearing screen)
- **THEN** `tryEmit` returns `false` and no scroll behavior occurs anywhere. The emission is silently lost; the producer MUST NOT block, throw, or buffer.

#### Scenario: Default value supports preview composition

- **WHEN** a feature screen renders inside a preview / screenshot test that does NOT wrap composition in a `LocalScrollToTopSignal` provider
- **THEN** `LocalScrollToTopSignal.current` returns the default empty `SharedFlow<Unit>`. The screen's `LaunchedEffect` collector subscribes successfully but never receives an emission. No exception is thrown.

#### Scenario: Multiple subscribers all receive the broadcast

- **WHEN** two feature screens are simultaneously composed (e.g., adaptive split-pane on a tablet) and both collect `LocalScrollToTopSignal`
- **THEN** a single `tryEmit(Unit)` from the producer SHALL deliver to BOTH collectors. Each screen scrolls its own `LazyListState` to position 0 in parallel.

### Requirement: MainShell emits the signal on bottom-nav tab RE-TAP only

The `:app/MainShell` composable SHALL provide a `LocalScrollToTopSignal` value via `CompositionLocalProvider` and emit `Unit` from its bottom-nav tab-tap handler when and only when the tapped tab equals the currently-active tab.

- A tap that switches tabs (`tappedTab != activeTab`) MUST navigate as before and MUST NOT emit the signal. The destination tab restores its last scroll position via Nav3's existing back-stack semantics; firing scroll-to-top on a fresh tab landing would defeat that.
- A tap that re-selects the active tab (`tappedTab == activeTab`) MUST call `tryEmit(Unit)` on the underlying `MutableSharedFlow` and MUST NOT navigate. The user remains on the same tab; the signal is the only side effect.
- The tab-tap handler MUST resolve `activeTab` from the post-mutation MainShell state (not the pre-tap snapshot) so a rapid double-tap during a tab-switch animation is interpreted correctly.

#### Scenario: Re-tap on the active tab fires the signal

- **WHEN** the user taps the bottom-nav `Feed` tab while `activeTab == Feed`
- **THEN** MainShell calls `tryEmit(Unit)` on the underlying `MutableSharedFlow`. No navigation occurs. Any feature screen collecting `LocalScrollToTopSignal` receives the emission.

#### Scenario: Tab switch does not fire the signal

- **WHEN** the user taps the bottom-nav `Profile` tab while `activeTab == Feed`
- **THEN** MainShell navigates to `Profile`. NO signal is emitted. Profile's screen restores its last scroll position untouched.
