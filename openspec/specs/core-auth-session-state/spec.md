# core-auth-session-state Specification

## Purpose
TBD - created by archiving change add-splash-routing. Update Purpose after archive.
## Requirements
### Requirement: `:core:auth` exposes a `SessionState` sealed type

`:core:auth` SHALL expose a public `SessionState` sealed interface representing the three reactive states a session can be in:

- `data object Loading : SessionState` — initial state; the store hasn't been queried yet (or a query is in flight).
- `data object SignedOut : SessionState` — the store returned no session.
- `data class SignedIn(val handle: String, val did: String) : SessionState` — the store returned a non-null session; the two String fields surface the user-identifying parts of `OAuthSession`. The full session (including DPoP keypair bytes) SHALL NOT be exposed via this type.

#### Scenario: SignedIn carries handle + did but nothing else

- **WHEN** a consumer destructures `SessionState.SignedIn`
- **THEN** only `handle: String` and `did: String` SHALL be accessible; no `accessToken`, `refreshToken`, `dpopPrivateKey`, or other session fields SHALL be reachable through the type

### Requirement: `:core:auth` provides a `SessionStateProvider` Hilt singleton

`:core:auth` SHALL expose a public `SessionStateProvider` interface and an internal `DefaultSessionStateProvider` implementation, bound `@Singleton` in Hilt's `SingletonComponent`. The interface SHALL declare:

- `val state: StateFlow<SessionState>` — synchronous-readable hot stream; initial value is `SessionState.Loading`.
- `suspend fun refresh()` — re-queries `OAuthSessionStore.load()` and emits either `SignedIn(...)` (when the store returns a non-null session) or `SignedOut` (when the store returns null).

The implementation SHALL back `state` with a `MutableStateFlow<SessionState>(Loading)` and SHALL NOT eagerly trigger a refresh in `init`; consumers (today: `MainActivity` on cold start; tomorrow: `AuthRepository` after mutations) drive refresh explicitly.

#### Scenario: Initial state is Loading

- **WHEN** `SessionStateProvider` is first injected (immediately after Hilt graph construction, before any `refresh()` call)
- **THEN** `state.value` SHALL be `SessionState.Loading`

#### Scenario: refresh() with a stored session emits SignedIn

- **WHEN** `OAuthSessionStore.load()` returns a non-null `OAuthSession` and `refresh()` is called
- **THEN** `state.value` SHALL transition to `SessionState.SignedIn(handle = session.handle, did = session.did)`

#### Scenario: refresh() with no stored session emits SignedOut

- **WHEN** `OAuthSessionStore.load()` returns `null` and `refresh()` is called
- **THEN** `state.value` SHALL transition to `SessionState.SignedOut`

#### Scenario: state is observable from a non-coroutine context

- **WHEN** a caller reads `sessionStateProvider.state.value` from outside a coroutine (e.g. the SplashScreen `setKeepOnScreenCondition` predicate, called on the platform's frame callback)
- **THEN** the read SHALL succeed and return the latest emitted value
