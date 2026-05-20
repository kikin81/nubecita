## 1. Library spelunk and exception inventory

- [x] 1.1 Confirm `OAuthDiscoveryException` message prefixes in `~/code/kikinlex/oauth/.../DiscoveryChain.kt` — pin the exact "Failed to resolve handle '$handle'" string used by the mapping test.
- [x] 1.2 Confirm the exception types reachable on the classpath: `IOException`, `UnknownHostException`, `SocketTimeoutException` (all JDK stdlib). Ktor's `HttpRequestTimeoutException` is intentionally excluded — Ktor is `implementation(...)` in `:core:auth`, not transitively on `:feature:login:impl`. Design and spec updated to match.

## 2. `LoginError` sum + VM mapping

- [x] 2.1 Replace `LoginError.Failure(cause: String?)` with `HandleNotFound(handle: String)`, `Network`, and `Generic` in `feature/login/impl/.../LoginContract.kt`. Keep `BlankHandle`. Annotate each variant `@Immutable`.
- [x] 2.2 Add `private fun Throwable.toLoginError(handle: String): LoginError` to `LoginViewModel.kt` with prefix-match for `OAuthDiscoveryException("Failed to resolve handle ...")` and a `cause`-chain walk for network exceptions.
- [x] 2.3 Update `LoginViewModel.submitLogin`'s `onFailure` arm to call `toLoginError(handle)` instead of constructing `LoginError.Failure(failure.message)`.
- [x] 2.4 Update the `broker.redirects` collector's `onFailure` arm in `LoginViewModel.init` to use the same `toLoginError(state.handle)` mapping.
- [x] 2.5 Add `LoginEvent.OpenSignup : LoginEvent` and a top-of-file `private const val BLUESKY_SIGNUP_URL = "https://bsky.app/signup"`. Handle the event in `LoginViewModel.handleEvent` by sending `LoginEffect.LaunchCustomTab(BLUESKY_SIGNUP_URL)` without mutating state.

## 3. String resources + screen mapping

- [x] 3.1 Add four error string resources to `feature/login/impl/src/main/res/values/strings.xml`: `login_error_blank_handle` (already exists — verify wording), `login_error_handle_not_found` (template with `%1$s`), `login_error_network`, `login_error_generic_failure` (already exists — verify wording).
- [x] 3.2 Add two sign-up CTA strings: `login_signup_cta_supporting` ("Don't have an account?") and `login_signup_cta_label` ("Create one on Bluesky").
- [x] 3.3 Update `displayStringFor(error: LoginError)` in `LoginScreen.kt` to be exhaustive over the four variants; remove the `Failure(cause)` branch. Use `stringResource(R.string.login_error_handle_not_found, error.handle)` for `HandleNotFound`.

## 4. Screen UI: secondary sign-up affordance

- [x] 4.1 Add the sign-up CTA below the primary `NubecitaPrimaryButton` in the stateless `LoginScreen` Column: a small caption `Text` with the supporting copy, then a secondary button (text button or outlined) wired to `onEvent(LoginEvent.OpenSignup)`. Use `MaterialTheme.spacing` tokens for vertical rhythm.
- [x] 4.2 Add at least three new `@Preview` composables to `LoginScreen.kt`: one each for `HandleNotFound`, `Network`, and `Generic` error states (light theme suffices — dark variants come from the `@PreviewNubecitaScreenPreviews` multi-preview wrapper if/when applied).
- [x] 4.3 Verify the existing `Empty`, `Typed`, `Loading`, and `BlankHandle` previews still compile against the new contract; remove the now-defunct `FailureErrorPreview` (or rename to `HandleNotFoundPreview`).

## 5. Unit tests (`LoginViewModelTest`)

- [x] 5.1 Add `submitLogin maps OAuthDiscoveryException("Failed to resolve handle ...") to LoginError.HandleNotFound(handle)`. Use a fake `AuthRepository` returning `Result.failure(OAuthDiscoveryException("Failed to resolve handle 'alise.bsky.social': ..."))`. Assert `state.errorMessage == LoginError.HandleNotFound("alise.bsky.social")`.
- [x] 5.2 Add `submitLogin maps direct IOException to LoginError.Network`.
- [x] 5.3 Add `submitLogin maps OAuthDiscoveryException wrapping IOException to LoginError.Network`. Use `OAuthDiscoveryException("Failed to fetch resource server metadata ...", IOException("..."))` to exercise the cause-walk.
- [x] 5.4 Add `submitLogin maps unknown Throwable to LoginError.Generic` (e.g. `IllegalStateException`). Assert no library message text appears in `state`.
- [x] 5.5 Add `OpenSignup emits LaunchCustomTab(bsky.app/signup) without mutating state`. Use Turbine on `effects` and assert `state` is unchanged.
- [x] 5.6 Update / replace any prior `FailureErrorPreview`-style mapping test that referenced `LoginError.Failure`.

## 6. Screenshot tests

- [x] 6.1 Add screenshot fixtures for the three new error states (`HandleNotFound`, `Network`, `Generic`) in `feature/login/impl/src/screenshotTest/`. Light + dark themes per the project's `@PreviewNubecitaScreenPreviews` wrapper.
- [x] 6.2 Add a screenshot fixture for the always-visible sign-up CTA (no error, empty handle). Light + dark themes. (Covered by the existing `empty-light` / `empty-dark` fixtures — the CTA is always visible, so the empty-state baseline now captures it.)
- [x] 6.3 Verify existing baselines for `Empty`, `Typed`, `Loading`, `BlankHandle` still match — re-baseline only those altered by the CTA addition.
- [x] 6.4 Add the `update-baselines` label to the PR once the screenshot tests have produced new images.

## 7. Instrumentation test

- [x] 7.1 Add a `LoginScreenInstrumentationTest` case `signupCtaLaunchesCustomTabForBlueskySignup` that taps the CTA and asserts a `CustomTabsIntent` (or equivalent `Intent` with `ACTION_VIEW` + `https://bsky.app/signup`) is launched. Use the existing instrumentation harness pattern in `:feature:login:impl/src/androidTest/`.
- [x] 7.2 Add the `run-instrumented` label to the PR so CI runs the instrumented job.

## 8. PR ceremony

- [x] 8.1 Run `./gradlew spotlessApply :feature:login:impl:testDebugUnitTest :feature:login:impl:validateDebugScreenshotTest :feature:login:impl:lintDebug` locally and clear all findings.
- [x] 8.2 Commit on `feat/nubecita-lq9t.3.3-login-friendlier-errors-and-signup-cta` with Conventional Commits; reference `nubecita-lq9t.3.3` in the body footer.
- [x] 8.3 Open the PR with `Closes: nubecita-lq9t.3.3` in the body; add `update-baselines` and `run-instrumented` labels.
- [x] 8.4 Address Copilot review feedback; resolve threads via GraphQL on reply.
- [x] 8.5 After merge, archive this OpenSpec change via `/opsx:archive add-login-error-mapping-and-signup-affordance`.
