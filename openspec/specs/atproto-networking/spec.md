# atproto-networking Specification

## Purpose
TBD - created by archiving change add-atproto-hilt-module. Update Purpose after archive.
## Requirements
### Requirement: Anonymous XrpcClient is available for public AppView queries

The app MUST expose a Hilt-provided `io.github.kikin81.atproto.runtime.XrpcClient` singleton whose `baseUrl` is `https://public.api.bsky.app` and whose `authProvider` is the library default (`NoAuth`). Feature code MUST obtain this client via `@Inject` constructor injection — never by constructing `XrpcClient` inline, and never with a different `baseUrl`, unless a future change adds a separately-qualified provider.

#### Scenario: Feature code resolves a handle

- **WHEN** any class declares `@Inject constructor(xrpcClient: XrpcClient, ...)` and calls `IdentityService(xrpcClient).resolveHandle(ResolveHandleRequest(Handle("bsky.app")))` from a coroutine
- **THEN** the call completes successfully and the response's `did` field holds a value starting with `did:plc:`.

#### Scenario: Compile-time graph resolution

- **WHEN** `./gradlew :app:assembleDebug` runs
- **THEN** Hilt's annotation processor validates the `XrpcClient` binding without error and the build produces an APK that can instantiate `XrpcClient` at runtime.

### Requirement: Ktor engine is explicitly declared and shared

The app MUST declare exactly one Ktor client engine — `io.ktor:ktor-client-okhttp` at the version matching the `ktor-client-core` version resolved transitively by the atproto-kotlin runtime — as an `implementation` dependency. The `HttpClient` that backs `XrpcClient` MUST use this engine and MUST be a `@Singleton` in the Hilt graph so both the anonymous client and any future authenticated client share one connection pool.

#### Scenario: OkHttp engine is the sole Ktor engine on the classpath

- **WHEN** `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` runs
- **THEN** the output shows `io.ktor:ktor-client-okhttp` resolved and no other `io.ktor:ktor-client-*` engine artifact is present.

#### Scenario: HttpClient is shared

- **WHEN** Hilt resolves `HttpClient` for two different injection sites in the same `SingletonComponent`
- **THEN** both sites receive the same instance (reference equality) for the lifetime of the process.

### Requirement: Anonymous XrpcClient uses no plugins that mutate request or response bodies

The shared `HttpClient` MUST NOT install Ktor plugins that encode, decode, or otherwise transform request or response bodies (for example, `ContentNegotiation` with a JSON plugin). The library's `XrpcClient` owns JSON serialization internally and installing a body-mutating plugin on the shared `HttpClient` would risk double-encoding or losing error payloads.

#### Scenario: No body-mutating plugin is installed

- **WHEN** the `HttpClient` provider runs
- **THEN** the returned client is constructed via `HttpClient(OkHttp)` with no `install(ContentNegotiation)` or equivalent body-mutating plugin call.

### Requirement: Feature code MUST NOT construct XrpcClient directly

Feature modules, ViewModels, repositories, and data sources MUST obtain `XrpcClient` via Hilt injection. They MUST NOT call the `XrpcClient` constructor, and MUST NOT build their own `HttpClient` for AT Protocol traffic. Construction is the `atproto-networking` capability's responsibility.

#### Scenario: No inline XrpcClient construction in feature code

- **WHEN** `git grep -n 'XrpcClient(' app/src/main` is run
- **THEN** the only match is inside the Hilt-annotated provider function in `AtProtoModule.kt`.
