## ADDED Requirements

### Requirement: AtProtoModule is installed in SingletonComponent

The app MUST include a `net.kikin.nubecita.data.AtProtoModule` declared as an `object` annotated `@Module` and `@InstallIn(SingletonComponent::class)`. The module MUST expose `HttpClient` and `io.github.kikin81.atproto.runtime.XrpcClient` via `@Provides @Singleton` functions. Provider functions MUST NOT rely on Android-framework state (`Context`, `Resources`) — the anonymous transport is framework-free.

#### Scenario: Module is annotated and installed

- **WHEN** the Hilt annotation processor scans the `:app` module
- **THEN** it discovers `AtProtoModule` as installed in `SingletonComponent` and registers its `HttpClient` and `XrpcClient` providers in the singleton graph.

#### Scenario: Both bindings are singleton-scoped

- **WHEN** `@Inject` sites request `HttpClient` or `XrpcClient` from the same component
- **THEN** each request receives the same instance (reference equality) for the process lifetime.

#### Scenario: Providers are framework-free

- **WHEN** the provider functions in `AtProtoModule` are invoked
- **THEN** they do not require an `@ApplicationContext` parameter, no Android SDK types, and can execute on a pure JVM unit-test classpath.
