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
