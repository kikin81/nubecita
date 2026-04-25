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
