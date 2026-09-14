## Context

`LoginViewModel.handleEvent(OpenSignup)` currently emits `LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL)` where `BLUESKY_SIGNUP_URL = "https://bsky.app/"`. The user lands on bsky.app's SPA, taps "Create account," steps through the web signup flow, then returns to nubecita and types their newly-created handle into the login field to start the OAuth flow over again.

atproto-kotlin 8.0.0 introduces `AtOAuth.beginSignup(authServer, requirePromptCreateSupport)` which:

1. Skips `DiscoveryChain.resolveHandle` (no DID is needed — the user doesn't have one yet).
2. Fetches `authServer/.well-known/oauth-authorization-server`.
3. Checks `promptValuesSupported` contains `"create"` (throws `OAuthSignupNotSupportedException` otherwise).
4. Performs PAR with PKCE + DPoP, with `prompt=create` in the request body.
5. Returns a `https://bsky.social/oauth/authorize?client_id=...&request_uri=...` URL.

The user opens that URL in a Custom Tab, bsky.social shows its signup UI, the user completes signup, bsky.social redirects to `net.kikin.nubecita:/oauth-redirect?code=...`, `OAuthRedirectBroker` catches it, `LoginViewModel`'s init-time collector calls `authRepository.completeLogin(redirectUri)`, and the user lands back signed in.

The entire redirect-back chain is **identical** to the existing login path. Only the URL handed to the Custom Tab changes.

## Goals / Non-Goals

**Goals:**

- Replace the static `https://bsky.app/` sign-up URL with a dynamic PAR'd authorize URL keyed on `prompt=create`.
- Keep the MVI seam clean: VM dispatches a coroutine, repo returns a `Result<String>`, success emits `LaunchCustomTab`, failure routes through the existing `toLoginError` mapping.
- Reuse the existing redirect-back path verbatim — no new collector, no new effect, no new state.

**Non-Goals:**

- Adding a multi-PDS picker UI.
- Building any signup UI inside nubecita (the bsky.social web flow owns it).
- Pre-fetching / caching authorization URLs (each call PARs fresh; `request_uri` has a short TTL).
- Localized error copy for `OAuthSignupNotSupportedException` — the exception fires only when an auth server drops `"create"` from its metadata, which `bsky.social` doesn't do today. Map to `Generic` for v1.

## Decisions

### 1. Add `beginSignup` to `AuthRepository`, not a parallel `SignupRepository`

`AuthRepository` already owns `beginLogin` / `completeLogin` / `signOut`. The signup flow uses the same redirect handling and the same `OAuthSession` output. Adding a parallel `SignupRepository` would split a single OAuth surface across two seams with overlapping responsibilities.

```kotlin
interface AuthRepository {
    // existing
    suspend fun beginLogin(handle: String): Result<String>
    suspend fun completeLogin(redirectUri: String): Result<Unit>
    suspend fun signOut(): Result<Unit>

    // new
    suspend fun beginSignup(): Result<String>
}
```

`beginSignup` takes no parameters at this layer. The upstream `AtOAuth.beginSignup(authServer, requirePromptCreateSupport)` defaults (`"bsky.social"`, `true`) are correct for every call site nubecita has today.

**Alternatives considered:**

- *Expose `authServer` and `requirePromptCreateSupport` at the AuthRepository layer.* Rejected — no caller needs to customize them; YAGNI.
- *Reuse `beginLogin(handle: String)` with a sentinel handle.* Rejected — overloads the type's contract and makes the call site ambiguous.

### 2. `LoginEvent.OpenSignup` becomes an async action, not a synchronous effect

Today:

```kotlin
LoginEvent.OpenSignup -> sendEffect(LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL))
```

After:

```kotlin
LoginEvent.OpenSignup -> openSignup()

private fun openSignup() {
    setState { copy(isLoading = true, errorMessage = null) }
    viewModelScope.launch {
        authRepository
            .beginSignup()
            .onSuccess { url ->
                setState { copy(isLoading = false) }
                sendEffect(LoginEffect.LaunchCustomTab(url))
            }.onFailure { failure ->
                setState { copy(isLoading = false, errorMessage = failure.toLoginError(handle = "")) }
            }
    }
}
```

Rationale:

- The PAR call is network-bound (talks to `bsky.social/.well-known/...` and `bsky.social/oauth/par`). Setting `isLoading = true` while it runs gives the screen the same disabled-button + spinner feedback the login path already uses, so the CTA tap doesn't appear to do nothing for the few hundred ms PAR takes.
- The `toLoginError(handle = "")` reuse is intentional: signup-flow failures route through the same network/generic mapping; `LoginError.HandleNotFound("")` is unreachable here (PAR isn't a handle resolution call), so the empty-string handle never surfaces to the UI.
- `OAuthSignupNotSupportedException` doesn't match the handle-not-found prefix and isn't an `IOException`, so it falls through to `LoginError.Generic`. The exception is highly unlikely to fire in practice (bsky.social advertises `"create"` today).

**Alternatives considered:**

- *Add a typed `LoginError.SignupNotSupported` variant with custom copy.* Rejected for v1 — exception won't fire in production. Worth adding only if we ever change auth servers or telemetry shows it firing.
- *Keep the synchronous effect dispatch and PAR inside the screen's `LaunchedEffect`.* Rejected — the screen would have to inject `AuthRepository`, breaking the existing MVI seam.

### 3. Drop `BLUESKY_SIGNUP_URL` entirely

The constant has only two readers today: the production VM and the instrumentation test. After this change neither needs it (the VM gets the URL from `beginSignup`, and the test asserts the dynamic URL's host + path prefix, not an exact match). Removing the constant prevents future regressions where someone "accidentally" re-introduces a static URL path.

### 4. Instrumentation test asserts URI shape, not exact URL

The dynamic authorization URL looks like `https://bsky.social/oauth/authorize?client_id=...&request_uri=urn:ietf:params:oauth:request_uri:...`. The `request_uri` is a server-issued opaque token with a short TTL; we can't pin it in a test fixture without mocking the PAR call.

The instrumentation test's `FakeAuthRepository` (already in `:feature:login:impl/src/androidTest/.../testing/`) returns a deterministic URL. For the signup path we add a `signupAuthorizationUrl: String` to the fake, default `"https://bsky.social/oauth/authorize?client_id=test&request_uri=urn:test"`, and the test asserts the launched intent's URI matches that fake URL exactly.

This is the same pattern the existing login-flow instrumentation test uses (`FakeAuthRepository.DEFAULT_AUTHORIZATION_URL`).

## Risks / Trade-offs

- **`OAuthSignupNotSupportedException` user-facing copy is generic** → If bsky.social ever drops `"create"` from `prompt_values_supported`, users see "Something went wrong. Try again." with no actionable next step. Mitigation: file a typed `SignupNotSupported` variant follow-up if the exception starts firing.
- **PAR fails offline → user can't sign up** → `beginSignup` fails with an `IOException`, maps to `LoginError.Network`, screen shows "Can't reach Bluesky right now." This is the same UX as offline login — acceptable.
- **DID hydration retry timing** → After signup completes and the redirect lands, `completeLogin` performs bounded retry to hydrate the new account's DID document from the PLC directory. If retry exhausts, `SessionState` stays in `Loading` (per the v8 adapter shipped in PR #262). Mitigation: the bounded retry in atproto-kotlin is already generous; if telemetry later shows users stuck on Loading after signup, widen the retry budget upstream.
- **Double-tap on the CTA** → While `isLoading = true`, the existing `TextButton(enabled = !state.isLoading)` already disables the CTA. No new guard needed.

## Migration Plan

Single PR. No data migration. No persisted state changes (existing v7 sessions continue to work via the same code path; v8 was forward-compatible per the atproto-kotlin release notes).

Rollout order inside the PR:

1. Add `beginSignup` to `AuthRepository` interface + `DefaultAuthRepository` impl.
2. Update `LoginViewModel.handleEvent(OpenSignup)` to call it.
3. Drop `BLUESKY_SIGNUP_URL` const.
4. Rewrite the `OpenSignup` unit test against the new contract; add the `OAuthSignupNotSupportedException` → Generic mapping case.
5. Update the instrumentation test's CTA-tap assertion to the new shape.
6. Update `:feature:login:impl/src/androidTest/.../testing/FakeAuthRepository.kt` to implement `beginSignup` (return a deterministic test URL).

Rollback: revert the PR. No persisted state to clean up. Users mid-signup who haven't redirected back would lose their PAR'd request_uri (short TTL anyway).

## Open Questions

- Should the LoginScreen's CTA copy change from "Create one on Bluesky" → "Sign up" once the user comes back signed in instead of just dropped on bsky.app? Default: **no**. The copy still describes the user-visible action ("create an account"), and the under-the-hood OAuth handoff doesn't need to surface in the label. Resolve at copy-review time if a design pass disagrees.
