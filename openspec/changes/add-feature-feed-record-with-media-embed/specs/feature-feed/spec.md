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

The construction of `EmbedUi.Images` / `EmbedUi.Video` / `EmbedUi.External` MUST go through three private wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) shared between the top-level dispatch and the new media-side dispatch in `RecordWithMediaView.toEmbedUiRecordWithMedia`. Inline duplicated construction at the two call sites is forbidden — it would risk drift (e.g. an `aspectRatio` calculation tweak applied in one path and forgotten in the other).

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

### Requirement: `FeedScreen` hosts a `FeedVideoPlayerCoordinator` scoped to its composition lifetime and supplies the `videoEmbedSlot` to PostCard

`FeedScreen` MUST host a `FeedVideoPlayerCoordinator` scoped to its composition lifetime — created in the screen's `LoadedFeedContent` `remember { }` block and released via `DisposableEffect.onDispose`. The coordinator is the single owner of the screen's `ExoPlayer` instance per the `feature-feed-video` spec.

For each visible feed item, `LoadedFeedContent` MUST build a `videoEmbedSlot: @Composable (EmbedUi.Video) -> Unit` lambda keyed by `(post.id, coordinator)` and pass it as the `videoEmbedSlot` parameter to `PostCard`. The slot's body invokes `PostCardVideoEmbed(video, postId = post.id, coordinator)` for the autoplay path, falling through to the phase-B static-poster path under `LocalInspectionMode.current`.

For each visible feed item, `LoadedFeedContent` MUST ALSO build a `quotedVideoEmbedSlot: @Composable ((QuotedEmbedUi.Video) -> Unit)?` lambda when `post.embed.quotedRecord != null`, keyed by `(post.embed.quotedRecord!!.uri, coordinator)`, and pass it as the `quotedVideoEmbedSlot` parameter to `PostCard`. The slot is null when the post carries no quoted post (whether top-level or inside a `RecordWithMedia.record`).

The `EmbedUi.quotedRecord` extension property (defined in `:data:models`) is the canonical answer to "where does this post's quoted content live." `LoadedFeedContent` MUST NOT inline the chained-cast pattern (e.g. `(post.embed as? EmbedUi.Record)?.quotedPost?.uri`) at the slot-builder site — single source of truth in the model layer.

#### Scenario: Coordinator is composition-scoped to FeedScreen

- **WHEN** `FeedScreen` enters composition
- **THEN** exactly one `FeedVideoPlayerCoordinator` is constructed (via `remember`) AND a `DisposableEffect` registers `onDispose { coordinator.release() }` so the coordinator's `ExoPlayer` is released when the screen leaves composition

#### Scenario: Slot builder for top-level quoted post

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed is EmbedUi.Record(quotedPost = qp)`
- **THEN** the `quotedVideoSlot` lambda passed to `PostCard.quotedVideoEmbedSlot` is non-null and is `remember`-keyed by `(qp.uri, coordinator)`

#### Scenario: Slot builder for quoted post inside RecordWithMedia

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed is EmbedUi.RecordWithMedia(record = EmbedUi.Record(quotedPost = qp), ...)`
- **THEN** the `quotedVideoSlot` lambda passed to `PostCard.quotedVideoEmbedSlot` is non-null and is `remember`-keyed by `(qp.uri, coordinator)` — same as the top-level case, via the shared `EmbedUi.quotedRecord` extension

#### Scenario: Slot builder is null when the post carries no quoted content

- **WHEN** `LoadedFeedContent` renders a feed item whose `post.embed.quotedRecord` returns null (any of `Empty`, `Images`, `Video`, `External`, `RecordUnavailable`, `RecordWithMedia` whose `record` is `RecordUnavailable`, or `Unsupported`)
- **THEN** `quotedVideoSlot` is null and PostCard does not invoke any quoted-video composable for the item
