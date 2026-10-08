# feature-login Specification

## Purpose
The OAuth login surface: the `:api` NavKey / `:impl` entry-provider split, the handle-entry screen with its typed `LoginError` sum and sign-up affordance, and `LoginViewModel`'s round-trip — `beginLogin` → Custom Tab → deep-linked redirect published to `OAuthRedirectBroker` → `completeLogin` → post-login navigation.
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

`LoginViewModel` SHALL extend `MviViewModel<LoginState, LoginEvent, LoginEffect>`. On receiving non-blank `LoginEvent.SubmitLogin`, it SHALL set `isLoading = true`, call `authRepository.beginLogin(state.handle)`, emit `LoginEffect.LaunchCustomTab(url)` on success, or map failures into typed `LoginError` variants (`HandleNotFound`, `Network`, `Generic`). Blank handle submissions SHALL set `errorMessage = LoginError.BlankHandle`. `LoginError.Failure` MUST NOT be used.

#### Scenario: Successful beginLogin emits LaunchCustomTab

- **WHEN** `LoginViewModel.handleEvent(SubmitLogin)` is called with `state.handle = "alice.bsky.social"` and a fake `AuthRepository` returns `Result.success("https://bsky.social/oauth/authorize?...")`
- **THEN** the VM SHALL emit a `LoginEffect.LaunchCustomTab` effect whose `url` matches the repository's returned value, and the subsequent state SHALL have `isLoading = false` and `errorMessage = null`

#### Scenario: Handle-not-found maps to HandleNotFound

- **WHEN** `SubmitLogin` is called with `state.handle = "alise.bsky.social"` and the fake `AuthRepository` returns `Result.failure(OAuthDiscoveryException("Failed to resolve handle 'alise.bsky.social': neither DNS TXT record nor HTTP returned a valid DID"))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.HandleNotFound("alise.bsky.social")` and `isLoading = false`, and no `LaunchCustomTab` effect SHALL be emitted

#### Scenario: Network failure (direct IOException) maps to Network

- **WHEN** `SubmitLogin` is called and the fake `AuthRepository` returns `Result.failure(IOException("no network"))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.Network` and `isLoading = false`

#### Scenario: Network failure (wrapped IOException) maps to Network

- **WHEN** `SubmitLogin` is called and the fake `AuthRepository` returns `Result.failure(OAuthDiscoveryException("Failed to fetch resource server metadata from https://example.com", IOException("connect timed out")))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.Network` and `isLoading = false`

#### Scenario: Unknown failure maps to Generic without leaking message

- **WHEN** `SubmitLogin` is called and the fake `AuthRepository` returns `Result.failure(IllegalStateException("authorization_endpoint missing from auth server metadata at https://example.com"))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.Generic` and `isLoading = false`, and the library message string SHALL NOT appear in any `LoginError` field

#### Scenario: Blank handle is rejected without calling the repository

- **WHEN** `SubmitLogin` is called with `state.handle = ""` (or whitespace-only)
- **THEN** `errorMessage` SHALL be set to `LoginError.BlankHandle`, `isLoading` SHALL remain `false`, and the `AuthRepository` SHALL NOT be invoked

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

`LoginViewModel` SHALL inject `OAuthRedirectBroker` and `AuthRepository` and collect `broker.redirects` in `viewModelScope`. For each emitted URI it SHALL invoke `authRepository.completeLogin(uri)`. On success it SHALL emit `LoginEffect.LoginSucceeded`; on failure it SHALL classify the `Throwable` into a typed `LoginError` variant (`HandleNotFound`, `Network`, or `Generic`) and set `state.errorMessage` without exposing library error text or emitting navigation effects.

#### Scenario: Broker emission triggers completeLogin and emits LoginSucceeded

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.success(Unit)`
- **THEN** `LoginViewModel.effects` SHALL emit `LoginEffect.LoginSucceeded` exactly once

#### Scenario: completeLogin generic failure populates errorMessage as Generic

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IllegalStateException("invalid code"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Generic` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted, and the string `"invalid code"` SHALL NOT appear anywhere in `state`

#### Scenario: completeLogin network failure populates errorMessage as Network

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IOException("connect failed"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Network` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted

### Requirement: `LoginEffect.LoginSucceeded` signals post-login navigation

`LoginEffect` SHALL include a `data object LoginSucceeded : LoginEffect` variant. It SHALL carry no payload — the destination is the screen's responsibility, not the VM's.

#### Scenario: Effect is a singleton object

- **WHEN** `LoginEffect.LoginSucceeded` is referenced from any module that depends on `:feature:login:impl`
- **THEN** it SHALL be the same instance across references (sealed-interface `data object` semantics)

### Requirement: `LoginScreen` `LaunchedEffect` handles both side-effecting effects

The stateful `LoginScreen()` SHALL collect `viewModel.effects` in a single `LaunchedEffect(viewModel)`:
- `LoginEffect.LaunchCustomTab(url)` launches `url` using `CustomTabsIntent`.
- `LoginEffect.LoginSucceeded` is a no-op at screen level because post-login routing is handled reactively by `MainActivity` observing `SessionStateProvider.state`.
The effect-handling `when` expression MUST remain exhaustive.

#### Scenario: LaunchCustomTab opens the URL in a Custom Tab

- **WHEN** the VM emits `LoginEffect.LaunchCustomTab("https://bsky.social/oauth/authorize?...")`
- **THEN** `LoginScreen` SHALL invoke `CustomTabsIntent.launchUrl(context, ...)` with that URL

#### Scenario: LoginSucceeded does not directly mutate the back stack

- **WHEN** the VM emits `LoginEffect.LoginSucceeded`
- **THEN** `LoginScreen` SHALL NOT invoke any `Navigator` method directly; the post-login destination swap SHALL come from `MainActivity`'s reactive observer of `SessionStateProvider.state` transitioning to `SignedIn`

#### Scenario: Post-login routing produces a single-entry back stack with Main

- **WHEN** `AuthRepository.completeLogin` succeeds (the VM emits `LoginSucceeded` and `SessionStateProvider` transitions to `SignedIn`)
- **THEN** `Navigator.backStack` SHALL contain exactly `[Main]` (the prior `[Login]` having been replaced via `navigator.replaceTo(Main)` from `MainActivity`'s collector)

### Requirement: `:app` AndroidManifest captures the OAuth redirect via deep link

`app/src/main/AndroidManifest.xml` SHALL declare `android:launchMode="singleTask"` on `MainActivity` so OAuth redirects re-deliver via `onNewIntent`. `MainActivity` SHALL declare an `<intent-filter>` matching `VIEW` and `BROWSABLE` for scheme `net.kikin.nubecita`. The scheme MUST equal the app's `applicationId` and the `redirect_uris` registered in `client-metadata.json`. The `LAUNCHER` filter SHALL remain untouched.

#### Scenario: Manifest declares both intent filters

- **WHEN** `app/src/main/AndroidManifest.xml` is parsed
- **THEN** the `<activity android:name=".MainActivity">` element SHALL contain both the `LAUNCHER` filter (existing) and the `VIEW` + `BROWSABLE` filter for `net.kikin.nubecita`

### Requirement: `MainActivity` publishes captured redirect URIs to `OAuthRedirectBroker`

`MainActivity` SHALL inject `OAuthRedirectBroker` and handle incoming OAuth redirect intents in `onCreate` and `onNewIntent`. When an incoming intent's scheme matches `net.kikin.nubecita`, `MainActivity` SHALL launch a coroutine to call `broker.publish(intent.data.toString())` and set `intent.data = null` to prevent re-firing on configuration changes. Intents with other schemes SHALL be ignored.

#### Scenario: Warm-start redirect publishes through the broker

- **WHEN** an authenticated browser invokes `net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz` and Android delivers it to the running `MainActivity` via `onNewIntent`
- **THEN** `MainActivity` SHALL call `broker.publish("net.kikin.nubecita:/oauth-redirect?code=abc&state=xyz")` and SHALL clear `intent.data`

#### Scenario: Cold-start redirect publishes through the broker

- **WHEN** the same redirect arrives while the app process is dead and Android cold-starts `MainActivity` with the redirect intent
- **THEN** `MainActivity.onCreate`'s intent handler SHALL publish the URI; the `LoginViewModel`'s `init`-time collector SHALL receive the buffered emission once it subscribes

### Requirement: `LoginScreen` wraps content in a `Scaffold` with `WindowInsets.safeDrawing`

The stateless `LoginScreen` composable MUST wrap its content in a `Scaffold(contentWindowInsets = WindowInsets.safeDrawing)` and apply the inner padding to its inner content with `Modifier.padding(innerPadding).consumeWindowInsets(innerPadding)`. This ensures proper edge-to-edge support, keeps fields visible when the IME opens, and prevents status and gesture bar occlusion.

#### Scenario: IME opens without occluding the handle field

- **WHEN** the user taps the `OutlinedTextField` and the soft keyboard opens
- **THEN** the field SHALL remain fully visible above the IME — the Scaffold's `WindowInsets.safeDrawing` includes the IME inset, and `adjustResize` (already declared in the AndroidManifest) re-lays out the content area accordingly

#### Scenario: Content respects status bar inset on cold start

- **WHEN** `LoginScreen` is the start destination and the app cold-starts with edge-to-edge enabled
- **THEN** the title `Text` SHALL appear below the status bar (no visual overlap), and the status bar area SHALL show the underlying surface color

#### Scenario: Content respects gesture-nav bottom inset

- **WHEN** `LoginScreen` is rendered on a 3-button or gesture-nav device
- **THEN** the submit button SHALL appear above the system gesture bar — the bottom inset SHALL be reflected in the Scaffold's `innerPadding.calculateBottomPadding()`

### Requirement: Login screen exposes a "Create one on Bluesky" sign-up affordance

`LoginScreen` SHALL render a secondary call-to-action below the sign-in button using supporting copy `R.string.login_signup_cta_supporting` and button label `R.string.login_signup_cta_label`. Tapping it SHALL dispatch `LoginEvent.OpenSignup`. `LoginViewModel` SHALL emit `LoginEffect.LaunchCustomTab("https://bsky.app/")`, handled via `CustomTabsIntent`. The target URL SHALL be defined as an `internal const val` in `LoginViewModel.kt`.

#### Scenario: CTA is rendered on the login screen

- **WHEN** `LoginScreen` is composed with any state
- **THEN** the composable tree SHALL contain a button whose text equals `stringResource(R.string.login_signup_cta_label)` and supporting copy whose text equals `stringResource(R.string.login_signup_cta_supporting)`

#### Scenario: Tapping the CTA dispatches OpenSignup

- **WHEN** the user taps the sign-up CTA
- **THEN** the screen's `onEvent` callback SHALL be invoked with `LoginEvent.OpenSignup`

#### Scenario: OpenSignup emits a LaunchCustomTab for bsky.app

- **WHEN** `LoginViewModel.handleEvent(LoginEvent.OpenSignup)` is called
- **THEN** the VM SHALL emit `LoginEffect.LaunchCustomTab("https://bsky.app/")` exactly once, and SHALL NOT mutate `state` (`isLoading`, `handle`, and `errorMessage` SHALL retain their prior values)

#### Scenario: CTA tap launches a Chrome Custom Tab on the device

- **WHEN** an instrumented test taps the sign-up CTA on `LoginScreen`
- **THEN** a `CustomTabsIntent` SHALL be launched whose target URI equals `https://bsky.app/`

### Requirement: `LoginError` is a typed sum with no free-form cause string

`LoginError` SHALL be a `@Immutable` `sealed interface` with implementations: `BlankHandle`, `HandleNotFound(val handle: String)`, `Network`, and `Generic`. `LoginError.Failure(cause: String?)` MUST NOT exist. The screen SHALL resolve `LoginError` to user-facing strings via a composable helper mapping each variant to a `stringResource(...)`. The ViewModel MUST NOT reference Android resources.

#### Scenario: Sum exhaustiveness

- **WHEN** `displayStringFor(error)` is compiled
- **THEN** the `when (error)` expression SHALL be exhaustive over all four `LoginError` variants without a residual `else` branch

#### Scenario: HandleNotFound carries the submitted handle

- **WHEN** the VM emits `LoginError.HandleNotFound("alise.bsky.social")`
- **THEN** `displayStringFor` SHALL produce a string that includes the substring `"alise.bsky.social"` (interpolated from the `HandleNotFound.handle` value into the resource template)

#### Scenario: Generic does not leak library text

- **WHEN** the screen renders `LoginError.Generic`
- **THEN** the rendered string SHALL equal `stringResource(R.string.login_error_generic_failure)` and SHALL NOT contain any substring originating from a throwable's `message`
