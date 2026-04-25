## Why

`nubecita-ck0` shipped the OAuth round-trip end-to-end *if* you can reach the Login screen. Today nothing routes the user there — `MainActivity` always opens at `Main` (the placeholder hello-android destination), and `Login` exists only as a registered destination nobody navigates to. That makes the OAuth feature unreachable from a clean install.

`nubecita-30c` closes that gap with three pieces:

1. **Reactive session state** in `:core:auth` — a `SessionStateProvider` exposing `Flow<SessionState>` (Loading / SignedOut / SignedIn) so any consumer can react to "is the user authenticated right now?"
2. **A system splash + auth-gated routing decision** in `:app` — `MainActivity` installs the `androidx.core.splashscreen.SplashScreen` API with `setKeepOnScreenCondition { state is Loading }`, observes the session state Flow, and `navigator.replaceTo(Main or Login)` once the bootstrap resolves.
3. **Sign-out plumbing** in `:core:auth` — `AuthRepository.signOut()` calls `AtOAuth.logout()` (server-side revocation + local clear) and triggers a `SessionStateProvider` refresh so consumers automatically re-route. No UI hookup yet — that lands when a settings/profile screen exists.

The architectural decision: **start the Nav 3 back stack at a `Splash` destination** (empty composable) rather than deferring `setContent`. The system splash overlays an empty render until the bootstrap resolves, then `replaceTo(Main or Login)` — keeps routing reactive so future `signOut()` events automatically swap destinations without re-architecting.

## What Changes

- **New in `:core:auth`:**
  - `SessionState` sealed interface — `Loading | SignedOut | SignedIn(handle: String, did: String)`. The two String fields on `SignedIn` come from the persisted `OAuthSession` so consumers don't need to re-load the store.
  - `SessionStateProvider` interface — public `state: StateFlow<SessionState>` + `suspend fun refresh()`. Hilt singleton.
  - `DefaultSessionStateProvider` (internal) — backs `state` with a `MutableStateFlow<SessionState>(Loading)`; `refresh()` re-reads `OAuthSessionStore.load()` and emits.
- **`AuthRepository` extended:**
  - New `suspend fun signOut(): Result<Unit>` — delegates to `AtOAuth.logout()` (revocation POST + session clear) and triggers a `SessionStateProvider` refresh.
  - `completeLogin` now also triggers a refresh after success so the SignedIn transition fires automatically.
- **`:app` changes:**
  - New `Splash : NavKey` placeholder in `:app/.../Splash.kt`.
  - `StartDestinationModule` updated to provide `Splash` instead of `Main`. Navigator's back stack now seeds at `Splash`.
  - `MainNavigation` registers `entry<Splash>` rendering an empty `Box` (system splash sits on top via the API).
  - `MainActivity.onCreate`:
    - `installSplashScreen().setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }`
    - In `lifecycleScope`, launch the bootstrap: `sessionStateProvider.refresh()` followed by `state.collect { route(it) }` where `route` calls `navigator.replaceTo(Main)` on `SignedIn` and `navigator.replaceTo(Login)` on `SignedOut`.
- **`LoginScreen` updated:**
  - The `LaunchedEffect` no longer calls `navigator.goBack()` on `LoginSucceeded`. With reactive routing, `MainActivity` handles the destination swap automatically when `SessionStateProvider` re-emits as `SignedIn` after `completeLogin`.
  - The `LoginSucceeded` effect remains in the contract — it's still emitted by the VM (useful for analytics, future onboarding nudge, and existing test assertions) — but the screen-side handler becomes a no-op.
- **New dep**: `androidx.core:core-splashscreen` for the SplashScreen API (in `:app`).

## Capabilities

### New Capabilities

- `core-auth-session-state`: a reactive `Flow<SessionState>` view over `OAuthSessionStore`, exposed via Hilt as `SessionStateProvider`. The single source of truth for "is the user authenticated right now?"
- `app-splash-routing`: `MainActivity`-side bootstrap that installs the system splash, holds it via `setKeepOnScreenCondition` while session state is Loading, and `Navigator.replaceTo`s the appropriate destination (Main if SignedIn, Login if SignedOut) reactively as state changes.

### Modified Capabilities

- `core-auth-oauth-bindings`: gains `AuthRepository.signOut()` and the explicit refresh-after-mutation contract for `completeLogin` + `signOut`.
- `feature-login`: `LoginScreen`'s `LaunchedEffect` no longer pops the back stack on `LoginSucceeded`; reactive routing in `MainActivity` handles the destination swap.

## Non-Goals

- **Onboarding screen / first-install flag.** The original bd description mentions Onboarding routing, but no onboarding feature module exists. Defer to its own bd issue when an onboarding feature is scoped.
- **A real Feed.** `Main` stays the placeholder hello-android destination. The eventual `:feature:home` (`nubecita-1d5`) will own it.
- **Sign-out UI.** No settings or profile screen exists yet. `AuthRepository.signOut()` ships ready; the UI hookup lands with a future settings PR.
- **Custom branded splash artwork.** This change uses the default theme-derived system splash. Branding (windowSplashScreenBackground, windowSplashScreenAnimatedIcon) is a future design polish.
- **Token refresh on bootstrap.** The session, if present, is treated as valid. Stale-token handling and silent refresh remain `atproto-oauth`'s responsibility once an `XrpcClient` is constructed.
- **Multi-account.** Single session per device per the existing `core-auth-session-storage` contract.

## Impact

- New module file: `core/auth/src/main/.../SessionState.kt` + `SessionStateProvider.kt` + `DefaultSessionStateProvider.kt`. Hilt `@Binds` added to `AuthBindingsModule`.
- New module file: `app/src/main/.../Splash.kt` (NavKey).
- `app/src/main/AndroidManifest.xml`: no change — the system SplashScreen API works with the existing `Theme.Nubecita`.
- `app/src/main/.../MainActivity.kt`: gains `installSplashScreen()` + `SessionStateProvider` injection + `lifecycleScope` bootstrap.
- `app/src/main/.../navigation/StartDestinationModule.kt`: provides `Splash` instead of `Main`.
- `app/src/main/.../Navigation.kt`: registers `entry<Splash> { Box(Modifier.fillMaxSize()) }`.
- `feature/login/impl/.../LoginScreen.kt`: drops the `navigator.goBack()` call on `LoginSucceeded`; effect collector becomes Custom-Tab-only.
- Catalog: new `androidx-core-splashscreen` library alias.
- **Downstream unblock**: future onboarding / feed feature modules can swap their start-destination decision into `MainActivity`'s reactive bootstrap without re-architecting routing.
