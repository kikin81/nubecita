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

### Requirement: `LoginViewModel` collects from `OAuthRedirectBroker` and completes login

`LoginViewModel` SHALL inject `OAuthRedirectBroker` and `AuthRepository` and, in its `init` block, launch a coroutine in `viewModelScope` that collects `broker.redirects`. For each emitted URI it SHALL call `authRepository.completeLogin(uri)`:

- on success, it SHALL emit `LoginEffect.LoginSucceeded`;
- on failure, it SHALL set `state.errorMessage = LoginError.Failure(failure.message)` and SHALL NOT emit a navigation effect.

The collection SHALL persist for the lifetime of the VM and SHALL stop when `viewModelScope` is cancelled (i.e., when the VM is cleared).

#### Scenario: Broker emission triggers completeLogin and emits LoginSucceeded

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.success(Unit)`
- **THEN** `LoginViewModel.effects` SHALL emit `LoginEffect.LoginSucceeded` exactly once

#### Scenario: completeLogin failure populates errorMessage instead of effect

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IllegalStateException("invalid code"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Failure("invalid code")` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted

### Requirement: `LoginEffect.LoginSucceeded` signals post-login navigation

`LoginEffect` SHALL include a `data object LoginSucceeded : LoginEffect` variant. It SHALL carry no payload — the destination is the screen's responsibility, not the VM's.

#### Scenario: Effect is a singleton object

- **WHEN** `LoginEffect.LoginSucceeded` is referenced from any module that depends on `:feature:login:impl`
- **THEN** it SHALL be the same instance across references (sealed-interface `data object` semantics)

### Requirement: `LoginScreen` `LaunchedEffect` handles both side-effecting effects

The stateful `LoginScreen()` overload SHALL collect `viewModel.effects` inside a single `LaunchedEffect(viewModel)` and dispatch:

- `LoginEffect.LaunchCustomTab(url)` → `CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())`, where `context` is `LocalContext.current`.
- `LoginEffect.LoginSucceeded` → `navigator.goBack()` (where `navigator` is the Hilt-bound `Navigator` from `:core:common`, obtained via the existing `EntryPoint` mechanism or `hiltViewModel`-style helper).

Subsequent variants of `LoginEffect` added in future PRs SHALL be handled here; the `when` must remain exhaustive.

#### Scenario: LaunchCustomTab opens the URL in a Custom Tab

- **WHEN** the VM emits `LoginEffect.LaunchCustomTab("https://bsky.social/oauth/authorize?...")`
- **THEN** `LoginScreen` SHALL invoke `CustomTabsIntent.launchUrl(context, ...)` with that URL

#### Scenario: LoginSucceeded pops the Login destination off the stack

- **WHEN** the VM emits `LoginEffect.LoginSucceeded` while `Login` is the top of the back stack
- **THEN** `LoginScreen` SHALL invoke `navigator.goBack()` and the back stack SHALL no longer contain `Login`

### Requirement: `:app` AndroidManifest captures the OAuth redirect via deep link

`app/src/main/AndroidManifest.xml` SHALL declare:

- `android:launchMode="singleTask"` on the `MainActivity` element so OAuth redirects re-deliver to the existing activity instance via `onNewIntent` instead of spawning a new task.
- A new `<intent-filter>` on `MainActivity` matching `<action android:name="android.intent.action.VIEW" />`, `<category android:name="android.intent.category.DEFAULT" />`, `<category android:name="android.intent.category.BROWSABLE" />`, `<data android:scheme="net.kikin.nubecita" />`. The scheme MUST equal the app's `applicationId` and the `redirect_uris` registered in `client-metadata.json`.

The existing `LAUNCHER` intent filter SHALL remain untouched.

#### Scenario: Manifest declares both intent filters

- **WHEN** `app/src/main/AndroidManifest.xml` is parsed
- **THEN** the `<activity android:name=".MainActivity">` element SHALL contain both the `LAUNCHER` filter (existing) and the `VIEW` + `BROWSABLE` filter for `net.kikin.nubecita`

### Requirement: `MainActivity` publishes captured redirect URIs to `OAuthRedirectBroker`

`MainActivity` SHALL inject `OAuthRedirectBroker` and SHALL handle the OAuth redirect intent in both `onCreate` (cold-start case) and `onNewIntent` (warm-start case). When an incoming intent's `data` is non-null and its `scheme` equals `net.kikin.nubecita`, `MainActivity` SHALL:

1. Launch a coroutine on `lifecycleScope` calling `broker.publish(intent.data.toString())`.
2. Set `intent.data = null` to prevent configuration changes (rotation, theme switch) from re-firing the redirect handler.

Intents whose scheme is not `net.kikin.nubecita` (e.g., the `LAUNCHER` MAIN intent on cold-start) SHALL be ignored by this handler.

#### Scenario: Warm-start redirect publishes through the broker

- **WHEN** an authenticated browser invokes `net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz` and Android delivers it to the running `MainActivity` via `onNewIntent`
- **THEN** `MainActivity` SHALL call `broker.publish("net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz")` and SHALL clear `intent.data`

#### Scenario: Cold-start redirect publishes through the broker

- **WHEN** the same redirect arrives while the app process is dead and Android cold-starts `MainActivity` with the redirect intent
- **THEN** `MainActivity.onCreate`'s intent handler SHALL publish the URI; the `LoginViewModel`'s `init`-time collector SHALL receive the buffered emission once it subscribes
