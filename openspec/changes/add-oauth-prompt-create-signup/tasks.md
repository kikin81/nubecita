## 1. AuthRepository: new `beginSignup` entry point

- [x] 1.1 Add `suspend fun beginSignup(): Result<String>` to `AuthRepository` interface in `:core:auth`.
- [x] 1.2 Implement `beginSignup()` in `DefaultAuthRepository` — delegate to `atOAuth.beginSignup(authServer = "bsky.social", requirePromptCreateSupport = true)`, wrap as `Result`. Match the existing `runCatching`/`Result.failure` pattern `beginLogin` uses.
- [x] 1.3 Update any existing `AuthRepository` implementations in test source sets to add the new method (default to `Result.success("https://bsky.social/oauth/authorize?test")` or similar deterministic stub).

## 2. `LoginViewModel`: re-wire `OpenSignup` to call beginSignup

- [x] 2.1 Remove `BLUESKY_SIGNUP_URL` const from `LoginViewModel.kt`. Drop any imports it pulled in if they're now unused.
- [x] 2.2 Replace the inline `LoginEvent.OpenSignup -> sendEffect(LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL))` branch with a call to a new `private fun openSignup()` method that sets `isLoading = true`, launches a coroutine, calls `authRepository.beginSignup()`, and routes success → `LaunchCustomTab(url)` + clear loading, failure → `setState { errorMessage = failure.toLoginError(handle = "") }` + clear loading.

## 3. Unit tests (`LoginViewModelTest`)

- [x] 3.1 Delete the `OpenSignup emits LaunchCustomTab(bsky_app_signup) without mutating state` test (its contract no longer matches reality — state DOES mutate now via `isLoading`).
- [x] 3.2 Add `OpenSignup successful delegates to beginSignup and emits LaunchCustomTab with the returned URL`. Use a `FakeAuthRepository` whose `beginSignup` returns `Result.success("https://bsky.social/oauth/authorize?test")`. Assert: VM emits `LaunchCustomTab("https://bsky.social/oauth/authorize?test")`, final state has `isLoading = false` and `errorMessage = null`.
- [x] 3.3 Add `OpenSignup IOException maps to LoginError.Network`. Fake returns `Result.failure(IOException("offline"))`. Assert `state.errorMessage == LoginError.Network`, `isLoading == false`, no effect emitted.
- [x] 3.4 Add `OpenSignup OAuthSignupNotSupportedException maps to LoginError.Generic`. Fake returns `Result.failure(OAuthSignupNotSupportedException(authServerUrl = "...", advertisedPromptValues = listOf("login")))`. Assert `state.errorMessage == LoginError.Generic` and the library exception's text doesn't appear anywhere in state.
- [x] 3.5 Extend `FakeAuthRepository` (test helper inside `LoginViewModelTest.kt`) with a `beginSignupResult: Result<String>` parameter, default `Result.success("https://bsky.social/oauth/authorize?default-test-url")`.

## 4. Instrumentation tests

- [x] 4.1 Update `:feature:login:impl/src/androidTest/.../testing/FakeAuthRepository.kt` to implement `beginSignup()` — default to a const `DEFAULT_SIGNUP_AUTHORIZATION_URL = "https://bsky.social/oauth/authorize?client_id=test&request_uri=urn:test"`.
- [x] 4.2 Update `LoginScreenInstrumentationTest.signupCtaLaunchesCustomTabForBlueskySignup` to assert the launched intent's URI equals `FakeAuthRepository.DEFAULT_SIGNUP_AUTHORIZATION_URL` instead of the now-removed `BLUESKY_SIGNUP_URL`.

## 5. Screen-side polish (optional sweep)

- [x] 5.1 Verify `LoginScreen` renders the sign-up CTA with `enabled = !state.isLoading` (already true today, but pin the expectation in case the contract drifts). No screenshot baseline regeneration needed — the `Empty` baseline already shows the enabled CTA; an additional fixture for `isLoading = true` with the CTA-disabled would be additive but is deferred to a separate cosmetic change if needed.

## 6. PR ceremony

- [x] 6.1 Run `./gradlew spotlessApply :core:auth:testDebugUnitTest :feature:login:impl:testDebugUnitTest :feature:login:impl:lintDebug :core:auth:lintDebug` locally and clear all findings.
- [ ] 6.2 Commit on `feat/nubecita-lq9t.3.5-feat-login-oauth-prompt-create-signup-user-lands-b` with Conventional Commits; reference `nubecita-lq9t.3.5` in the body footer.
- [ ] 6.3 Open the PR with `Closes: nubecita-lq9t.3.5` in the body; add `run-instrumented` label.
- [ ] 6.4 Address Copilot review feedback; resolve threads via GraphQL on reply.
- [ ] 6.5 After merge, archive this OpenSpec change.
