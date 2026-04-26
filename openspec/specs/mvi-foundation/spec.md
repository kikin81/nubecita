# mvi-foundation Specification

## Purpose
TBD - created by archiving change add-mvi-foundation. Update Purpose after archive.
## Requirements
### Requirement: MVI role markers

The system SHALL expose three empty marker interfaces — `UiState`, `UiEvent`, and `UiEffect` — in the `net.kikin.nubecita.ui.mvi` package. Feature screens SHALL declare per-screen sealed hierarchies or data classes that implement these markers so the base class can be parameterized over concrete types without leaking framework types into features.

#### Scenario: A screen declares its own state / event / effect types

- **WHEN** a developer creates a new feature screen
- **THEN** they define a `data class FooState(...) : UiState`, a `sealed interface FooEvent : UiEvent`, and a `sealed interface FooEffect : UiEffect` scoped to that screen

#### Scenario: Markers carry no behavior

- **WHEN** inspecting `UiState`, `UiEvent`, or `UiEffect`
- **THEN** each is an empty interface with no members, no default methods, and no identity/equality contract beyond the Kotlin default

### Requirement: MviViewModel base class

The system SHALL provide an abstract generic `MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S)` extending `androidx.lifecycle.ViewModel`. The base class SHALL expose read-only `uiState: StateFlow<S>` and `effects: Flow<F>`, provide protected `setState(reducer: S.() -> S)` and `sendEffect(effect: F)` operations, and require subclasses to implement `fun handleEvent(event: E)`. The base class SHALL NOT provide `Flow`/coroutine wrapping helpers — feature VMs inline `Flow.onEach { }.catch { }.launchIn(viewModelScope)` and `viewModelScope.launch { try { } catch { } }`.

#### Scenario: State is exposed as a hot StateFlow with the initial value

- **WHEN** a VM subclass is instantiated with an initial state
- **THEN** `uiState.value` equals that initial state and `uiState` is a `StateFlow<S>` (always has a current value, conflates duplicates)

#### Scenario: setState applies the reducer atomically

- **WHEN** a VM calls `setState { copy(field = new) }`
- **THEN** `uiState.value` reflects the reduced state before `setState` returns, and concurrent `setState` calls never produce a lost update

#### Scenario: Effects are buffered one-shot emissions, not re-delivered to late subscribers

- **WHEN** a VM calls `sendEffect(effect)` before any collector subscribes
- **THEN** the effect is buffered and delivered to the first collector that subscribes
- **AND WHEN** a second collector subscribes after the effect has been consumed
- **THEN** the second collector does not receive the already-consumed effect

#### Scenario: handleEvent is the single entry point for UI intent

- **WHEN** a composable calls `viewModel::handleEvent(event)`
- **THEN** the VM dispatches on the event type, may call `setState` and/or `sendEffect`, and no other public mutator exists on the VM

#### Scenario: Hilt can construct a VM subclass

- **WHEN** a subclass is annotated `@HiltViewModel` with `@Inject constructor(...)` dependencies and a hand-rolled initial state
- **THEN** Hilt resolves and constructs the VM without additional base-class configuration

### Requirement: Flat UI-ready state with effect-based errors

Feature state classes SHALL be flat and UI-ready: concrete fields (`isLoading: Boolean`, `items: ImmutableList<T>`, `selected: Foo?`, …) that composables can read directly. Feature state classes SHALL NOT wrap remote data in a generic VM-layer sum type such as `Async<T>` or `Result<T>` — that shape leaks presentation vocabulary the UI should not care about. Errors from remote sources SHALL be emitted as a `FooEffect.ShowError(message: String)` (or equivalent) rather than stored in state, unless the screen explicitly needs a sticky error indicator (in which case the indicator is a concrete field on `FooState`, e.g. `errorBanner: String?`).

The flat-fields rule applies to **independent** flags — fields whose values can vary independently of one another (e.g., `isLoading` and `errorBanner` may be true simultaneously during a "retry-while-still-showing-stale-error" interaction). For **mutually-exclusive view modes** — sets of states where exactly one is active at any instant and where multiple-true combinations would be invalid (e.g., a feed's `idle / initial-loading / refreshing / appending / initial-error` lifecycle) — feature state classes SHALL declare a per-screen `sealed interface FooStatus` (or `FooLoadStatus`, `FooMode`, etc.) and expose it as a single field on `FooState`. The host composable branches via `when (state.status)` and the type system makes invalid combinations unrepresentable.

The decision rule between flat booleans and a sealed status sum is:

- **Flat boolean** when two or more flags can legitimately coexist (e.g., `isLoading: Boolean`, `errorBanner: String?`).
- **Sealed status sum** when the flags are mutually exclusive and a combinatorial invariant ("exactly one true at a time") would otherwise need to be enforced by reducer code rather than by the type system.

This is NOT a license to wrap remote data in a generic `Async<T>` — the prohibition on framework-style data wrappers stands. Sealed status sums are per-screen, named after the screen's specific lifecycle (`FeedLoadStatus`, not `FetchState<T>`), and may carry per-variant payloads (e.g., `data class InitialError(val error: FeedError) : FeedLoadStatus`).

#### Scenario: State default values form a usable initial UI

- **WHEN** a `FooState` is instantiated with default values in the VM's `super(...)` call
- **THEN** the composable rendering `uiState.value` produces a valid "loading" UI (e.g. `isLoading = true`, or `loadStatus = InitialLoading`) with no further coordination from the VM constructor

#### Scenario: Remote error emits an effect and clears isLoading

- **WHEN** a `Flow` wired via `onEach { setState(...) }.catch { ... }.launchIn(viewModelScope)` throws and the screen has independent flags (`isLoading: Boolean`)
- **THEN** the VM sets `isLoading = false` on state and emits a `ShowError`-shaped effect carrying the error message
- **AND WHEN** the screen has collected `effects` in a `LaunchedEffect`
- **THEN** the error is rendered once (typically via `SnackbarHostState.showSnackbar`)

#### Scenario: Refresh resets isLoading and restarts collection

- **WHEN** `handleEvent(Refresh)` is called on a VM whose state currently holds data from a prior load
- **THEN** the VM cancels any in-flight collection `Job`, sets `isLoading = true` (or transitions the sealed status to `Refreshing`), and starts a fresh collection (cold-flow re-subscription)
- **AND** the effects flow receives no effect on the happy path

#### Scenario: Mutually-exclusive load modes use a sealed status sum

- **WHEN** a screen's load lifecycle has more than two mutually-exclusive states (e.g., `Idle`, `InitialLoading`, `Refreshing`, `Appending`, `InitialError`)
- **THEN** the screen SHALL declare `sealed interface FooLoadStatus` with one variant per mode and expose `loadStatus: FooLoadStatus` as a single field on `FooState` rather than declaring three independent boolean fields

#### Scenario: Independent flags stay flat

- **WHEN** a screen has flags whose values can vary independently (e.g., login screen's `isLoading` may be true while `errorMessage != null` during error-clear-then-retry sequences)
- **THEN** these flags SHALL remain flat fields on `FooState` and SHALL NOT be combined into a sealed sum type
