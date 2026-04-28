## ADDED Requirements

### Requirement: `EmbedUi` exposes `RecordOrUnavailable` and `MediaEmbed` marker sealed interfaces

The `EmbedUi` sealed interface in `:data:models` MUST expose two nested marker sealed interfaces:

- `EmbedUi.RecordOrUnavailable : EmbedUi` — implemented by `EmbedUi.Record` and `EmbedUi.RecordUnavailable` only. No other variant of `EmbedUi` MAY implement this marker. Used to constrain the `record` slot of [EmbedUi.RecordWithMedia] at the type system.
- `EmbedUi.MediaEmbed : EmbedUi` — implemented by `EmbedUi.Images`, `EmbedUi.Video`, and `EmbedUi.External` only. No other variant of `EmbedUi` MAY implement this marker. Used to constrain the `media` slot of [EmbedUi.RecordWithMedia].

Both markers MUST extend `EmbedUi` directly so any value of either type is automatically an `EmbedUi`. Implementers SHOULD declare just `: RecordOrUnavailable` (or `: MediaEmbed`) without redundant `: EmbedUi` — the marker's parent declaration covers it.

The markers MUST NOT add any abstract members or behavior — they are purely type-discriminating sealed interfaces. They exist solely to express the recursion bound for [EmbedUi.RecordWithMedia] at the compile-time type system.

#### Scenario: RecordOrUnavailable is implemented by exactly the two record variants

- **WHEN** the project source is searched for `: EmbedUi.RecordOrUnavailable` declarations
- **THEN** exactly two declarations are found — `EmbedUi.Record` and `EmbedUi.RecordUnavailable`. No other variant declares this marker.

#### Scenario: MediaEmbed is implemented by exactly the three media variants

- **WHEN** the project source is searched for `: EmbedUi.MediaEmbed` declarations
- **THEN** exactly three declarations are found — `EmbedUi.Images`, `EmbedUi.Video`, and `EmbedUi.External`. No other variant declares this marker.

#### Scenario: Markers extend EmbedUi (transitively assignable)

- **WHEN** a value of type `EmbedUi.RecordOrUnavailable` (or `EmbedUi.MediaEmbed`) is assigned to a variable of type `EmbedUi`
- **THEN** the assignment compiles without an explicit cast — the marker's `: EmbedUi` parent declaration makes the upcast implicit

### Requirement: `EmbedUi` exposes a `RecordWithMedia` variant for `app.bsky.embed.recordWithMedia#view`

The `EmbedUi` sealed interface MUST expose a `RecordWithMedia` data class variant carrying:

- `record: EmbedUi.RecordOrUnavailable` — either a resolved `EmbedUi.Record` (with its `QuotedPostUi`) or an `EmbedUi.RecordUnavailable` (when the wire-side `record.record` is `viewNotFound` / `viewBlocked` / `viewDetached` / Unknown). Same set of values the top-level `EmbedUi.Record` / `RecordUnavailable` variants represent — they are reused verbatim in this slot.
- `media: EmbedUi.MediaEmbed` — exactly one of `EmbedUi.Images`, `EmbedUi.Video`, or `EmbedUi.External`. Reused verbatim from the top-level variants.

The marker constraints make the following structurally inexpressible at compile time:

- `RecordWithMedia` inside `RecordWithMedia` (any slot) — `RecordWithMedia` itself does NOT implement either marker.
- `Images` / `Video` / `External` in the `record` slot — they implement `MediaEmbed`, not `RecordOrUnavailable`.
- `Record` / `RecordUnavailable` in the `media` slot — they implement `RecordOrUnavailable`, not `MediaEmbed`.
- `Empty` / `Unsupported` in either slot — they implement neither marker.

`RecordWithMedia` itself MUST NOT implement `RecordOrUnavailable` or `MediaEmbed` (would re-open the recursion). It implements only `EmbedUi`.

`RecordWithMedia` is `@Immutable` per the sealed interface's existing stability annotation. Both fields are `@Immutable` types (the markers transitively guarantee this since their implementers are all `@Immutable` data classes).

#### Scenario: RecordWithMedia variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.RecordWithMedia`; omitting it is a compile error

#### Scenario: RecordWithMedia cannot nest

- **WHEN** the project source attempts to construct `EmbedUi.RecordWithMedia(record = EmbedUi.RecordWithMedia(...), ...)` or `EmbedUi.RecordWithMedia(media = EmbedUi.RecordWithMedia(...), ...)`
- **THEN** the compiler SHALL reject the construction — `RecordWithMedia` doesn't implement `RecordOrUnavailable` or `MediaEmbed`

#### Scenario: RecordWithMedia rejects wrong-slot values

- **WHEN** the project source attempts `EmbedUi.RecordWithMedia(record = EmbedUi.Images(...), ...)` or `EmbedUi.RecordWithMedia(media = EmbedUi.Record(...), ...)`
- **THEN** the compiler SHALL reject the construction — the value's marker doesn't match the slot's type

#### Scenario: Stable for Compose skipping

- **WHEN** a `PostUi` whose `embed is EmbedUi.RecordWithMedia` is passed to a `@Composable` whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable. Compose Compiler stability reports MUST mark composables consuming `EmbedUi.RecordWithMedia` as `restartable skippable` (no regression vs the sibling embed variants).

### Requirement: `EmbedUi.quotedRecord` extension property centralizes "where do quoted posts hide"

The `:data:models` module MUST expose a public extension property:

```kotlin
val EmbedUi.quotedRecord: QuotedPostUi?
    get() = when (this) {
        is EmbedUi.Record           -> quotedPost
        is EmbedUi.RecordWithMedia  -> (record as? EmbedUi.Record)?.quotedPost
        else                        -> null
    }
```

This property MUST be the single source of truth for the question "given an `EmbedUi`, where (if anywhere) is a quoted post?" Both feature-feed's slot wiring and feature-feed-video's bind-target resolver MUST consume this property rather than re-deriving the chained casts inline.

When future lexicon evolution introduces another composite embed type that contains a quoted post, this extension property is the single point of update.

#### Scenario: Returns the quoted post for EmbedUi.Record

- **WHEN** `quotedRecord` is read on an `EmbedUi.Record(quotedPost = qp)`
- **THEN** the result is `qp`

#### Scenario: Returns the quoted post for EmbedUi.RecordWithMedia whose record is Record

- **WHEN** `quotedRecord` is read on an `EmbedUi.RecordWithMedia(record = EmbedUi.Record(quotedPost = qp), media = ...)`
- **THEN** the result is `qp`

#### Scenario: Returns null for EmbedUi.RecordWithMedia whose record is RecordUnavailable

- **WHEN** `quotedRecord` is read on an `EmbedUi.RecordWithMedia(record = EmbedUi.RecordUnavailable(...), media = ...)`
- **THEN** the result is `null` — there's no resolved quoted post to return

#### Scenario: Returns null for variants that don't carry a quoted post

- **WHEN** `quotedRecord` is read on `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Video`, `EmbedUi.External`, `EmbedUi.RecordUnavailable`, or `EmbedUi.Unsupported`
- **THEN** the result is `null` for every case
