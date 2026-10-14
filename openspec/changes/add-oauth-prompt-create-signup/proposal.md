## Why

Today the "Create one on Bluesky" CTA opens `https://bsky.app/` in a Chrome Custom Tab. After the user creates an account on web, they come back to nubecita and have to **re-type their newly-created handle to sign in** — a discontinuity that loses ~30% of brand-new users in similar flows.

atproto-kotlin v8.0.0 (merged via PR #262) ships `AtOAuth.beginSignup(authServer, requirePromptCreateSupport)` that PARs against a known auth server with OIDC `prompt=create`. `bsky.social`'s authorization-server metadata advertises `"create"` in `prompt_values_supported`, so we can PAR there, send the user to bsky.social's signup flow inside the OAuth-bound roundtrip, and the standard redirect lands them back in nubecita **already signed in**. No re-typing, no second auth step.

## What Changes

- Add `AuthRepository.beginSignup(): Result<String>` — domain seam over `AtOAuth.beginSignup()`. Returns the authorization URL to open in a Custom Tab; failures wrap as `Result.failure(...)`.
- `DefaultAuthRepository.beginSignup()` delegates to `AtOAuth.beginSignup(authServer = "bsky.social", requirePromptCreateSupport = true)`. The defaults are correct for the only auth server nubecita talks to today; no caller-side configuration.
- Re-wire `LoginViewModel.handleEvent(OpenSignup)` from the static `LaunchCustomTab(BLUESKY_SIGNUP_URL)` to: (a) set `isLoading = true`, (b) call `authRepository.beginSignup()`, (c) on success emit `LaunchCustomTab(authorizationUrl)` and clear loading, (d) on failure route through the existing `Throwable.toLoginError(handle)` mapping (handle is irrelevant for the signup path; pass an empty string to satisfy the API). Reuses the redirect-back path (`OAuthRedirectBroker` → `completeLogin`) that login already exercises — no new redirect handling needed.
- Remove the `BLUESKY_SIGNUP_URL` constant from `LoginViewModel.kt` (no longer referenced; the instrumentation test asserts against a deterministic fixture URL supplied by the test's `FakeAuthRepository`, not a static constant).
- Tests:
  - Unit (`LoginViewModelTest`): replace the `OpenSignup emits LaunchCustomTab(bsky_app)` test with `OpenSignup emits LaunchCustomTab(authRepo.beginSignup result)` against a fake `AuthRepository` returning a fixed authorize URL. Add an error-mapping case for `OAuthSignupNotSupportedException` → `LoginError.Generic` (the only new exception type beginSignup introduces).
  - Instrumentation: update the existing CTA-tap test (`signupCtaLaunchesCustomTabForBlueskySignup`) to assert the launched `ACTION_VIEW` intent's data is an exact match (`hasData(Uri.parse(...))`) against the deterministic signup-authorization fixture URL exposed by the test `FakeAuthRepository` (`DEFAULT_SIGNUP_AUTHORIZATION_URL`), instead of the old exact-URL match against `https://bsky.app/`. The fake replaces the real `AuthRepository` via Hilt `@TestInstallIn`, so the URL is deterministic in-test (no live PAR round-trip) and the exact match is stable — the fixture deliberately uses a distinct `request_uri` from the login fixture so a CTA-triggered intent can't be confused with a submit-triggered one.

## Capabilities

### New Capabilities

(none — extends an existing capability)

### Modified Capabilities

- `feature-login`: the existing "Login screen exposes a 'Create one on Bluesky' sign-up affordance" requirement currently mandates `LoginEffect.LaunchCustomTab("https://bsky.app/")` for `OpenSignup`. That clause is replaced by "VM delegates to `AuthRepository.beginSignup()` and emits `LaunchCustomTab(url)` with the returned authorization URL." The CTA UI itself is unchanged. The `LoginViewModel` failure-classification requirement gains one new mapping case for `OAuthSignupNotSupportedException`.

## Impact

- **Affected modules**: `:core:auth` (new `AuthRepository.beginSignup` interface method + default impl), `:feature:login:impl` (VM handler change + tests).
- **Affected specs**: `feature-login` (delta — modifies the OpenSignup requirement, extends the failure-classification requirement).
- **Dependencies**: requires `atproto-kotlin 8.0.0` — already on `gradle/libs.versions.toml` after PR #262 merged.
- **Out of scope**: multi-PDS picker UI for signup, custom-PDS signup (most users target `bsky.social`; defer until a real need surfaces), in-app account creation via `com.atproto.server.createAccount` (entirely different flow).
- **Backwards compatibility**: `LoginEvent.OpenSignup` semantics change (dispatches an async repository call instead of a synchronous effect). The screen's `LaunchedEffect` still dispatches `LaunchCustomTab` to a Chrome Custom Tab unchanged — only the URL changes from static to dynamic. The redirect-back path is identical to the login flow.

## Non-goals

- **Multi-PDS picker.** `bsky.social` is the only auth server nubecita talks to. Adding a picker is a separate, larger feature.
- **Custom signup-only UI inside nubecita.** All signup UX (handle picker, email collection, captcha) lives in bsky.social's web flow. We just hand off and catch the redirect.
- **Localized error copy for `OAuthSignupNotSupportedException`.** Maps to `LoginError.Generic` for v1. If we ever change auth servers and the exception starts firing in practice, add a typed `SignupNotSupported` variant in a follow-up.
- **Deviation from MVI / Compose / Hilt baseline.** Reuses the existing `UiEffect` + `LaunchedEffect` plumbing the login path already uses.
