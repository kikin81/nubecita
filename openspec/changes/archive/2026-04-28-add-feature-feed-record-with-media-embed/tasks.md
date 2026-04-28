## 1. Data model in `:data:models`

- [x] 1.1 Add `EmbedUi.RecordOrUnavailable` and `EmbedUi.MediaEmbed` marker sealed interfaces inside `EmbedUi`. Both extend `EmbedUi`. Both contain no abstract members. Add KDoc explaining the recursion-bound role each marker plays for `RecordWithMedia`.
- [x] 1.2 Update existing variants' supertype declarations to implement the relevant marker:
  - `Record` and `RecordUnavailable` → declare `: RecordOrUnavailable` (drops the redundant `: EmbedUi` since the marker extends it).
  - `Images`, `Video`, `External` → declare `: MediaEmbed`.
  No payload changes; pure supertype rewiring. Existing instances remain `equals`-compatible (data classes don't include supertypes in `equals`).
- [x] 1.3 Add `EmbedUi.RecordWithMedia(record: RecordOrUnavailable, media: MediaEmbed)` data class as a new top-level variant on the sealed interface. `@Immutable` inherited from the parent.
- [x] 1.4 Add the `EmbedUi.quotedRecord: QuotedPostUi?` extension property in `:data:models` (new file `EmbedUiExtensions.kt`).
- [x] 1.5 Add temporary `PostCard.EmbedSlot` `when` arm for `EmbedUi.RecordWithMedia` that routes to `PostCardUnsupportedEmbed("app.bsky.embed.recordWithMedia")` (keeps compilation green while the real composable is pending in tasks 4). Update `PostCard.kt` only.
- [x] 1.6 Unit tests (`PostUiTest`):
  - Compile-time exhaustiveness: `when (embed: EmbedUi)` over all 8 concrete variants (Empty, Images, Video, External, Record, RecordUnavailable, RecordWithMedia, Unsupported) without `else`.
  - `quotedRecord` returns the right `QuotedPostUi` for `Record`, the inner one for `RecordWithMedia(record = Record(...), ...)`, and null for every other variant.

## 2. Mapper in `:feature:feed:impl`

- [x] 2.1 Extract three new wrapper-construction helpers from existing inline construction in `toEmbedUi`:
  - `private fun ImagesView.toEmbedUiImages(): EmbedUi.Images`
  - `private fun VideoView.toEmbedUiVideo(): EmbedUi.Video?` (null when playlist is blank)
  - `private fun ExternalView.toEmbedUiExternal(): EmbedUi.External`
  Refactor the parent `toEmbedUi` arms for `is ImagesView`, `is VideoView`, `is ExternalView` to call these. Re-run existing `FeedViewPostMapperTest` — should be green with zero behavior change.
- [x] 2.2 Rename 6vq's `RecordView.toEmbedUiFromRecordView()` to `toRecordOrUnavailable()` and tighten the return type from `EmbedUi` to `EmbedUi.RecordOrUnavailable`. The parent `is RecordView ->` arm continues to compile because `RecordOrUnavailable : EmbedUi`. Pure type-narrowing refactor.
- [x] 2.3 Add `private fun RecordWithMediaViewMediaUnion.toMediaEmbed(): EmbedUi.MediaEmbed?` that dispatches over the lexicon's media union: `ImagesView` → `toEmbedUiImages()`, `VideoView` → `toEmbedUiVideo()` (null falls through), `ExternalView` → `toEmbedUiExternal()`, else → null.
- [x] 2.4 Add `private fun RecordWithMediaView.toEmbedUiRecordWithMedia(): EmbedUi` that:
  - Calls `media.toMediaEmbed()`. If null (malformed media), returns `EmbedUi.Unsupported(typeUri = "app.bsky.embed.recordWithMedia")` — whole composition fails through.
  - Otherwise calls `record.toRecordOrUnavailable()` for the record side.
  - Returns `EmbedUi.RecordWithMedia(record, media)`.
- [x] 2.5 Replace the existing `is RecordWithMediaView -> EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")` arm in `toEmbedUi` with `is RecordWithMediaView -> toEmbedUiRecordWithMedia()`.
- [x] 2.6 Add `FeedViewPostMapperTest` cases:
  - Resolved `viewRecord` + Images media → `RecordWithMedia(Record(quotedPost), Images(items))`. Asserts the inner quotedPost.uri/cid/text/author shape.
  - Resolved `viewRecord` + External media → `RecordWithMedia(Record(...), External(uri, domain, ...))`. Asserts the precomputed domain.
  - Resolved `viewRecord` + Video media → `RecordWithMedia(Record(...), Video(...))`. Asserts the playlistUrl + aspectRatio.
  - `viewNotFound` / `viewBlocked` / `viewDetached` record + Images media → `RecordWithMedia(RecordUnavailable(matching reason), Images(...))`.
  - Malformed quoted-record `value` JSON → `RecordWithMedia(RecordUnavailable.Unknown, ...)`. Parent post still maps non-null.
  - Empty video playlist on media side → `EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")`. Whole-thing fallthrough.
  - Unknown media variant (synthetic `$type = "app.bsky.embed.somethingNew"`) on media side → `EmbedUi.Unsupported("app.bsky.embed.recordWithMedia")`.

## 3. Coordinator extension in `:feature:feed:impl`

- [x] 3.1 Update `videoBindingFor(post: PostUi)` in `VideoBindingTarget.kt` to add the two new precedence rules. Adopted the cleaner approach: parent video → `RecordWithMedia.media is Video` (parent post id) → `quotedRecord?.embed is QuotedEmbedUi.Video` (quoted uri). The `quotedRecord` extension covers both top-level `Record` AND `RecordWithMedia.record-is-Record` in one call.
- [x] 3.2 Extend `MostVisibleVideoTargetTest`:
  - `mostVisibleVideoTarget` integration: media-Video binds with parent id; nested quoted-Video binds with quoted uri when media is non-video; media-Video wins over nested quoted-Video on the same item.
  - `videoBindingFor` standalone: media video / nested quoted video / no videos anywhere / unavailable record + media video — expected outcome for each.

## 4. `PostCardRecordWithMediaEmbed` composable in `:designsystem`

- [x] 4.1 Add `PostCardRecordWithMediaEmbed(record, media, modifier, onExternalMediaTap, videoEmbedSlot, quotedVideoEmbedSlot)` composable in `:designsystem`. Signature per the design-system spec. NO surrounding `Surface`. NO `Modifier.clickable` on the root.
- [x] 4.2 Implement the composable body as a single `Column(modifier = modifier.fillMaxWidth())` containing:
  - `when (media) { Images → PostCardImageEmbed; Video → videoEmbedSlot?.invoke(media); External → PostCardExternalEmbed(... onTap = onExternalMediaTap) }` — exhaustive over `MediaEmbed`, no `else`.
  - 8 dp `Spacer`.
  - `when (record) { Record → PostCardQuotedPost(record.quotedPost, quotedVideoEmbedSlot); RecordUnavailable → PostCardRecordUnavailable(record.reason) }` — exhaustive over `RecordOrUnavailable`, no `else`.
- [x] 4.3 Update `PostCard.EmbedSlot`'s `EmbedUi.RecordWithMedia` arm to invoke the new composable, threading `callbacks.onExternalEmbedTap`, `videoEmbedSlot`, and `quotedVideoEmbedSlot` through. Replaces the temporary `PostCardUnsupportedEmbed` route from task 1.5.
- [x] 4.4 Add `@Preview` composables for `PostCardRecordWithMediaEmbed`: resolved + Images (with light + dark), resolved + External, Unavailable + Images, Unavailable + External.
- [x] 4.5 Add `PostCardRecordWithMediaEmbedScreenshotTest` × {light, dark} for: resolved + Images, resolved + External, Unavailable + Images, Unavailable + External = **8 baselines**. Lives in `:designsystem`'s screenshot-test source set. Video baseline lands in task 4.6.
- [x] 4.6 Add `PostCardRecordWithMediaEmbedWithVideoScreenshotTest` × {light, dark} = **2 baselines** in `:feature:feed:impl`'s screenshot-test source set (because the `videoEmbedSlot` body imports `PostCardVideoEmbed` which is internal to that module). Exercises the resolved-quote + Video media case using the phase-B static-poster overload (inspection-mode-safe).
- [x] 4.7 Add a `PostCard` `@Preview` showing `EmbedUi.RecordWithMedia` (resolved + Images) to exercise the integrated dispatch, plus replace the now-redundant "with unsupported embed (record-with-media)" preview with a generic "somethingNew" Unsupported case (record-with-media is no longer Unsupported).

## 5. FeedScreen wiring in `:feature:feed:impl`

- [x] 5.1 Update `LoadedFeedContent`'s `quotedVideoSlot` builder to consume `post.embed.quotedRecord` instead of inline chained casts. The slot's null-when-no-quoted-post invariant is preserved; the slot now correctly fires for both top-level `EmbedUi.Record` AND `EmbedUi.RecordWithMedia(record = Record(...), ...)` cases.
- [x] 5.2 Verified by inspection + the MostVisibleVideoTargetTest cases (task 3.2): existing `videoSlot` builder keys on `post.id` and the recordWithMedia.media-video case ALSO binds under `post.id` (per `videoBindingFor`'s rule 2), so the slot fires uniformly for both shapes without changes.

## 6. Verification + close-out

- [x] 6.1 `./gradlew :feature:feed:impl:testDebugUnitTest :data:models:testDebugUnitTest` — all mapper + coordinator + model tests green.
- [x] 6.2 `./gradlew :designsystem:validateDebugScreenshotTest :feature:feed:impl:validateDebugScreenshotTest` — all new baselines green (8 in `:designsystem` + 2 in `:feature:feed:impl`).
- [x] 6.3 `./gradlew spotlessCheck lint` — clean.
- [x] 6.4 Compose Compiler stability report (`./gradlew :app:assembleRelease -PcomposeReports=true -PdebugSignedRelease=true`) — `PostCardRecordWithMediaEmbed` confirmed `restartable skippable`. No non-skippable regressions in `:designsystem` or `:feature:feed:impl`.
- [ ] 6.5 Manual smoke on Pixel 10 Pro XL: feed containing each of (resolved-quote + Images / Video / External; RecordUnavailable + Images; recordWithMedia inside a quote → renders as the existing `QuotedEmbedUi.Unsupported` chip per the recursion bound). Visual rendering matches the official Bluesky Android client. **(Device-side; user task.)**
- [ ] 6.6 120 Hz scroll spot check via `dumpsys gfxinfo` over a release-build APK on a 120Hz device, weighted toward recordWithMedia content. Compare against the 6vq baseline (0.22% jank, 95th percentile = 7ms). **(Device-side; user task.)**
