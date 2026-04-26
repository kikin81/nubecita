## ADDED Requirements

### Requirement: `:core:auth` provides an `XrpcClientProvider` Hilt binding

`:core:auth` SHALL expose a public `XrpcClientProvider` interface and an `internal class DefaultXrpcClientProvider` implementation, bound `@Singleton` in `SingletonComponent`. The interface SHALL declare:

```kotlin
interface XrpcClientProvider {
    suspend fun authenticated(): XrpcClient
}
```

`authenticated()` MUST delegate to `AtOAuth.createClient()` (suspend, performs DPoP setup) for the currently-persisted session. The returned `XrpcClient` MUST be cached across calls keyed by the active session's DID. The cache MUST invalidate when the session DID changes (login as a different account, signOut, refresh-failure clearing the session). When no session is persisted, `authenticated()` MUST throw `NoSessionException` (a `:core:auth`-defined `IllegalStateException` subclass) rather than returning a non-authenticated client.

The implementation MUST use a `Mutex` to serialize concurrent `authenticated()` calls so a single cache miss produces exactly one `AtOAuth.createClient()` invocation, not N parallel ones.

#### Scenario: First call constructs a client; second call returns the cached instance

- **WHEN** `xrpcClientProvider.authenticated()` is called twice with no session change between calls
- **THEN** `AtOAuth.createClient()` is invoked exactly once and both calls return the same `XrpcClient` instance

#### Scenario: Session change invalidates the cache

- **WHEN** the active session DID changes (signOut + login as a different account, or refresh-failure clearing the session) between two `xrpcClientProvider.authenticated()` calls
- **THEN** the second call invokes `AtOAuth.createClient()` again and returns a fresh `XrpcClient` instance

#### Scenario: No session throws NoSessionException

- **WHEN** `xrpcClientProvider.authenticated()` is called with no session persisted
- **THEN** the call throws `NoSessionException` and does NOT return a non-authenticated client

#### Scenario: Concurrent callers do not double-create the client

- **WHEN** N coroutines call `xrpcClientProvider.authenticated()` concurrently from a cold cache state
- **THEN** `AtOAuth.createClient()` is invoked exactly once and all N callers receive the same `XrpcClient` instance

### Requirement: Feature modules MUST inject `XrpcClientProvider`, never `AtOAuth`, for authenticated XRPC calls

Feature modules (`:feature:*:impl`) and any future cross-feature data layers (`:core:feed`, `:core:profile`, etc.) that need an authenticated `XrpcClient` MUST inject `XrpcClientProvider` and call `authenticated()` rather than injecting `AtOAuth` to call `createClient()` themselves. `AtOAuth` MUST NOT appear as an `@Inject` constructor parameter, `@Provides` return type, or `@Binds` source/target outside `:core:auth`.

#### Scenario: Feature module uses XrpcClientProvider

- **WHEN** a `@HiltViewModel` or repository class outside `:core:auth` needs to make an authenticated XRPC call
- **THEN** its constructor SHALL declare `private val xrpcClientProvider: XrpcClientProvider` and SHALL NOT declare `private val atOAuth: AtOAuth`

#### Scenario: AtOAuth stays internal to :core:auth

- **WHEN** the project source is grepped for `@Inject` parameters of type `AtOAuth` outside `:core:auth/`
- **THEN** there SHALL be zero matches
