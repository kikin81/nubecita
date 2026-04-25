## 1. SessionState + SessionStateProvider in `:core:auth`

- [ ] 1.1 Create `core/auth/src/main/kotlin/.../SessionState.kt` — public `sealed interface SessionState` with `data object Loading`, `data object SignedOut`, `data class SignedIn(val handle: String, val did: String)`. No other fields on `SignedIn`.
- [ ] 1.2 Create `core/auth/src/main/kotlin/.../SessionStateProvider.kt` — public interface with `val state: StateFlow<SessionState>` and `suspend fun refresh()`.
- [ ] 1.3 Create `core/auth/src/main/kotlin/.../DefaultSessionStateProvider.kt` — `internal class DefaultSessionStateProvider @Inject constructor(private val sessionStore: OAuthSessionStore) : SessionStateProvider`. Backs `state` with `MutableStateFlow<SessionState>(Loading)`. `refresh()` reads `sessionStore.load()` and updates `state.value` with `SignedIn(session.handle, session.did)` or `SignedOut`. No eager refresh in `init`.
- [ ] 1.4 Extend `core/auth/src/main/kotlin/.../di/AuthBindingsModule.kt` with `@Binds @Singleton fun bindSessionStateProvider(impl: DefaultSessionStateProvider): SessionStateProvider`.
- [ ] 1.5 Unit test `core/auth/src/test/kotlin/.../DefaultSessionStateProviderTest.kt`:
  - Initial `state.value` is `Loading`.
  - `refresh()` with non-null session in fake store → `state.value` is `SignedIn(handle, did)`.
  - `refresh()` with null session → `state.value` is `SignedOut`.
  - Concurrent multi-collector reads see the same emitted value.

## 2. `AuthRepository.signOut` + completeLogin refresh

- [ ] 2.1 Extend `core/auth/src/main/kotlin/.../AuthRepository.kt` interface with `suspend fun signOut(): Result<Unit>`.
- [ ] 2.2 Update `core/auth/src/main/kotlin/.../DefaultAuthRepository.kt`: add constructor parameter `private val sessionStateProvider: SessionStateProvider`. Add `override suspend fun signOut(): Result<Unit> = runCatching { atOAuth.logout(); sessionStateProvider.refresh() }`. Update `completeLogin` to also call `sessionStateProvider.refresh()` after `atOAuth.completeLogin(redirectUri)` succeeds.
- [ ] 2.3 Extend `core/auth/src/test/kotlin/.../DefaultAuthRepositoryTest.kt` (or create if it doesn't exist) with:
  - `signOut` success: `AtOAuth.logout` returns normally → `Result.success(Unit)` + provider's `refresh()` was invoked.
  - `signOut` failure: `AtOAuth.logout` throws → `Result.failure(...)` + provider state unchanged.
  - `completeLogin` success now triggers `refresh()`.
  - Use a fake `OAuthSessionStore` + a fake `SessionStateProvider` (state-tracking) to assert the refresh happened.

## 3. `Splash` NavKey + Navigator start change

- [ ] 3.1 Create `app/src/main/java/net/kikin/nubecita/Splash.kt` — `@Serializable data object Splash : NavKey`. (Same package as `Main`.)
- [ ] 3.2 Update `app/src/main/java/.../navigation/StartDestinationModule.kt` to return `Splash` instead of `Main`. Update the doc comment.
- [ ] 3.3 Update `app/src/main/java/.../Navigation.kt` `MainNavigation` to register `entry<Splash> { Box(Modifier.fillMaxSize()) }`. Keep the existing `entry<Main>` registration; the back stack now starts at `Splash` and `replaceTo`s to `Main` or `Login` from `MainActivity`.

## 4. SplashScreen API + reactive routing in MainActivity

- [ ] 4.1 Add `androidx-core-splashscreen` to `gradle/libs.versions.toml` (`androidx.core:core-splashscreen:1.2.0` or latest stable).
- [ ] 4.2 Add `implementation(libs.androidx.core.splashscreen)` to `app/build.gradle.kts`.
- [ ] 4.3 Update `app/src/main/java/.../MainActivity.kt`:
  - Add `@Inject lateinit var sessionStateProvider: SessionStateProvider`.
  - In `onCreate`, BEFORE `super.onCreate(savedInstanceState)`: `val splashScreen = installSplashScreen(); splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }`.
  - After `setContent { ... }` and `handleIntent(intent)`, launch two `lifecycleScope` coroutines:
    1. `lifecycleScope.launch { sessionStateProvider.refresh() }` — kick off the initial state read.
    2. `lifecycleScope.launch { sessionStateProvider.state.collect { state -> when (state) { Loading -> Unit; SignedOut -> navigator.replaceTo(Login); is SignedIn -> navigator.replaceTo(Main) } } }`.
  - Inject `Navigator` directly (extending `@Inject lateinit var navigator: Navigator`) — `MainActivity` already gets the singleton via Hilt for the broker injection pattern.
- [ ] 4.4 Verify `./gradlew :app:assembleDebug` compiles and `installSplashScreen()` resolves (correct package: `androidx.core.splashscreen.SplashScreen`).

## 5. LoginScreen post-login no-op

- [ ] 5.1 Update `feature/login/impl/src/main/kotlin/.../LoginScreen.kt`:
  - In the `LaunchedEffect(viewModel)` `when (effect)`, change `LoginEffect.LoginSucceeded -> navigator.goBack()` to `LoginEffect.LoginSucceeded -> Unit` (or remove the `navigator` reference entirely if it's no longer used elsewhere in the composable).
  - If `navigator` becomes unused, remove the `EntryPointAccessors.fromApplication(...)` block + the `NavigatorEntryPoint` import. Keep the rest of the LaunchedEffect intact.
- [ ] 5.2 Verify the existing 8 `LoginViewModelTest`s still pass — they assert on the VM emitting `LoginSucceeded`, not on screen-side behavior. Screenshot baselines (10 PNGs) should be unaffected.

## 6. Manual / instrumented validation

- [ ] 6.1 Manual smoke (cold-start, no session): launch app → splash visible briefly → land on Login. Verified before commit.
- [ ] 6.2 Manual smoke (cold-start, session present): launch app → splash visible briefly → land on Main. Will require manually completing an OAuth flow first (or seeding the store via instrumented test setup).
- [ ] 6.3 Manual smoke (rotation during splash): rotate device while splash is visible → no crash, no flicker, lands on the correct destination after rotation. Verified at least once before commit.
- [ ] 6.4 Document the missing instrumented coverage on `nubecita-16a` (real Keystore + real session round-trip + cold-start splash → routing). Append a note to that issue.

## 7. Lint, format, pre-commit

- [ ] 7.1 `./gradlew spotlessApply` then `./gradlew test lint`.
- [ ] 7.2 `./gradlew :feature:login:impl:validateDebugScreenshotTest` — baselines unchanged (no UI render changes; only effect-handler logic changed).
- [ ] 7.3 `pre-commit run --all-files`.

## 8. Bd graph + PR ceremony

- [ ] 8.1 `nubecita-30c` claimed; `nubecita-e9s` already closed as superseded (see commit messages).
- [ ] 8.2 Branch: `feat/nubecita-30c-splash-auth-routing` off `main`.
- [ ] 8.3 Conventional Commit: `feat(splash): add SessionState + system splash + auth-gated routing in MainActivity`. Footer: `Refs: nubecita-30c`.
- [ ] 8.4 PR body: `Closes: nubecita-30c`.
- [ ] 8.5 Post-merge: `bd close nubecita-30c` and `openspec archive add-splash-routing -y`.
