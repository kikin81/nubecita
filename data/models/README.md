# `:data:models`

Plain-Kotlin UI model types consumed by `:designsystem` composables and produced by `:feature:*:impl` mapper layers. Hosts the canonical "what does the UI need to render this entity" data classes — currently `PostUi` and its supporting types (`AuthorUi`, `EmbedUi`, `ImageUi`, `PostStatsUi`, `ViewerStateUi`).

Established by openspec change `add-postcard-component` (capability `data-models`).

## Conventions

This module follows three rules. They keep `:data:models` lean and prevent the "DTO leak" anti-pattern that would couple every feature module to AT Protocol response shapes.

### 1. No service abstractions

`:data:models` MUST NOT depend on `atproto:runtime`, `atproto:oauth`, `atproto:compose`, or `atproto:compose-material3`. It MUST NOT define classes that mirror response envelopes (`PostView`, `FeedViewPost`), pagination cursors, or error envelopes from the AT Protocol lexicon. Service-layer types live with the mapper that produces them.

### 2. AT Protocol wire-data primitives ARE allowed

`atproto:models` is the one `atproto:*` dependency this module declares. Lexicon-defined primitive types — `Facet`, `FacetByteSlice`, `FacetMention`, `FacetLink`, `FacetTag` (and any future `Datetime`-like primitive that lands in `:models`) — MAY appear directly as field types on UI models. They're closer to `String` than to `PostView`; mirroring them as our own types would be pure copy-paste maintenance.

Non-`models` typed wrappers (`Did`, `Handle`, `AtUri` from `atproto:runtime`) are NOT used here — the UI doesn't need wire-level type safety on these. Mappers unwrap them to their `.raw` strings before constructing UI models.

### 3. Compose-stable, immutable, no Compose UI

Every model is `@Stable` (or `@Immutable` for sealed types whose variants are themselves stable). Every collection field uses `kotlinx.collections.immutable.ImmutableList<T>` (or sibling immutable types). The only `androidx.compose.*` dependency is `compose-runtime` for the stability annotations — never `compose-ui`, never `material3`. UI primitives belong in `:designsystem`; this module is data shapes.

## Adding a new UI model

When adding a new entity (e.g., `ProfileUi`, `NotificationUi`, `ThreadUi`):

1. Add the data class under `net.kikin.nubecita.data.models` in `src/main/kotlin/.../data/models/`.
2. Annotate `@Stable` (data class with mutable-looking fields) or `@Immutable` (truly frozen, e.g. sealed-type variants).
3. Use `ImmutableList<T>` for any collection field.
4. If a field needs an AT Protocol wire-data primitive, import from `atproto:models` only.
5. Add a fixture in `src/test/kotlin/.../data/models/` (mirroring `PostUiFixtures.kt`) so downstream `:designsystem` previews and feature unit tests can construct cheap fakes.
6. Update this README's intro line listing the model types.

## Why a separate module rather than colocating in `:designsystem`

Feature modules (`:feature:feed:impl` etc.) need to construct UI models without depending on `:designsystem` (which would drag the entire Compose UI graph onto their compile classpath, including atproto-compose-material3, Coil, slack-compose-lints, etc.). A standalone `:data:models` breaks the dependency cycle. See `openspec/changes/add-postcard-component/design.md` Decision 1 for the full rationale.
