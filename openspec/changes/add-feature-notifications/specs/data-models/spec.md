## ADDED Requirements

### Requirement: `NotificationItemUi` is a sealed Single / Aggregated type in `:data:models`

`:data:models` SHALL expose `NotificationItemUi` as an `@Stable sealed interface` with two variants:

- `Single` — exactly one actor; carries `subjectPost: PostUi?` (null for follow / verified / starterpack-joined / unverified reasons).
- `Aggregated` — two or more actors collapsed from same-reason events on the same `reasonSubject` (or same calendar day for `follow`); carries the same `subjectPost: PostUi?` plus the full `actors: ImmutableList<AuthorUi>`.

Both variants SHALL expose: `itemKey: String` (stable LazyColumn key), `reason: NotificationReason`, `indexedAt: kotlinx.datetime.Instant`, `isRead: Boolean`, `actors: ImmutableList<AuthorUi>`.

#### Scenario: Single carries one actor

- **WHEN** a `NotificationItemUi.Single` is constructed
- **THEN** `actors.size` SHALL equal 1

#### Scenario: Aggregated carries two or more actors

- **WHEN** a `NotificationItemUi.Aggregated` is constructed
- **THEN** `actors.size` SHALL be greater than or equal to 2

### Requirement: `NotificationReason` enum covers known lexicon values plus `Unknown`

`:data:models` SHALL expose `NotificationReason` as a Kotlin `enum class` with values: `Like`, `Repost`, `Follow`, `Mention`, `Reply`, `Quote`, `StarterpackJoined`, `Verified`, `Unverified`, `LikeViaRepost`, `RepostViaRepost`, `SubscribedPost`, `ContactMatch`, and `Unknown`. The `Unknown` value SHALL be assigned to any reason string the mapper does not recognize, preserving forward compatibility when the lexicon adds new values.

#### Scenario: Unknown reason maps to Unknown

- **WHEN** the mapper encounters a `reason` string not in the known list (e.g. `"future-reason"`)
- **THEN** the mapper SHALL set `NotificationReason.Unknown` and the row SHALL still render with a fallback icon

### Requirement: `NotificationFilter` enum exposes the slice-1 filter chip set

`:data:models` SHALL expose `NotificationFilter` as a Kotlin `enum class` with exactly the values `All`, `Mentions`, `Reposts`, `Follows`, `Likes`. Each value SHALL expose an internal `reasons: List<String>?` property that maps to the lexicon `reasons[]` request parameter (null for `All`).

#### Scenario: Mentions filter maps to three reason values

- **WHEN** `NotificationFilter.Mentions.reasons` is read
- **THEN** the returned list SHALL equal `["mention", "reply", "quote"]`

### Requirement: `NotificationItemUi` ships fixture factories for previews and tests

`:data:models` SHALL provide a `NotificationItemUiFixtures` object exposing factory functions for `singleLike`, `aggregatedLikes(actorCount)`, `singleFollow`, `aggregatedFollows(actorCount)`, `singleReply`, `singleQuote`, `singleMention`, and at least one fixture per other known reason. Fixtures SHALL be usable from any module that depends on `:data:models` (previews, screenshot tests, unit tests).

#### Scenario: Fixture is consumable from a preview

- **WHEN** a `@Preview` composable in `:designsystem` or `:feature:notifications:impl` references `NotificationItemUiFixtures.aggregatedLikes(3)`
- **THEN** the fixture SHALL return a valid `NotificationItemUi.Aggregated` with three actors and a non-null `subjectPost`
