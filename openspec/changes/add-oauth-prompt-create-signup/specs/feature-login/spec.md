## MODIFIED Requirements

### Requirement: Login screen exposes a "Create one on Bluesky" sign-up affordance

`LoginScreen` SHALL render a secondary call-to-action below the primary "Sign in" button that lets a user without a Bluesky account reach Bluesky's web sign-up flow. The CTA SHALL be:

- Always visible (not gated behind an error state).
- A separate tappable button — not a clickable span inside an error message.
- Composed of supporting copy from `R.string.login_signup_cta_supporting` ("Don't have an account?") and a button labelled `R.string.login_signup_cta_label` ("Create one on Bluesky").
- Disabled (`enabled = !state.isLoading`) while the OAuth signup PAR is in flight.

Tapping the button SHALL dispatch `LoginEvent.OpenSignup`. `LoginViewModel` SHALL respond by:

1. Setting `state.isLoading = true` and clearing `state.errorMessage`.
2. Calling `authRepository.beginSignup()` on `viewModelScope`.
3. On success, setting `state.isLoading = false` and emitting `LoginEffect.LaunchCustomTab(authorizationUrl)` with the URL returned by the repository (a PAR'd `https://bsky.social/oauth/authorize?…` URL with `prompt=create`).
4. On failure, setting `state.isLoading = false` and classifying the wrapped `Throwable` via the same `toLoginError(handle = "")` mapping the login path uses. No `LaunchCustomTab` effect is emitted on failure.

The screen's existing `LaunchedEffect(viewModel)` collector dispatches `LaunchCustomTab` to a Chrome Custom Tab via `CustomTabsIntent`; no separate effect variant is required. After the user completes signup in the Custom Tab, `bsky.social` redirects to `net.kikin.nubecita:/oauth-redirect?code=...` and the existing `OAuthRedirectBroker` → `completeLogin` chain in `LoginViewModel.init` lands the user signed in — the same redirect-handling path the login flow uses.

The static `BLUESKY_SIGNUP_URL` constant SHALL NOT exist in production code; the authorization URL is computed per-call by `AuthRepository.beginSignup()`.

#### Scenario: CTA is rendered on the login screen

- **WHEN** `LoginScreen` is composed with any state
- **THEN** the composable tree SHALL contain a button whose text equals `stringResource(R.string.login_signup_cta_label)` and supporting copy whose text equals `stringResource(R.string.login_signup_cta_supporting)`

#### Scenario: CTA is disabled while signup PAR is in flight

- **WHEN** `LoginScreen` is composed with `state.isLoading = true`
- **THEN** the sign-up `TextButton` SHALL be visually disabled and non-tappable

#### Scenario: Tapping the CTA dispatches OpenSignup

- **WHEN** the user taps the sign-up CTA
- **THEN** the screen's `onEvent` callback SHALL be invoked with `LoginEvent.OpenSignup`

#### Scenario: OpenSignup delegates to AuthRepository and emits the returned authorization URL

- **WHEN** `LoginViewModel.handleEvent(LoginEvent.OpenSignup)` is called and a fake `AuthRepository.beginSignup()` returns `Result.success("https://bsky.social/oauth/authorize?client_id=test&request_uri=urn:test")`
- **THEN** the VM SHALL emit `LoginEffect.LaunchCustomTab("https://bsky.social/oauth/authorize?client_id=test&request_uri=urn:test")` exactly once, and the resulting state SHALL have `isLoading = false` and `errorMessage = null`

#### Scenario: OpenSignup failure routes through the typed LoginError sum

- **WHEN** `LoginViewModel.handleEvent(LoginEvent.OpenSignup)` is called and a fake `AuthRepository.beginSignup()` returns `Result.failure(IOException("offline"))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.Network` and `isLoading = false`, and no `LaunchCustomTab` effect SHALL be emitted

#### Scenario: OAuthSignupNotSupportedException maps to Generic

- **WHEN** `LoginViewModel.handleEvent(LoginEvent.OpenSignup)` is called and a fake `AuthRepository.beginSignup()` returns `Result.failure(OAuthSignupNotSupportedException(authServerUrl = "https://example.com", advertisedPromptValues = listOf("login")))`
- **THEN** the resulting state SHALL have `errorMessage = LoginError.Generic` and `isLoading = false`, and no library text SHALL leak into any `LoginError` field

#### Scenario: Instrumented CTA tap launches a Chrome Custom Tab for the PAR'd URL

- **WHEN** an instrumented test taps the sign-up CTA on `LoginScreen` with a `FakeAuthRepository` whose `beginSignup` returns a fixed test URL
- **THEN** a `CustomTabsIntent` SHALL be launched whose target URI equals the fake's returned URL

## ADDED Requirements

### Requirement: `AuthRepository` exposes a `beginSignup` entry point

The `AuthRepository` interface in `:core:auth` SHALL expose a `suspend fun beginSignup(): Result<String>` method. The method SHALL delegate to `AtOAuth.beginSignup(authServer = "bsky.social", requirePromptCreateSupport = true)` and wrap the result as `Result.success(authorizationUrl)` or `Result.failure(throwable)`.

The method SHALL NOT accept any handle, DID, or auth-server parameter — the defaults are correct for every nubecita call site. If a future caller needs a non-default auth server or override, the interface gains parameters in a later change.

#### Scenario: Successful PAR returns the authorization URL

- **WHEN** the underlying `AtOAuth.beginSignup()` returns `"https://bsky.social/oauth/authorize?…"`
- **THEN** `AuthRepository.beginSignup()` SHALL return `Result.success(that-url)` unchanged

#### Scenario: Network failure during PAR wraps as Result.failure

- **WHEN** `AtOAuth.beginSignup()` throws an `IOException`
- **THEN** `AuthRepository.beginSignup()` SHALL return `Result.failure(IOException(...))`

#### Scenario: OAuthSignupNotSupportedException propagates unchanged

- **WHEN** `AtOAuth.beginSignup()` throws `OAuthSignupNotSupportedException` (the auth server's metadata does not advertise `"create"`)
- **THEN** `AuthRepository.beginSignup()` SHALL return `Result.failure(OAuthSignupNotSupportedException(...))` with the thrown exception preserved
