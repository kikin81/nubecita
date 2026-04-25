## Why

`nubecita-uf5` (login screen + MVI) shipped the `LaunchCustomTab(url)` effect but stopped short of consuming it; today the app emits the URL and nothing opens. `nubecita-ck0` closes the loop: launch the Custom Tab, capture the OAuth redirect that comes back through Android's deep-link plumbing, run `AtOAuth.completeLogin`, and navigate the user off the Login destination on success.

This is mostly mechanical wiring (Custom Tabs, `onNewIntent`, manifest intent filter, broker), but it forces one architectural decision: how does a feature module's `@HiltViewModel` know "we're authenticated, navigate forward" without breaking the `@IntoSet EntryProviderInstaller` pattern (no per-call-site callbacks) and without coupling the VM to Android navigation classes? Answer: a small `Navigator` interface in `:core:common` that owns the shared `SnapshotStateList<NavKey>` back stack, Hilt-bound singleton, callable from any composable or ViewModel. `MainNavigation` becomes a reader of the navigator's back stack rather than the owner.

## What Changes

- **New `Navigator` interface in `:core:common`** with `goTo(NavKey)`, `goBack()`, `replaceTo(NavKey)`. Default implementation holds a `SnapshotStateList<NavKey>` initialized with the start destination (`Main`). Singleton-scoped via Hilt.
- **`MainNavigation` rewired** to read its back stack from the injected `Navigator` instead of `rememberNavBackStack(Main)`. Same end behavior; ownership inverted.
- **`OAuthRedirectBroker` in `:core:auth`** — `Channel<String>`-based, single-consumer, buffered. `MainActivity` publishes redirect URIs; `LoginViewModel` collects in `init`. Buffered so a redirect that arrives before `LoginViewModel` exists (cold-start deep-link) is delivered when the VM eventually subscribes.
- **`AuthRepository` extended** with `suspend fun completeLogin(redirectUri: String): Result<Unit>` delegating to `AtOAuth.completeLogin` and wrapping exceptions.
- **`LoginViewModel` collects from the broker** in `init`, calls `completeLogin`, emits a new `LoginEffect.LoginSucceeded` on success or sets `errorMessage = LoginError.Failure(...)` on failure.
- **`LoginScreen` `LaunchedEffect`** — already had no effect collector beyond the VM-internal flow; now catches both `LaunchCustomTab` (calls `CustomTabsIntent.launchUrl(context, ...)`) and `LoginSucceeded` (calls `navigator.goBack()`).
- **`AndroidManifest.xml` (`:app`)** — `MainActivity` gets `android:launchMode="singleTask"` and a new `<intent-filter>` for `data android:scheme="net.kikin.nubecita"` so OAuth redirects re-deliver to the existing instance via `onNewIntent`.
- **`MainActivity` updated** — captures the redirect URI in `onCreate` (cold-start) and `onNewIntent` (warm-start) and publishes via the broker. Consumes `intent.data` after handling so configuration changes don't re-fire it.
- **New dep**: `androidx.browser:browser` for `CustomTabsIntent`. Added to the catalog and pulled in by `:feature:login:impl`.

## Capabilities

### New Capabilities

- `core-common-navigation`: a `Navigator` Hilt singleton owning the app's `SnapshotStateList<NavKey>` back stack. Composables and ViewModels mutate the stack through `goTo` / `goBack` / `replaceTo`; `MainNavigation` reads from it. Replaces the local `rememberNavBackStack(Main)` previously held by `MainNavigation`.

### Modified Capabilities

- `core-auth-oauth-bindings`: gains `AuthRepository.completeLogin(redirectUri)` and the `OAuthRedirectBroker` Hilt binding.
- `feature-login`: gains the broker-driven completeLogin path on `LoginViewModel`, the new `LoginEffect.LoginSucceeded` variant, and the `LoginScreen` `LaunchedEffect` that handles both effects (Custom Tab launch + post-login navigation).

## Non-Goals

- **Auth-gated routing / splash.** `nubecita-30c` decides where the user lands after login. This change only pops `Login` off the stack via `navigator.goBack()`; the user lands wherever they were before.
- **Sign-out flow.** `AtOAuth.logout` exists; wiring `AuthRepository.signOut` lives with the splash + auth-gated routing PR.
- **Token refresh testing.** Refresh is handled transparently by the `atproto-oauth` library's `DpopAuthProvider` once an `XrpcClient` is constructed; no explicit code in this change exercises it.
- **End-to-end OAuth verification against bsky.social.** Requires a real device + GitHub Pages serving the metadata + manual user authorization. Tracked as an instrumented scenario under `nubecita-16a`.
- **Multi-account / account switching.** The session store holds one session at a time (per `add-core-auth-session-storage`'s spec). Account switching is a future feature.
- **A `:core:navigation` module.** `Navigator` lives in `:core:common` for now; promote to its own module when cross-feature concerns (deep-link router, scene strategy registry) actually appear.

## Impact

- New module file: `core/common/src/main/kotlin/.../navigation/Navigator.kt` + `DefaultNavigator.kt` + `di/NavigatorModule.kt`.
- New module files: `core/auth/src/main/kotlin/.../OAuthRedirectBroker.kt` + impl + binding.
- `:core:auth` extends `AuthRepository` interface and `DefaultAuthRepository` impl.
- `:feature:login:impl` extends `LoginContract` (new `LoginSucceeded` effect), updates `LoginViewModel` (broker injection + collection), updates `LoginScreen` (LaunchedEffect for both effects), gains `androidx.browser:browser` dep.
- `:app/src/main/AndroidManifest.xml` — adds `launchMode="singleTask"` + new `<intent-filter>`.
- `:app/src/main/java/.../MainActivity.kt` — adds intent handling.
- `:app/src/main/java/.../Navigation.kt` — reads back stack from injected `Navigator` instead of `rememberNavBackStack(Main)`.
- Version catalog: new `androidx-browser` library alias.
- **Downstream unblock**: `nubecita-30c` (splash + auth-gated routing) can build on the `Navigator` and `LoginSucceeded` effect.
