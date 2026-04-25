## ADDED Requirements

### Requirement: `AuthRepository` exposes `completeLogin(redirectUri)`

`:core:auth`'s `AuthRepository` interface SHALL declare:

```kotlin
suspend fun completeLogin(redirectUri: String): Result<Unit>
```

The default implementation SHALL delegate to `AtOAuth.completeLogin(redirectUri)` and convert any thrown exception into `Result.failure(...)`. On success, the underlying call must have persisted the resulting `OAuthSession` via the bound `OAuthSessionStore`; the return value carries no payload because the session is reachable through the store after success.

#### Scenario: Successful completeLogin wraps as Result.success(Unit)

- **WHEN** `AtOAuth.completeLogin(redirectUri)` returns normally for a valid redirect
- **THEN** `AuthRepository.completeLogin(redirectUri)` SHALL return `Result.success(Unit)` and a subsequent `OAuthSessionStore.load()` SHALL return a non-null session

#### Scenario: Failed completeLogin wraps the exception

- **WHEN** `AtOAuth.completeLogin(redirectUri)` throws any subclass of `Exception` (malformed URI, missing PKCE state, server error)
- **THEN** `AuthRepository.completeLogin(redirectUri)` SHALL return `Result.failure(...)` carrying the original exception; the call SHALL NOT propagate the exception to the caller

### Requirement: `:core:auth` provides an `OAuthRedirectBroker` Hilt singleton

`:core:auth` SHALL expose a public `OAuthRedirectBroker` interface and an internal `DefaultOAuthRedirectBroker` implementation, bound `@Singleton` in `SingletonComponent`. The interface SHALL declare:

- `val redirects: Flow<String>` — the redirect URIs published since the broker was constructed, delivered at-most-once to a single collector.
- `suspend fun publish(redirectUri: String)` — emits the URI; suspends only if the buffered channel fills (highly unlikely — at most one redirect is in flight per OAuth flow).

The implementation SHALL use a `Channel<String>(Channel.BUFFERED)` exposed via `receiveAsFlow()`. The broker SHALL NOT use `SharedFlow` with replay or `StateFlow` (replay-on-subscribe semantics would re-deliver stale redirects on subsequent collector subscriptions).

#### Scenario: Cold-start redirect is buffered until the consumer subscribes

- **WHEN** `broker.publish("net.kikin.nubecita:/oauth-redirect?code=abc")` is called before any collector subscribes
- **AND** a subsequent collector calls `broker.redirects.collect { ... }`
- **THEN** the collector SHALL receive the previously-buffered URI exactly once

#### Scenario: Each emission is delivered to one collector only

- **WHEN** two coroutines collect from `broker.redirects` simultaneously and `publish` is called once
- **THEN** exactly one collector SHALL receive the emission; the other SHALL receive nothing for that emission
