## Why

Nubecita's `README.md` and `CLAUDE.md` both call out MVI on `ViewModel` + `StateFlow` as the app's architecture, but the codebase has no base class, no marker interfaces, and no shared async/error conventions. The one existing `MainScreenViewModel` hand-rolls `Loading/Error/Success` on a vanilla `ViewModel`, so every new feature (auth, feed, compose, search, notifications) would reinvent the same plumbing — and drift. We need a single tiny foundation in place before the auth, feed, and design-system work lands on top of it.

The bar is stay-native: no Mavericks, no Orbit, no framework. A ~100-LOC foundation built from `ViewModel`, `MutableStateFlow`, and `Channel` — the exact primitives the Android team recommends — with a couple of small helpers to kill boilerplate.

## What Changes

- Add `ui/mvi/` package with:
  - **Marker interfaces** — `UiState`, `UiEvent`, `UiEffect` (empty sealed-friendly markers).
  - **`MviViewModel<S, E, F>`** abstract base exposing `StateFlow<S>` and an effects `Flow<F>`, plus protected `setState { }` and `sendEffect(...)`. One abstract `handleEvent(event: E)`.
  - **`Async<T>` sealed interface** — `Uninitialized`, `Loading`, `Success<T>`, `Failure(Throwable)` — with small conveniences (`getOrNull`, `map`).
  - **Coroutine helpers** — `launchSafe(onError: (Throwable) -> F, block)` and `Flow<T>.collectSafely(onError: (Throwable) -> F) { }` so VMs stop hand-rolling `.catch { sendEffect(ShowError(...)) }`. The `onError` lambda keeps the base class decoupled from any specific effect shape.
- **Migrate `MainScreenViewModel`** onto the new base so the foundation ships with one real consumer. Its ad-hoc `MainScreenUiState` sealed interface collapses into `MainScreenState(data: Async<List<String>>)` + a `MainScreenEvent.Refresh` + a `MainScreenEffect.ShowError`.
- **Unit tests** using the existing `JUnit 4 + kotlinx-coroutines-test` stack covering state reducer, effect emission, and error-path coverage for the helpers. A throwaway `MainDispatcherRule` lives alongside the tests; promoting it to a shared `test-support` module is out of scope.

### Non-goals

- **No new test libraries.** JUnit 5 / Turbine / MockK adoption is tracked as a separate bd task.
- **No SavedStateHandle / process-death persistence** baked into the base. Screens that need it inject `SavedStateHandle` directly via Hilt.
- **No event ingress Flow / debounce / throttle plumbing in the base.** Those are per-VM concerns (search, autocomplete, draft autosave, firehose sampling) and get wired inside the specific VM when needed.
- **No Compose-side helpers** (`rememberEventSink`, `collectEffects`, etc.) in this change. Views call `vm::handleEvent` and `collectAsStateWithLifecycle` directly; any Compose glue comes later once patterns emerge.
- **No screen-specific migrations beyond `MainScreenViewModel`** — nothing else currently uses `ViewModel` in the app.

## Capabilities

### New Capabilities
- `mvi-foundation`: Defines the contract for presentation-layer state management across the app — the `UiState`/`UiEvent`/`UiEffect` roles, the shape and guarantees of the `MviViewModel` base class, the `Async<T>` vocabulary for async data, and the error-handling helpers every feature VM consumes.

### Modified Capabilities
- (none)

## Impact

- **Code**
  - New package `net.kikin.nubecita.ui.mvi` (6 files, ~100 LOC total).
  - `app/src/main/java/net/kikin/nubecita/ui/main/MainScreenViewModel.kt` rewritten; the inline `MainScreenUiState` sealed interface is deleted and replaced by `MainScreenState` / `MainScreenEvent` / `MainScreenEffect` files.
  - `MainScreen.kt` composable updated to read `state.data: Async<List<String>>` and to call `viewModel::handleEvent` for refresh; no navigation effects yet.
- **Dependencies** — adds `org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0` (Apache-2.0, JetBrains-maintained). Foundation otherwise uses only `androidx.lifecycle:lifecycle-viewmodel` and `kotlinx-coroutines-core`, both already on the classpath. `ImmutableList` is adopted so list-typed `UiState` fields satisfy Compose's `@Stable` contract, preserving skippable recomposition on unchanged feed/list inputs — required for the 120hz scroll target.
- **Tests** — new unit tests under `app/src/test/java/net/kikin/nubecita/ui/mvi/` and an updated `MainScreenViewModelTest` (if one exists; otherwise added). No screenshot-test or instrumentation-test impact.
- **Downstream unblocks** — epics `nubecita-baf` (MVI architecture foundation) and anything that wanted to build a VM on top of it: auth (`nubecita-gr4`), design-system hosts, future feed / compose / search VMs.
- **Follow-up bd issues** to file before closing this change:
  - Adopt Turbine + MockK + JUnit 5 in the test stack.
  - Document `SavedStateHandle` usage pattern once the first screen needs it.
