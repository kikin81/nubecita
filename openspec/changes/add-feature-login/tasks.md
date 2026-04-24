## 1. Scaffold `:feature:login:api`

- [ ] 1.1 Create `feature/login/api/build.gradle.kts` applying `com.android.library`, `org.jetbrains.kotlin.plugin.serialization`, and `com.squareup.sort-dependencies`. Namespace `net.kikin.nubecita.feature.login.api`. `compileSdk = 37`, `minSdk = 26`, `jvmToolchain(17)`. No Hilt, no Compose.
- [ ] 1.2 Create `feature/login/api/consumer-rules.pro` (empty) and wire `consumerProguardFiles("consumer-rules.pro")` in the defaultConfig.
- [ ] 1.3 Add `include(":feature:login:api")` to `settings.gradle.kts` (alphabetically adjacent to `:designsystem`).
- [ ] 1.4 Add to `gradle/libs.versions.toml`: library alias `androidx-navigation3-entry = { module = "androidx.navigation3:navigation3-entry", version.ref = "nav3Core" }` (if needed to see the `NavKey` interface from `:api`). Verify whether `navigation3-runtime` already exposes `NavKey` transitively.
- [ ] 1.5 Create `feature/login/api/src/main/kotlin/net/kikin/nubecita/feature/login/api/Login.kt`: `@Serializable data object Login : NavKey`. Dependencies: `implementation(libs.androidx.navigation3.runtime)` (or `.entry` per 1.4), `implementation(libs.kotlinx.serialization.json)`.
- [ ] 1.6 Verify `./gradlew :feature:login:api:assembleDebug` succeeds; verify no unexpected deps with `./gradlew :feature:login:api:dependencies --configuration debugCompileClasspath | head -30`.

## 2. Scaffold `:feature:login:impl`

- [ ] 2.1 Create `feature/login/impl/build.gradle.kts` applying `com.android.library`, `org.jetbrains.kotlin.plugin.compose`, `com.google.dagger.hilt.android`, `com.google.devtools.ksp`, and `com.squareup.sort-dependencies`. Namespace `net.kikin.nubecita.feature.login.impl`. `compileSdk = 37`, `minSdk = 26`, `jvmToolchain(17)`, `buildFeatures { compose = true }`.
- [ ] 2.2 Create `feature/login/impl/consumer-rules.pro` (empty) and wire it.
- [ ] 2.3 Add `include(":feature:login:impl")` to `settings.gradle.kts`.
- [ ] 2.4 Dependencies: `api(project(":feature:login:api"))` (Login NavKey is part of `:impl`'s public surface — consumers of `:impl` that need the key see it transitively), plus `implementation(project(":core:auth"))`, `implementation(project(":designsystem"))`, `implementation(platform(libs.androidx.compose.bom))`, `implementation(libs.androidx.compose.material3)`, `implementation(libs.androidx.compose.ui)`, `implementation(libs.androidx.compose.ui.tooling.preview)`, `implementation(libs.androidx.hilt.navigation.compose)`, `implementation(libs.androidx.navigation3.runtime)`, `implementation(libs.androidx.navigation3.ui)`, `implementation(libs.androidx.lifecycle.runtime.compose)`, `implementation(libs.androidx.lifecycle.viewmodel.compose)`, `implementation(libs.hilt.android)`, `implementation(libs.kotlinx.collections.immutable)`, `implementation(libs.kotlinx.coroutines.core)`, `implementation(libs.kotlinx.serialization.json)`, `ksp(libs.hilt.android.compiler)`, `debugImplementation(libs.androidx.compose.ui.tooling)`, `testImplementation(libs.junit)`, `testImplementation(libs.kotlinx.coroutines.test)`.
- [ ] 2.5 Add `kotlinx-coroutines-core` to the version catalog if not already aliased (it's currently only via `kotlinx-coroutines-test`). Verify first and add the alias only if needed.
- [ ] 2.6 Verify `./gradlew :feature:login:impl:assembleDebug` succeeds (empty module builds clean — real source lands below).

## 3. MVI contract + ViewModel

- [ ] 3.1 Hoist the `:app` MVI base classes out of `:app` into `:core:auth`? **Decision:** no — they are tightly coupled to `:app`'s MVI convention, not auth. Instead, promote them to a new `:core:ui-mvi` module (or just use them cross-module via `implementation(project(":app"))` — rejected, illegal back-dep). **Correct path:** extract `MviViewModel`, `UiState`, `UiEvent`, `UiEffect` into `:core:common` (new module) OR defer by copying the base into `:feature:login:impl` and de-duplicating later. **Implementation choice:** create a minimal `:core:common` library module in this change that re-hosts those four classes (they are 4 files, ~50 lines total) so `:feature:login:impl` can extend the same `MviViewModel<S, E, F>` as `:app`'s `MainScreenViewModel`. Update `:app` to depend on `:core:common` and update imports.
- [ ] 3.2 Create `:core:common` module: build.gradle.kts (library, Kotlin-android, no Compose, no Hilt, namespace `net.kikin.nubecita.core.common`). Move `app/src/main/java/net/kikin/nubecita/ui/mvi/` into `core/common/src/main/kotlin/net/kikin/nubecita/core/common/mvi/`. Update imports across `:app`.
- [ ] 3.3 Add `implementation(project(":core:common"))` to `:feature:login:impl` and `:app`. Move `:app`'s MVI test helpers (`MainDispatcherRule`, `MviViewModelTest`) to `:core:common/src/test/...` too. Run `./gradlew :app:testDebugUnitTest` to verify nothing regressed.
- [ ] 3.4 Create `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/LoginContract.kt`: `data class LoginState(val handle: String = "", val isLoading: Boolean = false, val errorMessage: String? = null) : UiState`, `sealed interface LoginEvent : UiEvent { data class HandleChanged(val handle: String) : LoginEvent; data object SubmitLogin : LoginEvent; data object ClearError : LoginEvent }`, `sealed interface LoginEffect : UiEffect { data class LaunchCustomTab(val url: String) : LoginEffect; data class ShowError(val message: String) : LoginEffect }`.
- [ ] 3.5 Create `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/LoginViewModel.kt`: `@HiltViewModel class LoginViewModel @Inject constructor(private val authRepository: AuthRepository) : MviViewModel<LoginState, LoginEvent, LoginEffect>()`. Initial state `LoginState()`. `handleEvent` dispatches `HandleChanged` (updates state, clears errorMessage), `SubmitLogin` (validates non-blank, sets isLoading, calls repository, emits effect/state based on Result), `ClearError` (sets errorMessage = null).
- [ ] 3.6 Unit test `feature/login/impl/src/test/kotlin/net/kikin/nubecita/feature/login/impl/LoginViewModelTest.kt` with a `FakeAuthRepository`. Scenarios: (a) success emits LaunchCustomTab, (b) failure populates errorMessage, (c) blank handle is rejected without calling repository, (d) HandleChanged updates state and clears errorMessage. Uses the existing `MainDispatcherRule` pattern.

## 4. Login screen composable

- [ ] 4.1 Create `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/LoginScreen.kt`. Stateless root `LoginScreen(state: LoginState, onEvent: (LoginEvent) -> Unit, modifier: Modifier = Modifier)` composable. Stateful wrapper `LoginScreen(viewModel: LoginViewModel = hiltViewModel(), modifier: Modifier = Modifier)` that collects state and delegates events.
- [ ] 4.2 UI contents (all using `NubecitaTheme` tokens from `:designsystem`): `Column` with `NubecitaSpacing` padding; a centered app mark or title; single `OutlinedTextField` for handle (`KeyboardType.Email`-ish, no password transformation); primary `Button("Sign in with Bluesky")` that fires `SubmitLogin` when tapped; `CircularProgressIndicator` shown when `isLoading`; `Text` in error color when `errorMessage != null`. Button disabled when `isLoading`.
- [ ] 4.3 `@Preview` functions for four states: empty, typed handle, loading, error. Add a screenshot test target if the module is wired for it (inherits from the `android.experimental.enableScreenshotTest` flag set on `:app`; may need to enable per-module).
- [ ] 4.4 Manual smoke: `./gradlew :app:installDebug` and verify the preview renders in Android Studio. UI in the live app stays unreachable until §6 wires the NavEntry.

## 5. AtOAuth + AuthRepository bindings in `:core:auth`

- [ ] 5.1 In `:core:auth/build.gradle.kts`, add `implementation(libs.ktor.client.okhttp)` (or whatever HTTP engine `AtOAuth` needs) if not already pulled in. Verify by inspecting `AtOAuth` constructor signature at `~/code/kikinlex/oauth/src/main/kotlin/io/github/kikin81/atproto/oauth/AtOAuth.kt`.
- [ ] 5.2 Decide where `OAUTH_CLIENT_METADATA_URL` lives. Option A: BuildConfig field on `:app`, read via `net.kikin.nubecita.BuildConfig.OAUTH_CLIENT_METADATA_URL` — but `:core:auth` would need to depend on `:app` (illegal). Option B: BuildConfig field on `:core:auth` itself. Option C: inject it as a `@Named("oauthClientMetadataUrl") String` from a `:app`-provided `@Module`. **Recommended:** Option B — enable `buildConfig = true` on `:core:auth` and declare the `buildConfigField` there. URL stays configurable per-variant without `:core:auth` → `:app` coupling.
- [ ] 5.3 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/AuthRepository.kt`: `interface AuthRepository { suspend fun beginLogin(handle: String): Result<String> }`.
- [ ] 5.4 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/DefaultAuthRepository.kt`: `internal class DefaultAuthRepository @Inject constructor(private val atOAuth: AtOAuth) : AuthRepository { override suspend fun beginLogin(handle: String) = runCatching { atOAuth.beginLogin(handle) } }`.
- [ ] 5.5 Create `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/di/AtOAuthModule.kt`: `@Module @InstallIn(SingletonComponent::class) internal object AtOAuthModule` with `@Provides @Singleton provideAtOAuth(sessionStore: OAuthSessionStore, httpClient: HttpClient)` returning `AtOAuth(clientMetadataUrl = BuildConfig.OAUTH_CLIENT_METADATA_URL, sessionStore = ..., httpClient = ...)` — exact constructor shape TBD per 5.1. Also `@Provides @Singleton provideHttpClient(): HttpClient = HttpClient(OkHttp) { install(HttpTimeout) { ... } }` if `:core:auth` owns it; otherwise consume from `:app`'s existing provider via a shared module.
- [ ] 5.6 Extend `core/auth/src/main/kotlin/net/kikin/nubecita/core/auth/di/AuthBindingsModule.kt` (from nubecita-nss) with `@Binds @Singleton internal abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository`.
- [ ] 5.7 Unit test `core/auth/src/test/kotlin/net/kikin/nubecita/core/auth/DefaultAuthRepositoryTest.kt` with a fake `AtOAuth` (or via an intermediate interface if `AtOAuth` is final). Scenarios: success → Result.success, throwing → Result.failure.

## 6. Nav 3 wiring in `:app`

- [ ] 6.1 Create `app/src/main/java/net/kikin/nubecita/navigation/EntryProviderInstaller.kt`: `typealias EntryProviderInstaller = EntryProviderScope<NavKey>.() -> Unit`.
- [ ] 6.2 Create `app/src/main/java/net/kikin/nubecita/navigation/NavigationEntryPoint.kt`: `@EntryPoint @InstallIn(SingletonComponent::class) interface NavigationEntryPoint { fun entryProviderInstallers(): Set<@JvmSuppressWildcards EntryProviderInstaller> }`.
- [ ] 6.3 Update `app/src/main/java/net/kikin/nubecita/Navigation.kt`'s `MainNavigation` to (a) obtain the installer set via `EntryPoints.get(LocalContext.current.applicationContext, NavigationEntryPoint::class.java).entryProviderInstallers()`, (b) pass all three decorators (`rememberSceneSetupNavEntryDecorator()`, `rememberSavedStateNavEntryDecorator()`, `rememberViewModelStoreNavEntryDecorator()`) to `NavDisplay.entryDecorators`, (c) inside `entryProvider { }`, keep the existing `entry<Main>` and spread the installer set via `installers.forEach { it() }`.
- [ ] 6.4 In `:feature:login:impl`, create `feature/login/impl/src/main/kotlin/net/kikin/nubecita/feature/login/impl/di/LoginNavigationModule.kt`: `@Module @InstallIn(SingletonComponent::class) internal object LoginNavigationModule` with `@Provides @IntoSet internal fun provideLoginEntries(): EntryProviderInstaller = { entry<Login> { LoginScreen() } }`. The typealias import comes from `:app`'s package — **problem:** `:app`-declared typealias isn't reachable from `:feature:login:impl` (illegal back-dep). **Fix:** relocate the typealias to `:core:common` (created in §3.2). Update §6.1 accordingly and keep the `NavigationEntryPoint` in `:app`.
- [ ] 6.5 `:app/build.gradle.kts` add `implementation(project(":feature:login:impl"))` and `buildFeatures { buildConfig = true }` (if not already set) plus `buildConfigField("String", "OAUTH_CLIENT_METADATA_URL", "\"https://kikin81.github.io/nubecita/oauth/client-metadata.json\"")` — **or** move the BuildConfig to `:core:auth` per 5.2 decision.
- [ ] 6.6 Verify with `./gradlew :app:assembleDebug` and by manually pushing `Login` onto the back stack from a temporary `MainScreen` button (revert the temp button before final commit) to confirm end-to-end rendering.

## 7. Test pass + verification

- [ ] 7.1 Run `./gradlew :feature:login:api:assembleDebug :feature:login:impl:testDebugUnitTest :core:auth:testDebugUnitTest :app:testDebugUnitTest :app:assembleDebug` and confirm all green.
- [ ] 7.2 Run `./gradlew spotlessApply` then `:core:auth:lint :feature:login:api:lint :feature:login:impl:lint :app:lint`. Address anything flagged; do not baseline unless unavoidable.
- [ ] 7.3 Spec requirement check: `grep -rn "LoginScreen\|LoginViewModel\|LoginContract" app/src/main/` SHALL return no matches (confirms `:app` does not import `:impl` internals).
- [ ] 7.4 Spec requirement check: `grep -rn "AtOAuth" app/src/main/` SHALL return no matches (confirms `:app` does not directly inject `AtOAuth`).
- [ ] 7.5 Spec requirement check: `./gradlew :feature:login:api:dependencies --configuration debugCompileClasspath` SHALL list only `navigation3-runtime` and `kotlinx-serialization-json` (plus Kotlin stdlib) — no Compose, no Hilt, no `:core:auth`.
- [ ] 7.6 Run `pre-commit run --all-files` and resolve anything.

## 8. Documentation

- [ ] 8.1 Create `feature/login/README.md` at the feature root (not per-submodule) documenting: module pair purpose, api/impl split rationale, `Login` NavKey entry point, Hilt multibinding pattern, how a second feature module copies this shape.
- [ ] 8.2 Defer CLAUDE.md convention update to the next feature module's PR (per design §8).

## 9. Bd graph + PR ceremony

- [ ] 9.1 Claim both `nubecita-uf5` and `nubecita-4g7`.
- [ ] 9.2 Branch: `feat/nubecita-uf5-oauth-login-screen` off `main`.
- [ ] 9.3 Conventional Commit with `Refs: nubecita-uf5 nubecita-4g7` footer. PR title: `feat(feature-login): OAuth login screen + :feature:login api/impl split + :core:auth AtOAuth bindings`. PR body includes `Closes: nubecita-uf5` and `Closes: nubecita-4g7`.
- [ ] 9.4 After merge: `bd close nubecita-uf5 nubecita-4g7` and `openspec archive add-feature-login -y`.
