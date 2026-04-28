# data-models Specification

## Purpose
TBD - created by archiving change add-postcard-component. Update Purpose after archive.
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

The module MUST permit AT Protocol wire-data primitive types as field types on UI models. Specifically: `api(libs.atproto.models)` is the only `atproto:*` dependency the module SHALL declare, and the lexicon-defined primitive types it exposes — currently `Facet`, `Did`, `Handle`, `AtUri`, `Datetime` — MUST be usable directly as field types without an intermediate nubecita-side mirror class. This permission applies ONLY to lexicon primitive values (typed wrappers around strings or simple structs); higher-level abstractions (`PostView`, `FeedViewPost`, response envelopes, paginated cursors) MUST NOT be used.

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

The module MUST define `data class PostUi` with the following fields, all stable, no nullability except where noted, and ordered as listed below for code-review consistency:

- `id: String` — stable identity for `LazyColumn` keying
- `author: AuthorUi`
- `createdAt: Instant` (kotlinx-datetime)
- `text: String`
- `facets: ImmutableList<Facet>`
- `embed: EmbedUi` — sealed type; `Empty`, `Images`, or `Unsupported` in v1
- `stats: PostStatsUi` — reply / repost / like / quote counts
- `viewer: ViewerStateUi` — current-user-specific flags (isLikedByViewer, isRepostedByViewer)
- `repostedBy: String?` — display name of the reposter when this post appears in the feed via someone's repost (null otherwise)

Supporting types MUST be defined in the same module: `AuthorUi`, `EmbedUi` (sealed), `ImageUi`, `PostStatsUi`, `ViewerStateUi`.

#### Scenario: Constructing a minimal PostUi for a preview

- **WHEN** test code calls `PostUi(id = "p1", author = AuthorUi(...), createdAt = Clock.System.now(), text = "hello", facets = persistentListOf(), embed = EmbedUi.Empty, stats = PostStatsUi(), viewer = ViewerStateUi(), repostedBy = null)`
- **THEN** the construction compiles, no field is missing a default that would force the test to provide irrelevant data, and PostCard renders it cleanly.

#### Scenario: The embed slot dispatches on a sealed type

- **WHEN** PostCard's body executes `when (post.embed)`
- **THEN** the compiler enforces exhaustiveness across `EmbedUi.Empty`, `EmbedUi.Images`, `EmbedUi.Unsupported`. Adding a new variant in a future change (e.g. `EmbedUi.External`) surfaces as a compile error at every dispatch site, naming the work needed.

### Requirement: `EmbedUi` exposes a `Video` variant for `app.bsky.embed.video#view`

The `EmbedUi` sealed interface in `:data:models` MUST expose a `Video` data class variant carrying:

- `posterUrl: String?` — fully-qualified URL to the JPEG/WebP poster, or `null` when the lexicon's `view` form omits the optional `thumbnail` field. The render layer falls back to a gradient placeholder when null.
- `playlistUrl: String` — fully-qualified URL to the HLS .m3u8 playlist. Required by the lexicon `view` form; the mapper falls through to `EmbedUi.Unsupported` when absent.
- `aspectRatio: Float` — width / height ratio from the lexicon (e.g. `1.777f` for 16:9). Used to size the poster + PlayerView surface. The mapper supplies a 16:9 fallback (`1.777f`) when the lexicon omits the optional `aspectRatio` field, since the render layer needs a stable measurement before the poster loads.
- `durationSeconds: Int?` — duration in seconds, or `null` when not available. **The `app.bsky.embed.video#view` lexicon does NOT currently expose a duration field** (verified against the upstream Bluesky lexicon; only `cid`, `playlist`, `thumbnail`, `aspectRatio`, `presentation`, `alt` are present). The mapper SHALL pass `null` for v1; the field is reserved for a future phase that sources duration either from a lexicon evolution or from the HLS manifest's `EXT-X-PLAYLIST-TYPE: VOD` segments after the player loads. Render layer renders the duration chip ONLY when this field is non-null.
- `altText: String?` — optional alt-text for accessibility surfaces.

The `EmbedUi` sealed interface remains `@Immutable` (the convention `EmbedUi` already follows — variants inherit the annotation and MUST contain only immutable value fields). All five new fields are immutable values, so `EmbedUi.Video` satisfies the existing stability contract without per-variant annotation. A null `EmbedUi` instance is NOT permissible — every well-formed video view yields a non-null `EmbedUi.Video`; a malformed view (missing required `playlist`) yields `EmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` instead.

#### Scenario: Video variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Video`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Video` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `EmbedUi.Video`'s fields are all immutable values) and skip recomposition when the embed reference is structurally equal across recompositions

### Requirement: `EmbedUi` exposes a `Record` variant for `app.bsky.embed.record#viewRecord`

The `EmbedUi` sealed interface in `:data:models` MUST expose a `Record` data class variant carrying a single `quotedPost: QuotedPostUi` field. `QuotedPostUi` is an `@Immutable` data class with the following shape:

- `uri: String` — the quoted post's AT URI (`at://did:.../app.bsky.feed.post/<rkey>`); used as the post-identity key for navigation and as the video-binding identity when the quoted post carries a video.
- `cid: String` — the quoted post's content ID; carried for caching and intent-handoff scenarios that require the exact-version contract the AT Protocol provides via `cid`. The render layer does not display this.
- `author: AuthorUi` — reuses the parent post's author shape; produced via the existing `ProfileViewBasic.toAuthorUi()` extension.
- `createdAt: Instant` — RFC3339 timestamp parsed from the quoted post's record `createdAt`. Mapper falls through to `EmbedUi.RecordUnavailable(Reason.Unknown)` when this is unparseable (per the malformed-record contract below).
- `text: String` — full body text of the quoted post; render layer applies no truncation.
- `facets: ImmutableList<Facet>` — facet annotations (mentions, links, tags) on the quoted post's text; same shape as `PostUi.facets`.
- `embed: QuotedEmbedUi` — the quoted post's own (one-level-bounded) embed; see the separate requirement below.

`QuotedPostUi` MUST NOT carry counts (`likeCount`, `repostCount`, `replyCount`, `quoteCount`) or a `viewer: ViewerStateUi` field — the v1 quoted-post render does not show interaction stats or viewer-relative state. Reserved for future per-variant copy upgrades.

#### Scenario: Record variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.Record`; omitting it is a compile error

#### Scenario: Stable for Compose skipping via the sealed-interface annotation

- **WHEN** a `PostUi` whose `embed is EmbedUi.Record` is passed to a `@Composable` function whose parameter is `EmbedUi`
- **THEN** Compose SHALL treat the parameter as stable (because `EmbedUi` is `@Immutable`-annotated at the sealed-interface level and `QuotedPostUi`'s fields are all immutable values; `QuotedPostUi` carries the `@Immutable` annotation explicitly)

### Requirement: `EmbedUi` exposes a `RecordUnavailable` variant for the unresolved-quote union members

The `EmbedUi` sealed interface MUST expose a `RecordUnavailable` data class variant carrying a single `reason: Reason` field. `Reason` is a nested enum with four members:

- `NotFound` — wire shape was `app.bsky.embed.record#viewNotFound` (quoted post deleted or never existed).
- `Blocked` — wire shape was `app.bsky.embed.record#viewBlocked` (block relationship between viewer and the quoted-post author).
- `Detached` — wire shape was `app.bsky.embed.record#viewDetached` (quoted post's author has detached the quote).
- `Unknown` — open-union `Unknown` member, OR a `viewRecord` whose record `value` failed to decode as a valid `app.bsky.feed.post`, OR whose `createdAt` failed to parse as an RFC3339 timestamp.

The `Reason` field MUST be carried even though v1 renders identical copy for all four — it enables a future per-variant-copy upgrade without breaking the data contract, and supports telemetry / debug logs that need to distinguish the wire-side cause.

#### Scenario: RecordUnavailable variant is part of the sealed hierarchy

- **WHEN** an exhaustive `when (embed: EmbedUi)` is written in the project source
- **THEN** the compiler SHALL require an arm for `EmbedUi.RecordUnavailable`; omitting it is a compile error

#### Scenario: All four Reason values are constructible

- **WHEN** the test suite enumerates `EmbedUi.RecordUnavailable.Reason.values()`
- **THEN** the array contains exactly `NotFound`, `Blocked`, `Detached`, `Unknown` in this order (stable for `ordinal`-based serialization should it ever be needed)

### Requirement: `QuotedEmbedUi` is a sealed interface that bounds quoted-post recursion at the type system

The `:data:models` module MUST expose a sealed interface `QuotedEmbedUi` representing the inner embed of a `QuotedPostUi`. It MUST contain the following variants and MUST NOT contain a `Record` variant:

- `Empty: QuotedEmbedUi` — the quoted post has no embed.
- `Images(items: ImmutableList<ImageUi>): QuotedEmbedUi` — same `ImageUi` payload as `EmbedUi.Images`.
- `Video(posterUrl, playlistUrl, aspectRatio, durationSeconds, altText): QuotedEmbedUi` — same field-set as `EmbedUi.Video`.
- `External(uri, domain, title, description, thumbUrl): QuotedEmbedUi` — same field-set as `EmbedUi.External` (including the precomputed `domain`).
- `QuotedThreadChip: QuotedEmbedUi` — sentinel for the recursion-bounded case (a quoted post that itself quotes another post). Carries no payload; the render layer renders a "View thread" placeholder. Produced by the mapper when the quoted post's `embeds` list contains a `RecordView`.
- `Unsupported(typeUri: String): QuotedEmbedUi` — degradation chip for embed types that are not yet rendered (e.g. `app.bsky.embed.recordWithMedia` while `nubecita-umn` is open).

The deliberate exclusion of a `Record` variant MUST be enforced by the type system, NOT by a runtime check at the dispatch site. The render layer's `when (embed: QuotedEmbedUi)` cannot have a `Record` arm because there's nothing to spell.

`QuotedEmbedUi` MUST be `@Immutable`-annotated at the interface level, mirroring `EmbedUi`'s stability contract.

#### Scenario: QuotedEmbedUi has no Record variant

- **WHEN** the project source is searched for `data class Record` or `data object Record` defined as a member of `QuotedEmbedUi`
- **THEN** there SHALL be no match; the recursion bound is a structural property of the type, not a runtime guard

#### Scenario: Inner Images payload is identical to EmbedUi.Images

- **WHEN** a `QuotedEmbedUi.Images` and an `EmbedUi.Images` are constructed from the same fixture `ImagesView`
- **THEN** their `items` fields SHALL be `equals`-equal — wrapper duplication does not extend to payload duplication

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
