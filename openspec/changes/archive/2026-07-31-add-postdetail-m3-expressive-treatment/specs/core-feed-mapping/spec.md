## ADDED Requirements

### Requirement: `:core:feed-mapping` is the single owner of atproto-wire-type → UI-model conversion helpers

The system SHALL host the shared atproto-wire-type → UI-model conversion primitives in a new `:core:feed-mapping` Android library module. The module MUST expose at minimum:

- `toPostUiCore(postView: PostView): PostUi?` — the per-post projection core. Returns `null` when the embedded `record` JSON cannot be decoded as a well-formed `app.bsky.feed.post` record (mirrors the existing `FeedViewPost.toPostUiOrNull` shape in `:feature:feed:impl`).
- `toAuthorUi(profile: ProfileViewBasic): AuthorUi`
- `toViewerStateUi(viewer: ViewerState?): ViewerStateUi`
- `toEmbedUi(embed: PostViewEmbedUnion?): EmbedUi` — the embed dispatch covering Empty / Images / Video / External / Record / RecordWithMedia / Unsupported, including the placeholder variants (`RecordViewNotFound` / `RecordViewBlocked` / `RecordViewDetached`) the existing feed mapper handles.
- The three private wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) used by both the top-level dispatch and the `RecordWithMediaView` media-side dispatch.

The helpers MUST be pure (no I/O, no Android types, no coroutines), MUST be unit-testable as functions against fixture JSON, and MUST NOT depend on `:feature:feed:impl` or `:feature:postdetail:impl` (dependency direction is one-way: features depend on `:core:feed-mapping`).

#### Scenario: Both consumers compile against the shared module

- **WHEN** `:feature:feed:impl/data/FeedViewPostMapper.kt` and `:feature:postdetail:impl/data/PostThreadMapper.kt` are inspected
- **THEN** both modules' `build.gradle.kts` declares `implementation(project(":core:feed-mapping"))`, both source files import the shared helpers (`toPostUiCore`, `toEmbedUi`, etc.), and neither file contains an inline duplicate of the embed-dispatch `when` block

#### Scenario: Helpers are pure and Android-free

- **WHEN** the `:core:feed-mapping` build classpath is inspected
- **THEN** the module declares no `androidx.*` dependencies and no `android.*` runtime references; helpers can be exercised under plain JVM unit tests against fixture JSON

#### Scenario: Embed dispatch is the single source of truth

- **WHEN** the same `app.bsky.embed.images#view` fixture is run through the feed and post-detail mappers
- **THEN** both yield identical `EmbedUi.Images(items)` values — bit-equal when serialized — because both delegate to `:core:feed-mapping`'s `toEmbedUi`

### Requirement: Feed timeline rendering is byte-for-byte unchanged through the extraction

The extraction of helpers from `:feature:feed:impl` to `:core:feed-mapping` SHALL preserve `FeedViewPostMapper`'s observable behavior. The `:feature:feed:impl` screenshot-test suite (with the same fixtures it had before this change) MUST continue to pass without baseline regeneration. The `:feature:feed:impl` unit-test suite MUST continue to pass.

This is the regression contract: the extraction is a pure relocation. Same inputs produce same outputs from the same code, just compiled in a different module.

#### Scenario: Feed screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after the extraction merges
- **THEN** every existing baseline matches without regeneration — no fixture file is modified by this change

#### Scenario: Feed unit tests unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after the extraction
- **THEN** the existing unit-test suite passes without modification of test fixtures or assertions
