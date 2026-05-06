## ADDED Requirements

### Requirement: `ActorTypeaheadRepository` exposes a typeahead query

The system SHALL expose `net.kikin.nubecita.core.posting.ActorTypeaheadRepository` as a Kotlin interface in the `:core:posting` capability. The interface MUST declare a single suspending function `searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>`. Returning `Result.success(emptyList())` SHALL signify "the call succeeded but no actors match"; returning `Result.failure(...)` SHALL signify any non-success outcome (network, auth, server, parse). Consumers SHALL NOT distinguish failure subtypes from this surface in V1.

#### Scenario: Interface contract

- **WHEN** `:core:posting`'s public API is searched for `ActorTypeaheadRepository`
- **THEN** the interface SHALL exist with exactly one method `suspend fun searchTypeahead(query: String): Result<List<ActorTypeaheadUi>>` and no other methods

### Requirement: `ActorTypeaheadUi` is the boundary type returned to consumers

The system SHALL expose `net.kikin.nubecita.core.posting.ActorTypeaheadUi` as a `@Stable data class` with exactly the fields `did: String`, `handle: String`, `displayName: String?`, `avatarUrl: String?`. The class MUST NOT expose `io.github.kikin81.atproto.*` types in its constructor or properties. `displayName` SHALL be `null` when the upstream `ProfileViewBasic.displayName` is missing or blank. `avatarUrl` SHALL be `null` when the upstream `ProfileViewBasic.avatar` is missing.

#### Scenario: Consumers do not see SDK types

- **WHEN** `:feature:composer:impl` declares its dependency on `:core:posting`
- **THEN** any `ActorTypeaheadUi` it receives SHALL NOT carry `io.github.kikin81.atproto.lexicon.app.bsky.actor.ProfileViewBasic` or any other SDK type as a property

#### Scenario: Null displayName when upstream is blank

- **GIVEN** a `ProfileViewBasic` with `displayName = ""` (blank) and `handle = "alice.bsky.social"`
- **WHEN** mapped to `ActorTypeaheadUi`
- **THEN** the resulting `ActorTypeaheadUi.displayName` SHALL be `null`

### Requirement: `DefaultActorTypeaheadRepository` calls `searchActorsTypeahead` with `q`

The default implementation `net.kikin.nubecita.core.posting.internal.DefaultActorTypeaheadRepository` MUST be the only production binding of `ActorTypeaheadRepository`. It MUST call `ActorService.searchActorsTypeahead(SearchActorsTypeaheadRequest(q = query, limit = 8))` via an `XrpcClient` obtained from the same `XrpcClientProvider.authenticated()` accessor used by the rest of `:core:posting`. The implementation MUST use the `q` parameter and SHALL NOT use the deprecated `term` parameter. The `limit` SHALL be 8 in V1.

#### Scenario: Wire format uses q parameter

- **WHEN** `DefaultActorTypeaheadRepository.searchTypeahead("alice")` is called and the underlying HTTP request is observed
- **THEN** the request URL SHALL contain a `q=alice` query parameter and SHALL NOT contain a `term=` query parameter

#### Scenario: Limit parameter is 8

- **WHEN** `DefaultActorTypeaheadRepository.searchTypeahead(any)` is called and the underlying HTTP request is observed
- **THEN** the request URL SHALL contain a `limit=8` query parameter

#### Scenario: Mapping ProfileViewBasic to ActorTypeaheadUi

- **GIVEN** the server returns `actors = [ProfileViewBasic(did = "did:plc:abc", handle = "alice.bsky.social", displayName = "Alice", avatar = "https://cdn/avatar.jpg")]`
- **WHEN** the call resolves
- **THEN** the result SHALL be `Result.success(listOf(ActorTypeaheadUi(did = "did:plc:abc", handle = "alice.bsky.social", displayName = "Alice", avatarUrl = "https://cdn/avatar.jpg")))`

#### Scenario: Network errors surface as Result.failure

- **GIVEN** the underlying XRPC call throws `IOException`
- **WHEN** `DefaultActorTypeaheadRepository.searchTypeahead(any)` is called
- **THEN** the result SHALL be `Result.failure(<IOException>)` AND no exception SHALL propagate to the caller

### Requirement: Repository is bound via Hilt and unavailable to outer-shell consumers

`DefaultActorTypeaheadRepository` MUST be bound to `ActorTypeaheadRepository` via the existing `:core:posting` Hilt module (`PostingModule` or a sibling module in the same package). The binding MUST be application-scoped via `@Singleton`. No production code outside the `:feature:composer:impl` module SHALL inject `ActorTypeaheadRepository` in V1.

#### Scenario: Hilt binding exists

- **WHEN** the `:core:posting` Hilt module graph is inspected
- **THEN** there SHALL be exactly one `@Provides` or `@Binds` for `ActorTypeaheadRepository`, returning `DefaultActorTypeaheadRepository`, scoped `@Singleton`
