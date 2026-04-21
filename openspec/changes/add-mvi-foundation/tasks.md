## 1. MVI package skeleton

- [x] 1.0 Add `kotlinx.collections.immutable` to `gradle/libs.versions.toml` (`kotlinxCollectionsImmutable = "0.4.0"` under `[versions]`, matching `kotlinx-collections-immutable = { module = "org.jetbrains.kotlinx:kotlinx-collections-immutable", version.ref = "kotlinxCollectionsImmutable" }` under `[libraries]`) and `implementation(libs.kotlinx.collections.immutable)` in `app/build.gradle.kts`. Run `./gradlew :app:dependencies --configuration debugRuntimeClasspath | grep immutable` to confirm resolution.
- [x] 1.1 Create `app/src/main/java/net/kikin/nubecita/ui/mvi/` package.
- [x] 1.2 Add `UiState.kt`, `UiEvent.kt`, `UiEffect.kt` — each a single empty interface with a short KDoc describing its role (spec: *MVI role markers*).
- [x] 1.3 Add `MviViewModel.kt` — abstract generic `MviViewModel<S : UiState, E : UiEvent, F : UiEffect>(initialState: S) : ViewModel()` with `uiState: StateFlow<S>`, `effects: Flow<F>`, protected `setState(reducer: S.() -> S)`, protected `sendEffect(effect: F)`, and abstract `fun handleEvent(event: E)`. KDoc on `handleEvent` notes it must dispatch and launch into `viewModelScope` (spec: *MviViewModel base class*).

## 2. Async\<T\>

- [x] 2.1 Add `Async.kt` — sealed interface with `data object Uninitialized`, `data object Loading`, `data class Success<out T>(val value: T)`, `data class Failure(val error: Throwable)` (spec: *Async\<T\> value type*).
- [x] 2.2 Add `getOrNull(): T?` and `map(transform: (T) -> R): Async<R>` as members or extensions. `map` MUST return the same singleton for `Loading`/`Uninitialized` and a new `Failure` carrying the original throwable for `Failure`.
- [x] 2.3 Add KDoc noting that adding a variant requires updating `map` / `getOrNull`.

## 3. Coroutine / flow helpers

- [x] 3.1 Add `launchSafe(onError: (Throwable) -> F, block: suspend CoroutineScope.() -> Unit): Job` as a protected member of `MviViewModel` (not a separate file — `protected` requires class membership). Launches in `viewModelScope`, re-throws `CancellationException`, and sends `onError(t)` on any other throwable (spec: *Safe coroutine / flow helpers*).
- [x] 3.2 Add protected member-extension `Flow<T>.collectSafely(onError: (Throwable) -> F, action: suspend (T) -> Unit): Job` inside `MviViewModel`, sharing the error-mapping contract with `launchSafe`.

## 4. Unit tests for the foundation

- [x] 4.1 Add `MainDispatcherRule.kt` as a test-local JUnit 4 rule under `app/src/test/java/net/kikin/nubecita/ui/mvi/` (using `Dispatchers.setMain` / `resetMain`). Keep it package-private to the test source set; no new production module.
- [x] 4.2 Add `MviViewModelTest` — one tiny in-file VM subclass + cases: initial state, `setState` reducer, `sendEffect` buffering before a collector attaches, one-shot effect delivery (second collector sees nothing after consumption), `handleEvent` dispatch.
- [x] 4.3 Add `AsyncTest` covering: `Uninitialized`/`Loading`/`Failure`.getOrNull == null; `Success(v).getOrNull == v`; `map` on `Success` transforms; `map` on `Loading`/`Uninitialized` returns the same singleton; `map` on `Failure(e)` returns `Failure(e)` without invoking the transform.
- [x] 4.4 Add `CoroutineHelpersTest` covering: `launchSafe` normal completion emits no effect; thrown `IOException` is converted to an effect exactly once; `CancellationException` is not mapped to an effect (use `runTest` and cancel the test job); `collectSafely` runs `action` for pre-error emissions then emits the error effect exactly once.

## 5. Migrate MainScreenViewModel

- [x] 5.1 Add `app/src/main/java/net/kikin/nubecita/ui/main/MainScreenState.kt` — `data class MainScreenState(val data: Async<ImmutableList<String>> = Async.Uninitialized) : UiState` using `kotlinx.collections.immutable.ImmutableList` (added in task 1.0).
- [x] 5.2 Add `MainScreenEvent.kt` — `sealed interface MainScreenEvent : UiEvent { data object Refresh : MainScreenEvent }`.
- [x] 5.3 Add `MainScreenEffect.kt` — `sealed interface MainScreenEffect : UiEffect { data class ShowError(val message: String) : MainScreenEffect }`.
- [x] 5.4 Rewrite `MainScreenViewModel.kt` to extend `MviViewModel<MainScreenState, MainScreenEvent, MainScreenEffect>`. On init, start a `collectSafely` of `dataRepository.data` that reduces to `Async.Success(it.toImmutableList())` and maps errors to `MainScreenEffect.ShowError`. `handleEvent(Refresh)` cancels any in-flight collection `Job` and starts a fresh one (cold-flow re-subscription) — capture the `Job` returned by `collectSafely` and call `cancel()` + restart on `Refresh`.
- [x] 5.5 Delete the now-orphan `MainScreenUiState` sealed interface from the old `MainScreenViewModel.kt`.
- [x] 5.6 Update `MainScreen.kt` composable to read `state.data: Async<…>` and render branches for `Uninitialized/Loading`, `Success`, and `Failure`. Wire a refresh trigger to `viewModel::handleEvent` with `MainScreenEvent.Refresh`.
- [x] 5.7 Update any existing unit test for `MainScreenViewModel` to use the new state type; add one if none existed, covering Success mapping and error-effect emission.

## 6. Verify and open follow-ups

- [x] 6.1 Run `./gradlew spotlessCheck lint testDebugUnitTest` and fix any violations locally.
- [x] 6.2 Run `./gradlew :app:assembleDebug` to confirm the app still builds; manually sanity-check `MainScreen` renders in an emulator or device. (APK built; manual smoke-test still owed by the author.)
- [x] 6.3 File a bd issue: "Adopt Turbine + MockK + JUnit 5 in the test stack" and link this change. (nubecita-e1a, P2)
- [x] 6.4 File a bd issue: "Document SavedStateHandle pattern for MVI VMs" (parked until the first screen actually needs process-death persistence). (nubecita-9xd, P3)
- [x] 6.5 Update `CLAUDE.md` with a short "MVI conventions" paragraph citing `ui/mvi/` and the non-goals (no Mavericks, no `SavedStateHandle` in base, no event flow in base).
