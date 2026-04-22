## ADDED Requirements

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

Feature state classes SHALL be flat and UI-ready: concrete fields (`isLoading: Boolean`, `items: ImmutableList<T>`, `selected: Foo?`, …) that composables can read directly. Feature state classes SHALL NOT wrap remote data in a VM-layer sum type such as `Async<T>` or `Result<T>` — that shape leaks presentation vocabulary the UI should not care about. Errors from remote sources SHALL be emitted as a `FooEffect.ShowError(message: String)` (or equivalent) rather than stored in state, unless the screen explicitly needs a sticky error indicator (in which case the indicator is a concrete field on `FooState`, e.g. `errorBanner: String?`).

#### Scenario: State default values form a usable initial UI

- **WHEN** a `FooState` is instantiated with default values in the VM's `super(...)` call
- **THEN** the composable rendering `uiState.value` produces a valid "loading" UI (e.g. `isLoading = true`) with no further coordination from the VM constructor

#### Scenario: Remote error emits an effect and clears isLoading

- **WHEN** a `Flow` wired via `onEach { setState(...) }.catch { ... }.launchIn(viewModelScope)` throws
- **THEN** the VM sets `isLoading = false` on state and emits a `ShowError`-shaped effect carrying the error message
- **AND WHEN** the screen has collected `effects` in a `LaunchedEffect`
- **THEN** the error is rendered once (typically via `SnackbarHostState.showSnackbar`)

#### Scenario: Refresh resets isLoading and restarts collection

- **WHEN** `handleEvent(Refresh)` is called on a VM whose state currently holds data from a prior load
- **THEN** the VM cancels any in-flight collection `Job`, sets `isLoading = true`, and starts a fresh collection (cold-flow re-subscription)
- **AND** the effects flow receives no effect on the happy path
