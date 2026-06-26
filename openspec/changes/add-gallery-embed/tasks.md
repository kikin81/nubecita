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

- [x] 5.1 `:core:image` — add `ImageDimensionDecoder` (bounds-only `BitmapFactory.Options.inJustDecodeBounds = true` decode of raw bytes → width/height, off the main thread) + `BitmapImageDimensionDecoder` impl + Hilt binding. (Decode at upload from the raw bytes the repo already reads, not pick-time — the picker callback is main-thread.)
- [x] 5.2 `:core:posting` — add `alt: String = ""` to `ComposerAttachment`; `uploadOne` decodes dimensions and returns `UploadedImage(blob, alt, dimensions)`.
- [x] 5.3 Enrich `ComposerEmbedIntent` to carry `List<UploadedImage>` (blob + alt + dimensions).
- [x] 5.4 Branch `resolveEmbed`: 0 → no embed; 1–4 → `Images` (optional `aspectRatio` populated + `alt`); **≥5** → `Gallery` (each `GalleryImage` with `image`+`alt`+required `aspectRatio`, 1:1 fallback). Preserve `RecordWithMedia` (Images/Gallery both implement `RecordWithMediaMediaUnion`).
- [x] 5.5 Unit tests: 4 images → Images wire type; 5 images → Gallery wire type; alt + aspectRatio present on records; quote+gallery → RecordWithMedia.

## 6. Composer multi-image + promote/demote + reorder — `:feature:composer` (bd: nubecita-vye3.6)

- [ ] 6.1 Raise `MAX_ATTACHMENTS` 4 → 10; update picker cap (`10 − current`) and add-attachment guard.
- [ ] 6.2 Add `MoveAttachment(from, to)` event + reducer; reorderable attachment-row UX.
- [ ] 6.3 Map attachment list (in order) → `ComposerAttachment` list for posting; post order = list order.
- [ ] 6.4 Unit tests: cap at 10; picker remaining-count; reorder changes order; image-only gallery submits.
- [ ] 6.5 Screenshot test for the multi-image (10) attachment row.

## 7. Alt-text editor + required-alt gate — `:feature:composer` (bd: nubecita-vye3.8)

(No spike: the composer is already an `adaptiveDialog()` `@MainShell` entry, so the editor is a composer-internal layer that inherits its presentation — see design D6. Also correct the stale "hand-rolled overlay / nubecita-11st" note in CLAUDE.md.)

- [ ] 7.1 `ComposerState.altEditTarget: Int? = null` + events `OpenAltEditor(index)`, `CloseAltEditor`, `SetAltText(index, text)` (+ reducers; SetAltText writes `ComposerAttachment.alt`).
- [ ] 7.2 `AltEditorLayer` composable: `HorizontalPager` over attachments opened at the target index — focused photo + its alt `OutlinedTextField` (value/onValueChange → SetAltText) + back/done; bottom thumbnail filmstrip with ✓ for described, tap to jump.
- [ ] 7.3 Host it in `ComposerScreenContent`: `if (altEditTarget != null) AltEditorLayer(...) else ComposerBody(...)` — inherits the composer's full-screen/dialog presentation.
- [ ] 7.4 Tap an attachment chip → `OpenAltEditor(index)`; add an "ALT" status badge to the chip (described = filled/✓, else outlined). Keep long-press = reorder, X = remove.
- [ ] 7.5 Gate submit when `attachments.size > 4` and any alt is blank; show a hint naming the requirement. No gate at ≤4. Demotion lifts the gate. The filmstrip + chip badges surface which photos still need alt.
- [ ] 7.6 Unit tests: open/close editor; SetAltText updates the right photo; gate disables/enables on completeness at >4; ≤4 never gated; demotion lifts gate; alt persists across reorder.
- [ ] 7.7 Screenshot tests: AltEditorLayer (focused photo + field + filmstrip) and chip ALT-badge states. Feature-module baselines via the `update-baselines` label.

## 8. Wrap-up

- [ ] 8.1 Run `./gradlew spotlessCheck lint testDebugUnitTest` and the touched modules' screenshot validation; fix findings.
- [ ] 8.2 Run `openspec validate add-gallery-embed`.
