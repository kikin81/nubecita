## Context

`LoginViewModel` today calls `authRepository.beginLogin(handle)` and, on failure, copies `failure.message` directly into `LoginError.Failure(cause)`. The screen renders that string verbatim via:

```kotlin
is LoginError.Failure ->
    error.cause?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.login_error_generic_failure)
```

`atproto-kotlin`'s `DiscoveryChain.resolveHandle` (in `~/code/kikinlex/oauth/.../DiscoveryChain.kt`) throws `OAuthDiscoveryException` with messages like:

- `Failed to resolve handle 'alice': neither DNS TXT record (_atproto.alice) nor HTTP (https://alice/.well-known/atproto-did) returned a valid DID` — handle didn't resolve.
- `Failed to fetch resource server metadata from <url>` (cause is the underlying `IOException` / `HttpRequestTimeoutException`) — network failure during discovery.
- `Resource server metadata at <url> returned 404` — handle resolved but PDS metadata is broken.
- `Unsupported DID method: did:foo` — exotic DID method.
- `authorization_endpoint missing from auth server metadata at <url>` — server config bug.

The screen exposes none of this hierarchy. A typo like `alise.bsky.social` produces the first message; a phone-on-airplane-mode submit produces the second; a genuine server bug produces the third — all rendered as raw library text.

`LoginEffect` already carries `LaunchCustomTab(url)` for the OAuth handoff. The screen collects it in `LaunchedEffect(viewModel)` and dispatches to `CustomTabsIntent.Builder().build().launchUrl(context, url.toUri())`. The same plumbing can carry a sign-up-URL launch with no new module deps.

## Goals / Non-Goals

**Goals:**

- Replace `LoginError.Failure(cause: String?)` with a typed sum so the screen owns the user-facing string for every error variant.
- Map `OAuthDiscoveryException` and network exceptions in `LoginViewModel` — keep the VM Android-resource-free; the screen handles the `LoginError` → `stringResource` resolution.
- Add a "Don't have an account? Create one on Bluesky" CTA on the login screen that launches a Chrome Custom Tab to `https://bsky.app/signup`.
- Cover the new states with unit tests (mapping), screenshot tests (each error variant + CTA), and one instrumentation test (CTA tap launches a `CustomTabsIntent`).

**Non-Goals:**

- Building in-app account creation via `com.atproto.server.createAccount`.
- Multi-PDS picker UI.
- Live handle-availability probing (rate-limited `resolveHandle` on every keystroke).
- Client-side handle validation beyond the existing blank-check (e.g., format checks for missing TLD, double dots).
- Localized strings beyond the default `values/strings.xml` resource family.

## Decisions

### 1. Typed `LoginError` sum, not free-form strings

Replace:

```kotlin
sealed interface LoginError {
    data object BlankHandle : LoginError
    data class Failure(val cause: String?) : LoginError
}
```

with:

```kotlin
sealed interface LoginError {
    data object BlankHandle : LoginError
    data class HandleNotFound(val handle: String) : LoginError
    data object Network : LoginError
    data object Generic : LoginError
}
```

Rationale:

- The VM no longer threads library text to the screen. The "Failed to resolve DID from /.well-known/atproto-did: 404" leak we're fixing was structurally enabled by `cause: String?`.
- `HandleNotFound` carries the handle so the screen can render "We couldn't find an account for **alice**." — the message itself is the actionable cue.
- `Network` and `Generic` are objects (no payload); their copy is fully resource-driven.

**Alternatives considered:**

- *Keep `Failure(cause: String?)` and add the typed variants alongside it.* Rejected — leaves the leaky path in place; future regressions can quietly route through it.
- *Carry a `cause: Throwable?` inside each variant.* Rejected — the screen doesn't need the throwable and tests don't need to round-trip one; the VM has the only place that inspects it.

### 2. Map exceptions in `LoginViewModel`, not in `:core:auth`

`AuthRepository` continues to return `Result<String>` / `Result<Unit>` with the raw `Throwable` from atproto-kotlin. `LoginViewModel` does the classification when the failure arm fires.

Rationale:

- Other future surfaces (re-auth dialog from a 401, settings → sign in to a second account) may want different copy for the same exception. Keeping the typed sum at the feature module preserves that flexibility.
- `:core:auth` stays as a thin domain seam — adding a per-feature error sum to it would be premature.
- Matches the pattern `:feature:profile:impl`'s `Throwable.toProfileError()` extension already uses (called out in the bd issue).

**Alternatives considered:**

- *Add a typed `AuthError` sum in `:core:auth` and have `beginLogin` return `Result<String, AuthError>`.* Rejected for this change — bigger blast radius across `:feature:login:impl`, `:feature:profile:impl`, and any future caller; we can lift to `:core:auth` later once a second consumer pulls in the same shape.

### 3. Mapping table

The VM uses a small private extension in `LoginViewModel.kt`:

```kotlin
private fun Throwable.toLoginError(handle: String): LoginError = when {
    this is OAuthDiscoveryException &&
        message?.startsWith("Failed to resolve handle") == true -> LoginError.HandleNotFound(handle)
    isNetworkError() -> LoginError.Network
    else -> LoginError.Generic
}

private fun Throwable.isNetworkError(): Boolean {
    var t: Throwable? = this
    while (t != null) {
        if (t is IOException || t is UnknownHostException || t is SocketTimeoutException) return true
        t = t.cause
    }
    return false
}
```

Rationale:

- Prefix-matching the "Failed to resolve handle" message is brittle but it's the only signal upstream surfaces for the "user typo" case without adding a new exception subclass in atproto-kotlin. If the upstream message ever changes, the test against `Result.failure(OAuthDiscoveryException("Failed to resolve handle 'alice': ..."))` will fail fast.
- Walking `cause` for network exceptions catches the `OAuthDiscoveryException("Failed to fetch resource server metadata ...", e)` wrapping that hides an underlying `IOException`. Without the walk we'd misclassify offline submissions as `Generic`.
- We rely on JDK stdlib types (`IOException`, `UnknownHostException`, `SocketTimeoutException`) and deliberately do NOT import Ktor's `HttpRequestTimeoutException`. `:feature:login:impl` doesn't have Ktor on its compile classpath (`:core:auth` exposes atproto-oauth as `api` but Ktor as `implementation`), and the OkHttp engine used at runtime surfaces socket-level timeouts as `SocketTimeoutException` / `IOException` anyway. If a future Ktor-side HttpTimeout plugin produces an `HttpRequestTimeoutException` that bypasses this set, the test for that case will fail with the throwable mis-classified as `Generic` — we widen the predicate then, without taking the Ktor dep eagerly.

**Open question (deferred):** filing an upstream issue against `kikin81/atproto-kotlin` for a typed `HandleNotFoundException` subclass would let us drop the prefix-match. Out of scope for this change; tracked separately if the prefix-match becomes flaky.

### 4. Sign-up CTA as a new effect, not an inline `Uri` launch

Add either:

- `LoginEffect.LaunchExternalUrl(url: String)`, OR
- a new `LoginEvent.OpenSignup` event that emits the existing `LoginEffect.LaunchCustomTab(url)`.

We pick the second: extend the existing `LaunchCustomTab` effect's semantics rather than adding a parallel variant. The screen's `LaunchedEffect` already dispatches `LaunchCustomTab` → `CustomTabsIntent.launchUrl(...)`; the sign-up URL is just another `LaunchCustomTab`. Adding a second variant would force a `when` change with no observably different behavior.

```kotlin
sealed interface LoginEvent : UiEvent {
    // existing: HandleChanged, SubmitLogin, ClearError
    data object OpenSignup : LoginEvent
}

// in handleEvent:
LoginEvent.OpenSignup -> sendEffect(LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL))
```

`BLUESKY_SIGNUP_URL = "https://bsky.app/signup"` lives as a private `const val` at the top of `LoginViewModel.kt`.

**Alternatives considered:**

- *Launch the `CustomTabsIntent` directly from the screen on click, with no VM round-trip.* Rejected — short-circuits the MVI seam and makes the sign-up button untestable as a "user intent". Routing through the VM keeps `LoginViewModel`'s contract uniform (all user actions are `LoginEvent`).

### 5. CTA placement: secondary button below primary submit

Below the primary "Sign in" button, add an outlined or text button labelled `R.string.login_signup_cta_label` ("Create one on Bluesky") with supporting `R.string.login_signup_cta_supporting` ("Don't have an account?") rendered as a small caption above the button.

Rationale:

- Always-visible (not gated behind an error state) — a brand-new user shouldn't have to type a wrong handle to discover the CTA.
- A separate button is more discoverable than a clickable span inside the error text, and avoids `AnnotatedString` / `ClickableText` machinery for a single link.

**Alternatives considered:**

- *Inline link inside the `HandleNotFound` error message.* Rejected for v1 — covered by the bd issue's "scope it up to 1 day" path and rejected by the "scope as filed, ~half-day" recommendation. Can be added in a follow-up if discoverability data motivates it.
- *Conditional CTA that only appears after a `HandleNotFound` error.* Rejected for the same reason — users without an account shouldn't have to fail first.

## Risks / Trade-offs

- **Brittle prefix-match on `OAuthDiscoveryException.message`** → A unit test pins the current upstream string. If atproto-kotlin refactors the message ("Could not resolve handle" → "Handle '$h' did not resolve"), the test fails fast and we either update the prefix or push a typed exception upstream.
- **Network-error classification depends on cause-walk depth** → A non-IOException cause (e.g. a kotlinx-serialization parsing failure during DNS-over-HTTPS) currently routes to `Generic`. That's the right answer for now; if telemetry later shows a real "offline DNS parse failure" path that should map to `Network`, we widen the predicate.
- **Sign-up returns the user to nubecita without context** → After bsky.app signup completes in the Custom Tab, the user comes back to the login screen with an empty handle field. The screen pre-fills nothing; the user types their newly-created handle. Acceptable for v1 — a smarter "we noticed you came back from signup, what's your new handle?" flow is in `nubecita-lq9t.3.3`'s sibling task scope.
- **`Generic` swallows server-side bugs we'd want telemetry on** → No telemetry today either; future production-infra epic (lq9t.4) introduces error reporting, at which point the VM can both surface `Generic` to the user and log the wrapped throwable to Sentry/Crashlytics.

## Migration Plan

Internal-only change to `:feature:login:impl`. No external module references `LoginError.Failure`; verified by `grep -rn "LoginError.Failure" .` returning zero hits outside `:feature:login:impl`. No backwards-compat shim required.

Ordering inside the PR:

1. Land the `LoginError` sum change + VM mapping + string resources + screen-side `displayStringFor` update.
2. Add the CTA + `OpenSignup` event + effect dispatch.
3. Add screenshot tests and instrumentation test.
4. Run `./gradlew :feature:login:impl:testDebugUnitTest :feature:login:impl:validateDebugScreenshotTest` plus the standard CI gates.

If the prefix-match exception classification later proves flaky in production, the rollback is "revert the typed sum and reintroduce `Failure(cause: String?)` for the leaking path" — single-file revert in the same module.

## Open Questions

- Do we want the supporting copy ("Don't have an account?") visible only when the handle field is empty, or always? Default: **always** (one less recomposition rule to chase). Resolve at copy-review time during implementation.
- Should `HandleNotFound` inline its sign-up link inside the error message in addition to the always-visible CTA? Default: **no** for v1. The always-visible CTA covers the discoverability case; a clickable span is additive polish for a follow-up.
