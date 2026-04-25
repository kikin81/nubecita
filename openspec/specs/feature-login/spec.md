# feature-login Specification

## Purpose
TBD - created by archiving change add-feature-login. Update Purpose after archive.
## Requirements
### Requirement: `:feature:login:api` exposes only NavKey types

The `:feature:login:api` Gradle module SHALL contain an `@Serializable data object Login : NavKey` type and no other production code. It SHALL NOT depend on Compose, Hilt, coroutines, `:core:auth`, or the atproto-oauth library. Any module that wants to navigate to the login screen SHALL depend on `:feature:login:api` alone, never on `:feature:login:impl`.

#### Scenario: Cross-feature link from another module

- **WHEN** a future feature module (e.g. `:feature:home:impl`) needs to add `Login` to its back stack to route an unauthenticated user to sign-in
- **THEN** it SHALL add `implementation(project(":feature:login:api"))` to its `build.gradle.kts` and SHALL NOT add a dependency on `:feature:login:impl`

#### Scenario: `:api` classpath hygiene

- **WHEN** `:feature:login:api/build.gradle.kts` is parsed
- **THEN** its declared `implementation` / `api` dependencies SHALL be empty or limited to `androidx.navigation3:navigation3-runtime` and `kotlinx-serialization-json` (required for the `NavKey` type and `@Serializable` annotation)

### Requirement: `:feature:login:impl` contributes a `NavEntry<Login>` via `@IntoSet` multibinding

The `:feature:login:impl` module SHALL expose a Hilt `@Module` that `@Provides @IntoSet` an `EntryProviderInstaller` — a function of type `EntryProviderScope<NavKey>.() -> Unit` — which registers `entry<Login> { LoginScreen(...) }`. The installer SHALL be installed in `SingletonComponent`. `:app` SHALL collect the `Set<@JvmSuppressWildcards EntryProviderInstaller>` via a Hilt `EntryPoint` and invoke every member inside `NavDisplay`'s `entryProvider { }` block.

#### Scenario: Login destination is reachable at runtime

- **WHEN** `MainNavigation` is composed and the back stack is pushed with `Login`
- **THEN** `NavDisplay` SHALL render `LoginScreen` without any `:app` code referencing `LoginScreen` directly

#### Scenario: `:app` does not import the login composable

- **WHEN** `:app` source files are inspected
- **THEN** no file in `app/src/main/` SHALL reference `LoginScreen`, `LoginViewModel`, or any `internal` symbol from `:feature:login:impl`

### Requirement: Login UI renders a single handle field plus submit and error

The login screen SHALL render: (a) an input field for a Bluesky handle (e.g. `alice.bsky.social`), (b) a primary "Sign in with Bluesky" button that submits, (c) an inline error area that appears when `state.errorMessage != null`, and (d) an inline loading indicator or disabled-button state when `state.isLoading == true`. The screen SHALL NOT render a password field, an OAuth / app-password toggle, or any reference to app passwords.

#### Scenario: Happy-path rendering

- **WHEN** `LoginScreen` is composed with an initial state (`handle = ""`, `isLoading = false`, `errorMessage = null`)
- **THEN** the screen SHALL show the handle field (empty), the submit button (enabled), and no error or loading indicator

#### Scenario: Loading state

- **WHEN** `LoginScreen` is composed with `isLoading = true`
- **THEN** the submit button SHALL be visually disabled (not tappable) and a loading indicator SHALL be visible

#### Scenario: Error state

- **WHEN** `LoginScreen` is composed with `errorMessage = "Handle not found"`
- **THEN** the string `"Handle not found"` SHALL appear in the inline error area; the submit button SHALL remain enabled so the user can retry

#### Scenario: No password field anywhere

- **WHEN** the composable tree of `LoginScreen` is inspected
- **THEN** it SHALL NOT contain any `TextField` or `OutlinedTextField` with a `PasswordVisualTransformation` or a `KeyboardType.Password` input type

### Requirement: `LoginViewModel` drives `beginLogin` and emits `LaunchCustomTab` on success

`LoginViewModel` SHALL extend `MviViewModel<LoginState, LoginEvent, LoginEffect>`. On receiving `LoginEvent.SubmitLogin` with a non-blank handle, it SHALL (a) set `isLoading = true`, (b) call `authRepository.beginLogin(state.handle)`, (c) on success emit `LoginEffect.LaunchCustomTab(url)` and reset `isLoading`, (d) on failure set `errorMessage` to the exception's `message` (or a generic fallback) and reset `isLoading`. Blank-handle submissions SHALL set `errorMessage` without invoking the repository.

#### Scenario: Successful beginLogin emits LaunchCustomTab

- **WHEN** `LoginViewModel.handleEvent(SubmitLogin)` is called with `state.handle = "alice.bsky.social"` and a fake `AuthRepository` returns `Result.success("https://bsky.social/oauth/authorize?...")`
- **THEN** the VM SHALL emit a `LoginEffect.LaunchCustomTab` effect whose `url` matches the repository's returned value, and the subsequent state SHALL have `isLoading = false` and `errorMessage = null`

#### Scenario: Failed beginLogin populates errorMessage

- **WHEN** `SubmitLogin` is called and the fake `AuthRepository` returns `Result.failure(OAuthException("handle not found"))`
- **THEN** the resulting state SHALL have `isLoading = false` and `errorMessage = "handle not found"`, and no `LaunchCustomTab` effect SHALL be emitted

#### Scenario: Blank handle is rejected without calling the repository

- **WHEN** `SubmitLogin` is called with `state.handle = ""` (or whitespace-only)
- **THEN** `errorMessage` SHALL be set to a non-empty user-facing string, `isLoading` SHALL remain `false`, and the `AuthRepository` SHALL NOT be invoked

#### Scenario: HandleChanged updates state and clears any prior error

- **WHEN** `LoginEvent.HandleChanged("a")` is sent to the VM
- **THEN** the subsequent state SHALL have `handle = "a"` and `errorMessage = null`

### Requirement: Nav 3 decorators wired in `NavDisplay` support Hilt ViewModels across entries

`:app`'s `MainNavigation` composable SHALL pass all three of `rememberSceneSetupNavEntryDecorator()`, `rememberSavedStateNavEntryDecorator()`, and `rememberViewModelStoreNavEntryDecorator()` to `NavDisplay`'s `entryDecorators` parameter, so any `hiltViewModel<T>()` call inside a feature-module `NavEntry` resolves an entry-scoped `ViewModelStore` and state survives recomposition and configuration changes.

#### Scenario: Login ViewModel is scoped to the Login NavEntry

- **WHEN** the back stack is `[Main, Login]` and `LoginScreen` calls `hiltViewModel<LoginViewModel>()`
- **THEN** the returned `LoginViewModel` instance SHALL be scoped to the `Login` entry (popping `Login` off the back stack SHALL clear this instance; re-pushing `Login` SHALL produce a fresh instance)

### Requirement: `:app` does not import `:feature:login:impl` internals

`:app`'s Kotlin source SHALL NOT reference any `internal` declaration of `:feature:login:impl`. `:app`'s interaction with login is limited to (a) adding `Login` to the back stack via the key from `:feature:login:api`, and (b) receiving the `EntryProviderInstaller` set via Hilt.

#### Scenario: Source inspection

- **WHEN** `grep -rn "LoginScreen\|LoginViewModel\|LoginContract\|DefaultLoginEntries" app/src/main/`
- **THEN** no match SHALL be found
