# core-auth-session-state Specification

## Purpose
The observable session lifecycle: a `SessionState` sealed type and the `SessionStateProvider` singleton that publishes it, so routing and feature code react to signed-in / signed-out / loading transitions instead of polling session storage.
## Requirements
### Requirement: `:core:auth` exposes a `SessionState` sealed type

`:core:auth` SHALL expose a public `SessionState` sealed interface representing three reactive states: `Loading` (initial state before store query completes), `SignedOut` (store returned no session), and `SignedIn(val handle: String, val did: String)` (store returned a valid session). Full session details like DPoP key material SHALL NOT be exposed via this type.

#### Scenario: SignedIn carries handle + did but nothing else

- **WHEN** a consumer destructures `SessionState.SignedIn`
- **THEN** only `handle: String` and `did: String` SHALL be accessible; no `accessToken`, `refreshToken`, `dpopPrivateKey`, or other session fields SHALL be reachable through the type

### Requirement: `:core:auth` provides a `SessionStateProvider` Hilt singleton

`:core:auth` SHALL expose a public `SessionStateProvider` interface with a `@Singleton` binding in `SingletonComponent`. The interface SHALL declare `val state: StateFlow<SessionState>` (initialized to `Loading`) and `suspend fun refresh()`, which loads `OAuthSessionStore` and emits either `SignedIn` or `SignedOut`. Implementations SHALL NOT eagerly refresh in `init`.

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
