## MODIFIED Requirements

### Requirement: Embed dispatch in the mapper mirrors PostCard v1 scope

The system's `toEmbedUi` mapping function SHALL produce:

- `EmbedUi.Empty` for `null` (no embed)
- `EmbedUi.Images` for `app.bsky.embed.images#view` (1–4 images)
- `EmbedUi.Video` for `app.bsky.embed.video#view` (per the `feature-feed-video` spec)
- `EmbedUi.External` for `app.bsky.embed.external#view` (per `nubecita-aku`)
- `EmbedUi.Record` for `app.bsky.embed.record#viewRecord` (per `nubecita-6vq`)
- `EmbedUi.RecordUnavailable` for `app.bsky.embed.record#view{NotFound,Blocked,Detached}` and the `Unknown` open-union fallback (per `nubecita-6vq`)
- `EmbedUi.Unsupported(typeUri = ...)` for every other lexicon embed type, including `app.bsky.embed.recordWithMedia#view` and any unknown `Unknown`-variant payload from the open union

The `typeUri` field in `EmbedUi.Unsupported` MUST carry the fully-qualified lexicon NSID so PostCard's `PostCardUnsupportedEmbed` can render the friendly-name label per the design-system spec.

This dispatch MUST be exhaustive over the `PostViewEmbedUnion` sealed type. When future changes extend `EmbedUi` (e.g., `EmbedUi.RecordWithMedia` per `nubecita-umn`), the mapper MUST be updated in the same change that adds the variant — the sealed type makes this a compile error otherwise.

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

#### Scenario: Unknown embed maps to EmbedUi.Unsupported

- **WHEN** `toEmbedUi` is called with `PostViewEmbedUnion.Unknown` carrying a `$type` of `"app.bsky.embed.somethingNew"`
- **THEN** the result is `EmbedUi.Unsupported(typeUri = "app.bsky.embed.somethingNew")`

## ADDED Requirements

### Requirement: Inner-embed mapping for quoted posts is bounded at one level by the type system

The system MUST expose an internal mapper extension `RecordViewRecordEmbedsUnion?.toQuotedEmbedUi(): QuotedEmbedUi` that dispatches the quoted post's `embeds` list (the lexicon allows multiple but in practice carries 0–1) and produces:

- `QuotedEmbedUi.Empty` for `null` (no inner embed) or an empty list — the mapper consumes `embeds.firstOrNull()`.
- `QuotedEmbedUi.Images` for an inner `ImagesView` (same payload as the parent `EmbedUi.Images` mapping).
- `QuotedEmbedUi.Video` for an inner `VideoView` whose `playlist` is non-blank; otherwise `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.video")`.
- `QuotedEmbedUi.External` for an inner `ExternalView` (with the same precomputed `domain` as the parent `EmbedUi.External` mapping; the existing `displayDomainOf` helper is reused).
- `QuotedEmbedUi.QuotedThreadChip` for an inner `RecordView` — this is the recursion-bound sentinel; the mapper does NOT recurse into the doubly-quoted post.
- `QuotedEmbedUi.Unsupported("app.bsky.embed.recordWithMedia")` for an inner `RecordWithMediaView` (out of scope; tracked under `nubecita-umn`).
- `QuotedEmbedUi.Unsupported(typeUri)` for the `Unknown` open-union member, carrying the wire `$type`.

To avoid logic duplication between the parent and inner mappers, the per-variant payload construction MUST be extracted into private helpers (e.g. `ImagesView.toImageUiList()`, `VideoView.toVideoPayload()`) called from both dispatch sites. Wrapper-type duplication (`EmbedUi.Images` vs `QuotedEmbedUi.Images`) is acceptable because the underlying payloads (`ImmutableList<ImageUi>`, etc.) are shared.

#### Scenario: Inner Images embed maps to QuotedEmbedUi.Images

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is an `ImagesView` with three image items
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Images` with three `ImageUi` entries, structurally equal to what the parent `toEmbedUi` would have produced for the same `ImagesView`

#### Scenario: Inner Video embed with non-blank playlist maps to QuotedEmbedUi.Video

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `VideoView` with `playlist.raw == "https://video.bsky.app/.../playlist.m3u8"` and `aspectRatio.width=1920, aspectRatio.height=1080`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Video` with `playlistUrl == "https://video.bsky.app/.../playlist.m3u8"`, `aspectRatio == 1920f / 1080f`, `durationSeconds == null`

#### Scenario: Inner Video embed with blank playlist falls through to QuotedEmbedUi.Unsupported

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `VideoView` with `playlist.raw == ""`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.video")` — same fallthrough rule as the parent video mapping

#### Scenario: Inner Record embed produces the QuotedThreadChip sentinel (one-level recursion bound)

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is itself a `RecordView` (a quote-of-a-quote)
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.QuotedThreadChip` — the mapper does NOT recurse and does NOT attempt to populate a nested `QuotedPostUi`

#### Scenario: Inner RecordWithMedia maps to QuotedEmbedUi.Unsupported with the lexicon URI

- **WHEN** the mapper processes a `RecordViewRecord` whose `embeds.firstOrNull()` is a `RecordWithMediaView`
- **THEN** `QuotedPostUi.embed` is `QuotedEmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")`

### Requirement: A malformed quoted record never drops the parent post

The system MUST decode a `RecordViewRecord.value: JsonObject` defensively. If the embedded post record cannot be decoded as a valid `app.bsky.feed.post` (missing required `text` / `createdAt`, type-incompatible value), OR if the decoded `createdAt` is not a parseable RFC3339 timestamp, the mapper MUST produce `EmbedUi.RecordUnavailable(Reason.Unknown)` for that embed slot. The parent post MUST still map to a non-null `PostUi` — a malformed quoted record is NEVER a reason to drop the parent post from the feed.

This contract preserves the existing `FeedViewPostMapper` total-over-the-response-shape rule from the parent-post decoding path — both layers use the same `runCatching { ... }.getOrNull()` shape against the same shared `recordJson` instance.

#### Scenario: Quoted post with malformed value yields RecordUnavailable.Unknown but the parent still maps

- **WHEN** the mapper processes a parent `FeedViewPost` whose embed is a `RecordView` whose `RecordViewRecord.value` is a `JsonObject` that lacks the required `text` field
- **THEN** the parent `toPostUiOrNull()` returns a non-null `PostUi` whose `embed == EmbedUi.RecordUnavailable(Reason.Unknown)`

#### Scenario: Quoted post with malformed createdAt yields RecordUnavailable.Unknown

- **WHEN** the mapper processes a `RecordView` whose `RecordViewRecord.value.createdAt` decodes as the string `"not-a-date"` (passes JSON decode but fails `Instant.parse`)
- **THEN** `EmbedUi.RecordUnavailable(Reason.Unknown)` is produced; the parent post is unaffected
