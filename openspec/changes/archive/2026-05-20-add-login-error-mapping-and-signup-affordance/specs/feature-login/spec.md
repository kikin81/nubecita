## MODIFIED Requirements

### Requirement: `LoginViewModel` drives `beginLogin` and emits `LaunchCustomTab` on success

`LoginViewModel` SHALL extend `MviViewModel<LoginState, LoginEvent, LoginEffect>`. On receiving `LoginEvent.SubmitLogin` with a non-blank handle, it SHALL (a) set `isLoading = true`, (b) call `authRepository.beginLogin(state.handle)`, (c) on success emit `LoginEffect.LaunchCustomTab(url)` and reset `isLoading`, (d) on failure classify the wrapped `Throwable` into a typed `LoginError` variant and set `state.errorMessage` accordingly without forwarding any library text to the screen. Blank-handle submissions SHALL set `errorMessage = LoginError.BlankHandle` without invoking the repository.

The failure-classification SHALL distinguish at least the following variants of `LoginError`:

- `LoginError.HandleNotFound(handle: String)` — the submitted handle did not resolve to a DID. Detected by `Throwable is OAuthDiscoveryException && message?.startsWith("Failed to resolve handle") == true`.
- `LoginError.Network` — a network failure occurred during discovery. Detected by walking the `Throwable.cause` chain for an `IOException`, `UnknownHostException`, or `SocketTimeoutException` (all JDK stdlib types; Ktor types are intentionally excluded because Ktor is not on `:feature:login:impl`'s compile classpath).
- `LoginError.Generic` — any other `Throwable`. The library message SHALL NOT be exposed to the UI.

`LoginError.Failure(cause: String?)` is **removed** by this change; no production code in `:feature:login:impl` SHALL reference it after the migration.

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

### Requirement: `LoginViewModel` collects from `OAuthRedirectBroker` and completes login

`LoginViewModel` SHALL inject `OAuthRedirectBroker` and `AuthRepository` and, in its `init` block, launch a coroutine in `viewModelScope` that collects `broker.redirects`. For each emitted URI it SHALL call `authRepository.completeLogin(uri)`:

- on success, it SHALL emit `LoginEffect.LoginSucceeded`;
- on failure, it SHALL classify the wrapped `Throwable` using the same mapping as the `beginLogin` failure arm (handle-not-found / network / generic) and set `state.errorMessage` to the resulting `LoginError` variant. The classification SHALL NOT forward library text. The handle used for any `LoginError.HandleNotFound` variant SHALL be `state.handle` at the time of failure (the `completeLogin` path normally cannot produce a handle-not-found result, but the mapping table is shared for consistency). No navigation effect SHALL be emitted on failure.

The collection SHALL persist for the lifetime of the VM and SHALL stop when `viewModelScope` is cancelled (i.e., when the VM is cleared).

#### Scenario: Broker emission triggers completeLogin and emits LoginSucceeded

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.success(Unit)`
- **THEN** `LoginViewModel.effects` SHALL emit `LoginEffect.LoginSucceeded` exactly once

#### Scenario: completeLogin generic failure populates errorMessage as Generic

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IllegalStateException("invalid code"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Generic` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted, and the string `"invalid code"` SHALL NOT appear anywhere in `state`

#### Scenario: completeLogin network failure populates errorMessage as Network

- **WHEN** the broker publishes a redirect URI and a fake `AuthRepository.completeLogin` returns `Result.failure(IOException("connect failed"))`
- **THEN** the VM's `state.errorMessage` SHALL become `LoginError.Network` and `LoginEffect.LoginSucceeded` SHALL NOT be emitted

## ADDED Requirements

### Requirement: Login screen exposes a "Create one on Bluesky" sign-up affordance

`LoginScreen` SHALL render a secondary call-to-action below the primary "Sign in" button that lets a user without a Bluesky account reach Bluesky's web sign-up flow. The CTA SHALL be:

- Always visible (not gated behind an error state).
- A separate tappable button — not a clickable span inside an error message.
- Composed of supporting copy from `R.string.login_signup_cta_supporting` ("Don't have an account?") and a button labelled `R.string.login_signup_cta_label` ("Create one on Bluesky").

Tapping the button SHALL dispatch `LoginEvent.OpenSignup`. `LoginViewModel` SHALL respond by emitting `LoginEffect.LaunchCustomTab("https://bsky.app/signup")`. The screen's existing `LaunchedEffect(viewModel)` collector dispatches `LaunchCustomTab` to a Chrome Custom Tab via `CustomTabsIntent`; no separate effect variant is required.

The sign-up URL SHALL be a private `const val` inside `LoginViewModel.kt` (`BLUESKY_SIGNUP_URL = "https://bsky.app/signup"`) so the production URL is asserted by a single source.

#### Scenario: CTA is rendered on the login screen

- **WHEN** `LoginScreen` is composed with any state
- **THEN** the composable tree SHALL contain a button whose text equals `stringResource(R.string.login_signup_cta_label)` and supporting copy whose text equals `stringResource(R.string.login_signup_cta_supporting)`

#### Scenario: Tapping the CTA dispatches OpenSignup

- **WHEN** the user taps the sign-up CTA
- **THEN** the screen's `onEvent` callback SHALL be invoked with `LoginEvent.OpenSignup`

#### Scenario: OpenSignup emits a LaunchCustomTab for bsky.app/signup

- **WHEN** `LoginViewModel.handleEvent(LoginEvent.OpenSignup)` is called
- **THEN** the VM SHALL emit `LoginEffect.LaunchCustomTab("https://bsky.app/signup")` exactly once, and SHALL NOT mutate `state` (`isLoading`, `handle`, and `errorMessage` SHALL retain their prior values)

#### Scenario: CTA tap launches a Chrome Custom Tab on the device

- **WHEN** an instrumented test taps the sign-up CTA on `LoginScreen`
- **THEN** a `CustomTabsIntent` SHALL be launched whose target URI equals `https://bsky.app/signup`

### Requirement: `LoginError` is a typed sum with no free-form cause string

`LoginError` SHALL be a `sealed interface` whose only allowed implementations are:

- `data object BlankHandle : LoginError` — blank or whitespace-only handle submitted.
- `data class HandleNotFound(val handle: String) : LoginError` — the submitted handle did not resolve to a DID. The `handle` SHALL be the literal string submitted by the user (after trim).
- `data object Network : LoginError` — a network failure occurred during the login flow.
- `data object Generic : LoginError` — any unclassified failure.

Each implementation SHALL be `@Immutable`-annotated to preserve Compose stability inference across module boundaries. `LoginError.Failure(cause: String?)` SHALL NOT exist after this change; no production code in `:feature:login:impl` SHALL reference it.

The screen SHALL own the `LoginError` → user-facing string resolution via a `displayStringFor(error: LoginError): String` `@Composable` helper that maps each variant to a `stringResource(...)` call. The VM SHALL NOT depend on Android resources or hold any user-facing string.

#### Scenario: Sum exhaustiveness

- **WHEN** `displayStringFor(error)` is compiled
- **THEN** the `when (error)` expression SHALL be exhaustive over all four `LoginError` variants without a residual `else` branch

#### Scenario: HandleNotFound carries the submitted handle

- **WHEN** the VM emits `LoginError.HandleNotFound("alise.bsky.social")`
- **THEN** `displayStringFor` SHALL produce a string that includes the substring `"alise.bsky.social"` (interpolated from the `HandleNotFound.handle` value into the resource template)

#### Scenario: Generic does not leak library text

- **WHEN** the screen renders `LoginError.Generic`
- **THEN** the rendered string SHALL equal `stringResource(R.string.login_error_generic_failure)` and SHALL NOT contain any substring originating from a throwable's `message`
