## MODIFIED Requirements

### Requirement: Embed dispatch in the mapper mirrors PostCard v1 scope

The system's `toEmbedUi` mapping function SHALL produce:

- `EmbedUi.Empty` for `null` (no embed)
- `EmbedUi.Images` for `app.bsky.embed.images#view` (1–4 images)
- `EmbedUi.Video` for `app.bsky.embed.video#view` (per the `feature-feed-video` spec)
- `EmbedUi.External` for `app.bsky.embed.external#view` (per `nubecita-aku`)
- `EmbedUi.Record` for `app.bsky.embed.record#viewRecord` (per `nubecita-6vq`)
- `EmbedUi.RecordUnavailable` for `app.bsky.embed.record#view{NotFound,Blocked,Detached}` and the `Unknown` open-union fallback (per `nubecita-6vq`)
- `EmbedUi.RecordWithMedia` for `app.bsky.embed.recordWithMedia#view` (per `nubecita-umn`)
- `EmbedUi.Unsupported(typeUri = ...)` for any unknown `Unknown`-variant payload from the open union

The `typeUri` field in `EmbedUi.Unsupported` MUST carry the fully-qualified lexicon NSID so PostCard's `PostCardUnsupportedEmbed` can render the friendly-name label per the design-system spec.

This dispatch MUST be exhaustive over the `PostViewEmbedUnion` sealed type. Future lexicon evolution that adds a new embed type MUST be handled in the same change that adds the variant — the sealed type makes this a compile error otherwise.

The construction of `EmbedUi.Images` / `EmbedUi.Video` / `EmbedUi.External` MUST go through three wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) shared between the top-level dispatch and the media-side dispatch in `RecordWithMediaView.toEmbedUiRecordWithMedia`. **These helpers — and the top-level `toEmbedUi` dispatch itself — live in the `:core:feed-mapping` module per the `core-feed-mapping` capability spec.** `:feature:feed:impl/data/FeedViewPostMapper.kt` consumes them via `implementation(project(":core:feed-mapping"))`; it MUST NOT redeclare any wrapper-construction helper inline. Inline duplicated construction at any call site is forbidden — it would risk drift (e.g. an `aspectRatio` calculation tweak applied in one path and forgotten in the other) and would defeat the cross-feature single-source-of-truth contract that lets `:feature:postdetail:impl`'s `PostThreadMapper` produce identical embed projections.

#### Scenario: Images embed maps to EmbedUi.Images

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedImagesView` carrying two image items
- **THEN** the result is `EmbedUi.Images(items)` where `items` is an `ImmutableList` of two `ImageUi`, each populated from the corresponding source image (url, altText, aspectRatio)

#### Scenario: External embed maps to EmbedUi.External with precomputed domain

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedExternalView` carrying `external.uri = "https://www.example.com/article"`
- **THEN** the result is `EmbedUi.External(uri = "https://www.example.com/article", domain = "example.com", title, description, thumbUrl)` — the `www.` prefix is stripped at mapping time so the render layer never re-parses the URI

#### Scenario: Record embed (viewRecord) maps to EmbedUi.Record with a fully populated QuotedPostUi

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedRecordView` whose `record` is a `RecordViewRecord` carrying author + uri + cid + a decodable `value` containing text + createdAt
- **THEN** the result is `EmbedUi.Record(quotedPost)` where `quotedPost.uri == record.uri.raw`, `quotedPost.cid == record.cid.raw`, `quotedPost.author` is the mapped `AuthorUi`, `quotedPost.text` is the decoded post text, `quotedPost.createdAt` is the parsed RFC3339 instant, `quotedPost.facets` is the (possibly empty) facet list, and `quotedPost.embed` is the inner-embed mapping per the separate requirement below

#### Scenario: Record embed unavailable variants map to EmbedUi.RecordUnavailable with the matching Reason

- **WHEN** `toEmbedUi` is called with a `RecordView` whose `record` union member is `RecordViewNotFound` / `RecordViewBlocked` / `RecordViewDetached` respectively
- **THEN** the result is `EmbedUi.RecordUnavailable(Reason.NotFound)` / `Reason.Blocked` / `Reason.Detached` accordingly

#### Scenario: RecordWithMedia embed maps to EmbedUi.RecordWithMedia with composed record + media

- **WHEN** `toEmbedUi` is called with a `PostViewEmbedUnion.AppBskyEmbedRecordWithMediaView` whose `record` resolves to a `RecordViewRecord` (decodable) and whose `media` is an `ImagesView` carrying two image items
- **THEN** the result is `EmbedUi.RecordWithMedia(record = EmbedUi.Record(quotedPost), media = EmbedUi.Images(items))` where the inner `quotedPost` is constructed by the same `RecordViewRecord.toEmbedUiRecord` helper used by the top-level `Record` arm, and `items` is the same `ImmutableList<ImageUi>` the top-level `Images` arm would produce — single source of truth for both wrapper constructions

#### Scenario: RecordWithMedia with malformed media falls through to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with a `RecordWithMediaView` whose `media` is a `VideoView` with a blank `playlist` field (or whose `media` is the open-union `Unknown` variant)
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")` — the whole composition falls through. The record side is NOT rendered standalone; half-rendering a recordWithMedia loses the post's communicative intent.

#### Scenario: RecordWithMedia with unavailable record still renders the media

- **WHEN** `toEmbedUi` is called with a `RecordWithMediaView` whose `record.record` is `RecordViewNotFound` and whose `media` is a valid `ImagesView`
- **THEN** the result is `EmbedUi.RecordWithMedia(record = EmbedUi.RecordUnavailable(Reason.NotFound), media = EmbedUi.Images(items))` — the unavailable record degrades gracefully (per its lexicon-defined `viewNotFound` shape) while the media still renders

#### Scenario: Unknown embed maps to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with `PostViewEmbedUnion.Unknown` carrying a `$type` of `"app.bsky.embed.somethingNew"`
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`

#### Scenario: Helpers are sourced from `:core:feed-mapping`

- **WHEN** `:feature:feed:impl/data/FeedViewPostMapper.kt` is inspected
- **THEN** the `toEmbedUi` dispatch and the three wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) are imported from `net.kikin.nubecita.core.feedmapping.*` rather than declared inline; the file's own declarations are limited to feed-specific concerns (cursor handling, `repostedBy` extraction from `feedViewPost.reason`, etc.)
