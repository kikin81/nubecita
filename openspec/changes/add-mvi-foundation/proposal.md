## Why

Nubecita's `README.md` and `CLAUDE.md` both call out MVI on `ViewModel` + `StateFlow` as the app's architecture, but the codebase has no base class, no marker interfaces, and no shared error-routing conventions. The one existing `MainScreenViewModel` hand-rolls `Loading/Error/Success` on a vanilla `ViewModel`, so every new feature (auth, feed, compose, search, notifications) would reinvent the same plumbing — and drift. We need a single tiny foundation in place before the auth, feed, and design-system work lands on top of it.

The bar is stay-native: no Mavericks, no Orbit, no framework. A ~60-LOC foundation built from `ViewModel`, `MutableStateFlow`, and `Channel` — the exact primitives the Android team recommends — with no helpers beyond what the base class needs to exist.

## What Changes

- Add `ui/mvi/` package with:
  - **Marker interfaces** — `UiState`, `UiEvent`, `UiEffect` (empty markers).
  - **`MviViewModel<S, E, F>`** abstract base exposing `StateFlow<S>` and an effects `Flow<F>`, plus protected `setState { }` and `sendEffect(...)`. One abstract `handleEvent(event: E)`. No helper wrappers — VMs inline `Flow.onEach { }.catch { }.launchIn(viewModelScope)` and `viewModelScope.launch { try { } catch { } }`.
- **Migrate `MainScreenViewModel`** onto the new base so the foundation ships with one real consumer. State is flat and UI-ready: `MainScreenState(items: ImmutableList<String>, isLoading: Boolean)` — no `Async<T>` wrapping. Errors route through `MainScreenEffect.ShowError(message: String)`, collected once in `MainScreen` and surfaced as a Snackbar.
- **Unit tests** using the existing `JUnit 4 + kotlinx-coroutines-test` stack covering state reducer, effect emission, and `MainScreenViewModel` state/effect transitions. A throwaway `MainDispatcherRule` lives alongside the tests; its KDoc makes the shared-scheduler requirement explicit (tests call `runTest(mainDispatcherRule.dispatcher)`). Promoting the rule to a shared `test-support` module is out of scope.

### Non-goals

- **No `Async<T>` / `Result<T>` at the VM→UI boundary.** VMs expose flat, UI-ready state; the Async-shaped sum type lived here briefly in earlier drafts but leaked a presentation-layer abstraction into Compose (Compose doing `when (state.data)` on a foundation type). Flat state + effect-based errors is the slabsnap pattern and it reads cleaner.
- **No `launchSafe` / `collectSafely` helpers on the base.** Inlining the `.catch` / `try { } catch { }` keeps the state-recovery shape visible at each call site (typically `setState { copy(isLoading = false) }; sendEffect(ShowError(...))`). Promote to a helper only when ≥3 screens share the identical shape.
- **No new test libraries.** JUnit 5 / Turbine / MockK adoption is tracked as a separate bd task.
- **No SavedStateHandle / process-death persistence** baked into the base. Screens that need it inject `SavedStateHandle` directly via Hilt.
- **No event ingress Flow / debounce / throttle plumbing in the base.** Those are per-VM concerns (search, autocomplete, draft autosave, firehose sampling) and get wired inside the specific VM when needed.
- **No Compose-side helpers** (`rememberEventSink`, `collectEffects`, etc.) in this change. Screens collect `viewModel.effects` in a single `LaunchedEffect` inside the outermost composable.
- **No screen-specific migrations beyond `MainScreenViewModel`** — nothing else currently uses `ViewModel` in the app.

## Capabilities

### New Capabilities
- `mvi-foundation`: Defines the contract for presentation-layer state management across the app — the `UiState`/`UiEvent`/`UiEffect` roles, the shape and guarantees of the `MviViewModel` base class, and the convention that VMs expose flat UI-ready state with errors routed through effects.

### Modified Capabilities
- (none)

## Impact

- **Code**
  - New package `net.kikin.nubecita.ui.mvi` (4 files, ~60 LOC total): `UiState`, `UiEvent`, `UiEffect` markers, and `MviViewModel`.
  - `app/src/main/java/net/kikin/nubecita/ui/main/MainScreenViewModel.kt` rewritten; the inline `MainScreenUiState` sealed interface is deleted and replaced by `MainScreenState` / `MainScreenEvent` / `MainScreenEffect` files.
  - `MainScreen.kt` composable updated to read flat `state.items` / `state.isLoading`, collect `viewModel.effects` in a `LaunchedEffect`, and surface `ShowError` effects via a `Scaffold` + `SnackbarHost`.
- **Dependencies** — adds `org.jetbrains.kotlinx:kotlinx-collections-immutable:0.4.0` (Apache-2.0, JetBrains-maintained). Foundation otherwise uses only `androidx.lifecycle:lifecycle-viewmodel` and `kotlinx-coroutines-core`, both already on the classpath. `ImmutableList` is adopted so list-typed `UiState` fields satisfy Compose's `@Stable` contract, preserving skippable recomposition — required for the 120hz scroll target.
- **Tests** — new unit tests under `app/src/test/java/net/kikin/nubecita/ui/mvi/` and an updated `MainScreenViewModelTest`. No screenshot-test or instrumentation-test impact beyond the baselines regenerated for the new `MainScreenContent` shape.
- **Downstream unblocks** — epics `nubecita-baf` (MVI architecture foundation) and anything that wanted to build a VM on top of it: auth (`nubecita-gr4`), design-system hosts, future feed / compose / search VMs.
- **Follow-up bd issues** to file before closing this change:
  - Adopt Turbine + MockK + JUnit 5 in the test stack.
  - Document `SavedStateHandle` usage pattern once the first screen needs it.
