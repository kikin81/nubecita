## ADDED Requirements

### Requirement: `:core:profile` exposes a shared current-account source

`:core:profile` SHALL expose a `CurrentAccountRepository` interface with a `StateFlow<CurrentAccount?>` of the signed-in account, where `CurrentAccount` carries `did`, `handle`, `displayName` and `avatarUrl`. It MUST be bound as an app-scoped singleton so every consumer observes the same instance.

#### Scenario: Consumers share one instance

- **WHEN** `DefaultOutgoingPostStore` and `ComposerViewModel` are both injected with `CurrentAccountRepository`
- **THEN** both receive the same singleton instance and observe the same `StateFlow`

### Requirement: Current account is seeded from the session and refreshed once per session

When the session becomes `SessionState.SignedIn`, the repository SHALL immediately emit a `CurrentAccount` built from the session (`did`, `handle`, `displayName = handle`, `avatarUrl = null`), then fetch the profile once via `ActorProfileRepository.fetchProfile(did)` and emit the enriched account. It MUST NOT refetch per consumer subscription.

#### Scenario: Immediate seed before the profile loads

- **GIVEN** the session is `SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")` and the profile fetch has not completed
- **WHEN** a consumer reads the flow
- **THEN** it sees `CurrentAccount(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "alice.bsky.social", avatarUrl = null)`

#### Scenario: Enriched after the profile loads

- **GIVEN** `fetchProfile("did:plc:alice")` returns display name `"Alice"` and an avatar URL
- **WHEN** the fetch completes
- **THEN** the flow emits `displayName = "Alice"` and that `avatarUrl`

#### Scenario: Display name falls back to the handle

- **GIVEN** the fetched profile has no display name
- **WHEN** the fetch completes
- **THEN** `displayName` equals the handle

#### Scenario: Fetch failure keeps the seed

- **GIVEN** `fetchProfile` fails
- **WHEN** the failure is observed
- **THEN** the flow keeps the session-seeded account and no error is surfaced to consumers

#### Scenario: One fetch per session

- **GIVEN** three consumers subscribe during one signed-in session
- **WHEN** they collect the flow
- **THEN** `fetchProfile` is invoked exactly once for that session

### Requirement: Current account clears on sign-out

When the session leaves `SessionState.SignedIn`, the repository SHALL emit `null`.

#### Scenario: Sign-out clears the account

- **GIVEN** the flow holds a `CurrentAccount`
- **WHEN** the session becomes `SignedOut`
- **THEN** the flow emits `null`

### Requirement: Bench flavor provides a fixed current account

The `bench` flavor of `:core:profile` SHALL bind a fake `CurrentAccountRepository` emitting a fixed, non-network `CurrentAccount`, so bench builds and screenshot fixtures render a deterministic identity. Adding it makes `:core:profile` apply `nubecita.android.flavors`.

#### Scenario: Bench build shows the fixed account

- **WHEN** the app runs in the `bench` flavor
- **THEN** `CurrentAccountRepository` emits the fixed bench account and performs no network request

### Requirement: Outgoing posts read the shared source

`DefaultOutgoingPostStore` SHALL take the posting author's display name and avatar from `CurrentAccountRepository` instead of resolving the profile itself.

#### Scenario: Outgoing card uses the shared identity

- **GIVEN** `CurrentAccountRepository` holds `displayName = "Alice"` with an avatar URL
- **WHEN** a post is queued as outgoing
- **THEN** its `AuthorUi` carries `"Alice"` and that avatar URL
