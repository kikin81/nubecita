## Context

`app.bsky.embed.gallery` is an official Bluesky embed (multi-image, soft-limit 10 / hard ceiling 20 per lexicon `maxLength`), merged to atproto `main` in PR #4827 and shipped ungated by social-app v1.123.0. The production appview indexes and emits `gallery#view`. The atproto-kotlin SDK on Nubecita's `main` (`io.github.kikin81.atproto` 9.6.0) already carries the generated gallery models — no dependency bump.

Confirmed SDK types (read from generated sources):

| Role | Type | Notes |
|---|---|---|
| View union (top-level) | `PostViewEmbedUnion.GalleryView` → `app.bsky.embed.GalleryView` | |
| View union (quoted media) | `RecordWithMediaViewMediaUnion` contains `GalleryView` | **Not** `RecordViewRecordEmbedsUnion` — it has no gallery variant |
| View payload | `GalleryView { items: List<GalleryViewItemsUnion> }` | items wrapped in a single-variant union |
| View image | `GalleryViewImage { thumbnail: Uri, fullsize: Uri, alt: String, aspectRatio: AspectRatio }` | `aspectRatio` **non-null**; field named `thumbnail` (images uses `thumb`) |
| Record | `app.bsky.embed.Gallery { items: List<GalleryItemsUnion> }` | |
| Record image | `GalleryImage { image: Blob, alt: String, aspectRatio: AspectRatio }` | `aspectRatio` **required** (images uses optional `AtField<AspectRatio>`) |
| Post embed union | `PostEmbedUnion.Gallery` / `.Images` | how the record is attached to `app.bsky.feed.post` |

Current Nubecita state (verified):
- `EmbedUi.Images(items: ImmutableList<ImageUi>)`; `ImageUi(fullsizeUrl, thumbUrl, altText, aspectRatio: Float?)` — already the exact shape a gallery image needs.
- `PostCard` renders multi-image as an **M3 carousel today** (`1 → SingleImage, else → MultiImageCarousel`). There is no 2×2 grid.
- The media viewer's pager already handles N images, but `MediaViewerViewModel` only accepts `embed is EmbedUi.Images`.
- Composer: `MAX_ATTACHMENTS = 4`; `ComposerAttachment(uri, mimeType)` — **no alt UI** (alt hardcoded `""`), **no aspectRatio** computed at upload.
- `DefaultPostingRepository.uploadOne` already re-encodes blobs >1 MB to WebP-lossy (`BLUESKY_BLOB_LIMIT_BYTES`), decoding the bitmap in the process.

## Goals / Non-Goals

**Goals:**
- Render galleries (top-level + quoted) so they stop showing as "unsupported embed".
- Open galleries in the full-screen lightbox across all N images.
- Author 5–10 image galleries with correct interop (Images ≤4 / Gallery 5–10), required alt, drag-reorder, and computed aspect ratio.
- Ship render independently of upload.

**Non-Goals:**
- `getEmbedExternalView` (`nubecita-kpqb`); composer scene-strategy migration (`nubecita-11st`).
- Required alt for `images` (≤4) posts.
- Gallery cap beyond 10.

## Decisions

### D1: Distinct `EmbedUi.Gallery`, rendered via the existing carousel
Add `EmbedUi.Gallery` / `QuotedEmbedUi.Gallery` reusing `ImageUi`, but dispatch them to the **existing** `PostCardImageEmbed` carousel — galleries render identically to images.
- *Why:* keeps the UI model wire-faithful (a gallery stays a gallery — aligns with the "stay close to atproto signals" principle) and keeps upload promote/demote symmetric, while the carousel already handles N so there is no new layout to build.
- *Alternatives:* (a) fold `gallery#view → EmbedUi.Images` — tiniest diff (mapper-only render) but the model lies and any future gallery-specific affordance needs a re-split; (b) adopt social-app's grid≤4/carousel>4 — would change existing Images rendering and force Images screenshot rebaselines for no Nubecita-side benefit. Rejected both.

### D2: Carousel reuse, no grid; shared `ImageContainerEmbed` supertype
`PostCardImageEmbed`/`MultiImageCarousel` is the single render path for both Images and Gallery. No 2×2 grid is introduced; existing Images rendering is untouched (no baseline churn). Because `EmbedUi.Images` and `EmbedUi.Gallery` both hold `items: ImmutableList<ImageUi>` + `contentWarning`, they share a sealed supertype `ImageContainerEmbed : MediaEmbed { val items: ImmutableList<ImageUi> }`. `PostCard.EmbedSlot` and `MediaViewerViewModel` then match `is ImageContainerEmbed` in a **single** branch instead of duplicating the Images/Gallery cases, while the two remain distinct concrete types (D1 wire fidelity is preserved).

### D3: Auto promote/demote is a pure function of `attachments.size`
The embed kind is derived at build time from the attachment count: `0 → no embed` (`AtField.Missing`), `1..4 → Images`, `>= 5 → Gallery`. Posting branches on `>= 5` (not a closed `5..10` range) so that an unexpected count never silently drops the embed; the composer is the sole cap enforcer (`MAX_ATTACHMENTS = 10`, well under the lexicon's hard ceiling of 20). There is **no** persistent "mode" field in `ComposerState` — promote/demote is implicit in the count. Adding a 5th image promotes; removing back to 4 demotes. Per-attachment `alt`/`aspectRatio` live on the attachment and survive promote/demote.
- *Why:* mirrors social-app and avoids an invalid-state surface (no "mode says gallery but only 3 images").

### D4: Required-alt gate is gallery-scoped
The submit affordance is disabled (with a hint) when `attachments.size > 4` and any attachment has blank alt. At ≤4 there is no gate. Demoting a gallery to ≤4 lifts the gate immediately.
- *Why:* the user chose the strongest accessibility stance for galleries, but gating ≤4 image posts on alt would regress today's image flow (which has no alt UI at all). Lexicon requires the `alt` *field* (allows `""`), so the gate is a Nubecita product rule, enforced client-side.

### D5: aspectRatio computed at upload time via a dedicated bounds-only decoder
`aspectRatio` is computed in `uploadOne` from the **raw source bytes** that the repository has already read (`byteSource.read(uri)`), using a new `:core:image` `ImageDimensionDecoder` — a bounds-only decode (`BitmapFactory.Options.inJustDecodeBounds = true`) that allocates **no** pixel buffer. The decoded width/height ride on the internal `UploadedImage` (blob + alt + dimensions); the resolver writes them onto `GalleryImage` (required `AspectRatio`, with a 1:1 fallback if the decode fails) and onto `ImagesImage` (optional `AtField<AspectRatio>`, previously `Missing`) as a free feed-layout improvement.
- *Why a dedicated decoder in `uploadOne`, not pick-time on `PickedImage`:* the picker's result callback runs on the **main thread** (it already does `ContentResolver.getType` per URI there), so decoding bounds for up to 10 images there risks main-thread I/O jank. `uploadOne` runs on the IO dispatcher and already holds the raw bytes, so a dedicated `ImageDimensionDecoder.decode(bytes)` is off-main, allocates nothing, and does **not** rely on `ImageEncoder` (which is pass-through with no decode for images ≤ 1 MB — the concern that ruled out reusing the encoder's decode). Aspect ratio is **scale-invariant**, so measuring the source bytes is correct regardless of any later re-encode/downscale.
- *Impact:* `:core:image` (new `ImageDimensionDecoder` + `BitmapImageDimensionDecoder` + Hilt binding) and `:core:posting` (`ComposerAttachment` gains `alt`; `ComposerEmbedIntent` carries `UploadedImage`; `resolveEmbed` branches Images/Gallery). `PickedImage` is unchanged.

### D6: Alt-text editor is a composer-internal paged layer (Messages-inspired)
Tapping an attachment chip opens a per-photo alt editor **inside the composer's own surface** — `ComposerScreenContent { if (altEditTarget != null) AltEditorLayer(…) else ComposerBody(…) }`, driven by `ComposerState.altEditTarget: Int?`. Because the composer is already an `adaptiveDialog()` `@MainShell` entry (full-screen phone / centered 640dp dialog tablet via `AdaptiveDialogSceneStrategy`), the layer **inherits** that presentation for free — no second nav route, no stacked dialog on tablet.

The editor is a **`HorizontalPager`** over `attachments` opened at the tapped index (Messages-style: one large focused photo + its own alt field), with a **bottom filmstrip** of thumbnails (✓ = described) to jump between photos and see completion at a glance. The pager's current page is local Compose state (terminates at the composable, like `LocalTabReTapSignal`); the alt field uses `value`/`onValueChange` → `SetAltText(index, text)` writing canonical state on `ComposerAttachment.alt` (added in vye3.7). Alt editing has no cursor-aware reducer work, so it doesn't need the `TextFieldState` exception; fall back to a single focused-page `TextFieldState` only if a cursor-jump appears.

Chips show an "ALT" status badge (filled/✓ described, outlined when not). Events: `OpenAltEditor(index)`, `CloseAltEditor`, `SetAltText(index, text)`. The editor is general (images and galleries); only the *gate* (D4) is gallery-scoped.

- *Why not the original separate `AltEdit` adaptiveDialog route:* it would stack a **second** Dialog over the composer's Dialog on tablet (awkward), and route-from-composer is unnecessary. The internal layer is the Messages model (a layer within compose, not a new screen) and is simpler.

### D7: Drag-to-reorder in the attachment row
New `MoveAttachment(from, to)` event over the attachment list; reorderable row UX. Post order = list order.

## Risks / Trade-offs

- **Alt-editor hosting (RESOLVED — no spike).** The earlier concern assumed the composer was still the hand-rolled overlay (per a now-stale CLAUDE.md note); in fact `ComposerNavigationModule` already registers `ComposerRoute` with `adaptiveDialog()` and callers `navState.add(ComposerRoute(...))`. The alt editor is hosted as a **composer-internal layer** (D6) rather than a separate route, so it inherits the composer's adaptive presentation and avoids stacking a second tablet dialog. CLAUDE.md's composer "hand-rolled overlay / nubecita-11st" note is corrected as part of vye3.8.
- **Interop: no graceful fallback** → emitting gallery for ≤4 would hide images on old clients. *Mitigation:* the count branch in D3 emits Images for ≤4; covered by a posting unit test asserting the wire type per count (4 → Images, 5 → Gallery).
- **`GalleryViewItemsUnion` wrapper** → items are a single-variant union, not a direct list like `ImagesView`. *Mitigation:* the mapper unwraps the union variant explicitly; unknown variants are skipped (defensive) rather than crashing.
- **Required-alt friction** → users may abandon a gallery post if every image must be described. *Mitigation:* clear inline hint naming the unmet requirement; gate only at >4 where the user opted into a larger post.
- **Screenshot host quirks** (carousel/indicator rendering) → mirror existing `PostCardImageEmbedScreenshotTest` patterns; reuse known-good fixture approach.

## Migration Plan

No data migration. Phase 1 (render: data-models → feed-mapping → designsystem → mediaviewer) is shippable and merges first behind normal PR review. Phase 2 (upload: composer + posting) follows. No feature flag — render is purely additive (a new union branch); upload changes are gated behind the composer reaching 5 attachments, which is impossible until the cap is raised in the same change.

## Open Questions

- None blocking. The single implementation unknown — alt-edit route hosting under the composer overlay — is captured as the first risk and scoped into `vye3.8` as a spike.
