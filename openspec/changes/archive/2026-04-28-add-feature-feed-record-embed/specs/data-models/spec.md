## ADDED Requirements

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
