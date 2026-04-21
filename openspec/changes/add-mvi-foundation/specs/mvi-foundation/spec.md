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

The system SHALL provide an abstract generic `MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S)` extending `androidx.lifecycle.ViewModel`. The base class SHALL expose read-only `uiState: StateFlow<S>` and `effects: Flow<F>`, provide protected `setState(reducer: S.() -> S)` and `sendEffect(effect: F)` operations, and require subclasses to implement `fun handleEvent(event: E)`.

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

### Requirement: Async\<T\> value type

The system SHALL provide a sealed `Async<T>` type representing the lifecycle of an asynchronous value with variants `Uninitialized`, `Loading`, `Success(value: T)`, and `Failure(error: Throwable)`. `Async<T>` SHALL provide `getOrNull(): T?` and `map(transform: (T) -> R): Async<R>` conveniences. Feature state classes SHOULD use `Async<T>` to represent remote or long-running data instead of hand-rolled boolean/nullable/error triplets.

#### Scenario: Default state uses Uninitialized

- **WHEN** a feature state declares `val data: Async<Feed> = Async.Uninitialized`
- **THEN** the initial state compiles, renders as the empty/placeholder UI, and does not require Loading to be represented as a boolean

#### Scenario: Success carries a value that survives map

- **WHEN** code calls `Async.Success(list).map { it.size }`
- **THEN** the result is `Async.Success(list.size)`

#### Scenario: Non-success variants short-circuit map

- **WHEN** code calls `Async.Loading.map { it.size }` or `Async.Failure(e).map { it.size }`
- **THEN** the result is the same variant (same instance for `Loading`/`Uninitialized`, `Failure(e)` with the original throwable) and the transform is not invoked

#### Scenario: getOrNull returns the value only for Success

- **WHEN** code calls `getOrNull()` on each variant
- **THEN** `Success(v).getOrNull() == v` and every other variant returns `null`

### Requirement: Safe coroutine / flow helpers

The system SHALL provide `launchSafe(onError: (Throwable) -> F, block: suspend CoroutineScope.() -> Unit): Job` and `Flow<T>.collectSafely(onError: (Throwable) -> F, action: suspend (T) -> Unit): Job` as protected members (or extensions) on `MviViewModel`. Both SHALL run the block inside `viewModelScope`, catch any `Throwable` that is not a `CancellationException`, invoke `onError` to produce an effect, and emit it via `sendEffect`. Cooperative cancellation MUST propagate — `CancellationException` MUST NOT be swallowed.

#### Scenario: Successful block emits no effect

- **WHEN** `launchSafe(onError = { ErrEffect(it) }) { doWork() }` completes normally
- **THEN** no effect is sent and the returned `Job` is in completed state

#### Scenario: Thrown exception is converted to an effect

- **WHEN** the block throws `IOException("boom")`
- **THEN** `onError` is invoked with that throwable, and the returned effect is delivered via the VM's effects flow exactly once

#### Scenario: CancellationException is not mapped to an effect

- **WHEN** the surrounding scope is cancelled and the block throws `CancellationException`
- **THEN** `onError` is not invoked, no effect is sent, and cancellation propagates

#### Scenario: collectSafely terminates on upstream error

- **WHEN** a flow passed to `collectSafely` emits two values and then throws
- **THEN** `action` runs for both values, `onError` is invoked once with the thrown throwable, and the resulting effect is delivered exactly once
