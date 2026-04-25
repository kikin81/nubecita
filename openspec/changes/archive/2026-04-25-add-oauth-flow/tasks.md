## 1. Navigator in `:core:common`

- [ ] 1.1 Create `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/Navigator.kt` — public interface with `backStack: SnapshotStateList<NavKey>`, `goTo(NavKey)`, `goBack()`, `replaceTo(NavKey)`.
- [ ] 1.2 Create `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/DefaultNavigator.kt` — `internal class DefaultNavigator @Inject constructor() : Navigator`. Initialize `backStack = mutableStateListOf(Main)` — but `Main` lives in `:app`, which `:core:common` can't depend on. **Resolve at implementation time:** either (a) inject the start destination via Hilt `@Provides`, (b) make the start key configurable via constructor parameter, or (c) initialize empty and have `:app` push `Main` from `MainNavigation`. Prefer (a) — `:app` provides `@StartDestination NavKey = Main`.
- [ ] 1.3 Create `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/di/NavigatorModule.kt` — Hilt `@Binds` for `DefaultNavigator → Navigator` in `SingletonComponent`.
- [ ] 1.4 Add the dep `androidx.compose.runtime:runtime` (or whatever provides `mutableStateListOf`/`SnapshotStateList`) to `:core:common`. Likely via the existing `androidx.lifecycle.runtime.compose` transitive, but verify and add explicitly.
- [ ] 1.5 Unit test `core/common/src/test/.../navigation/DefaultNavigatorTest.kt`: scenarios for `goTo` appends, `goBack` pops, `goBack` on empty is no-op, `replaceTo` clears + appends.

## 2. `MainNavigation` reads from injected Navigator

- [ ] 2.1 Update `app/src/main/.../Navigation.kt` `MainNavigation` to obtain `Navigator` via the existing `NavigationEntryPoint` (extend the entry point with a `navigator()` accessor). Replace `rememberNavBackStack(Main)` with `navigator.backStack`. `onBack` becomes `{ navigator.goBack() }`.
- [ ] 2.2 If start destination is provided via Hilt (per §1.2 option a), add `:app/.../navigation/StartDestinationModule.kt` providing `@StartDestination NavKey = Main` qualifier-bound.
- [ ] 2.3 Verify `./gradlew :app:assembleDebug` compiles and `MainScreen` still renders on launch (smoke test the placeholder destination).

## 3. `OAuthRedirectBroker` in `:core:auth`

- [ ] 3.1 Create `core/auth/src/main/kotlin/.../OAuthRedirectBroker.kt` — public interface with `redirects: Flow<String>` and `suspend fun publish(redirectUri: String)`.
- [ ] 3.2 Create `core/auth/src/main/kotlin/.../DefaultOAuthRedirectBroker.kt` — `internal class DefaultOAuthRedirectBroker @Inject constructor() : OAuthRedirectBroker`. Backed by `Channel<String>(Channel.BUFFERED)` exposed via `receiveAsFlow()`.
- [ ] 3.3 Extend `core/auth/src/main/.../di/AuthBindingsModule.kt` with `@Binds @Singleton fun bindOAuthRedirectBroker(impl: DefaultOAuthRedirectBroker): OAuthRedirectBroker`.
- [ ] 3.4 Unit test `core/auth/src/test/.../DefaultOAuthRedirectBrokerTest.kt`: scenarios for cold-start buffering, single-collector delivery, ordering preservation.

## 4. `AuthRepository.completeLogin`

- [ ] 4.1 Extend `core/auth/src/main/.../AuthRepository.kt` interface with `suspend fun completeLogin(redirectUri: String): Result<Unit>`.
- [ ] 4.2 Extend `core/auth/src/main/.../DefaultAuthRepository.kt` impl: `override suspend fun completeLogin(redirectUri: String): Result<Unit> = runCatching { atOAuth.completeLogin(redirectUri) }`. Note: `AtOAuth.completeLogin` returns `Unit` (its job is to side-effect into the session store).
- [ ] 4.3 Extend `core/auth/src/test/.../DefaultAuthRepositoryTest.kt` (or create if it doesn't exist yet) with success / failure / wrapped-exception scenarios for `completeLogin`. Same pattern as the existing `beginLogin` tests.

## 5. `LoginViewModel` consumes broker + emits LoginSucceeded

- [ ] 5.1 Update `feature/login/impl/src/main/.../LoginContract.kt`: add `data object LoginSucceeded : LoginEffect` to the sealed interface.
- [ ] 5.2 Update `feature/login/impl/src/main/.../LoginViewModel.kt`: add `OAuthRedirectBroker` as a constructor parameter. In `init`, launch a coroutine in `viewModelScope` that collects `broker.redirects` and dispatches each URI to `authRepository.completeLogin`, emitting `LoginEffect.LoginSucceeded` on success or setting `errorMessage = LoginError.Failure(...)` on failure.
- [ ] 5.3 Extend `feature/login/impl/src/test/.../LoginViewModelTest.kt`:
  - Add a `FakeOAuthRedirectBroker` test double exposing a `MutableSharedFlow<String>` (test-controllable) that the VM consumes.
  - Add scenarios: (a) broker emission with successful repository → `LoginSucceeded` effect; (b) broker emission with failure → `errorMessage = Failure(...)`; (c) broker emission while `isLoading` is true does not race with the submit-path.

## 6. Custom Tab launcher + `LoginSucceeded` handler in `LoginScreen`

- [ ] 6.1 Add `androidx-browser` to `gradle/libs.versions.toml` (`androidx.browser:browser:1.10.0` or current stable).
- [ ] 6.2 Add `implementation(libs.androidx.browser)` to `:feature:login:impl/build.gradle.kts`.
- [ ] 6.3 Update the stateful `LoginScreen` overload in `feature/login/impl/src/main/.../LoginScreen.kt` to add a `LaunchedEffect(viewModel)` that collects `viewModel.effects` and handles both variants:
  - `LaunchCustomTab` → `CustomTabsIntent.Builder().build().launchUrl(LocalContext.current, effect.url.toUri())`.
  - `LoginSucceeded` → `navigator.goBack()` (navigator obtained via Hilt entry point — same pattern as `NavigationEntryPoint`).
- [ ] 6.4 Verify the screenshot baselines from `nubecita-uf5` still hold (no UI rendering changes, only effect handling). Run `./gradlew :feature:login:impl:validateDebugScreenshotTest`.

## 7. AndroidManifest + MainActivity intent handling

- [ ] 7.1 Update `app/src/main/AndroidManifest.xml`:
  - Add `android:launchMode="singleTask"` to `<activity android:name=".MainActivity">`.
  - Add a new `<intent-filter>` inside the activity: `<action android:name="android.intent.action.VIEW" />`, `<category android:name="android.intent.category.DEFAULT" />`, `<category android:name="android.intent.category.BROWSABLE" />`, `<data android:scheme="net.kikin.nubecita" />`.
- [ ] 7.2 Update `app/src/main/.../MainActivity.kt`:
  - Add `@Inject lateinit var broker: OAuthRedirectBroker`.
  - Add `private fun handleIntent(intent: Intent)` that publishes when `intent.data?.scheme == "net.kikin.nubecita"` and clears `intent.data` after.
  - Call `handleIntent(intent)` from `onCreate` after `setContent`.
  - Override `onNewIntent(intent: Intent)`, call `super.onNewIntent(intent); setIntent(intent); handleIntent(intent)`.
- [ ] 7.3 Verify `./gradlew :app:assembleDebug` compiles and the merged manifest contains both filters. Manual check on device deferred to `nubecita-16a`.

## 8. Lint, format, pre-commit

- [ ] 8.1 `./gradlew spotlessApply` then `./gradlew test lint`.
- [ ] 8.2 `pre-commit run --all-files`.
- [ ] 8.3 Verify `:feature:login:impl:validateDebugScreenshotTest` still passes (no new baselines needed).

## 9. Bd graph + PR ceremony

- [ ] 9.1 `nubecita-ck0` claimed.
- [ ] 9.2 Branch: `feat/nubecita-ck0-oauth-flow` off `main`.
- [ ] 9.3 Conventional Commit subject: `feat(oauth): add Custom Tab launch + redirect callback + Navigator + AuthRepository.completeLogin`. Body details all four pieces; footer `Refs: nubecita-ck0`.
- [ ] 9.4 PR body: `Closes: nubecita-ck0`.
- [ ] 9.5 Post-merge: `bd close nubecita-ck0` and `openspec archive add-oauth-flow -y`.
