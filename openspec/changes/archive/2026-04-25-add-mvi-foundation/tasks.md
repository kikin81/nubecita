## 1. MVI package skeleton

- [x] 1.0 Add `kotlinx.collections.immutable` to `gradle/libs.versions.toml` (`kotlinxCollectionsImmutable = "0.4.0"` under `[versions]`, matching `kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }` under `[libraries]`) and `implementation(libs.kotlinx.collections.immutable)` in `app/build.gradle.kts`. Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep immutable` to confirm resolution.
- [x] 1.1 Create `app/src/main/java/net/kikin/nubecita/ui/mvi/` package.
- [x] 1.2 Add `UiState.kt`, `UiEvent.kt`, `UiEffect.kt` — each a single empty interface with a short KDoc describing its role (spec: *MVI role markers*).
- [x] 1.3 Add `MviViewModel.kt` — abstract generic `MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S) : ViewModel()` with `uiState: StateFlow<S>`, `effects: Flow<F>`, protected `setState(reducer: S.() -> S)`, protected `sendEffect(effect: F)`, and abstract `fun handleEvent(event: E)`. KDoc on `handleEvent` notes it must dispatch and launch into `viewModelScope` via inline `onEach { }.catch { }.launchIn(...)` / `launch { try { } catch { } }` (spec: *MviViewModel base class*).

## 2. Unit tests for the foundation

- [x] 2.1 Add `MainDispatcherRule.kt` as a test-local JUnit 4 rule under `app/src/test/java/net/kikin/nubecita/ui/mvi/` (using `Dispatchers.setMain` / `resetMain`). KDoc documents the shared-scheduler requirement: tests MUST call `runTest(mainDispatcherRule.dispatcher)` so Main and the test scope share a single `TestCoroutineScheduler`. Keep the rule package-private to the test source set; no new production module.
- [x] 2.2 Add `MviViewModelTest` — one tiny in-file VM subclass + cases: initial state, `setState` reducer, `sendEffect` buffering before a collector attaches, one-shot effect delivery (second collector sees nothing after consumption), `handleEvent` dispatch.

## 3. Migrate MainScreenViewModel

- [x] 3.1 Add `app/src/main/java/net/kikin/nubecita/ui/main/MainScreenState.kt` — `data class MainScreenState(val items: ImmutableList<String> = persistentListOf(), val isLoading: Boolean = true) : UiState` using `kotlinx.collections.immutable.ImmutableList` (added in task 1.0). Flat, UI-ready fields only — no `Async<T>` wrapper.
- [x] 3.2 Add `MainScreenEvent.kt` — `sealed interface MainScreenEvent : UiEvent { data object Refresh : MainScreenEvent }`.
- [x] 3.3 Add `MainScreenEffect.kt` — `sealed interface MainScreenEffect : UiEffect { data class ShowError(val message: String) : MainScreenEffect }`.
- [x] 3.4 Rewrite `MainScreenViewModel.kt` to extend `MviViewModel<MainScreenState, MainScreenEvent, MainScreenEffect>`. On init, start an inline `dataRepository.data.onEach { setState(items, isLoading = false) }.catch { setState(isLoading = false); sendEffect(ShowError(...)) }.launchIn(viewModelScope)`. Capture the returned `Job`; `handleEvent(Refresh)` cancels it, sets `isLoading = true`, and restarts collection.
- [x] 3.5 Delete the now-orphan `MainScreenUiState` sealed interface from the old `MainScreenViewModel.kt`.
- [x] 3.6 Update `MainScreen.kt` composable to read `state.items` / `state.isLoading`, wire `Refresh` via `viewModel::handleEvent`, and collect `viewModel.effects` in a single `LaunchedEffect` that routes `ShowError` through `SnackbarHostState.showSnackbar`. Use `Scaffold` to host the `SnackbarHost`.
- [x] 3.7 Update the existing unit test for `MainScreenViewModel` to assert on the flat state (`isLoading`, `items`) plus the `ShowError` effect on the error path. Use `runTest(mainDispatcherRule.dispatcher)` so Main and the test scope share a scheduler.

## 4. Verify and open follow-ups

- [x] 4.1 Run `./gradlew spotlessCheck lint testDebugUnitTest` and fix any violations locally.
- [x] 4.2 Run `./gradlew :app:assembleDebug` and `:app:updateDebugScreenshotTest` / `:app:validateDebugScreenshotTest` to confirm the app builds and the Compose preview baselines are current.
- [x] 4.3 File a bd issue: "Adopt Turbine + MockK + JUnit 5 in the test stack" and link this change. (nubecita-e1a, P2)
- [x] 4.4 File a bd issue: "Document SavedStateHandle pattern for MVI VMs" (parked until the first screen actually needs process-death persistence). (nubecita-9xd, P3)
- [x] 4.5 Update `CLAUDE.md` with a short "MVI conventions" paragraph citing `ui/mvi/`, the flat-state convention, effect-based errors, and the non-goals (no `Async<T>` at the boundary, no helpers on the base, no Mavericks, no `SavedStateHandle` in base, no event flow in base).
