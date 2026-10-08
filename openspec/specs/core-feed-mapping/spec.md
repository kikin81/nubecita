# core-feed-mapping Specification

## Purpose
`:core:feed-mapping` is the single owner of atproto wire-type → UI-model conversion (`PostUi`, `EmbedUi`, `AuthorUi`), extracted so the feed and post-detail surfaces share one mapper rather than each re-deriving projections. The extraction is behaviour-preserving: feed timeline rendering is byte-for-byte unchanged through it.
## Requirements
### Requirement: `:core:feed-mapping` is the single owner of atproto-wire-type → UI-model conversion helpers

The system SHALL expose top-level pure mapping functions in `:core:feed-mapping` package `net.kikin.nubecita.core.feedmapping`: `toPostUiCore`, `toAuthorUi`, `toViewerStateUi`, `toEmbedUi`, and media wrapper constructors. Helpers MUST be pure, unit-testable against fixture JSON, and MUST NOT depend on `:feature:feed:impl` or `:feature:postdetail:impl`.

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

Extracting helpers from `:feature:feed:impl` to `:core:feed-mapping` SHALL preserve `FeedViewPostMapper` observable behavior. The `:feature:feed:impl` screenshot-test suite and unit-test suite MUST continue to pass without baseline regeneration.

#### Scenario: Feed screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after the extraction merges
- **THEN** every existing baseline matches without regeneration — no fixture file is modified by this change

#### Scenario: Feed unit tests unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after the extraction
- **THEN** the existing unit-test suite passes without modification of test fixtures or assertions
