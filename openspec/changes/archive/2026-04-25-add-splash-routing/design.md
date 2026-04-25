## Context

The OAuth chain (e16 → nss → 4g7 → uf5 → ck0) shipped a working sign-in flow but left a routing hole: after a clean install, the user lands on the placeholder `Main` destination with no path to `Login`. Conversely, after a sign-in completes, there's no central decision-maker that says "you're authenticated now — get off the Login destination."

`nubecita-30c`'s job is to centralize that routing decision so the destination is always a function of session state. The shape: a `SessionStateProvider` exposing `Flow<SessionState>` in `:core:auth`, observed by `MainActivity`, which calls `navigator.replaceTo(...)` reactively.

The Android system `SplashScreen` API (`androidx.core:core-splashscreen`) handles the visible "while we figure it out" gap natively — `setKeepOnScreenCondition` keeps the system splash on top until our condition predicate returns false. That maps cleanly to "splash visible while `SessionState` is `Loading`."

Stakeholders downstream: every future feature that wants to react to sign-in or sign-out state (settings screen with sign-out button, profile screen showing "signed in as alice.bsky.social", auth-required screens that need to redirect) consumes `SessionStateProvider`.

## Goals / Non-Goals

**Goals:**

- After cold-start: signed-in users land on `Main`, signed-out users land on `Login`. No flicker between splash and destination.
- Routing is reactive — a future `signOut()` triggered from anywhere in the app causes `MainActivity` to swap the back stack to `Login` without anyone explicitly navigating.
- `LoginScreen`'s post-success behavior stops manually popping the back stack; the destination swap is owned by the same reactive router that handled the start case.
- `SessionStateProvider` is the single source of truth for "is the user authenticated right now?" — every consumer (`MainActivity`, future settings screens, auth-required screens) reads from it.
- `AuthRepository` mutations (`completeLogin`, `signOut`) explicitly trigger a `SessionStateProvider` refresh so the reactive routing fires automatically.

**Non-Goals:**

- Onboarding screen — no feature module exists; deferred.
- A real Feed — `Main` stays placeholder.
- Sign-out UI — no settings/profile screen exists; `signOut()` ships ready for a future hookup.
- Branded splash artwork — uses default theme-derived splash.
- Token refresh on bootstrap — `atproto-oauth`'s `DpopAuthProvider` handles refresh transparently once an authenticated `XrpcClient` is constructed.
- Multi-account / account switching — single-session by design (per `core-auth-session-storage`).
- Replacing the `@StartDestination` Hilt qualifier or restructuring `Navigator` — the Navigator's contract is unchanged; only what `:app` provides as the start key changes (from `Main` to `Splash`).

## Decisions

### 1. Start the back stack at a `Splash` NavKey, not deferred `setContent`

**Decision:** `:app/.../Splash.kt` defines `@Serializable data object Splash : NavKey`. `StartDestinationModule.provideStartDestination()` returns `Splash` instead of `Main`. `MainNavigation` registers `entry<Splash> { Box(Modifier.fillMaxSize()) }` — an empty surface. `MainActivity.onCreate` runs `setContent { ... }` immediately; the system splash overlays the empty render via `installSplashScreen().setKeepOnScreenCondition { ... }` until the bootstrap resolves.

**Alternatives considered:**

- **Defer `setContent` until bootstrap resolves.** Anti-pattern with the modern SplashScreen API — defeats the system-splash overlap window and forces a manual blank-frame render. Also breaks reactivity for future sign-out flows: by the time `setContent` runs, the bootstrap is "done" and you'd need a separate mechanism to handle later state changes.
- **Render the splash as a Compose composable in `MainNavigation` (no system splash).** Loses the OS-level launch handoff. Configuration changes during the splash window cause flicker.
- **Have `Navigator.backStack` start empty and let `MainActivity` push the first destination.** `NavDisplay`'s behavior on an empty back stack is undefined (and risks crashes); a no-op `Splash` destination is the safe minimum.

**Rationale:** Matches the official Android SplashScreen API guidance ("install the splash screen before super.onCreate, set a keep-on-screen condition while you load critical state"). Keeps Compose rendering immediate; the system splash hides what would be a one-frame empty `Box`. Replacing `Splash` via `navigator.replaceTo(Main or Login)` is reactive — fires whenever `SessionStateProvider.state` emits.

### 2. `SessionStateProvider` exposes `StateFlow<SessionState>` with explicit `refresh()`

**Decision:**

```kotlin
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val handle: String, val did: String) : SessionState
}

interface SessionStateProvider {
    val state: StateFlow<SessionState>
    suspend fun refresh()
}

internal class DefaultSessionStateProvider @Inject constructor(
    private val sessionStore: OAuthSessionStore,
) : SessionStateProvider {
    private val _state = MutableStateFlow<SessionState>(SessionState.Loading)
    override val state: StateFlow<SessionState> = _state.asStateFlow()

    override suspend fun refresh() {
        val session = sessionStore.load()
        _state.value = if (session != null) {
            SessionState.SignedIn(handle = session.handle, did = session.did)
        } else {
            SessionState.SignedOut
        }
    }
}
```

`AuthRepository.completeLogin` and `AuthRepository.signOut` both call `sessionStateProvider.refresh()` after a successful mutation so reactive consumers transition automatically.

**Alternatives considered:**

- **`OAuthSessionStore` exposes its own `Flow<OAuthSession?>`.** Cleaner — no manual refresh needed. But it'd require modifying the upstream `atproto-oauth` library's `OAuthSessionStore` interface. We don't fork.
- **`SessionStateProvider` polls the store on a timer.** Fragile.
- **Expose just `Flow<SessionState>` without an explicit `refresh()`.** Closes the door on commands triggering re-evaluation. The mutation→refresh pattern is explicit and easy to reason about.
- **`SessionState.SignedIn` carries the full `OAuthSession`.** Tempting for convenience, but exposes DPoP keypair bytes to UI consumers. `handle` and `did` are the public-facing identifiers; everything else stays in the store.

**Rationale:** `MutableStateFlow` gives `MainActivity`'s `setKeepOnScreenCondition` a synchronous `state.value` read — important because the predicate is called from the platform's frame callback, not a coroutine. The explicit `refresh()` makes the AuthRepository → SessionStateProvider edge visible at the call site.

### 3. `MainActivity` observes `state` and calls `navigator.replaceTo(...)` on each emission

**Decision:**

```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    val splashScreen = installSplashScreen()
    splashScreen.setKeepOnScreenCondition { sessionStateProvider.state.value is SessionState.Loading }
    super.onCreate(savedInstanceState)

    enableEdgeToEdge()
    setContent { /* MainNavigation */ }
    handleIntent(intent)

    lifecycleScope.launch { sessionStateProvider.refresh() }
    lifecycleScope.launch {
        sessionStateProvider.state.collect { state ->
            when (state) {
                SessionState.Loading -> Unit  // Splash holds; nothing to do.
                SessionState.SignedOut -> navigator.replaceTo(Login)
                is SessionState.SignedIn -> navigator.replaceTo(Main)
            }
        }
    }
}
```

`navigator.replaceTo` is idempotent — calling it with the current top-of-stack key just resets the stack to that single key. Re-emissions don't cause user-visible jank because Compose's recomposition diff sees no change.

**Alternatives considered:**

- **Conditional `replaceTo` (only if current top != desired).** Slightly fewer no-op writes but adds branching that's easy to get wrong. Idempotent calls are simpler.
- **`MainViewModel` collecting state and exposing routing decisions to a Compose-side `LaunchedEffect`.** Adds a layer for no benefit — `MainActivity` already has `lifecycleScope` and the navigator is Hilt-singleton.
- **Listen via `repeatOnLifecycle(STARTED)`.** Worth considering for production hygiene (avoid collecting while the activity is in the background); for v1, `lifecycleScope.launch` is acceptable since the navigator's mutations are cheap and the activity is the start.

**Rationale:** Reactive — every state transition triggers re-routing. Sign-out flows will Just Work when wired (a future settings-screen click → `authRepository.signOut()` → `SessionStateProvider` re-emits `SignedOut` → `MainActivity` reactively `replaceTo(Login)`).

### 4. `LoginScreen` no longer pops the back stack on `LoginSucceeded`

**Decision:** Remove the `LoginEffect.LoginSucceeded → navigator.goBack()` branch from `LoginScreen`'s `LaunchedEffect`. The `LoginSucceeded` effect remains in the contract — `LoginViewModel` still emits it on successful `completeLogin` — but the screen-side handler becomes a no-op (or gets removed entirely from the `when`).

The destination swap after sign-in now happens automatically: `completeLogin` succeeds → `SessionStateProvider.refresh()` → state becomes `SignedIn` → `MainActivity`'s collector calls `navigator.replaceTo(Main)`.

**Alternatives considered:**

- **Keep the `goBack()` and let MainActivity's `replaceTo` win.** Both writers race to mutate the same back stack. Only the second write is visible, but the first one briefly mutates the stack and could cause a one-frame visual glitch (Login briefly disappears under Splash before Main appears).
- **Remove the `LoginSucceeded` effect entirely.** Tempting for cleanup, but the effect is meaningful — it signals "the OAuth flow completed" which future analytics or onboarding nudges might consume. Existing VM tests assert on it being emitted.

**Rationale:** Single-writer principle for the back stack. `MainActivity`'s reactive routing is the only place that maps session state to destinations; `LoginScreen` shouldn't second-guess.

### 5. `AuthRepository` calls `sessionStateProvider.refresh()` after mutating operations

**Decision:** `DefaultAuthRepository.completeLogin` and `DefaultAuthRepository.signOut` both call `sessionStateProvider.refresh()` after the underlying store mutation succeeds. The dependency is constructor-injected.

**Alternatives considered:**

- **Have `SessionStateProvider` observe the store directly.** Would require an upstream interface change to `OAuthSessionStore` (add `data: Flow<OAuthSession?>`). We don't fork the library.
- **MainActivity refreshes on lifecycle events (e.g. `onResume`).** Misses non-lifecycle-driven transitions like an in-app sign-out button click.
- **Domain event bus / `EventBroker` pattern.** Over-engineered for a one-flow case. The `AuthRepository → SessionStateProvider` direct-call edge is explicit and easy to grep.

**Rationale:** The mutation site is the right place to trigger the refresh — it's where we know the store actually changed. Three suspending function calls in one Hilt-injected class; minor coupling for clear semantics.

### 6. `signOut()` returns `Result<Unit>` and is best-effort on the network revocation

**Decision:**

```kotlin
override suspend fun signOut(): Result<Unit> = runCatching {
    atOAuth.logout() // hits revocation endpoint + clears local store via the bound OAuthSessionStore
    sessionStateProvider.refresh()
}
```

If `AtOAuth.logout()` throws (network error talking to the revocation endpoint), the local session is *also* not cleared (because `AtOAuth.logout` does the network call before the local clear inside the upstream library). Caller gets `Result.failure`.

**Alternatives considered:**

- **Two-phase: revoke remotely, then clear locally even if revocation failed.** More defensive against the "user wants to be signed out NOW even if the server is down" case. But also means a stale local "signed out" state with a refresh token still alive on the server — security risk if the device is later compromised. Per the upstream library's design, prefer the all-or-nothing approach.
- **Synchronous best-effort: revoke async, clear local immediately.** Same security concern.

**Rationale:** Match the upstream library's behavior; surface failures to the caller. UI can decide whether to retry, force-clear locally as an escape hatch (a future "force sign out" button in settings), or just show a "couldn't sign out, check your connection" message.

## Risks / Trade-offs

- **Reactive `replaceTo` on every state emission risks unintended back-stack churn.** If a feature module emits state-flow updates rapidly during normal flow (it shouldn't — state transitions are limited to login completion + sign-out), `MainActivity` would re-write the back stack on each emission. Mitigation: `replaceTo` is idempotent; `Compose` recomposition diffs same-content renders to no-op. Still — if observed in practice, switch to "only `replaceTo` if current top != desired" branching.
- **System splash visible while bootstrap runs is whatever `Theme.Nubecita`'s `windowBackground` is.** Currently undefined → defaults. Real branding is a future polish issue.
- **`SessionState.Loading` after sign-out emission would lock the app on splash forever.** Current contract: `SessionStateProvider` only emits `Loading` once at construction; mutations transition to `SignedIn` or `SignedOut` only. If a future re-architecture re-emits `Loading` mid-session, `MainActivity` would re-mount the splash. Acceptable as long as the contract is documented.
- **The activity collector survives across configuration changes via `lifecycleScope`.** No re-route on rotation. Verified by manual rotation test against the existing pattern; documented as a known-good case.
- **`AuthRepository.signOut` requiring a successful network revocation might prevent users from signing out when offline.** Documented in non-goals; force-clear is a future affordance when a settings screen exists.

## Open Questions

- **Should `SessionState.SignedIn` carry more than `handle` + `did`?** Profile name, avatar URL? No — those come from atproto profile lookups, not the session token. Keep `SignedIn` minimal; profile data flows through a separate query path when the profile screen lands.
- **Does `installSplashScreen()` need to run before or after `super.onCreate`?** Per Google's docs, *before* `super.onCreate` for the keep-on-screen condition to take effect on the first frame. Verified at implementation time.
- **Should `:core:auth/AuthBindingsModule` provide `SessionStateProvider`, or does it warrant its own DI module file?** Single `@Binds` line; folds into the existing `AuthBindingsModule`. New file would be over-organized.
