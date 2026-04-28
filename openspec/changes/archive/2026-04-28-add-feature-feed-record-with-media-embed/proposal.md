## Why

Bluesky's `app.bsky.embed.recordWithMedia` is a composite embed — a quoted post on the bottom and a media embed (images / video / external link card) on top. Common in newsy "screenshot of the article + the journalist's post quoting it" content and in any context where a user wants to attach their own media to a quote-post. Today the `FeedViewPostMapper` routes `RecordWithMediaView` to `EmbedUi.Unsupported`, so every such post in the user's timeline degrades to a "Unsupported embed: quoted post with media" chip — visible breakage for a meaningfully common embed shape.

This was the v3 gate in `2026-04-25-postcard-embed-scope-v1.md`, explicitly blocked on its constituent parts:

> Cannot ship before BOTH the record (v2) AND images-or-video tiers exist.

Both blockers cleared (`nubecita-6vq` shipped record / quoted-post; `nubecita-xsu` shipped video / images precedes that). 6vq's primitives were factored cleanly enough that the bd issue calls this composition work out as small:

> Renderer is just a Column/Row composition of the existing embed renderers; little new code if the Record + Images/Video ones are well-factored.

This change closes the last v1.x embed gap and makes nubecita's embed dispatch reach lexicon-feature parity with the official Bluesky Android client.

## What Changes

- **MODIFIED capability** `data-models` — adds two tiny marker sealed interfaces (`EmbedUi.RecordOrUnavailable` and `EmbedUi.MediaEmbed`) that the existing `Record` / `RecordUnavailable` / `Images` / `Video` / `External` variants implement. Adds the new `EmbedUi.RecordWithMedia(record: RecordOrUnavailable, media: MediaEmbed)` variant. **No payload-side duplication** — the existing data classes are reused verbatim, just declared as implementing the relevant marker. Compile-time bounds prevent (a) `RecordWithMedia` inside another `RecordWithMedia` (no marker implemented), (b) `Images`/`Video`/`External` in the record slot, (c) `Record`/`RecordUnavailable` in the media slot, (d) `Empty`/`Unsupported` in either slot. Adds an `EmbedUi.quotedRecord: QuotedPostUi?` extension property that centralizes "where do quoted posts hide" — used by both `FeedScreen`'s slot builder and the `videoBindingFor` coordinator helper.
- **MODIFIED capability** `feature-feed` — `FeedViewPostMapper.toEmbedUi`'s `RecordWithMediaView` arm is replaced from a fall-through `Unsupported` to a real dispatch via a new `RecordWithMediaView.toEmbedUiRecordWithMedia()` private helper. The 6vq function `RecordView.toEmbedUiFromRecordView()` is renamed to `toRecordOrUnavailable()` and its return type tightened from `EmbedUi` to `EmbedUi.RecordOrUnavailable` (every value it produces is structurally one of the two; the upcast at the parent dispatch site is implicit since the marker extends `EmbedUi`). Three small wrapper-construction helpers (`toEmbedUiImages`, `toEmbedUiVideo`, `toEmbedUiExternal`) are extracted from the existing inline construction so the parent dispatch and the new media dispatch share one path — eliminates any chance of construction drift (e.g. `aspectRatio` calculation getting updated in one path and not the other). Error contract: malformed quoted record value still degrades to `RecordUnavailable.Unknown` on the record side without dropping the parent post; malformed media (empty video playlist or unknown media variant) falls the whole composition through to `EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")` — the asymmetry is deliberate, since the record side has explicit `view{NotFound,Blocked,Detached}` wire shapes for graceful degradation but the media side does not.
- **MODIFIED capability** `feature-feed-video` — extends the most-visible-video coordinator's `videoBindingFor` helper with two new precedence rules positioned between the existing parent-video and quoted-post-video rules: **(2)** `RecordWithMedia.media is Video` → bind key = parent `post.id`; **(3)** `RecordWithMedia.record is Record` whose `quotedPost.embed is Video` → bind key = `quotedPost.uri`. Final precedence: parent video > recordWithMedia.media video > nested quoted video. Both new rules use the new `EmbedUi.quotedRecord` extension to keep the resolver readable. Visibility math unchanged — still parent feed-item granularity, no `Modifier.onGloballyPositioned` plumbing.
- **MODIFIED capability** `design-system` — adds `PostCardRecordWithMediaEmbed(record: RecordOrUnavailable, media: MediaEmbed, ...)` composable in `:designsystem`. Stacks media-on-top, quoted card below (matches the official Bluesky Android client per the bd issue's UX note). No surrounding `Surface` — the media renders at its native treatment, the quoted card at its own (`PostCardQuotedPost`'s `surfaceContainerLow`); visual grouping comes from adjacency + the parent post body context. Internally just two `when` dispatches forwarding to existing leaf composables — `PostCardImageEmbed`, `PostCardExternalEmbed`, video-via-slot, `PostCardQuotedPost`, `PostCardRecordUnavailable`. Zero new rendering code beyond the dispatch shell. `PostCard.EmbedSlot` gains the corresponding `is EmbedUi.RecordWithMedia ->` arm threading the existing `videoEmbedSlot` / `quotedVideoEmbedSlot` / `callbacks.onExternalEmbedTap` through.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `data-models`: adds `EmbedUi.RecordOrUnavailable` + `EmbedUi.MediaEmbed` marker sealed interfaces, the `EmbedUi.RecordWithMedia` variant, and the `EmbedUi.quotedRecord: QuotedPostUi?` extension property.
- `feature-feed`: `FeedViewPostMapper` dispatches `RecordWithMediaView` to `EmbedUi.RecordWithMedia` via new helpers; refactors 6vq's `toEmbedUiFromRecordView` to `toRecordOrUnavailable` (tighter type); extracts shared wrapper-construction helpers (`toEmbedUiImages`/`Video`/`External`).
- `feature-feed-video`: `videoBindingFor` descends into `RecordWithMedia.media` and `RecordWithMedia.record.quotedPost`; precedence is parent video > media video > nested quoted video.
- `design-system`: `PostCard.EmbedSlot` renders the new `EmbedUi.RecordWithMedia` arm via `PostCardRecordWithMediaEmbed`, which composes the existing leaf composables.

## Impact

- **Non-goals (logged here so they don't slip in):**
  - Tap target on the recordWithMedia card as a whole — same deferral as 6vq's quoted card. Lands in the post-detail-screen follow-up bd issue.
  - Sub-rect visibility for nested videos — coordinator stays at parent feed-item granularity, B-lite for both 6vq and umn.
  - Wrapping `Surface` for visual grouping of media + quote — adjacency is enough per the official client; revisit only if user feedback shows otherwise.
  - Per-`Reason` copy on the unavailable-record-inside-recordWithMedia path — still single-stub per 6vq's design.
  - `recordWithMedia` whose media is itself a `recordWithMedia` — structurally inexpressible (lexicon's `RecordWithMediaViewMediaUnion` doesn't include `recordWithMedia` as a member; the marker `MediaEmbed` enforces this at the type system independently).

- **Code:**
  - Modified: `data/models/.../EmbedUi.kt` — two new marker interfaces, new `RecordWithMedia` variant, marker membership added to existing variants. `quotedRecord` extension property in a sibling file.
  - Modified: `feature/feed/impl/.../data/FeedViewPostMapper.kt` — new `toEmbedUiRecordWithMedia` + `toMediaEmbed` private helpers; `toEmbedUiFromRecordView` renamed to `toRecordOrUnavailable` and return type tightened; three new wrapper helpers (`toEmbedUiImages`/`Video`/`External`) extracted; parent dispatch and media dispatch route through them.
  - Modified: `feature/feed/impl/.../data/FeedViewPostMapperTest.kt` — new tests for the four happy paths (resolved + each media), the three unavailable variants × media combinations, malformed-record-degrades-gracefully, malformed-media-falls-through-to-Unsupported.
  - New: `designsystem/.../component/PostCardRecordWithMediaEmbed.kt`.
  - Modified: `designsystem/.../component/PostCard.kt` — `EmbedSlot`'s `is EmbedUi.RecordWithMedia ->` arm.
  - New: `designsystem/.../screenshotTest/.../PostCardRecordWithMediaEmbedScreenshotTest.kt` (8 baselines: resolved + Images/External, Unavailable + Images/External × {light, dark}).
  - New: `feature/feed/impl/.../screenshotTest/.../PostCardRecordWithMediaEmbedWithVideoScreenshotTest.kt` (2 baselines for the with-video media slot, lives in `:feature:feed:impl` due to the slot importing `PostCardVideoEmbed`).
  - Modified: `feature/feed/impl/.../video/VideoBindingTarget.kt` — `videoBindingFor` extended with the two new precedence rules.
  - Modified: `feature/feed/impl/.../video/MostVisibleVideoTargetTest.kt` — new tests for the two new bind paths.
  - Modified: `feature/feed/impl/.../FeedScreen.kt` — `quotedVideoSlot` builder uses the new `quotedRecord` extension instead of inline chained casts.

- **Dependencies:** none added.

- **Risk:** the marker-interface addition is non-breaking — existing `EmbedUi` consumers' `when` branches stay valid (the new markers don't add variant dispatch obligations, only new interface types). The `EmbedUi.RecordWithMedia` variant addition IS breaking at the type level — every `when (embed)` consumer becomes non-exhaustive at compile time and surfaces the dispatch site that needs an arm. Same intentional surfacing as the previous `Record` + `RecordUnavailable` additions.

- **Roadmap context:** completes the v1.x embed-scope roadmap from `2026-04-25-postcard-embed-scope-v1.md`. After this lands, every `app.bsky.embed.*` lexicon variant has a real renderer (no `Unsupported` chips for current lexicon types). Next embed-related bd issue would be a future lexicon evolution, not a v1.x gap.
