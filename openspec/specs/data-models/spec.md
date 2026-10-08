# data-models Specification

## Purpose
`:data:models` — the canonical, service-free home for the `@Stable` UI model types every feature and the design system render from (`PostUi`, `AuthorUi`, `EmbedUi` and its quoted-record variants, `FeedItemUi`, `NotificationItemUi`, …), together with the fixture factories previews and tests build on.
## Requirements
### Requirement: `:data:models` is the canonical location for UI model types

UI model types — plain Kotlin data classes that represent "one frame's worth of state for a visual entity" — MUST live in the `:data:models` Gradle module under the `net.kikin.nubecita.data.models` package. The module MUST apply the `nubecita.android.library` convention plugin (no Compose, no Hilt). Other modules (`:designsystem` for consumption, `:feature:*:impl` for production) declare `implementation(project(":data:models"))` to consume these types.

#### Scenario: Module exists and is wired into settings

- **WHEN** `./gradlew :data:models:assembleDebug` is invoked
- **THEN** the build succeeds, the module's namespace resolves to `net.kikin.nubecita.data.models`, and the published classpath contains the UI model types under that package.

#### Scenario: A UI-consuming module imports a model

- **WHEN** `:designsystem`'s `build.gradle.kts` declares `implementation(project(":data:models"))`
- **THEN** PostCard can reference `net.kikin.nubecita.data.models.PostUi` without adding a transitive Compose or Hilt dependency to `:data:models`.

### Requirement: `:data:models` MUST NOT depend on service abstractions

The module MUST NOT import any of: `atproto:runtime`, `atproto:oauth`, `atproto:compose`, `atproto:compose-material3`. It MUST NOT define classes that mirror response envelopes (`PostView`, `FeedViewPost`, `OutputContainer`), pagination cursors, or error-envelope shapes from the AT Protocol lexicon. Service-layer types belong with the mapper that produces them, not with the UI models that consume the mapper's output.

#### Scenario: A new dependency is proposed

- **WHEN** a developer adds `implementation(libs.atproto.runtime)` (or any other prohibited service dep) to `:data:models/build.gradle.kts`
- **THEN** code review rejects the change with a pointer to this requirement.

#### Scenario: A response-envelope shape is proposed

- **WHEN** a developer proposes adding `data class PostViewUi(val record: ..., val thread: ..., val cursor: String?)` that mirrors `app.bsky.feed.defs#postView`
- **THEN** the proposal is rejected; the mapper in `:feature:*:impl` should flatten the envelope into its already-defined UI types (e.g., `PostUi`, `ThreadUi`).

### Requirement: AT Protocol wire-data primitives are explicitly allowed

The module MUST permit AT Protocol wire-data primitive types as field types on UI models via `api(libs.atproto.models)`. Lexicon-defined primitives (`Facet`, `Did`, `Handle`, `AtUri`, `Datetime`) MAY be used directly without mirror classes. Higher-level service abstractions (`PostView`, `FeedViewPost`, envelopes, cursors) MUST NOT be used.

#### Scenario: PostUi carries a Facet

- **WHEN** `data class PostUi(val text: String, val facets: ImmutableList<Facet>, ...)` is declared
- **THEN** the build accepts it; `Facet` is a lexicon primitive and downstream consumers (PostCard) can pass it directly to `rememberBlueskyAnnotatedString`.

#### Scenario: AuthorUi standardizes did and handle on String

- **WHEN** `data class AuthorUi(val did: String, val handle: String, val displayName: String, val avatarUrl: String?)` is declared
- **THEN** the build accepts it; `AuthorUi` standardizes on `String` for these identity fields rather than the typed wrappers (`Did`, `Handle`) from `atproto:runtime`. UI rendering doesn't need wire-level type safety on identifiers — the mapper unwraps the typed wrappers' `.raw` values before constructing the UI model. Keeping `:data:models` free of `atproto:runtime` is a goal of this capability (see the no-service-abstractions requirement).

### Requirement: UI models are stable and use immutable collections

Every UI model class in `:data:models` MUST be annotated `@Stable` (from `androidx.compose.runtime`). Every collection field MUST use `kotlinx.collections.immutable.ImmutableList<T>` (or a sibling immutable type) rather than `List<T>` or `MutableList<T>`.

The `@Stable` annotation requires the module to declare `api(libs.androidx.compose.runtime)` — this is the ONLY Compose dependency `:data:models` declares (runtime only, never UI).

#### Scenario: A model is consumed in a LazyColumn

- **WHEN** `LazyColumn { items(posts, key = { it.id }) { post -> PostCard(post) } }` runs and the parent recomposes without changing any post's structural content
- **THEN** Compose skips re-running the inner item composables because each `PostUi` is structurally equal across compositions.

#### Scenario: A new model is added without `@Stable`

- **WHEN** a developer declares `data class FooUi(...)` without the `@Stable` annotation
- **THEN** Compose stability inference treats it as unstable and Slack's compose-lint rules (already wired in `nubecita.android.library.compose` — but not `nubecita.android.library`, so this fires when the model is consumed) flag the call site. Developers MUST add `@Stable` to align with the convention.

### Requirement: PostUi captures everything needed to render a single post

The module MUST define `@Stable data class PostUi` with fields: `id: String`, `author: AuthorUi`, `createdAt: Instant`, `text: String`, `facets: ImmutableList<Facet>`, `embed: EmbedUi`, `stats: PostStatsUi`, `viewer: ViewerStateUi`, and `repostedBy: String?`. Supporting types `AuthorUi`, `EmbedUi`, `ImageUi`, `PostStatsUi`, and `ViewerStateUi` MUST reside in `:data:models`.

#### Scenario: Constructing a minimal PostUi for a preview

- **WHEN** test code calls `PostUi(id = "p1", author = AuthorUi(...), createdAt = Clock.System.now(), text = "hello", facets = persistentListOf(), embed = EmbedUi.Empty, stats = PostStatsUi(), viewer = ViewerStateUi(), repostedBy = null)`
- **THEN** the construction compiles, no field is missing a default that would force the test to provide irrelevant data, and PostCard renders it cleanly.

#### Scenario: The embed slot dispatches on a sealed type

- **WHEN** PostCard's body executes `when (post.embed)`
- **THEN** the compiler enforces exhaustiveness across `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Unsupported`. Adding a new variant in a future change (e.g. `EmbedUi.External`) surfaces as a compile error at every dispatch site, naming the work needed.

### Requirement: `EmbedUi` exposes a `Video` variant for `app.bsky.embed.video#view`

`EmbedUi` in `:data:models` MUST expose a `@Immutable Video` data class variant with fields: `posterUrl: String?`, `playlistUrl: String`, `aspectRatio: Float`, `durationSeconds: Int?`, and `altText: String?`. All fields are immutable. If `playlist` is absent on the wire, the mapper falls back to `EmbedUi.Unsupported`.

#### Scenario: Video variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Video`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Video` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `EmbedUi.Video`'s fields are all immutable values) and skip recomposition when the embed reference is structurally equal across recompositions

### Requirement: `EmbedUi` exposes a `Record` variant for `app.bsky.embed.record#viewRecord`

`EmbedUi` MUST expose a `@Immutable Record(val quotedPost: QuotedPostUi)` variant. `QuotedPostUi` MUST be an `@Immutable` data class containing: `uri: String`, `cid: String`, `author: AuthorUi`, `createdAt: Instant`, `text: String`, `facets: ImmutableList<Facet>`, and `embed: QuotedEmbedUi`. It MUST NOT carry interaction counts or viewer state.

#### Scenario: Record variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Record`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Record` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `QuotedPostUi`'s fields are all immutable values; `QuotedPostUi` carries the `@Immutable` annotation explicitly)

### Requirement: `EmbedUi` exposes a `RecordUnavailable` variant for the unresolved-quote union members

`EmbedUi` MUST expose a `RecordUnavailable(val reason: Reason)` variant where `Reason` is an enum with values: `NotFound`, `Blocked`, `Detached`, and `Unknown`. The reason MUST be preserved for telemetry, logging, and future per-variant UI copy.

#### Scenario: RecordUnavailable variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.RecordUnavailable`; omitting it is a compile error

#### Scenario: All four Reason values are constructible

- **WHEN** the test suite enumerates `EmbedUi.RecordUnavailable.Reason.values()`
- **THEN** the array contains exactly `NotFound`, `Blocked`, `Detached`, `Unknown` in this order (stable for `ordinal`-based serialization should it ever be needed)

### Requirement: `QuotedEmbedUi` is a sealed interface that bounds quoted-post recursion at the type system

`:data:models` MUST expose `@Immutable sealed interface QuotedEmbedUi` representing inner embeds within `QuotedPostUi`. Allowed variants are: `Empty`, `Images(items: ImmutableList<ImageUi>)`, `Video`, `External`, `QuotedThreadChip`, and `Unsupported(typeUri: String)`. It MUST NOT contain a `Record` variant, preventing recursive quote trees at compile time.

#### Scenario: QuotedEmbedUi has no Record variant

- **WHEN** the project source is searched for `data class Record` or `data object Record` defined as a member of `QuotedEmbedUi`
- **THEN** there SHALL be no match; the recursion bound is a structural property of the type, not a runtime guard

#### Scenario: Inner Images payload is identical to EmbedUi.Images

- **WHEN** a `QuotedEmbedUi.Images` and an `EmbedUi.Images` are constructed from the same fixture `ImagesView`
- **THEN** their `items` fields SHALL be `equals`-equal — wrapper duplication does not extend to payload duplication

### Requirement: `EmbedUi` exposes `RecordOrUnavailable` and `MediaEmbed` marker sealed interfaces

`EmbedUi` MUST expose two nested marker sealed interfaces extending `EmbedUi`:
- `RecordOrUnavailable`: implemented only by `EmbedUi.Record` and `EmbedUi.RecordUnavailable`.
- `MediaEmbed`: implemented only by `EmbedUi.Images`, `EmbedUi.Video`, and `EmbedUi.External`.
These markers exist purely to enforce compile-time bounds on `EmbedUi.RecordWithMedia` slots.

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

`EmbedUi` MUST expose an `@Immutable` `RecordWithMedia(val record: EmbedUi.RecordOrUnavailable, val media: EmbedUi.MediaEmbed)` variant. By using marker interfaces, nesting of `RecordWithMedia` or invalid variants in either slot is prevented at compile time. `RecordWithMedia` itself MUST NOT implement `RecordOrUnavailable` or `MediaEmbed`.

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

`:data:models` MUST expose public extension property `val EmbedUi.quotedRecord: QuotedPostUi?` returning `quotedPost` for `EmbedUi.Record` and `(record as? EmbedUi.Record)?.quotedPost` for `EmbedUi.RecordWithMedia`, or `null` otherwise. All downstream feature consumers MUST use this property to resolve embedded quoted posts.

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

### Requirement: `NotificationItemUi` is a sealed Single / Aggregated type in `:data:models`

`:data:models` SHALL expose `@Stable sealed interface NotificationItemUi` with variants: `Single(itemKey, reason, indexedAt, isRead, actors, subjectPost: PostUi?)` and `Aggregated(itemKey, reason, indexedAt, isRead, actors: ImmutableList<AuthorUi>, subjectPost: PostUi?)`. Both variants expose common notification metadata.

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

`:data:models` SHALL expose `NotificationFilter` as a Kotlin `enum class` with exactly the values `All`, `Mentions`, `Reposts`, `Follows`, `Likes`. Each value SHALL expose a public `reasons: ImmutableList<String>?` property that maps to the lexicon `reasons[]` request parameter (null for `All`). The property is `public` because cross-module consumers (`:feature:notifications:impl`) need to read it, and `ImmutableList` matches the data-models capability's immutable-collections convention.

#### Scenario: Mentions filter maps to three reason values

- **WHEN** `NotificationFilter.Mentions.reasons` is read
- **THEN** the returned list SHALL equal `["mention", "reply", "quote"]`

### Requirement: `NotificationItemUi` ships fixture factories for previews and tests

`:data:models` SHALL provide a `NotificationItemUiFixtures` object exposing factory functions for `singleLike`, `aggregatedLikes(actorCount)`, `singleFollow`, `aggregatedFollows(actorCount)`, `singleReply`, `singleQuote`, `singleMention`, and at least one fixture per other known reason. Fixtures SHALL be usable from any module that depends on `:data:models` (previews, screenshot tests, unit tests).

#### Scenario: Fixture is consumable from a preview

- **WHEN** a `@Preview` composable in `:designsystem` or `:feature:notifications:impl` references `NotificationItemUiFixtures.aggregatedLikes(3)`
- **THEN** the fixture SHALL return a valid `NotificationItemUi.Aggregated` with three actors and a non-null `subjectPost`

### Requirement: `:data:models` provides a `FeedItemUi` sealed type for feed projections

`:data:models` SHALL expose public `@Stable sealed interface FeedItemUi` with variants: `Single(val post: PostUi)` and `ReplyCluster(val root: PostUi, val parent: PostUi, val leaf: PostUi, val hasEllipsis: Boolean)`. Per-feed metadata remains on individual `PostUi` models rather than on `FeedItemUi`.

#### Scenario: Single variant carries one PostUi

- **WHEN** a feed entry's `feedViewPost.reply` is null (or `replyRef.parent` is `BlockedPost`/`NotFoundPost`)
- **THEN** the mapper produces `FeedItemUi.Single(leaf)` with the leaf's `PostUi` projection

#### Scenario: ReplyCluster variant carries root + parent + leaf

- **WHEN** a feed entry's `feedViewPost.reply.parent` is a renderable `PostView` and `replyRef.root` is a renderable `PostView`
- **THEN** the mapper produces `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis)` where `root`, `parent`, `leaf` are full `PostUi` projections and `hasEllipsis` follows the heuristic in the `feature-feed` capability

#### Scenario: FeedItemUi is exhaustive at compile time

- **WHEN** Kotlin compiles a `when (item: FeedItemUi)` expression in a render dispatch (e.g. `FeedScreen.LoadedFeedContent`)
- **THEN** the compiler SHALL warn if any variant is unhandled — the sealed-interface declaration enables exhaustive `when` checking

### Requirement: `FeedItemUi` exposes a `SelfThreadChain` sealed variant for same-author chains

`FeedItemUi` SHALL include a `@Stable` variant `SelfThreadChain(val posts: ImmutableList<PostUi>)` for chronological same-author thread chains. It MUST have `posts.size >= 2`, identical `author.did` across all posts, order starting from root post to leaf post, and `key == posts.last().id`.

#### Scenario: A 3-post chain has size 3 and ends on the leaf

- **WHEN** the feed mapper produces a `SelfThreadChain` from three consecutive same-author self-replies whose URIs form `root.uri → reply1.uri → reply2.uri`
- **THEN** the resulting `SelfThreadChain.posts` has size 3, `posts[0].id == root.uri`, `posts[1].id == reply1.uri`, `posts[2].id == reply2.uri`, and `key == reply2.uri`.

#### Scenario: All chain posts share the same author DID

- **WHEN** any `SelfThreadChain` value is inspected
- **THEN** `posts.distinctBy { it.author.did }.size == 1` — the chain MUST be pure same-author by construction.

#### Scenario: Sealed-interface exhaustiveness propagates to consumers

- **WHEN** any consumer of `FeedItemUi` is recompiled after `SelfThreadChain` is introduced
- **THEN** every `when (item: FeedItemUi)` block without an `is FeedItemUi.SelfThreadChain ->` branch SHALL fail compilation with a non-exhaustive-when warning treated as an error (Kotlin sealed-interface contract).

### Requirement: `SelfThreadChain` MUST NOT introduce new module-level dependencies

The addition of the `SelfThreadChain` variant SHALL NOT change the dependency graph of `:data:models`. The variant uses only types already exposed by the module: `PostUi`, `kotlinx.collections.immutable.ImmutableList`, `androidx.compose.runtime.Stable`. No new `atproto-*` dependencies, no new Compose dependencies, no Hilt.

#### Scenario: No new dependency lines

- **WHEN** `:data:models/build.gradle.kts` is diffed before/after this change
- **THEN** the `dependencies { ... }` block SHALL be unchanged.
