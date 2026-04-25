# core-auth-oauth-bindings Specification

## Purpose
TBD - created by archiving change add-feature-login. Update Purpose after archive.
## Requirements
### Requirement: `:core:auth` provides an `AtOAuth` Hilt binding

`:core:auth` SHALL provide a `@Singleton`-scoped Hilt binding for `io.github.kikin81.atproto.oauth.AtOAuth`, constructed from (a) a `clientMetadataUrl: String` sourced from `BuildConfig.OAUTH_CLIENT_METADATA_URL`, (b) the `OAuthSessionStore` already bound by `:core:auth`, and (c) a Ktor `HttpClient` (either injected or constructed by the module). The binding SHALL NOT hard-code the client metadata URL. The `AtOAuth` type SHALL NOT appear in any `:app` `@Inject` parameter (consumers inject `AuthRepository` instead).

#### Scenario: Consumer injects AtOAuth through the DI graph

- **WHEN** a Hilt-managed class in `:core:auth` declares a constructor parameter of type `AtOAuth`
- **THEN** Hilt SHALL resolve it at graph construction time using the BuildConfig URL and the existing `OAuthSessionStore` binding

#### Scenario: URL is build-variant configurable

- **WHEN** `:app/build.gradle.kts` declares `buildConfigField("String", "OAUTH_CLIENT_METADATA_URL", ...)` and the module is rebuilt
- **THEN** the new URL SHALL be picked up by the provided `AtOAuth` without any source-file edit in `:core:auth`

#### Scenario: `:app` does not directly inject AtOAuth

- **WHEN** `:app` source files are inspected
- **THEN** no `@Inject` constructor parameter, `@Provides` return type, or field in `:app` SHALL be typed as `AtOAuth`

### Requirement: `:core:auth` provides an `AuthRepository` interface and Hilt binding

`:core:auth` SHALL expose an `AuthRepository` interface (public to consumers) with at minimum a method `suspend fun beginLogin(handle: String): Result<String>` that returns an authorization URL on success. The interface's implementation SHALL delegate to `AtOAuth.beginLogin` and SHALL convert thrown exceptions into `Result.failure(...)`. `:core:auth` SHALL `@Binds` the implementation to the interface inside `SingletonComponent`. The implementation SHALL be `internal` to `:core:auth`; consumers SHALL only see the interface.

#### Scenario: LoginViewModel injects AuthRepository

- **WHEN** a `@HiltViewModel` class in `:feature:login:impl` declares a constructor parameter of type `AuthRepository`
- **THEN** Hilt SHALL resolve it to the `:core:auth`-provided singleton

#### Scenario: beginLogin wraps success as Result.success

- **WHEN** `AtOAuth.beginLogin(handle)` returns a non-blank URL
- **THEN** `AuthRepository.beginLogin(handle)` SHALL return `Result.success(url)` with the same URL

#### Scenario: beginLogin wraps failure as Result.failure

- **WHEN** `AtOAuth.beginLogin(handle)` throws any `Exception` subclass
- **THEN** `AuthRepository.beginLogin(handle)` SHALL return `Result.failure(exception)` carrying that exception; the exception SHALL NOT propagate to the caller

#### Scenario: Implementation class is not leaked

- **WHEN** `:app` or `:feature:login:impl` source is inspected
- **THEN** neither SHALL reference the concrete `DefaultAuthRepository` implementation class — only the `AuthRepository` interface

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
