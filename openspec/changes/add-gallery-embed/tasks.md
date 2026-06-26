# Tasks

Grouped to match the beads epic `nubecita-vye3`. Each group names its bd child id. Phase 1 (groups 1–4) is independently shippable.

## 1. Data model — `:data:models` (bd: nubecita-vye3.2)

- [ ] 1.1 Add a sealed supertype `ImageContainerEmbed : MediaEmbed { val items: ImmutableList<ImageUi> }`; make both `EmbedUi.Images` and a new `EmbedUi.Gallery(items, contentWarning)` implement it (lets render/viewer match one branch while staying distinct types).
- [ ] 1.2 Add `QuotedEmbedUi.Gallery(...)` mirroring `QuotedEmbedUi.Images`.
- [ ] 1.3 Add gallery fixtures (5- and 10-image) alongside the existing image fixtures.
- [ ] 1.4 Verify `@Immutable` + `ImmutableList` per `:data:models` conventions.

## 2. Mapper — `:core:feed-mapping` (bd: nubecita-vye3.3)

- [ ] 2.1 Add a `GalleryView.toImageUiList()` helper that unwraps `List<GalleryViewItemsUnion>` → `GalleryViewImage`, reading `thumbnail`/`fullsize`/`alt`/`aspectRatio` (non-null), skipping unknown item variants.
- [ ] 2.2 Add `is GalleryView -> toEmbedUiGallery()` to the top-level `PostViewEmbedUnion.toEmbedUi()`.
- [ ] 2.3 Add `is GalleryView -> QuotedEmbedUi.Gallery(...)` to `RecordWithMediaViewMediaUnion.toQuotedMediaEmbed()` (NOT `RecordViewRecordEmbedsUnion`).
- [ ] 2.4 Unit tests: top-level gallery → `EmbedUi.Gallery`; quoted gallery → `QuotedEmbedUi.Gallery`; unknown item variant skipped; no longer `Unsupported`.

## 3. Render — `:designsystem` (bd: nubecita-vye3.4)

- [ ] 3.1 Match `is ImageContainerEmbed ->` in `PostCard.EmbedSlot` (single branch covering Images + Gallery), dispatching to the existing `PostCardImageEmbed`/`MultiImageCarousel` (no new layout).
- [ ] 3.2 Add `@Preview` fixtures for 5- and 10-image galleries.
- [ ] 3.3 Add gallery screenshot tests mirroring `PostCardImageEmbedScreenshotTest`; generate baselines. Confirm existing Images baselines are untouched.

## 4. Lightbox — `:feature:mediaviewer` (bd: nubecita-vye3.5)

- [ ] 4.1 Change `MediaViewerViewModel`'s guard to `embed is ImageContainerEmbed` (covers Images + Gallery in one check), pulling `.items` from the supertype.
- [ ] 4.2 Confirm gallery tap → viewer navigation reuses the existing `onImageClick(index)` wiring (postUri + index route); no count cap.
- [ ] 4.3 Unit test: gallery embed yields a `Loaded` state across all N images; index clamping holds.

## 5. Image dimensions + posting — `:core:image` + `:core:posting` (bd: nubecita-vye3.7)

- [ ] 5.1 `:core:image` — add image dimensions (width/height) to `PickedImage`, computed via a bounds-only decode (`BitmapFactory.Options.inJustDecodeBounds = true`) off the main thread when picking. (The picker already opens the `Uri` for its MIME type — no extra `EncodedImage`/decode work in `uploadOne`.)
- [ ] 5.2 `:core:posting` — add `alt: String = ""` and `aspectRatio` (from `PickedImage` dimensions) to `ComposerAttachment`.
- [ ] 5.3 Enrich `ComposerEmbedIntent` to carry per-image `(blob, alt, aspectRatio)`.
- [ ] 5.4 Branch `resolveEmbed`: 0 → no embed; 1–4 → `Images` (with `aspectRatio` now populated + `alt`); **≥5** → `Gallery` (`PostEmbedUnion.Gallery`, each `GalleryImage` with `image`+`alt`+`aspectRatio`). Preserve `RecordWithMedia` for the quote+media case at both counts.
- [ ] 5.5 Unit tests: 4 images → Images wire type; 5 images → Gallery wire type; alt + aspectRatio present on records; quote+gallery → RecordWithMedia.

## 6. Composer multi-image + promote/demote + reorder — `:feature:composer` (bd: nubecita-vye3.6)

- [ ] 6.1 Raise `MAX_ATTACHMENTS` 4 → 10; update picker cap (`10 − current`) and add-attachment guard.
- [ ] 6.2 Add `MoveAttachment(from, to)` event + reducer; reorderable attachment-row UX.
- [ ] 6.3 Map attachment list (in order) → `ComposerAttachment` list for posting; post order = list order.
- [ ] 6.4 Unit tests: cap at 10; picker remaining-count; reorder changes order; image-only gallery submits.
- [ ] 6.5 Screenshot test for the multi-image (10) attachment row.

## 7. Alt-text editor + required-alt gate — `:feature:composer` (bd: nubecita-vye3.8)

- [ ] 7.1 **Spike first:** resolve how an adaptive alt-edit route is hosted from the composer's hand-rolled overlay (composer-internal nested presentation vs. MainShell `navState`); document the chosen approach.
- [ ] 7.2 Add an `AltEdit(index)` adaptive route tagged `adaptiveDialog()` (full-screen phone / dialog tablet) showing the image + a text field.
- [ ] 7.3 Add `SetAltText(index, text)` event + reducer writing `alt` onto the attachment.
- [ ] 7.4 Tap thumbnail → open editor; show an "ALT" badge on thumbnails with non-blank alt.
- [ ] 7.5 Gate submit when `attachments.size > 4` and any alt is blank; show a hint naming the requirement. No gate at ≤4. Demotion lifts the gate.
- [ ] 7.6 Unit tests: gate disables/enables on alt completeness at >4; ≤4 never gated; demotion lifts gate; alt persists across promote/demote.
- [ ] 7.7 Screenshot test: alt-edit route (compact + dialog) and the "ALT" badge.

## 8. Wrap-up

- [ ] 8.1 Run `./gradlew spotlessCheck lint testDebugUnitTest` and the touched modules' screenshot validation; fix findings.
- [ ] 8.2 Run `openspec validate add-gallery-embed`.
