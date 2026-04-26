## ADDED Requirements

### Requirement: `FeedViewModel` is the canonical entry point for the Following timeline

The system SHALL expose `net.kikin.nubecita.feature.feed.impl.FeedViewModel` as the only `ViewModel` that orchestrates `app.bsky.feed.getTimeline`. `FeedViewModel` MUST extend `MviViewModel<FeedState, FeedEvent, FeedEffect>` and MUST be `@HiltViewModel`-annotated. Every screen rendering the Following timeline (today: the placeholder; tomorrow: the real `FeedScreen` from `nubecita-1d5`) MUST consume this ViewModel via `hiltViewModel()` rather than instantiating its own.

#### Scenario: A screen consumes FeedViewModel

- **WHEN** the Nav3 entry for `Feed` composes its content
- **THEN** the composable obtains `FeedViewModel` via `hiltViewModel()` and forwards `FeedEvent`s through `viewModel::handleEvent`

#### Scenario: No second timeline ViewModel exists

- **WHEN** the source tree is searched for classes calling `FeedService.getTimeline` (transitively or directly)
- **THEN** the only call site SHALL be `DefaultFeedRepository`

### Requirement: `FeedRepository` is the only layer that calls `FeedService` directly

The system SHALL expose an `internal interface FeedRepository` in `:feature:feed:impl` with at minimum a single method `suspend fun getTimeline(cursor: String?, limit: Int = TIMELINE_PAGE_LIMIT): Result<TimelinePage>`. The `DefaultFeedRepository` implementation MUST be the only class in `:feature:feed:impl` that imports `io.github.kikin81.atproto.app.bsky.feed.FeedService`. `FeedViewModel` MUST inject the interface, never the concrete class. The interface and its implementation MUST stay `internal` to `:feature:feed:impl` until a second consumer (post detail, search) requires the same fetch surface — at that point a follow-on change promotes them to a `:core:feed` module.

#### Scenario: VM injects the interface

- **WHEN** `FeedViewModel`'s constructor is inspected
- **THEN** it MUST declare a `private val feedRepository: FeedRepository` parameter (interface type) and MUST NOT declare `DefaultFeedRepository` or `FeedService`

#### Scenario: Single import of FeedService

- **WHEN** the project source is grepped for `import io.github.kikin81.atproto.app.bsky.feed.FeedService`
- **THEN** the only match in production code SHALL be `DefaultFeedRepository.kt`

### Requirement: `FeedViewPostMapper` is pure and total over the response shape

The system SHALL expose top-level `internal` mapping functions in `:feature:feed:impl` package `data` (no class wrapper):

- `internal fun FeedViewPost.toPostUiOrNull(): PostUi?`
- `internal fun PostViewEmbedUnion?.toEmbedUi(): EmbedUi`
- Any helpers required to extract `text` / `facets` from `PostView.record: JsonObject`.

`toPostUiOrNull` MUST return `null` for inputs whose embedded `record` JSON cannot be decoded as a well-formed `app.bsky.feed.post` record (missing required fields, malformed types). It MUST NOT throw. Every spec-conforming `FeedViewPost` MUST yield a non-null `PostUi`. The mapper MUST NOT perform I/O, MUST NOT depend on Android types, and MUST be unit-testable as pure functions against fixture JSON.

#### Scenario: Spec-conforming post produces a non-null PostUi

- **WHEN** `toPostUiOrNull()` is called on a fixture `FeedViewPost` whose `post.record` is a well-formed `app.bsky.feed.post` JSON
- **THEN** the result is a non-null `PostUi` whose `text` matches the record's `text` field and whose `facets` matches the decoded facet array

#### Scenario: Malformed record returns null

- **WHEN** `toPostUiOrNull()` is called on a `FeedViewPost` whose `post.record` is missing the required `text` field or contains a type-incompatible value
- **THEN** the function returns `null` and does NOT throw

#### Scenario: Repository drops nulls

- **WHEN** `DefaultFeedRepository.getTimeline` decodes a response containing one well-formed and one malformed `FeedViewPost`
- **THEN** the returned `TimelinePage.posts` contains exactly one `PostUi` (the well-formed one) and the call returns `Result.success`

### Requirement: Pagination cursor advances only on successful append

The system SHALL preserve `FeedState.nextCursor` on append failure. After a successful `LoadMore`, the VM MUST update `nextCursor` to the cursor returned by the repository (which may be `null` to signal end-of-feed). On `LoadMore` failure (the repository returns `Result.failure`), the VM MUST leave `nextCursor` unchanged so a subsequent retry can re-attempt with the same cursor. On a successful response with `cursor == null`, the VM MUST set `endReached = true` and treat further `LoadMore` events as no-ops.

#### Scenario: Successful append advances the cursor

- **WHEN** `FeedState.nextCursor == "page-3"` and `LoadMore` succeeds with response cursor `"page-4"`
- **THEN** `FeedState.nextCursor` becomes `"page-4"` and `endReached == false`

#### Scenario: Failed append preserves the cursor

- **WHEN** `FeedState.nextCursor == "page-3"` and `LoadMore` fails (e.g. network exception)
- **THEN** `FeedState.nextCursor` remains `"page-3"`, `loadStatus` returns to `Idle`, and a `FeedEffect.ShowError` is emitted

#### Scenario: End-of-feed disables LoadMore

- **WHEN** the most recent successful page returned `cursor == null` and the VM has set `endReached = true`
- **THEN** subsequent `LoadMore` events SHALL NOT call the repository and SHALL NOT change state

### Requirement: Embed dispatch in the mapper mirrors PostCard v1 scope

The system's `toEmbedUi` mapping function SHALL produce:

- `EmbedUi.Empty` for `null` (no embed)
- `EmbedUi.Images` for `app.bsky.embed.images#view` (1–4 images)
- `EmbedUi.Unsupported(typeUri = ...)` for every other lexicon embed type, including `app.bsky.embed.external#view`, `app.bsky.embed.record#view`, `app.bsky.embed.video#view`, `app.bsky.embed.recordWithMedia#view`, and any unknown `Unknown`-variant payload from the open union

The `typeUri` field in `EmbedUi.Unsupported` MUST carry the fully-qualified lexicon NSID so PostCard's `PostCardUnsupportedEmbed` can render the friendly-name label per the design-system spec.

This dispatch MUST be exhaustive over the `PostViewEmbedUnion` sealed type. When future changes extend `EmbedUi` (e.g., `EmbedUi.External` per `nubecita-aku`, `EmbedUi.Record` per `nubecita-6vq`), the mapper MUST be updated in the same change that adds the variant — the sealed type makes this a compile error otherwise.

#### Scenario: Images embed maps to EmbedUi.Images

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedImagesView` carrying two image items
- **THEN** the result is `EmbedUi.Images(items)` where `items` is an `ImmutableList` of two `ImageUi`, each populated from the corresponding source image (url, altText, aspectRatio)

#### Scenario: External embed maps to EmbedUi.Unsupported with the lexicon URI

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedExternalView`
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.external")`

#### Scenario: Unknown embed maps to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with `PostViewEmbedUnion.Unknown` carrying a `$type` of `"app.bsky.embed.somethingNew"`
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`

### Requirement: `FeedState` exposes a sealed `FeedLoadStatus` for mutually-exclusive load modes

The system's `FeedState` MUST declare a single field `loadStatus: FeedLoadStatus` of type `sealed interface FeedLoadStatus` with at minimum the variants `Idle`, `InitialLoading`, `Refreshing`, `Appending`, and `InitialError(error: FeedError)`. The state MUST NOT use independent boolean fields (`isInitialLoading`, `isRefreshing`, `isAppending`) for these modes — the type system MUST make invalid combinations unrepresentable. `posts: ImmutableList<PostUi>`, `nextCursor: String?`, and `endReached: Boolean` remain flat fields on the state per their independent semantics.

#### Scenario: Initial load transitions through InitialLoading

- **WHEN** `Load` is dispatched on a freshly-constructed VM
- **THEN** `FeedState.loadStatus` transitions `Idle → InitialLoading → Idle` on success (or `Idle → InitialLoading → InitialError(...)` on failure)

#### Scenario: Refresh and append never coexist

- **WHEN** the VM is observed at any point during its lifecycle
- **THEN** `loadStatus` SHALL be exactly one of {`Idle`, `InitialLoading`, `Refreshing`, `Appending`, `InitialError`} — there is no representable state where the VM is both refreshing and appending simultaneously

### Requirement: Initial-load error is sticky in state; refresh and append errors are effects

The system's VM MUST set `loadStatus = FeedLoadStatus.InitialError(error)` only when initial-load fails and `posts.isEmpty()`. The host screen renders a full-screen retry layout against this sticky state. Refresh and append failures (when `posts` is non-empty) MUST set `loadStatus` back to `Idle` and emit `FeedEffect.ShowError(error)` for snackbar display. The `posts` list MUST be preserved across refresh / append failures.

#### Scenario: Initial-load failure populates InitialError

- **WHEN** the VM is fresh (`posts.isEmpty()`) and `Load` dispatch's repository call fails
- **THEN** `loadStatus = FeedLoadStatus.InitialError(error)`, `posts.isEmpty()`, and no `FeedEffect.ShowError` is emitted (the sticky state IS the error display)

#### Scenario: Refresh failure with existing data emits a snackbar effect

- **WHEN** `posts` is non-empty and `Refresh` dispatch's repository call fails
- **THEN** `loadStatus` returns to `Idle`, `posts` is preserved unchanged, and exactly one `FeedEffect.ShowError(error)` is emitted

#### Scenario: Append failure preserves the cursor and emits a snackbar effect

- **WHEN** `posts` is non-empty, `nextCursor != null`, and `LoadMore` dispatch's repository call fails
- **THEN** `loadStatus` returns to `Idle`, `posts` is preserved, `nextCursor` is preserved, and exactly one `FeedEffect.ShowError(error)` is emitted

### Requirement: `FeedEvent` declares the full screen-interaction surface from day one

The system's `FeedEvent` sealed interface MUST include `OnPostTapped`, `OnAuthorTapped`, `OnLikeClicked`, `OnRepostClicked`, `OnReplyClicked`, and `OnShareClicked` variants from the initial implementation, even before the write-path follow-on ticket lands. The VM MAY handle the tap / author events by emitting `FeedEffect.NavigateToPost` / `NavigateToAuthor` and MUST handle the like / repost / reply / share events as no-ops in this initial implementation (no state mutation, no repository call, no effect).

The shape of these events is acknowledged-as-illusory lock-in: the write-path follow-on is allowed to amend the contract if it discovers the surface is wrong (e.g., needs optimistic UI state, undo support, confirm-on-failure follow-up events). The justification is that `nubecita-1d5` (the screen ticket) can wire `PostCallbacks` to dispatch real event names instead of `TODO` placeholders, regardless of when the write path lands.

#### Scenario: Like dispatch is a no-op on state and repository

- **WHEN** `OnLikeClicked(post)` is dispatched
- **THEN** `FeedState.posts` does NOT change, no repository call is made, and no `FeedEffect` is emitted

#### Scenario: PostTap emits a navigation effect

- **WHEN** `OnPostTapped(post)` is dispatched
- **THEN** exactly one `FeedEffect.NavigateToPost(post)` is emitted and no state field changes
