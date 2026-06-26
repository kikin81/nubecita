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

### D2: Carousel reuse, no grid
`PostCardImageEmbed`/`MultiImageCarousel` is the single render path for both Images and Gallery. No 2×2 grid is introduced; existing Images rendering is untouched (no baseline churn).

### D3: Auto promote/demote is a pure function of `attachments.size`
The embed kind is derived at build time: `size <= 4 → Images`, `5..10 → Gallery`. There is **no** persistent "mode" field in `ComposerState` — promote/demote is implicit in the count. Adding a 5th image promotes; removing back to 4 demotes. Per-attachment `alt`/`aspectRatio` live on the attachment and survive promote/demote.
- *Why:* mirrors social-app and avoids an invalid-state surface (no "mode says gallery but only 3 images").

### D4: Required-alt gate is gallery-scoped
The submit affordance is disabled (with a hint) when `attachments.size > 4` and any attachment has blank alt. At ≤4 there is no gate. Demoting a gallery to ≤4 lifts the gate immediately.
- *Why:* the user chose the strongest accessibility stance for galleries, but gating ≤4 image posts on alt would regress today's image flow (which has no alt UI at all). Lexicon requires the `alt` *field* (allows `""`), so the gate is a Nubecita product rule, enforced client-side.

### D5: aspectRatio computed at upload, populated for both paths
`aspectRatio` is derived from bitmap bounds during `uploadOne` (the bitmap is already decoded there for WebP re-encoding — header-bounds decode is effectively free). It is set on `GalleryImage` (required) and also on `ImagesImage` (currently `AtField.Missing`) as a free feed-layout improvement.
- *Why:* gallery requires it; computing once at the existing decode site avoids a second decode and avoids storing derived data on `ComposerAttachment`.

### D6: Alt-text editor is a full-screen adaptive route
Tap an attachment thumbnail → push an `AltEdit(index)` screen tagged `adaptiveDialog()` (full-screen phone / centered dialog tablet, reusing `AdaptiveDialogSceneStrategy`). Thumbnails show an "ALT" badge once alt is non-blank. `ComposerAttachment` gains `alt: String = ""`; new `SetAltText(index, text)` event. The editor is general (works for images and galleries); only the *gate* (D4) is gallery-scoped.

### D7: Drag-to-reorder in the attachment row
New `MoveAttachment(from, to)` event over the attachment list; reorderable row UX. Post order = list order.

## Risks / Trade-offs

- **Alt-edit route hosting from the composer overlay** → The composer is still the hand-rolled launcher/overlay (CLAUDE.md; scene-strategy migration deferred to `nubecita-11st`), so `navState.add(AltEdit(...))` onto MainShell is not directly available from inside the overlay. *Mitigation:* the upload task resolves hosting explicitly — most likely a composer-internal nested presentation / its own small `NavDisplay` rather than MainShell's `navState`; spike this first in `vye3.8` before building the editor UI.
- **Interop: no graceful fallback** → emitting gallery for ≤4 would hide images on old clients. *Mitigation:* the count branch in D3 emits Images for ≤4; covered by a posting unit test asserting the wire type per count (4 → Images, 5 → Gallery).
- **`GalleryViewItemsUnion` wrapper** → items are a single-variant union, not a direct list like `ImagesView`. *Mitigation:* the mapper unwraps the union variant explicitly; unknown variants are skipped (defensive) rather than crashing.
- **Required-alt friction** → users may abandon a gallery post if every image must be described. *Mitigation:* clear inline hint naming the unmet requirement; gate only at >4 where the user opted into a larger post.
- **Screenshot host quirks** (carousel/indicator rendering) → mirror existing `PostCardImageEmbedScreenshotTest` patterns; reuse known-good fixture approach.

## Migration Plan

No data migration. Phase 1 (render: data-models → feed-mapping → designsystem → mediaviewer) is shippable and merges first behind normal PR review. Phase 2 (upload: composer + posting) follows. No feature flag — render is purely additive (a new union branch); upload changes are gated behind the composer reaching 5 attachments, which is impossible until the cap is raised in the same change.

## Open Questions

- None blocking. The single implementation unknown — alt-edit route hosting under the composer overlay — is captured as the first risk and scoped into `vye3.8` as a spike.
