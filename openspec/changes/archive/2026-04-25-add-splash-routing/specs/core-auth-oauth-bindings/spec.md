## ADDED Requirements

### Requirement: `AuthRepository` exposes `signOut()`

`:core:auth`'s `AuthRepository` interface SHALL declare:

```kotlin
suspend fun signOut(): Result<Unit>
```

The default implementation SHALL delegate to `AtOAuth.logout()` (which performs a server-side revocation POST and clears the local `OAuthSessionStore`) and SHALL convert any thrown exception into `Result.failure(...)`. After a successful logout, the implementation SHALL trigger a `SessionStateProvider.refresh()` so reactive consumers transition to `SessionState.SignedOut` automatically.

`signOut` SHALL NOT silently swallow network revocation failures by clearing only the local store — failures propagate as `Result.failure` so callers can choose retry / force-clear / surface-error behavior.

#### Scenario: Successful signOut returns success and triggers SignedOut transition

- **WHEN** `AtOAuth.logout()` returns normally and `SessionStateProvider.state` was previously `SignedIn`
- **THEN** `AuthRepository.signOut()` SHALL return `Result.success(Unit)`, `OAuthSessionStore.load()` SHALL return `null`, and `sessionStateProvider.state.value` SHALL be `SessionState.SignedOut` after the refresh completes

#### Scenario: Failed signOut wraps the exception and does not transition state

- **WHEN** `AtOAuth.logout()` throws an exception (network error, revocation endpoint unreachable)
- **THEN** `AuthRepository.signOut()` SHALL return `Result.failure(...)` carrying the original exception; `sessionStateProvider.state.value` SHALL remain at its prior value (typically `SignedIn`)

### Requirement: `AuthRepository.completeLogin` triggers a SessionStateProvider refresh on success

The `completeLogin(redirectUri)` implementation SHALL call `sessionStateProvider.refresh()` after the underlying `AtOAuth.completeLogin` succeeds and the new session is persisted. This makes the post-login `SignedIn` transition observable to reactive consumers (e.g. `MainActivity`'s splash routing) without explicit fan-out from each caller.

The refresh SHALL run within the same `runCatching` boundary that wraps `AtOAuth.completeLogin` — if either the upstream call or the refresh throws, the result is `Result.failure`. (In practice `refresh()` only suspends to call `sessionStore.load()`, which is unlikely to fail right after `AtOAuth.completeLogin` succeeded.)

#### Scenario: Successful completeLogin emits SignedIn before returning

- **WHEN** `AtOAuth.completeLogin(redirectUri)` succeeds and the session store now holds a session for `alice.bsky.social`
- **THEN** by the time `AuthRepository.completeLogin(redirectUri)` returns `Result.success(Unit)`, `sessionStateProvider.state.value` SHALL be `SessionState.SignedIn(handle = "alice.bsky.social", did = ...)`

#### Scenario: Failed completeLogin does not refresh

- **WHEN** `AtOAuth.completeLogin(redirectUri)` throws
- **THEN** `sessionStateProvider.state.value` SHALL remain at its prior value; the failure SHALL be wrapped as `Result.failure` per the existing completeLogin contract
